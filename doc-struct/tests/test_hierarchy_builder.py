"""测试层级构建器。"""

from __future__ import annotations

from typing import List

import pytest

from docstruct.builder.hierarchy_builder import HierarchyBuilder
from docstruct.output.structure_tree import (
    DocumentElement,
    DocumentMeta,
    ElementContent,
    StructureTree,
)


class TestHierarchyBuilder:
    """测试 HierarchyBuilder 的层级构建功能。"""

    @pytest.fixture
    def builder(self) -> HierarchyBuilder:
        return HierarchyBuilder()

    @pytest.fixture
    def doc_meta(self) -> DocumentMeta:
        return DocumentMeta(paragraph_count=5)

    def test_build_simple_hierarchy(self, builder: HierarchyBuilder, doc_meta: DocumentMeta):
        """测试基本层级构建。"""
        elements = [
            DocumentElement(id="h1", type="heading", level=1,
                            content=ElementContent(text="第一章")),
            DocumentElement(id="p1", type="paragraph",
                            content=ElementContent(text="正文1")),
            DocumentElement(id="h2", type="heading", level=2,
                            content=ElementContent(text="1.1 小节")),
            DocumentElement(id="p2", type="paragraph",
                            content=ElementContent(text="正文2")),
        ]
        tree = builder.build(elements, doc_meta)
        root = tree.root
        assert len(root.children) == 1  # 只有 h1 是根的直接子元素
        assert root.children[0].type == "heading"
        assert root.children[0].level == 1
        # h1 下有两个子元素：p1 和 h2
        assert len(root.children[0].children) == 2

    def test_build_multiple_top_level_headings(
        self, builder: HierarchyBuilder, doc_meta: DocumentMeta
    ):
        """测试多个顶级标题。"""
        elements = [
            DocumentElement(id="h1", type="heading", level=1,
                            content=ElementContent(text="第一章")),
            DocumentElement(id="p1", type="paragraph",
                            content=ElementContent(text="正文1")),
            DocumentElement(id="h2", type="heading", level=1,
                            content=ElementContent(text="第二章")),
            DocumentElement(id="p2", type="paragraph",
                            content=ElementContent(text="正文2")),
        ]
        tree = builder.build(elements, doc_meta)
        assert len(tree.root.children) == 2  # 两个一级标题

    def test_build_nested_headings(self, builder: HierarchyBuilder, doc_meta: DocumentMeta):
        """测试多级标题嵌套。"""
        elements = [
            DocumentElement(id="h1", type="heading", level=1,
                            content=ElementContent(text="第1章")),
            DocumentElement(id="h2", type="heading", level=2,
                            content=ElementContent(text="1.1")),
            DocumentElement(id="h3", type="heading", level=3,
                            content=ElementContent(text="1.1.1")),
            DocumentElement(id="p1", type="paragraph",
                            content=ElementContent(text="内容")),
        ]
        tree = builder.build(elements, doc_meta)
        chapter = tree.root.children[0]
        assert chapter.type == "heading"
        assert chapter.level == 1
        section = chapter.children[0]
        assert section.type == "heading"
        assert section.level == 2
        subsection = section.children[0]
        assert subsection.type == "heading"
        assert subsection.level == 3

    def test_build_flat_index(self, builder: HierarchyBuilder, doc_meta: DocumentMeta):
        """测试 flat_index 构建。"""
        elements = [
            DocumentElement(id="h1", type="heading", level=1),
            DocumentElement(id="p1", type="paragraph"),
        ]
        tree = builder.build(elements, doc_meta)
        assert "h1" in tree.flat_index
        assert "p1" in tree.flat_index
        assert tree.flat_index["h1"] == "heading"

    def test_build_empty_elements(self, builder: HierarchyBuilder, doc_meta: DocumentMeta):
        """测试空元素列表。"""
        tree = builder.build([], doc_meta)
        assert tree.root is not None
        assert len(tree.root.children) == 0

    def test_build_paragraph_attached_to_nearest_heading(
        self, builder: HierarchyBuilder, doc_meta: DocumentMeta
    ):
        """测试正文段落归属到最近标题。"""
        elements = [
            DocumentElement(id="h1", type="heading", level=1,
                            content=ElementContent(text="第1章")),
            DocumentElement(id="p1", type="paragraph",
                            content=ElementContent(text="正文")),
            DocumentElement(id="h2", type="heading", level=2,
                            content=ElementContent(text="1.1")),
            DocumentElement(id="p2", type="paragraph",
                            content=ElementContent(text="正文2")),
        ]
        tree = builder.build(elements, doc_meta)
        # p1 应位于 h1 下，p2 应位于 h2 下
        chapter = tree.root.children[0]
        assert chapter.children[0] is elements[1]  # p1 在 h1 下
        section = chapter.children[1]
        assert section.children[0] is elements[3]  # p2 在 h2 下


class TestHierarchyBuilderEdgeCases:
    """测试边界情况。"""

    def test_all_paragraphs_no_headings(self):
        """测试无标题的全段落文档。"""
        builder = HierarchyBuilder()
        meta = DocumentMeta(paragraph_count=3)
        elements = [
            DocumentElement(id="p1", type="paragraph",
                            content=ElementContent(text="段1")),
            DocumentElement(id="p2", type="paragraph",
                            content=ElementContent(text="段2")),
        ]
        tree = builder.build(elements, meta)
        assert len(tree.root.children) == 2

    def test_all_headings_no_paragraphs(self):
        """测试无正文的全标题文档。"""
        builder = HierarchyBuilder()
        meta = DocumentMeta(paragraph_count=3)
        elements = [
            DocumentElement(id="h1", type="heading", level=1),
            DocumentElement(id="h2", type="heading", level=2),
            DocumentElement(id="h3", type="heading", level=3),
        ]
        tree = builder.build(elements, meta)
        assert len(tree.root.children) == 1
        assert len(tree.root.children[0].children) == 1
        assert len(tree.root.children[0].children[0].children) == 1

    def test_heading_level_gap(self):
        """测试标题级别跳跃（h1 直接到 h3）。"""
        builder = HierarchyBuilder()
        meta = DocumentMeta()
        elements = [
            DocumentElement(id="h1", type="heading", level=1),
            DocumentElement(id="h3", type="heading", level=3),
            DocumentElement(id="p1", type="paragraph",
                            content=ElementContent(text="正文")),
        ]
        tree = builder.build(elements, meta)
        # h3 应成为 h1 的子元素
        assert len(tree.root.children) == 1
        chapter = tree.root.children[0]
        # h3 是 h1 的子元素
        assert chapter.children[0].id == "h3"
