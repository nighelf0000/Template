"""测试 CLI 入口。"""

from __future__ import annotations

import json
from pathlib import Path

import pytest
from click.testing import CliRunner

from docstruct.cli.cli import main


class TestCLI:
    """测试 CLI 的各种命令和参数。"""

    @pytest.fixture
    def runner(self) -> CliRunner:
        return CliRunner()

    def test_version(self, runner: CliRunner):
        """测试 --version 参数。"""
        result = runner.invoke(main, ["--version"])
        assert result.exit_code == 0
        assert "doc-struct" in result.output

    def test_list_rulesets(self, runner: CliRunner):
        """测试 --list-rulesets 参数。"""
        result = runner.invoke(main, ["--list-rulesets"])
        assert result.exit_code == 0
        assert "可用的规则集" in result.output or "没有找到" in result.output

    def test_help(self, runner: CliRunner):
        """测试 --help 参数。"""
        result = runner.invoke(main, ["--help"])
        assert result.exit_code == 0
        assert "doc-struct" in result.output.lower() or "Usage" in result.output

    def test_recognize_simple(self, runner: CliRunner, simple_docx_path: Path):
        """测试单文件识别（输出到 stdout）。"""
        result = runner.invoke(main, [str(simple_docx_path)])
        assert result.exit_code == 0
        # 输出应为有效 JSON
        try:
            data = json.loads(result.output)
            assert "version" in data
            assert "root" in data
        except json.JSONDecodeError:
            pytest.fail("输出不是有效的 JSON")

    def test_recognize_with_output_file(self, runner: CliRunner, simple_docx_path: Path, tmp_path: Path):
        """测试单文件识别并保存到文件。"""
        output_path = tmp_path / "output.json"
        result = runner.invoke(main, [str(simple_docx_path), "-o", str(output_path)])
        assert result.exit_code == 0
        assert output_path.exists()
        data = json.loads(output_path.read_text(encoding="utf-8"))
        assert "root" in data

    def test_recognize_with_ruleset(self, runner: CliRunner, simple_docx_path: Path):
        """测试指定规则集。"""
        result = runner.invoke(main, [str(simple_docx_path), "-r", "default"])
        assert result.exit_code == 0

    def test_recognize_debug_mode(self, runner: CliRunner, simple_docx_path: Path):
        """测试调试模式。"""
        result = runner.invoke(main, [str(simple_docx_path), "--debug"])
        assert result.exit_code == 0

    def test_no_metadata(self, runner: CliRunner, simple_docx_path: Path):
        """测试 --no-metadata 参数。"""
        result = runner.invoke(main, [str(simple_docx_path), "--no-metadata"])
        assert result.exit_code == 0

    def test_nonexistent_file(self, runner: CliRunner):
        """测试不存在的文件。"""
        result = runner.invoke(main, ["/nonexistent/file.docx"])
        assert result.exit_code != 0

    def test_train_mode_missing_params(self, runner: CliRunner):
        """测试训练模式缺少参数。"""
        result = runner.invoke(main, ["--mode", "train"])
        assert result.exit_code != 0
        assert "需要" in result.output or "--train-id" in result.output

    def test_train_mode_with_params(self, runner: CliRunner):
        """测试训练模式参数（不实际连接 API）。"""
        result = runner.invoke(main, [
            "--mode", "train",
            "--train-id", "1",
            "--api-url", "http://localhost:8080",
        ])
        # 会尝试连接 API，预期失败
        assert result.exit_code != 0
