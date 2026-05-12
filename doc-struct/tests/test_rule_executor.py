"""测试规则执行器。"""

from __future__ import annotations

from pathlib import Path

import pytest

from docstruct.rule.models import Condition, ClassificationResult, Rule, RuleSet
from docstruct.rule.rule_executor import RuleExecutor
from docstruct.rule.rule_parser import RuleParser
from docstruct.style.feature import StyleFeature


class TestRuleExecutor:
    """测试 RuleExecutor 的规则匹配功能。"""

    @pytest.fixture
    def basic_ruleset(self) -> RuleSet:
        """创建基础测试规则集。"""
        return RuleSet(
            name="test",
            version="1.0",
            description="测试规则集",
            rules=[
                Rule(
                    id="heading_1",
                    priority=100,
                    element_type="heading",
                    level=1,
                    match_strategy="any",
                    conditions=[
                        Condition(field="style_name", operator="eq", value="heading 1"),
                        Condition(field="outline_level", operator="eq", value=1),
                    ],
                    confidence=0.95,
                ),
                Rule(
                    id="code_block",
                    priority=60,
                    element_type="code_block",
                    match_strategy="any",
                    conditions=[
                        Condition(field="font_name", operator="in",
                                  value=["Courier New", "Consolas"]),
                    ],
                    confidence=0.7,
                ),
                Rule(
                    id="list_ordered",
                    priority=40,
                    element_type="list_ordered",
                    match_strategy="all",
                    conditions=[
                        Condition(field="has_numbering", operator="eq", value=True),
                        Condition(field="numbering_format", operator="in",
                                  value=["decimal", "lowerLetter"]),
                    ],
                    confidence=0.8,
                ),
                Rule(
                    id="fallback",
                    priority=0,
                    element_type="paragraph",
                    match_strategy="any",
                    conditions=[],
                    confidence=0.5,
                ),
            ],
        )

    def test_classify_heading(self, basic_ruleset: RuleSet):
        """测试标题匹配。"""
        executor = RuleExecutor(basic_ruleset)
        feature = StyleFeature(
            style_name="Heading 1",
            outline_level=1,
        )
        result = executor.classify(feature)
        assert result.element_type == "heading"
        assert result.level == 1
        assert result.matched_rule_id == "heading_1"
        assert result.confidence == 0.95

    def test_classify_code_block(self, basic_ruleset: RuleSet):
        """测试代码块匹配。"""
        executor = RuleExecutor(basic_ruleset)
        feature = StyleFeature(
            style_name="Normal",
            font_name="Consolas",
        )
        result = executor.classify(feature)
        assert result.element_type == "code_block"
        assert result.matched_rule_id == "code_block"

    def test_classify_fallback(self, basic_ruleset: RuleSet):
        """测试兜底规则匹配。"""
        executor = RuleExecutor(basic_ruleset)
        feature = StyleFeature(
            style_name="SomeCustomStyle",
            font_name="SomeWeirdFont",
        )
        result = executor.classify(feature)
        assert result.element_type == "paragraph"
        assert result.fallback is False  # fallback 规则匹配，不是完全无匹配
        assert result.matched_rule_id == "fallback"

    def test_classify_empty_ruleset(self):
        """测试空规则集。"""
        ruleset = RuleSet(name="empty", rules=[])
        executor = RuleExecutor(ruleset)
        feature = StyleFeature(style_name="Heading 1")
        result = executor.classify(feature)
        assert result.element_type == "paragraph"
        assert result.fallback is True

    def test_match_operator_eq(self, basic_ruleset: RuleSet):
        """测试 eq 操作符。"""
        executor = RuleExecutor(basic_ruleset)
        # style_name eq "heading 1" (case-sensitive in data, lowercased)
        feature = StyleFeature(style_name="heading 1", outline_level=1)
        result = executor.classify(feature)
        assert result.element_type == "heading"

    def test_match_operator_in(self, basic_ruleset: RuleSet):
        """测试 in 操作符。"""
        executor = RuleExecutor(basic_ruleset)
        feature = StyleFeature(
            font_name="Courier New",
            font_size=10.0,
        )
        result = executor.classify(feature)
        assert result.element_type == "code_block"

    def test_match_operator_regex(self):
        """测试 regex 操作符。"""
        ruleset = RuleSet(
            name="test_regex",
            rules=[
                Rule(
                    id="toc",
                    priority=80,
                    element_type="toc",
                    match_strategy="any",
                    conditions=[
                        Condition(field="style_name", operator="regex",
                                  value="(?i)^toc\\s+heading"),
                    ],
                    confidence=0.85,
                ),
            ],
        )
        executor = RuleExecutor(ruleset)
        feature = StyleFeature(style_name="TOC Heading")
        result = executor.classify(feature)
        assert result.element_type == "toc"

    def test_match_all_strategy(self, basic_ruleset: RuleSet):
        """测试 all 匹配策略（所有条件必须满足）。"""
        executor = RuleExecutor(basic_ruleset)
        # 满足 has_numbering 和 numbering_format
        feature = StyleFeature(
            style_name="Normal",
            has_numbering=True,
            numbering_format="decimal",
        )
        result = executor.classify(feature)
        assert result.element_type == "list_ordered"

    def test_match_all_strategy_fail(self, basic_ruleset: RuleSet):
        """测试 all 匹配策略失败时走后续规则。"""
        executor = RuleExecutor(basic_ruleset)
        # 只满足 has_numbering，不满足 numbering_format
        feature = StyleFeature(
            style_name="Normal",
            has_numbering=True,
            numbering_format="bullet",
        )
        result = executor.classify(feature)
        # 不会匹配 list_ordered，但 bullet 的但 list_ordered conditions 要求 in ["decimal", "lowerLetter"]
        # 所以 bullet 不匹配，走 fallback
        assert result.element_type == "paragraph"

    def test_batch_classify(self, basic_ruleset: RuleSet):
        """测试批量分类。"""
        executor = RuleExecutor(basic_ruleset)
        features = [
            StyleFeature(style_name="Heading 1", outline_level=1),
            StyleFeature(style_name="Normal"),
            StyleFeature(font_name="Consolas"),
        ]
        results = executor.classify_batch(features)
        assert len(results) == 3
        assert results[0].element_type == "heading"
        assert results[2].element_type == "code_block"

    def test_rule_priority_order(self):
        """测试规则按优先级排序。"""
        ruleset = RuleSet(
            name="priority_test",
            rules=[
                Rule(id="low", priority=0, element_type="paragraph",
                     match_strategy="any", conditions=[]),
                Rule(id="high", priority=100, element_type="heading",
                     match_strategy="any",
                     conditions=[Condition(field="style_name", operator="eq", value="heading 1")]),
            ],
        )
        executor = RuleExecutor(ruleset)
        feature = StyleFeature(style_name="heading 1")
        result = executor.classify(feature)
        assert result.matched_rule_id == "high"  # 高优先级先匹配


class TestRuleParser:
    """测试规则解析器。"""

    def test_parse_default_ruleset(self, default_ruleset_path: Path):
        """测试解析默认规则集。"""
        ruleset = RuleParser.parse_file(default_ruleset_path)
        assert ruleset.name == "default"
        assert len(ruleset.rules) > 0

    def test_parse_academic_ruleset(self, academic_ruleset_path: Path):
        """测试解析学术规则集。"""
        ruleset = RuleParser.parse_file(academic_ruleset_path)
        assert ruleset.name == "academic"
        assert len(ruleset.rules) > 0

    def test_parse_technical_ruleset(self, technical_ruleset_path: Path):
        """测试解析技术文档规则集。"""
        ruleset = RuleParser.parse_file(technical_ruleset_path)
        assert ruleset.name == "technical"
        assert len(ruleset.rules) > 0

    def test_validate_invalid_rule_missing_field(self):
        """测试缺少必填字段的规则解析失败。"""
        with pytest.raises(Exception):
            RuleParser.parse_dict({
                "name": "test",
                "rules": [
                    {"element_type": "paragraph"},  # 缺少 id
                ],
            })

    def test_sorted_rules(self):
        """测试规则排序。"""
        ruleset = RuleSet(
            name="test",
            rules=[
                Rule(id="a", priority=10, element_type="p", match_strategy="any",
                     conditions=[]),
                Rule(id="b", priority=100, element_type="h", match_strategy="any",
                     conditions=[]),
                Rule(id="c", priority=50, element_type="p", match_strategy="any",
                     conditions=[]),
            ],
        )
        sorted_rules = ruleset.get_sorted_rules()
        assert sorted_rules[0].id == "b"  # 最高优先级
        assert sorted_rules[1].id == "c"
        assert sorted_rules[2].id == "a"

    def test_classification_result_to_dict(self):
        """测试 ClassificationResult 的 to_dict 方法。"""
        result = ClassificationResult(
            element_type="heading",
            confidence=0.95,
            matched_rule_id="heading_1",
            matched_conditions=["style_name"],
            level=1,
        )
        d = result.to_dict()
        assert d["element_type"] == "heading"
        assert d["level"] == 1
        assert d["confidence"] == 0.95
