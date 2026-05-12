"""测试分类器引擎。"""

from __future__ import annotations

from pathlib import Path

import pytest

from docstruct.classifier.engine import ClassifierEngine
from docstruct.classifier.context import ContextCorrector
from docstruct.output.structure_tree import (
    DocumentElement,
    ElementContent,
    ElementMetadata,
    StructureTree,
)
from docstruct.rule.models import RuleSet, Rule, Condition
from docstruct.loader.document_loader import DocumentLoader


class TestClassifierEngine:
    """测试分类器引擎的整体流程。"""

    def test_recognize_returns_structure_tree(self, simple_docx_path: Path):
        """测试 recognize 返回 StructureTree。"""
        engine = ClassifierEngine(ruleset_name="default")
        tree = engine.recognize(str(simple_docx_path))
        assert isinstance(tree, StructureTree)
        assert tree.root is not None
        assert tree.root.type == "document"

    def test_recognize_has_children(self, simple_docx_path: Path):
        """测试识别结果包含子元素。"""
        engine = ClassifierEngine(ruleset_name="default")
        tree = engine.recognize(str(simple_docx_path))
        assert len(tree.root.children) > 0

    def test_recognize_with_custom_ruleset(self, simple_docx_path: Path, default_ruleset_path: Path):
        """测试使用自定义规则集。"""
        from docstruct.rule.rule_parser import RuleParser
        ruleset = RuleParser.parse_file(default_ruleset_path)
        engine = ClassifierEngine(ruleset=ruleset)
        tree = engine.recognize(str(simple_docx_path))
        assert tree.root is not None

    def test_recognize_fails_on_nonexistent_file(self):
        """测试不存在的文件应抛出异常。"""
        engine = ClassifierEngine(ruleset_name="default")
        with pytest.raises(Exception):
            engine.recognize("/nonexistent/file.docx")

    def test_recognize_flat_index(self, simple_docx_path: Path):
        """测试 flat_index 包含元素。"""
        engine = ClassifierEngine(ruleset_name="default")
        tree = engine.recognize(str(simple_docx_path))
        assert len(tree.flat_index) > 0
        # 所有根节点子元素应在 flat_index 中
        for child in tree.root.children:
            assert child.id in tree.flat_index


class TestContextCorrector:
    """测试上下文矫正器。"""

    def test_merge_consecutive_paragraphs(self):
        """测试合并连续正文段落。"""
        corrector = ContextCorrector()
        elements = [
            DocumentElement(id="h1", type="heading", level=1,
                            content=ElementContent(text="标题")),
            DocumentElement(id="p1", type="paragraph",
                            content=ElementContent(text="第一段"),
                            metadata=ElementMetadata(style_name="Normal")),
            DocumentElement(id="p2", type="paragraph",
                            content=ElementContent(text="第二段"),
                            metadata=ElementMetadata(style_name="Normal")),
            DocumentElement(id="p3", type="paragraph",
                            content=ElementContent(text="第三段"),
                            metadata=ElementMetadata(style_name="Normal")),
        ]
        corrected = corrector.correct(elements)
        # 正文段落应被合并
        assert len(corrected) < len(elements)

    def test_do_not_merge_different_types(self):
        """测试不同类型不合并。"""
        corrector = ContextCorrector()
        elements = [
            DocumentElement(id="h1", type="heading", level=1,
                            content=ElementContent(text="标题")),
            DocumentElement(id="p1", type="paragraph",
                            content=ElementContent(text="一段正文")),
        ]
        corrected = corrector.correct(elements)
        assert len(corrected) == 2

    def test_empty_elements(self):
        """测试空元素列表。"""
        corrector = ContextCorrector()
        corrected = corrector.correct([])
        assert corrected == []

    def test_single_element(self):
        """测试单个元素。"""
        corrector = ContextCorrector()
        elements = [
            DocumentElement(id="p1", type="paragraph",
                            content=ElementContent(text="只有一个段落")),
        ]
        corrected = corrector.correct(elements)
        assert len(corrected) == 1
