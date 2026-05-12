"""元数据提取器 — 提取文档级和元素级元数据。"""

from __future__ import annotations

from typing import Any, Dict, List, Optional

import docx
from docx.oxml.ns import qn

from ..output.structure_tree import DocumentElement, DocumentMeta


class MetadataExtractorError(Exception):
    """元数据提取异常。"""


class MetadataExtractor:
    """元数据提取器。

    提取文档级和元素级的元数据：页码估算、创建时间、作者等。
    """

    def __init__(self, document: docx.Document):
        self._document = document

    def extract_document_meta(
        self, elements: List[DocumentElement]
    ) -> DocumentMeta:
        """提取文档级元数据。

        Args:
            elements: 元素列表（用于统计）。

        Returns:
            文档级元数据。
        """
        meta = DocumentMeta()

        # 基础统计
        meta.paragraph_count = len(self._document.paragraphs)
        meta.table_count = len(self._document.tables)

        # 元素统计
        image_count = sum(1 for e in elements if e.type == "image")
        meta.image_count = image_count

        # 字数统计
        total_words = 0
        for elem in elements:
            if elem.content and elem.content.text:
                total_words += len(elem.content.text.split())
        meta.word_count = total_words

        # 文档属性
        props = self._document.core_properties
        try:
            if props.author:
                meta.author = props.author
        except Exception:
            pass
        try:
            if props.created:
                meta.created_time = props.created.isoformat()
        except Exception:
            pass
        try:
            if props.modified:
                meta.modified_time = props.modified.isoformat()
        except Exception:
            pass
        try:
            if props.title:
                meta.title = props.title
        except Exception:
            pass
        try:
            if props.language:
                meta.language = props.language
        except Exception:
            pass

        # 页数估算
        meta.page_count = self._estimate_page_count(elements)

        return meta

    def extract_element_meta(
        self, element: DocumentElement, paragraph_index: int
    ) -> Dict[str, Any]:
        """提取元素级元数据。

        Args:
            element: 文档元素。
            paragraph_index: 段落序号。

        Returns:
            元素元数据字典。
        """
        return {
            "position": {
                "index": paragraph_index,
            },
        }

    def _estimate_page_count(self, elements: List[DocumentElement]) -> int:
        """估算文档页数。

        基于段落数和平均每段行数进行粗略估算。
        """
        total_lines = 0

        for elem in elements:
            if elem.content and elem.content.text:
                text = elem.content.text
                # 粗略估算行数（按每行 80 字符估算）
                lines = max(1, len(text) // 80 + 1)
                total_lines += lines

        # 按每页约 40 行估算
        estimated_pages = max(1, total_lines // 40)

        return estimated_pages
