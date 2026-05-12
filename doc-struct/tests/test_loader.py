"""测试文档加载器。"""

from __future__ import annotations

from pathlib import Path

import docx
import pytest

from docstruct.loader.document_loader import (
    DocumentLoader,
    FileNotFoundError_,
    InvalidFormatError,
    FileCorruptedError,
)


class TestDocumentLoader:
    """测试 DocumentLoader 的各种场景。"""

    def test_load_file(self, simple_docx_path: Path):
        """测试从文件路径加载。"""
        doc = DocumentLoader.load(simple_docx_path)
        # 验证返回对象有 paragraphs 属性
        assert hasattr(doc, "paragraphs")
        assert len(doc.paragraphs) > 0

    def test_load_bytes(self, simple_docx_bytes: bytes):
        """测试从字节流加载。"""
        doc = DocumentLoader.load(simple_docx_bytes)
        assert hasattr(doc, "paragraphs")
        assert len(doc.paragraphs) > 0

    def test_load_bytes_method(self, simple_docx_path: Path):
        """测试 load_bytes 方法。"""
        data = DocumentLoader.load_bytes(simple_docx_path)
        assert isinstance(data, bytes)
        assert len(data) > 0

    def test_load_nonexistent_file(self):
        """测试不存在的文件应抛出异常。"""
        with pytest.raises(FileNotFoundError_):
            DocumentLoader.load("/path/to/nonexistent/file.docx")

    def test_load_invalid_format(self, tmp_path: Path):
        """测试非 .docx 格式应抛出异常。"""
        txt_file = tmp_path / "test.txt"
        txt_file.write_text("This is not a docx file.")
        with pytest.raises(InvalidFormatError):
            DocumentLoader.load(str(txt_file))

    def test_validate_valid_file(self, simple_docx_path: Path):
        """测试 validate 对有效文件返回 True。"""
        assert DocumentLoader.validate(simple_docx_path) is True

    def test_validate_invalid_file(self, tmp_path: Path):
        """测试 validate 对无效文件返回 False。"""
        txt_file = tmp_path / "test.txt"
        txt_file.write_text("not a docx")
        assert DocumentLoader.validate(str(txt_file)) is False

    def test_validate_nonexistent_file(self):
        """测试 validate 对不存在的文件返回 False。"""
        assert DocumentLoader.validate("/nonexistent.docx") is False

    def test_validate_valid_bytes(self, simple_docx_bytes: bytes):
        """测试 validate 对有效字节流返回 True。"""
        assert DocumentLoader.validate(simple_docx_bytes) is True

    def test_validate_invalid_bytes(self):
        """测试 validate 对无效字节流返回 False。"""
        assert DocumentLoader.validate(b"not a docx") is False

    def test_validate_empty_bytes(self):
        """测试 validate 对空字节流返回 False。"""
        assert DocumentLoader.validate(b"") is False

    def test_load_empty_bytes(self):
        """测试空字节流加载应抛出异常。"""
        with pytest.raises(InvalidFormatError):
            DocumentLoader.load(b"")
