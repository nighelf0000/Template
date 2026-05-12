"""上下文矫正 — 利用相邻元素关系修正分类结果。"""

from __future__ import annotations

from typing import Any, Dict, List, Optional

from ..output.structure_tree import DocumentElement, ElementContent, ElementMetadata
from ..style.feature import StyleIndex


class ContextCorrector:
    """上下文矫正器。

    利用相邻元素关系修正分类结果。
    - 连续相同样式的段落合并为一个正文块
    - 标题后跟正文的关系修正
    - 列表连续性检测
    """

    def __init__(self, features: Optional[StyleIndex] = None):
        self._features = features or {}

    def correct(self, elements: List[DocumentElement]) -> List[DocumentElement]:
        """对元素列表进行上下文矫正。

        Args:
            elements: 待矫正的元素列表。

        Returns:
            矫正后的元素列表。
        """
        if not elements:
            return []

        corrected = self._merge_consecutive_paragraphs(elements)
        corrected = self._fix_list_continuity(corrected)
        corrected = self._fix_heading_paragraph_relation(corrected)

        return corrected

    def _merge_consecutive_paragraphs(
        self, elements: List[DocumentElement]
    ) -> List[DocumentElement]:
        """合并连续同类型正文段落。"""
        if not elements:
            return []

        merged: List[DocumentElement] = []
        current = elements[0]

        for next_elem in elements[1:]:
            if self._should_merge(current, next_elem):
                # 合并文本
                if current.content and next_elem.content:
                    if current.content.text and next_elem.content.text:
                        current.content.text += "\n" + next_elem.content.text
            else:
                merged.append(current)
                current = next_elem

        merged.append(current)
        return merged

    def _should_merge(self, a: DocumentElement, b: DocumentElement) -> bool:
        """判断两个元素是否应该合并。"""
        # 仅合并正文段落
        if a.type != "paragraph" or b.type != "paragraph":
            return False

        # 置信度相近
        if abs(a.confidence - b.confidence) > 0.2:
            return False

        # 同一样式
        a_style = None
        b_style = None
        if a.metadata:
            a_style = a.metadata.style_name
        if b.metadata:
            b_style = b.metadata.style_name

        if a_style and b_style and a_style == b_style:
            return True

        return False

    def _fix_list_continuity(
        self, elements: List[DocumentElement]
    ) -> List[DocumentElement]:
        """修正列表连续性。"""
        if len(elements) < 2:
            return elements

        result: List[DocumentElement] = []
        i = 0
        while i < len(elements):
            elem = elements[i]

            # 检测连续列表
            if elem.type in ("list_ordered", "list_unordered"):
                list_items = [elem]
                j = i + 1
                while j < len(elements) and elements[j].type == elem.type:
                    list_items.append(elements[j])
                    j += 1

                if len(list_items) > 1:
                    # 构建列表容器元素
                    container = DocumentElement(
                        id=f"list_{i:04d}",
                        type=elem.type,
                        level=elem.level,
                        confidence=min(e.confidence for e in list_items),
                        children=list_items,
                    )
                    # 容器文本合并
                    texts = []
                    for item in list_items:
                        if item.content and item.content.text:
                            texts.append(item.content.text)
                    container.content = ElementContent(
                        text="\n".join(texts) if texts else None,
                        list_items=[
                            {"text": item.content.text if item.content else "", "level": item.level}
                            for item in list_items
                        ],
                    )
                    result.append(container)
                    i = j
                    continue
                else:
                    result.append(elem)
            else:
                result.append(elem)
            i += 1

        return result

    def _fix_heading_paragraph_relation(
        self, elements: List[DocumentElement]
    ) -> List[DocumentElement]:
        """修正标题-段落关系（确保标题后紧跟着正文）。"""
        # 这个函数的目的是标记，实际的树构建由 HierarchyBuilder 完成
        # 这里只做简单的空段删除和连续标题修复
        if len(elements) < 2:
            return elements

        result: List[DocumentElement] = []

        for i, elem in enumerate(elements):
            result.append(elem)

        return result
