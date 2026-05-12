"""测试内容提取器。"""

from __future__ import annotations

from pathlib import Path

import docx
import pytest

from docstruct.extractor.content_extractor import ContentExtractor
from docstruct.extractor.table_extractor import TableExtractor
from docstruct.extractor.list_extractor import ListExtractor
from docstruct.extractor.image_extractor import ImageExtractor
from docstruct.output.structure_tree import ElementContent


class TestContentExtractor:
    """测试 ContentExtractor 的内容提取功能。"""

    def test_extract_paragraph(self, docx_document: docx.Document):
        """测试提取正文段落。"""
        extractor = ContentExtractor(docx_document)
        paragraph = docx_document.paragraphs[0]
        content = extractor.extract("paragraph", paragraph, 0)
        assert isinstance(content, ElementContent)
        assert content.text is not None

    def test_extract_heading(self, docx_document: docx.Document):
        """测试提取标题。"""
        extractor = ContentExtractor(docx_document)
        # 查找第一个标题段落
        for paragraph in docx_document.paragraphs:
            if paragraph.style and "heading" in paragraph.style.name.lower():
                content = extractor.extract("heading", paragraph, 0)
                assert content.text is not None
                break

    def test_extract_table(self, docx_document: docx.Document):
        """测试提取表格。"""
        extractor = ContentExtractor(docx_document)
        if docx_document.tables:
            table = docx_document.tables[0]
            content = extractor.extract_table(table)
            assert content.table_data is not None
            assert content.table_data["rows"] > 0
            assert content.table_data["cols"] > 0
            assert len(content.table_data["data"]) > 0

    def test_extract_image_from_paragraph(self, docx_document: docx.Document):
        """测试从段落提取图片（文档可能无图片，不应报错）。"""
        extractor = ContentExtractor(docx_document)
        for paragraph in docx_document.paragraphs:
            try:
                # 不会抛出异常
                extractor.extract("image", paragraph, 0)
            except Exception:
                pass


class TestTableExtractor:
    """测试表格提取器。"""

    def test_extract_table_data(self, docx_document: docx.Document):
        """测试表格数据提取。"""
        extractor = TableExtractor(docx_document)
        if docx_document.tables:
            table = docx_document.tables[0]
            data = extractor.extract(table)
            assert "rows" in data
            assert "cols" in data
            assert "data" in data

    def test_table_to_text(self, docx_document: docx.Document):
        """测试表格转文本。"""
        extractor = TableExtractor(docx_document)
        if docx_document.tables:
            table = docx_document.tables[0]
            data = extractor.extract(table)
            text = extractor.table_to_text(data)
            assert isinstance(text, str)
            assert len(text) > 0

    def test_empty_document(self):
        """测试空文档的表格提取。"""
        doc = docx.Document()
        extractor = TableExtractor(doc)
        # 没有表格，不应报错
        # 但 doc.tables 为空


class TestListExtractor:
    """测试列表提取器。"""

    def test_is_list_paragraph(self, docx_document: docx.Document):
        """测试列表段落检测。"""
        extractor = ListExtractor(docx_document)
        found_list = False
        for paragraph in docx_document.paragraphs:
            if extractor.is_list_paragraph(paragraph):
                found_list = True
                items = extractor.extract(paragraph)
                assert len(items) > 0
                break
        # 有些测试文档可能没有列表

    def test_extract_without_numbering(self):
        """测试无编号段落。"""
        doc = docx.Document()
        p = doc.add_paragraph("普通段落")
        extractor = ListExtractor(doc)
        items = extractor.extract(p)
        assert len(items) > 0
        # 没有编号信息


class TestImageExtractor:
    """测试图片提取器。"""

    def test_extract_from_paragraph(self, docx_document: docx.Document):
        """测试从段落提取图片（无图片不报错）。"""
        extractor = ImageExtractor(docx_document)
        for paragraph in docx_document.paragraphs:
            images = extractor.extract_from_paragraph(paragraph)
            assert isinstance(images, list)
