"""列表提取 — 提取列表（层级、序号）。"""

from __future__ import annotations

from typing import Any, Dict, List, Optional

import docx
from docx.oxml.ns import qn


class ListExtractorError(Exception):
    """列表提取异常。"""


class ListExtractor:
    """列表提取器。

    从 Word 文档中提取列表信息（层级、序号格式、编号值）。
    """

    def __init__(self, document: docx.Document):
        self._document = document

    def extract(self, paragraph: docx.text.paragraph.Paragraph) -> List[Dict[str, Any]]:
        """提取段落的列表信息。

        Args:
            paragraph: 段落对象。

        Returns:
            列表项信息字典列表。
        """
        items: List[Dict[str, Any]] = []

        text = paragraph.text.strip()
        p_element = paragraph._element

        # 查找编号属性
        ppr = p_element.find(qn("w:pPr"))
        if ppr is not None:
            num_pr = ppr.find(qn("w:numPr"))
            if num_pr is not None:
                # 获取编号级别
                ilvl = num_pr.find(qn("w:ilvl"))
                level = 0
                if ilvl is not None:
                    val = ilvl.get(qn("w:val"))
                    if val:
                        try:
                            level = int(val)
                        except (ValueError, TypeError):
                            pass

                # 获取 numId
                num_id_elem = num_pr.find(qn("w:numId"))
                num_id = None
                if num_id_elem is not None:
                    val = num_id_elem.get(qn("w:val"))
                    if val:
                        num_id = int(val)

                # 判断列表类型
                list_type = self._detect_list_type(num_id, level)

                # 获取编号值
                num_text = ""
                num_elem = num_pr.find(qn("w:numFmt"))
                if num_elem is not None:
                    num_text = num_elem.get(qn("w:val"), "")

                items.append({
                    "level": level,
                    "num_id": num_id,
                    "list_type": list_type,
                    "text": text,
                })

        if not items:
            # 如果没有编号信息，但样式可能表明是列表
            items.append({
                "level": 0,
                "list_type": "unknown",
                "text": text,
            })

        return items

    def _detect_list_type(self, num_id: Optional[int], level: int) -> str:
        """检测列表类型（有序/无序）。"""
        # 通过 python-docx 的编号定义尝试判断
        try:
            if num_id is not None and self._document.part is not None:
                # 查找编号定义
                numbering_part = self._document.part.numbering_part
                if numbering_part is not None:
                    # 简化判断：通过 numId 在 numbering.xml 中查找
                    num_def = self._document.part.element.find(
                        f".//{{{qn('w:num').split('}')[0].strip('{')}}}num[@{{{qn('w:numId').split('}')[0].strip('{')}}}numId='{num_id}']"
                    )
                    if num_def is not None:
                        # 查找编号格式
                        fmt = num_def.find(f".//{qn('w:numFmt')}")
                        if fmt is not None:
                            val = fmt.get(qn("w:val"))
                            if val in ("bullet", "hybridBullet"):
                                return "list_unordered"
                            elif val:
                                return "list_ordered"
        except Exception:
            pass

        return "list_ordered"  # 默认有序

    def is_list_paragraph(self, paragraph: docx.text.paragraph.Paragraph) -> bool:
        """判断段落是否为列表项。"""
        ppr = paragraph._element.find(qn("w:pPr"))
        if ppr is not None:
            num_pr = ppr.find(qn("w:numPr"))
            return num_pr is not None
        return False
