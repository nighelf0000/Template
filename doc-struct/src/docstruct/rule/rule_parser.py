"""规则解析器 — 将 YAML 规则定义解析为 Rule 对象列表。"""

from __future__ import annotations

import os
from pathlib import Path
from typing import Any, Dict, List, Optional, Union

import yaml

from .models import Condition, Rule, RuleSet


class RuleParseError(Exception):
    """规则解析异常。"""


class RuleParser:
    """规则解析器。

    将规则定义文件（YAML/JSON）或字典解析为 Rule 和 RuleSet 对象。
    """

    SUPPORTED_OPERATORS = {
        "eq", "neq", "gt", "gte", "lt", "lte",
        "contains", "regex", "in",
    }
    SUPPORTED_MATCH_STRATEGIES = {"all", "any"}
    REQUIRED_RULE_FIELDS = {"id", "element_type"}
    OPTIONAL_RULE_FIELDS = {
        "priority", "conditions", "match_strategy",
        "confidence", "level", "description",
    }

    @classmethod
    def parse_file(cls, filepath: Union[str, Path]) -> RuleSet:
        """从 YAML/JSON 文件解析规则集。

        Args:
            filepath: 规则文件路径。

        Returns:
            解析后的 RuleSet 对象。

        Raises:
            RuleParseError: 文件不存在或格式错误。
        """
        path = Path(filepath)
        if not path.exists():
            raise RuleParseError(f"规则文件不存在: {path}")

        try:
            with open(path, "r", encoding="utf-8") as f:
                raw = yaml.safe_load(f)
        except yaml.YAMLError as e:
            raise RuleParseError(f"YAML 解析失败: {e}")
        except Exception as e:
            raise RuleParseError(f"读取文件失败: {e}")

        if raw is None:
            raise RuleParseError("规则文件为空")

        return cls.parse_dict(raw)

    @classmethod
    def parse_dict(cls, data: Dict[str, Any]) -> RuleSet:
        """从字典解析规则集。

        Args:
            data: 规则集字典。

        Returns:
            解析后的 RuleSet 对象。

        Raises:
            RuleParseError: 字典结构不合法。
        """
        if not isinstance(data, dict):
            raise RuleParseError("规则集必须是字典格式")

        name = data.get("name", "")
        if not name:
            raise RuleParseError("规则集缺少 name 字段")

        version = str(data.get("version", "1.0"))
        description = data.get("description", "")

        rules_data = data.get("rules", [])
        if not isinstance(rules_data, list):
            raise RuleParseError("规则集的 rules 字段必须是列表")

        rules = []
        for i, rule_data in enumerate(rules_data):
            try:
                rule = cls._parse_rule(rule_data)
                rules.append(rule)
            except RuleParseError as e:
                raise RuleParseError(f"第 {i+1} 条规则解析失败: {e}")

        metadata = data.get("metadata", {})

        return RuleSet(
            name=name,
            version=version,
            description=description,
            rules=rules,
            metadata=metadata,
        )

    @classmethod
    def parse_rule(cls, data: Dict[str, Any]) -> Rule:
        """解析单条规则。"""
        return cls._parse_rule(data)

    @classmethod
    def _parse_rule(cls, data: Dict[str, Any]) -> Rule:
        if not isinstance(data, dict):
            raise RuleParseError("规则必须是字典格式")

        # 检查必填字段
        for field in cls.REQUIRED_RULE_FIELDS:
            if field not in data:
                raise RuleParseError(f"缺少必填字段: {field}")

        rule_id = str(data["id"])
        element_type = str(data["element_type"])
        priority = int(data.get("priority", 0))
        match_strategy = data.get("match_strategy", "all")

        if match_strategy not in cls.SUPPORTED_MATCH_STRATEGIES:
            raise RuleParseError(
                f"不支持的 match_strategy: {match_strategy}，"
                f"支持: {', '.join(cls.SUPPORTED_MATCH_STRATEGIES)}"
            )

        confidence = float(data.get("confidence", 0.9))
        if not (0.0 <= confidence <= 1.0):
            raise RuleParseError(f"confidence 必须在 0-1 之间: {confidence}")

        level = data.get("level")
        if level is not None:
            level = int(level)

        description = data.get("description", "")

        conditions = []
        for cond_data in data.get("conditions", []):
            condition = cls._parse_condition(cond_data)
            conditions.append(condition)

        return Rule(
            id=rule_id,
            priority=priority,
            element_type=element_type,
            conditions=conditions,
            match_strategy=match_strategy,
            confidence=confidence,
            level=level,
            description=description,
        )

    @classmethod
    def _parse_condition(cls, data: Dict[str, Any]) -> Condition:
        if not isinstance(data, dict):
            raise RuleParseError("条件必须是字典格式")

        field = data.get("field")
        if not field:
            raise RuleParseError("条件缺少 field 字段")

        operator = data.get("operator", "eq")
        if operator not in cls.SUPPORTED_OPERATORS:
            raise RuleParseError(
                f"不支持的 operator: {operator}，"
                f"支持: {', '.join(cls.SUPPORTED_OPERATORS)}"
            )

        if "value" not in data:
            raise RuleParseError(f"条件 '{field}' 缺少 value 字段")

        value = data["value"]

        return Condition(field=field, operator=operator, value=value)

    @classmethod
    def validate_ruleset(cls, ruleset: RuleSet) -> List[str]:
        """校验规则集的合法性，返回错误信息列表。"""
        errors: List[str] = []

        if not ruleset.name:
            errors.append("规则集名称不能为空")

        existing_ids = set()
        for i, rule in enumerate(ruleset.rules):
            if rule.id in existing_ids:
                errors.append(f"第 {i+1} 条规则 ID 重复: {rule.id}")
            existing_ids.add(rule.id)

            if not (0.0 <= rule.confidence <= 1.0):
                errors.append(f"规则 '{rule.id}' 的 confidence 超出范围: {rule.confidence}")

        return errors
