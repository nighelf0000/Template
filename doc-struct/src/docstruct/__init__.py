"""doc-struct: Word 文档结构识别引擎 SDK。"""

from __future__ import annotations

import datetime
import logging
from pathlib import Path
from typing import Any, Dict, List, Optional, Union, Literal

import docx

from .__version__ import __version__
from .classifier.engine import ClassifierEngine, ClassifierEngineError
from .output.structure_tree import (
    DocumentElement,
    DocumentMeta,
    ElementContent,
    ElementMetadata,
    ElementType,
    StructureTree,
)
from .output.serializer import StructureTreeSerializer
from .rule.models import RuleSet
from .rule.rule_repository import RuleRepository, RuleRepositoryError

logger = logging.getLogger(__name__)


class DocStructSDK:
    """doc-struct SDK 主类。

    封装核心识别流程，提供便捷的调用接口。
    """

    def __init__(
        self,
        ruleset_path: Optional[Union[str, Path]] = None,
        ruleset_name: str = "default",
        debug: bool = False,
    ):
        """初始化 SDK。

        Args:
            ruleset_path: 规则集文件路径，为 None 时使用内置规则集。
            ruleset_name: 规则集名称（当 ruleset_path 为 None 时使用）。
            debug: 是否输出调试信息。
        """
        self._debug = debug
        self._ruleset: Optional[RuleSet] = None
        self._ruleset_name = ruleset_name

        if ruleset_path is not None:
            from .rule.rule_parser import RuleParser
            ruleset_path = Path(ruleset_path)
            if ruleset_path.exists():
                self._ruleset = RuleParser.parse_file(ruleset_path)
                self._ruleset_name = self._ruleset.name
            else:
                raise FileNotFoundError(f"规则集文件不存在: {ruleset_path}")

    def recognize(
        self,
        filepath: Union[str, Path],
        output_format: Literal["structure_tree", "flat_list"] = "structure_tree",
        include_metadata: bool = True,
        include_style_features: bool = False,
    ) -> StructureTree:
        """识别文档结构。

        Args:
            filepath: .docx 文件路径。
            output_format: 输出格式，structure_tree（树形）或 flat_list（扁平列表）。
            include_metadata: 是否包含元数据。
            include_style_features: 是否包含样式特征（调试用）。

        Returns:
            StructureTree 结构树。

        Raises:
            ClassifierEngineError: 识别过程出错。
            FileNotFoundError: 文件不存在。
        """
        path = Path(filepath)
        if not path.exists():
            raise FileNotFoundError(f"文件不存在: {path}")

        engine = ClassifierEngine(
            ruleset=self._ruleset,
            ruleset_name=self._ruleset_name,
            debug=self._debug,
        )

        try:
            tree = engine.recognize(str(path))
            tree.source_file = path.name

            if not include_metadata:
                tree.document_meta = None
                self._strip_metadata(tree.root)

            if not include_style_features:
                self._strip_style_features(tree.root)

            return tree

        except Exception as e:
            raise ClassifierEngineError(f"文档识别失败: {e}") from e

    def recognize_to_json(
        self,
        filepath: Union[str, Path],
        output_file: Optional[Union[str, Path]] = None,
        indent: int = 2,
        **kwargs: Any,
    ) -> str:
        """识别文档结构并输出 JSON 字符串。

        Args:
            filepath: .docx 文件路径。
            output_file: 可选的输出文件路径。
            indent: JSON 缩进空格数。

        Returns:
            JSON 字符串。
        """
        tree = self.recognize(filepath, **kwargs)
        json_str = StructureTreeSerializer.to_json(tree, indent=indent)

        if output_file is not None:
            output_path = Path(output_file)
            output_path.parent.mkdir(parents=True, exist_ok=True)
            with open(output_path, "w", encoding="utf-8") as f:
                f.write(json_str)

        return json_str

    def save_to_api(
        self,
        tree: StructureTree,
        api_url: str,
        template_id: int,
        source_file: str,
        file_size: int,
        source_checksum: str = "",
        internal_token: Optional[str] = None,
    ) -> Dict[str, Any]:
        """将识别结果保存到 Template 后端 API。

        Args:
            tree: 结构树。
            api_url: Template 后端 API 基地址。
            template_id: 模板 ID。
            source_file: 源文件名。
            file_size: 文件大小（字节）。
            source_checksum: 文件 SHA256 校验和。
            internal_token: 内部调用 Token。

        Returns:
            API 响应字典。

        Raises:
            ConnectionError: API 调用失败。
        """
        import json

        import requests

        url = f"{api_url.rstrip('/')}/api/parse-record"
        payload = {
            "templateId": template_id,
            "sourceFile": source_file,
            "sourceChecksum": source_checksum,
            "fileSize": file_size,
            "structureTree": tree.to_dict(),
        }

        headers = {"Content-Type": "application/json"}
        if internal_token:
            headers["X-Internal-Token"] = internal_token

        try:
            resp = requests.post(url, json=payload, headers=headers, timeout=30)
            resp.raise_for_status()
            return resp.json()
        except requests.RequestException as e:
            raise ConnectionError(f"保存解析结果失败: {e}") from e

    def list_rulesets(self) -> List[str]:
        """列出所有可用规则集。"""
        repo = RuleRepository()
        return repo.list_rulesets()

    @staticmethod
    def _strip_metadata(element: DocumentElement) -> None:
        """递归移除元素元数据。"""
        element.metadata = None
        for child in element.children:
            DocStructSDK._strip_metadata(child)

    @staticmethod
    def _strip_style_features(element: DocumentElement) -> None:
        """递归移除样式特征。"""
        if element.metadata and element.metadata.style_features:
            element.metadata.style_features = None
        for child in element.children:
            DocStructSDK._strip_style_features(child)
