"""内容提取器 — 根据元素类型分发到不同提取器。"""

from __future__ import annotations

from typing import Any, Dict, List, Optional

import docx

from ..output.structure_tree import ElementContent
from .table_extractor import TableExtractor
from .image_extractor import ImageExtractor
from .list_extractor import ListExtractor


class ContentExtractor:
    """内容提取器。

    根据分类结果，提取各类元素的具体内容。
    不同类型提取逻辑不同，分发到对应的子提取器。
    """

    def __init__(self, document: docx.Document):
        self._document = document
        self._table_extractor = TableExtractor(document)
        self._image_extractor = ImageExtractor(document)
        self._list_extractor = ListExtractor(document)

    def extract(
        self,
        element_type: str,
        paragraph: Optional[docx.text.paragraph.Paragraph],
        index: int,
    ) -> ElementContent:
        """提取段落的内容。

        Args:
            element_type: 元素类型。
            paragraph: 段落对象（可为 None，用于 XML 解析来源）。
            index: 段落序号。

        Returns:
            提取的结构化内容。
        """
        text = paragraph.text.strip() if paragraph else ""

        if element_type in ("heading", "paragraph", "caption", "block_quote"):
            return ElementContent(text=text)

        elif element_type in ("list_ordered", "list_unordered"):
            if paragraph is None:
                return ElementContent(text=text)
            items = self._list_extractor.extract(paragraph)
            return ElementContent(
                text=text,
                list_items=items,
            )

        elif element_type == "code_block":
            return ElementContent(text=text)

        elif element_type == "toc":
            return ElementContent(text=text)

        elif element_type == "page_break":
            return ElementContent(text="")

        elif element_type == "section_break":
            return ElementContent(text="")

        elif element_type in ("header", "footer"):
            # header/footer 保留 run 级富文本格式
            return ElementContent(
                text=text,
                rich_text=self._extract_rich_text(paragraph) if paragraph else None,
            )

        elif element_type in ("footnote", "endnote", "comment"):
            return ElementContent(text=text)

        else:
            return ElementContent(text=text)

    @staticmethod
    def _extract_rich_text(
        paragraph: docx.text.paragraph.Paragraph,
    ) -> Optional[List[Dict[str, Any]]]:
        """从段落提取 run 级富文本格式信息。

        Args:
            paragraph: python-docx Paragraph 对象。

        Returns:
            run 级格式信息列表，每个 run 包含文本及其格式属性。
        """
        if not paragraph:
            return None

        runs: List[Dict[str, Any]] = []
        for run in paragraph.runs:
            run_info: Dict[str, Any] = {"text": run.text}

            if run.bold is not None:
                run_info["bold"] = run.bold
            if run.italic is not None:
                run_info["italic"] = run.italic
            if run.underline is not None:
                run_info["underline"] = run.underline
            if run.font.name:
                run_info["font_name"] = run.font.name
            if run.font.size:
                run_info["font_size"] = str(run.font.size)

            runs.append(run_info)

        return runs if runs else None

    def extract_table(self, table: docx.table.Table) -> ElementContent:
        """提取表格内容。

        Args:
            table: 表格对象。

        Returns:
            提取的表格数据。
        """
        table_data = self._table_extractor.extract(table)
        return ElementContent(
            text=self._table_extractor.table_to_text(table_data),
            table_data=table_data,
        )

    def extract_image(self, rel_id: str, rId: str) -> Optional[ElementContent]:
        """提取图片内容。"""
        image_data = self._image_extractor.extract_by_rel_id(rId)
        if image_data:
            return ElementContent(image_data=image_data)
        return None
