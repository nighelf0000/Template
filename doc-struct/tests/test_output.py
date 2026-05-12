"""测试输出模块（结构树数据模型和序列化）。"""

from __future__ import annotations

import json
from pathlib import Path

import pytest

from docstruct.output.structure_tree import (
    DocumentElement,
    DocumentMeta,
    ElementContent,
    ElementMetadata,
    ElementType,
    StructureTree,
)
from docstruct.output.serializer import StructureTreeSerializer


class TestStructureTree:
    """测试 StructureTree 数据模型。"""

    def test_document_element_creation(self):
        """测试创建 DocumentElement。"""
        elem = DocumentElement(
            id="elem_0000",
            type="heading",
            level=1,
            content=ElementContent(text="第一章"),
            metadata=ElementMetadata(
                position={"index": 0},
                style_name="Heading 1",
            ),
            confidence=0.95,
        )
        assert elem.id == "elem_0000"
        assert elem.type == "heading"
        assert elem.level == 1
        assert elem.content.text == "第一章"
        assert elem.confidence == 0.95

    def test_document_element_to_dict(self):
        """测试 DocumentElement 的 to_dict 方法。"""
        elem = DocumentElement(
            id="elem_0000",
            type="heading",
            level=1,
            content=ElementContent(text="第一章"),
            confidence=0.95,
        )
        d = elem.to_dict()
        assert d["id"] == "elem_0000"
        assert d["type"] == "heading"
        assert d["level"] == 1
        assert d["content"]["text"] == "第一章"
        assert d["confidence"] == 0.95

    def test_document_element_with_children(self):
        """测试带子元素的 DocumentElement。"""
        parent = DocumentElement(id="root", type="document")
        child = DocumentElement(
            id="child_0", type="paragraph",
            content=ElementContent(text="子元素"),
        )
        parent.children.append(child)
        d = parent.to_dict()
        assert len(d["children"]) == 1
        assert d["children"][0]["id"] == "child_0"

    def test_document_meta_creation(self):
        """测试创建 DocumentMeta。"""
        meta = DocumentMeta(
            page_count=10,
            paragraph_count=100,
            table_count=3,
            image_count=5,
            author="Test Author",
            word_count=5000,
        )
        assert meta.page_count == 10
        assert meta.paragraph_count == 100
        assert meta.word_count == 5000

    def test_document_meta_to_dict(self):
        """测试 DocumentMeta 的 to_dict 方法。"""
        meta = DocumentMeta(
            page_count=10,
            paragraph_count=100,
            table_count=3,
            image_count=5,
            word_count=5000,
            title="测试文档",
        )
        d = meta.to_dict()
        assert d["page_count"] == 10
        assert d["paragraph_count"] == 100
        assert d["title"] == "测试文档"

    def test_structure_tree_creation(self):
        """测试创建 StructureTree。"""
        tree = StructureTree(
            version="1.0.0",
            generated_at="2026-05-12T00:00:00",
            source_file="test.docx",
            document_meta=DocumentMeta(paragraph_count=10),
            root=DocumentElement(id="root", type="document"),
        )
        assert tree.version == "1.0.0"
        assert tree.source_file == "test.docx"
        assert tree.document_meta.paragraph_count == 10

    def test_structure_tree_to_dict(self):
        """测试 StructureTree 的 to_dict 方法。"""
        tree = StructureTree(
            version="1.0.0",
            generated_at="2026-05-12T00:00:00",
            source_file="test.docx",
            root=DocumentElement(id="root", type="document"),
            flat_index={"root": "document"},
        )
        d = tree.to_dict()
        assert d["version"] == "1.0.0"
        assert d["flat_index"]["root"] == "document"

    def test_element_type_values(self):
        """测试 ElementType 枚举值。"""
        assert ElementType.DOCUMENT.value == "document"
        assert ElementType.HEADING.value == "heading"
        assert ElementType.PARAGRAPH.value == "paragraph"
        assert ElementType.TABLE.value == "table"
        assert ElementType.IMAGE.value == "image"
        assert ElementType.LIST_ORDERED.value == "list_ordered"
        assert ElementType.LIST_UNORDERED.value == "list_unordered"

    def test_element_content_defaults(self):
        """测试 ElementContent 默认值。"""
        content = ElementContent()
        assert content.text is None
        assert content.rich_text is None
        assert content.table_data is None


class TestStructureTreeSerializer:
    """测试 StructureTreeSerializer。"""

    def test_serialize_to_json(self):
        """测试序列化为 JSON。"""
        tree = StructureTree(
            version="1.0.0",
            generated_at="2026-05-12T00:00:00",
            source_file="test.docx",
            root=DocumentElement(id="root", type="document"),
        )
        json_str = StructureTreeSerializer.to_json(tree)
        data = json.loads(json_str)
        assert data["version"] == "1.0.0"
        assert data["root"]["id"] == "root"

    def test_serialize_to_file(self, tmp_path: Path):
        """测试序列化到文件。"""
        tree = StructureTree(
            version="1.0.0",
            generated_at="2026-05-12T00:00:00",
            source_file="test.docx",
            root=DocumentElement(id="root", type="document"),
        )
        filepath = tmp_path / "output.json"
        StructureTreeSerializer.to_file(tree, str(filepath))
        assert filepath.exists()
        data = json.loads(filepath.read_text(encoding="utf-8"))
        assert data["version"] == "1.0.0"

    def test_deserialize_from_dict(self):
        """测试从字典反序列化。"""
        data = {
            "version": "1.0.0",
            "generated_at": "2026-05-12T00:00:00",
            "source_file": "test.docx",
            "root": {
                "id": "root",
                "type": "document",
                "content": None,
                "metadata": None,
                "children": [
                    {
                        "id": "h1",
                        "type": "heading",
                        "level": 1,
                        "content": {"text": "第一章"},
                        "confidence": 0.95,
                    },
                ],
                "confidence": 1.0,
            },
            "flat_index": {"root": "document", "h1": "heading"},
        }
        tree = StructureTreeSerializer.from_dict(data)
        assert tree.version == "1.0.0"
        assert tree.root is not None
        assert len(tree.root.children) == 1
        assert tree.root.children[0].type == "heading"
        assert tree.root.children[0].level == 1
