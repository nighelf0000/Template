"""规则数据模型 — Rule, Condition, RuleSet, ClassificationResult。"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional


@dataclass
class Condition:
    """规则条件。"""

    field: str  # StyleFeature 中的字段名
    operator: str  # eq | neq | gt | gte | lt | lte | contains | regex | in
    value: Any

    def to_dict(self) -> Dict[str, Any]:
        return {"field": self.field, "operator": self.operator, "value": self.value}

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> Condition:
        return cls(
            field=data["field"],
            operator=data["operator"],
            value=data["value"],
        )


@dataclass
class Rule:
    """一条识别规则。"""

    id: str
    priority: int
    element_type: str
    conditions: List[Condition] = field(default_factory=list)
    match_strategy: str = "all"  # "all" | "any"
    confidence: float = 0.9
    level: Optional[int] = None
    description: str = ""

    def to_dict(self) -> Dict[str, Any]:
        d: Dict[str, Any] = {
            "id": self.id,
            "priority": self.priority,
            "element_type": self.element_type,
            "conditions": [c.to_dict() for c in self.conditions],
            "match_strategy": self.match_strategy,
            "confidence": self.confidence,
            "description": self.description,
        }
        if self.level is not None:
            d["level"] = self.level
        return d

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> Rule:
        conditions = [Condition.from_dict(c) for c in data.get("conditions", [])]
        return cls(
            id=data["id"],
            priority=data.get("priority", 0),
            element_type=data["element_type"],
            conditions=conditions,
            match_strategy=data.get("match_strategy", "all"),
            confidence=data.get("confidence", 0.9),
            level=data.get("level"),
            description=data.get("description", ""),
        )


@dataclass
class RuleSet:
    """规则集。"""

    name: str
    version: str = "1.0"
    description: str = ""
    rules: List[Rule] = field(default_factory=list)
    metadata: Dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "name": self.name,
            "version": self.version,
            "description": self.description,
            "rules": [r.to_dict() for r in self.rules],
            "metadata": self.metadata,
        }

    def get_sorted_rules(self) -> List[Rule]:
        """按优先级降序排序（高优先级先匹配）。"""
        return sorted(self.rules, key=lambda r: r.priority, reverse=True)


@dataclass
class ClassificationResult:
    """规则引擎的分类结果。"""

    element_type: str
    confidence: float = 0.0
    matched_rule_id: Optional[str] = None
    matched_conditions: List[str] = field(default_factory=list)
    fallback: bool = False
    level: Optional[int] = None

    def to_dict(self) -> Dict[str, Any]:
        d: Dict[str, Any] = {
            "element_type": self.element_type,
            "confidence": self.confidence,
            "matched_rule_id": self.matched_rule_id,
            "matched_conditions": self.matched_conditions,
            "fallback": self.fallback,
        }
        if self.level is not None:
            d["level"] = self.level
        return d
