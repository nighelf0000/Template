"""规则执行器 — 接收 StyleFeature 和 RuleSet，返回 ClassificationResult。"""

from __future__ import annotations

import re
from typing import Any, List, Optional, Set

from .models import ClassificationResult, Condition, Rule, RuleSet
from ..style.feature import StyleFeature


class RuleExecutionError(Exception):
    """规则执行异常。"""


class RuleExecutor:
    """规则执行器。

    接收段落/元素的 StyleFeature 和 RuleSet，遍历规则集进行匹配，
    返回分类结果（元素类型 + 置信度 + 匹配的规则 ID）。
    """

    def __init__(self, ruleset: RuleSet):
        self._ruleset = ruleset
        self._sorted_rules = ruleset.get_sorted_rules()

    @property
    def ruleset(self) -> RuleSet:
        return self._ruleset

    @ruleset.setter
    def ruleset(self, ruleset: RuleSet) -> None:
        self._ruleset = ruleset
        self._sorted_rules = ruleset.get_sorted_rules()

    def classify(self, feature: StyleFeature) -> ClassificationResult:
        """对给定的样式特征进行分类。

        Args:
            feature: 段落的样式特征。

        Returns:
            分类结果。
        """
        for rule in self._sorted_rules:
            matched_conditions = self._match_rule(rule, feature)
            if matched_conditions is not None:
                confidence = self._calculate_confidence(rule, matched_conditions, len(rule.conditions))
                return ClassificationResult(
                    element_type=rule.element_type,
                    confidence=confidence,
                    matched_rule_id=rule.id,
                    matched_conditions=matched_conditions,
                    level=rule.level,
                )

        # 无规则匹配 — 返回兜底段落
        return ClassificationResult(
            element_type="paragraph",
            confidence=0.3,
            fallback=True,
        )

    def classify_batch(
        self, features: List[StyleFeature]
    ) -> List[ClassificationResult]:
        """批量分类多个样式特征。"""
        return [self.classify(f) for f in features]

    def _match_rule(self, rule: Rule, feature: StyleFeature) -> Optional[List[str]]:
        """尝试将规则与样式特征匹配。

        Args:
            rule: 规则对象。
            feature: 样式特征。

        Returns:
            如果匹配成功，返回匹配的条件字段名列表；否则返回 None。
        """
        if not rule.conditions:
            # 空条件 = 总是匹配（用于兜底规则）
            return []

        matched_fields: List[str] = []

        if rule.match_strategy == "all":
            # AND：所有条件必须满足
            for condition in rule.conditions:
                if self._evaluate_condition(condition, feature):
                    matched_fields.append(condition.field)
                else:
                    return None
            return matched_fields

        elif rule.match_strategy == "any":
            # OR：任一条件满足即可
            for condition in rule.conditions:
                if self._evaluate_condition(condition, feature):
                    matched_fields.append(condition.field)
                    return matched_fields
            return None

        return None

    def _evaluate_condition(self, condition: Condition, feature: StyleFeature) -> bool:
        """评估单个条件是否满足。"""
        field_value = self._get_field_value(feature, condition.field)

        # 如果字段值不存在，条件不满足（除非 operator 是特殊处理）
        if field_value is None:
            return condition.operator == "neq" and condition.value is not None

        operator = condition.operator
        target = condition.value

        try:
            if operator == "eq":
                return self._to_comparable(field_value) == self._to_comparable(target)

            elif operator == "neq":
                return self._to_comparable(field_value) != self._to_comparable(target)

            elif operator == "gt":
                return float(field_value) > float(target)

            elif operator == "gte":
                return float(field_value) >= float(target)

            elif operator == "lt":
                return float(field_value) < float(target)

            elif operator == "lte":
                return float(field_value) <= float(target)

            elif operator == "contains":
                return str(target).lower() in str(field_value).lower()

            elif operator == "regex":
                pattern = str(target)
                return bool(re.search(pattern, str(field_value)))

            elif operator == "in":
                if isinstance(target, (list, tuple, set)):
                    return self._to_comparable(field_value) in [self._to_comparable(v) for v in target]
                return str(field_value) in str(target)

            else:
                raise RuleExecutionError(f"不支持的 operator: {operator}")

        except (ValueError, TypeError) as e:
            raise RuleExecutionError(
                f"条件评估失败: field={condition.field}, "
                f"operator={operator}, value={target}, "
                f"field_value={field_value}, error={e}"
            )

    def _get_field_value(self, feature: StyleFeature, field: str) -> Any:
        """从 StyleFeature 中获取指定字段的值。"""
        return getattr(feature, field, None)

    def _to_comparable(self, value: Any) -> Any:
        """将值转为可比较的形式。"""
        if isinstance(value, str):
            return value.lower()
        return value

    def _calculate_confidence(
        self, rule: Rule, matched_fields: List[str], total_conditions: int
    ) -> float:
        """计算匹配的置信度。

        空条件规则使用默认置信度。
        有条件的规则根据匹配比例调整置信度。
        """
        if total_conditions == 0:
            return rule.confidence

        if rule.match_strategy == "any":
            # OR 匹配：使用规则默认置信度
            return rule.confidence
        else:
            # AND 匹配：使用规则默认置信度
            return rule.confidence
