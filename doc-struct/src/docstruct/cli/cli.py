"""CLI 入口 — 命令行接口（click 框架）。

支持双模式：
  1. 单文件识别模式: python -m docstruct input.docx [OPTIONS]
  2. 批量训练模式: python -m docstruct --mode train --train-id ID --api-url URL [--ruleset NAME]
"""

from __future__ import annotations

import hashlib
import io
import json
import logging
import sys
import time
from pathlib import Path
from typing import Any, Dict, List, Optional

import click

from .. import DocStructSDK
from ..__version__ import __version__
from ..classifier.engine import ClassifierEngine
from ..output.serializer import StructureTreeSerializer
from ..rule.rule_repository import RuleRepository

logger = logging.getLogger(__name__)

# 错误码
EXIT_SUCCESS = 0
EXIT_FILE_NOT_FOUND = 1
EXIT_INVALID_FORMAT = 2
EXIT_FILE_CORRUPTED = 3
EXIT_RULESET_NOT_FOUND = 4
EXIT_RULESET_INVALID = 5
EXIT_PARSE_ERROR = 6
EXIT_INTERNAL_ERROR = 7


@click.command()
@click.argument("input_file", required=False)
@click.option("-o", "--output", type=click.Path(), help="输出 JSON 文件路径（默认 stdout）")
@click.option("-r", "--ruleset", default="default", help="规则集名称 (default|academic|technical)")
@click.option("-f", "--format", "output_format", type=click.Choice(["structure_tree", "flat_list"]),
              default="structure_tree", help="输出格式")
@click.option("--no-metadata", is_flag=True, help="不输出元数据")
@click.option("--debug", is_flag=True, help="输出调试信息（包含命中规则、置信度）")
@click.option("-l", "--list-rulesets", is_flag=True, help="列出所有可用规则集")
@click.option("-v", "--version", is_flag=True, help="显示版本号")
@click.option("--mode", type=click.Choice(["file", "train"]), default="file",
              help="运行模式: file（单文件识别，默认）, train（批量训练）")
@click.option("--train-id", type=int, help="训练任务 ID（train 模式下必填）")
@click.option("--api-url", help="Template 后端 API 基地址")
@click.option("--file-id", type=int, help="文件 ID（单文件 API 模式使用）")
@click.option("--template-id", type=int, help="模板 ID")
@click.option("--internal-token", help="内部回调认证 Token（用于 /progress 和 /status 接口）")
def main(
    input_file: Optional[str] = None,
    output: Optional[str] = None,
    ruleset: str = "default",
    output_format: str = "structure_tree",
    no_metadata: bool = False,
    debug: bool = False,
    list_rulesets: bool = False,
    version: bool = False,
    mode: str = "file",
    train_id: Optional[int] = None,
    api_url: Optional[str] = None,
    file_id: Optional[int] = None,
    template_id: Optional[int] = None,
    internal_token: Optional[str] = None,
) -> None:
    """doc-struct: Word 文档结构识别引擎。

    输入 .docx 文件，识别文档结构，输出 JSON 结构树。
    """
    if version:
        click.echo(f"doc-struct v{__version__}")
        sys.exit(EXIT_SUCCESS)

    if list_rulesets:
        _list_rulesets()
        sys.exit(EXIT_SUCCESS)

    if mode == "train":
        if not train_id:
            click.echo("错误: train 模式需要 --train-id 参数", err=True)
            sys.exit(EXIT_INTERNAL_ERROR)
        if not api_url:
            click.echo("错误: train 模式需要 --api-url 参数", err=True)
            sys.exit(EXIT_INTERNAL_ERROR)
        _run_train_mode(train_id=train_id, api_url=api_url, ruleset_name=ruleset, debug=debug, internal_token=internal_token)
        sys.exit(EXIT_SUCCESS)

    # 单文件模式
    if not input_file:
        click.echo(ctx.get_help())
        sys.exit(EXIT_SUCCESS)

    try:
        sdk = DocStructSDK(ruleset_name=ruleset, debug=debug)

        tree = sdk.recognize(
            input_file,
            output_format=output_format,  # type: ignore[arg-type]
            include_metadata=not no_metadata,
            include_style_features=debug,
        )

        json_output = StructureTreeSerializer.to_json(tree)

        if output:
            Path(output).write_text(json_output, encoding="utf-8")
            click.echo(f"结果已保存到: {output}")
        else:
            click.echo(json_output)

        sys.exit(EXIT_SUCCESS)

    except FileNotFoundError as e:
        click.echo(f"错误: {e}", err=True)
        sys.exit(EXIT_FILE_NOT_FOUND)
    except Exception as e:
        click.echo(f"错误: {e}", err=True)
        if debug:
            import traceback
            traceback.print_exc()
        sys.exit(EXIT_INTERNAL_ERROR)


def _list_rulesets() -> None:
    """列出所有可用的规则集。"""
    repo = RuleRepository()
    names = repo.list_rulesets()
    if names:
        click.echo("可用的规则集:")
        for name in names:
            try:
                ruleset = repo.load(name)
                click.echo(f"  - {name}: {ruleset.description or '无描述'}")
            except Exception:
                click.echo(f"  - {name}")
    else:
        click.echo("没有找到规则集")


def _run_train_mode(
    train_id: int,
    api_url: str,
    ruleset_name: str = "default",
    debug: bool = False,
    internal_token: Optional[str] = None,
) -> None:
    """批量训练模式入口。

    通过 REST API 从 Java 后端获取文件列表，逐个识别并回调进度。
    """
    import requests

    sdk = DocStructSDK(ruleset_name=ruleset_name, debug=debug)
    base_url = api_url.rstrip("/")

    try:
        # 1. 获取训练任务信息
        click.echo(f"获取训练任务信息: train_id={train_id}")
        task_resp = requests.get(
            f"{base_url}/api/train-task/{train_id}",
            timeout=30,
        )
        task_resp.raise_for_status()
        task_data = task_resp.json().get("data", task_resp.json())
        template_id = task_data.get("templateId")

        if not template_id:
            click.echo("错误: 训练任务中未找到 templateId", err=True)
            _update_status(base_url, train_id, "FAILED", "未找到 templateId")
            sys.exit(EXIT_PARSE_ERROR)

        # 2. 获取训练文件列表
        click.echo(f"获取训练文件列表: template_id={template_id}")
        file_resp = requests.get(
            f"{base_url}/api/train-file/list",
            params={"templateId": template_id, "page": 1, "size": 100},
            timeout=30,
        )
        file_resp.raise_for_status()
        file_data = file_resp.json()
        file_list = []

        # 处理分页响应格式
        if isinstance(file_data, dict):
            data = file_data.get("data", file_data)
            if isinstance(data, dict):
                file_list = data.get("records", data.get("list", []))
            elif isinstance(data, list):
                file_list = data
        elif isinstance(file_data, list):
            file_list = file_data

        if not file_list:
            click.echo("没有训练文件，标记任务失败", err=True)
            _update_status(base_url, train_id, "FAILED", "没有训练文件", internal_token)
            sys.exit(EXIT_PARSE_ERROR)

        total_files = len(file_list)
        click.echo(f"找到 {total_files} 个训练文件")

        # 3. 更新状态为 RUNNING
        _update_progress(base_url, train_id, 0, 0, total_files, "开始训练...", internal_token)
        _update_status(base_url, train_id, "RUNNING", None, internal_token)

        # 4. 逐个处理文件
        success_count = 0
        error_files: List[str] = []
        first_error: Optional[str] = None

        for idx, file_info in enumerate(file_list):
            file_id = file_info.get("id")
            file_name = file_info.get("originalName", f"file_{file_id}")
            file_size = file_info.get("originalSize", 0)

            click.echo(f"[{idx + 1}/{total_files}] 处理: {file_name}")

            try:
                # 4a. 下载文件
                raw_resp = requests.get(
                    f"{base_url}/api/train-file/{file_id}/raw",
                    timeout=60,
                )
                raw_resp.raise_for_status()
                file_bytes = raw_resp.content

                # 4b. 计算 SHA256
                sha256_hash = hashlib.sha256(file_bytes).hexdigest()

                # 4c. 识别文档结构
                # 保存到临时文件用于识别
                import tempfile
                with tempfile.NamedTemporaryFile(suffix=".docx", delete=False) as tmp:
                    tmp.write(file_bytes)
                    tmp_path = tmp.name

                try:
                    tree = sdk.recognize(tmp_path)
                    tree.source_file = file_name
                finally:
                    Path(tmp_path).unlink(missing_ok=True)

                # 4d. 保存解析结果到 API
                parse_payload = {
                    "templateId": template_id,
                    "sourceFile": file_name,
                    "sourceChecksum": sha256_hash,
                    "fileSize": file_size,
                    "structureTree": tree.to_dict(),
                }

                parse_resp = requests.post(
                    f"{base_url}/api/parse-record",
                    json=parse_payload,
                    headers={"Content-Type": "application/json"},
                    timeout=30,
                )
                parse_resp.raise_for_status()

                success_count += 1
                click.echo(f"  -> 完成: {file_name}")

            except requests.RequestException as e:
                err_msg = f"网络错误 (文件 {file_name}): {e}"
                click.echo(f"  -> {err_msg}", err=True)
                error_files.append(file_name)
                if first_error is None:
                    first_error = str(e)

            except Exception as e:
                err_msg = f"解析失败 (文件 {file_name}): {e}"
                click.echo(f"  -> {err_msg}", err=True)
                error_files.append(file_name)
                if first_error is None:
                    first_error = str(e)

            # 4e. 更新进度
            progress = int((idx + 1) / total_files * 100)
            _update_progress(
                base_url, train_id, progress, idx + 1, total_files,
                f"正在处理: {file_name}",
                internal_token,
            )

        # 5. 全部完成
        if error_files:
            error_msg = f"{len(error_files)}/{total_files} 个文件处理失败"
            if first_error:
                error_msg += f"，首次错误: {first_error}"
            click.echo(f"训练完成，但有 {len(error_files)} 个文件失败")
            _update_status(base_url, train_id, "FAILED", error_msg, internal_token)
        else:
            click.echo("训练全部完成")
            _update_status(base_url, train_id, "SUCCESS", None, internal_token)

    except requests.RequestException as e:
        click.echo(f"致命网络错误: {e}", err=True)
        try:
            _update_status(base_url, train_id, "FAILED", f"网络错误: {e}", internal_token)
        except Exception:
            pass
        sys.exit(EXIT_INTERNAL_ERROR)

    except Exception as e:
        click.echo(f"训练过程出错: {e}", err=True)
        try:
            _update_status(base_url, train_id, "FAILED", str(e), internal_token)
        except Exception:
            pass
        if debug:
            import traceback
            traceback.print_exc()
        sys.exit(EXIT_INTERNAL_ERROR)


def _update_progress(
    api_url: str, train_id: int,
    progress: int, current_file: int, total_files: int,
    message: str,
    internal_token: Optional[str] = None,
) -> None:
    """更新训练进度（调用 Java 后端 API）。"""
    import requests

    headers = {"Content-Type": "application/json"}
    if internal_token:
        headers["X-Internal-Token"] = internal_token

    try:
        resp = requests.put(
            f"{api_url}/api/train-task/{train_id}/progress",
            json={
                "progress": progress,
                "currentFile": current_file,
                "totalFiles": total_files,
                "message": message,
            },
            headers=headers,
            timeout=10,
        )
        resp.raise_for_status()
    except requests.RequestException as e:
        click.echo(f"进度回调失败: {e}", err=True)


def _update_status(
    api_url: str, train_id: int,
    status: str, error_message: Optional[str],
    internal_token: Optional[str] = None,
) -> None:
    """更新训练状态（调用 Java 后端 API）。"""
    import requests

    headers = {"Content-Type": "application/json"}
    if internal_token:
        headers["X-Internal-Token"] = internal_token

    try:
        body: Dict[str, Any] = {"status": status}
        if error_message:
            body["errorMessage"] = error_message
        resp = requests.put(
            f"{api_url}/api/train-task/{train_id}/status",
            json=body,
            headers=headers,
            timeout=10,
        )
        resp.raise_for_status()
    except requests.RequestException as e:
        click.echo(f"状态回调失败: {e}", err=True)


if __name__ == "__main__":
    main()
