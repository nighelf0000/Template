"""规则仓库 — 管理规则集的加载、保存、列表。"""

from __future__ import annotations

import os
from pathlib import Path
from typing import Dict, List, Optional, Union

import yaml

from .models import RuleSet
from .rule_parser import RuleParseError, RuleParser


class RuleRepositoryError(Exception):
    """规则仓库异常。"""


class RuleRepository:
    """规则仓库。

    管理规则集的加载、保存、增删改查。
    规则以 YAML 文件形式存储在本地 rulesets 目录。
    """

    def __init__(self, rulesets_dir: Optional[Union[str, Path]] = None):
        """初始化规则仓库。

        Args:
            rulesets_dir: 规则集文件目录。为 None 时使用默认路径。
        """
        if rulesets_dir is not None:
            self._rulesets_dir = Path(rulesets_dir)
        else:
            # 尝试查找包内建规则目录
            self._rulesets_dir = self._find_default_rulesets_dir()

        self._cache: Dict[str, RuleSet] = {}

    def load(self, ruleset_name: str) -> RuleSet:
        """加载指定名称的规则集。

        Args:
            ruleset_name: 规则集名称（不含后缀）。

        Returns:
            RuleSet 对象。

        Raises:
            RuleRepositoryError: 规则集不存在或加载失败。
        """
        if ruleset_name in self._cache:
            return self._cache[ruleset_name]

        # 尝试不同的扩展名
        for ext in [".yaml", ".yml", ".json"]:
            filepath = self._rulesets_dir / f"{ruleset_name}{ext}"
            if filepath.exists():
                try:
                    ruleset = RuleParser.parse_file(filepath)
                    self._cache[ruleset_name] = ruleset
                    return ruleset
                except RuleParseError as e:
                    raise RuleRepositoryError(f"规则集 '{ruleset_name}' 解析失败: {e}")

        raise RuleRepositoryError(
            f"规则集 '{ruleset_name}' 不存在"
            f"（在 {self._rulesets_dir} 中未找到）"
        )

    def save(self, ruleset: RuleSet, filepath: Optional[Union[str, Path]] = None) -> None:
        """保存规则集到文件。

        Args:
            ruleset: 要保存的规则集。
            filepath: 保存路径。为 None 时保存到默认目录。
        """
        if filepath is None:
            filepath = self._rulesets_dir / f"{ruleset.name}.yaml"

        path = Path(filepath)
        path.parent.mkdir(parents=True, exist_ok=True)

        data = ruleset.to_dict()
        with open(path, "w", encoding="utf-8") as f:
            yaml.dump(data, f, default_flow_style=False, allow_unicode=True, sort_keys=False)

        # 更新缓存
        self._cache[ruleset.name] = ruleset

    def list_rulesets(self) -> List[str]:
        """列出所有可用的规则集名称。"""
        names: List[str] = []
        if not self._rulesets_dir.exists():
            return names

        for f in self._rulesets_dir.iterdir():
            if f.suffix in (".yaml", ".yml", ".json") and not f.name.startswith("."):
                names.append(f.stem)

        return sorted(names)

    def delete(self, ruleset_name: str) -> None:
        """删除指定名称的规则集。"""
        for ext in [".yaml", ".yml", ".json"]:
            filepath = self._rulesets_dir / f"{ruleset_name}{ext}"
            if filepath.exists():
                filepath.unlink()
                self._cache.pop(ruleset_name, None)
                return

        raise RuleRepositoryError(f"规则集 '{ruleset_name}' 不存在")

    def reload(self, ruleset_name: str) -> RuleSet:
        """重新加载规则集（清除缓存）。"""
        self._cache.pop(ruleset_name, None)
        return self.load(ruleset_name)

    @property
    def rulesets_dir(self) -> Path:
        return self._rulesets_dir

    def _find_default_rulesets_dir(self) -> Path:
        """查找默认规则集目录。"""
        # 搜索顺序：
        # 1. 当前工作目录下的 rulesets/
        # 2. 包目录下的 rulesets/（通过 __file__ 推测）
        # 3. 父目录下的 rulesets/
        candidates = [
            Path.cwd() / "rulesets",
            Path(__file__).parent.parent.parent.parent / "rulesets",
            Path(__file__).parent.parent.parent / "rulesets",
            Path(__file__).parent.parent / "rulesets",
        ]

        for candidate in candidates:
            if candidate.exists() and candidate.is_dir():
                return candidate

        # 兜底：使用包内 rulesets 目录（如果不存在则创建）
        for candidate in candidates:
            try:
                candidate.mkdir(parents=True, exist_ok=True)
                return candidate
            except Exception:
                continue

        return candidates[0]
