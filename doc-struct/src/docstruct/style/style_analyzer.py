"""样式分析器 — 读取 Word 文档中所有段落的样式信息。"""

from __future__ import annotations

import re
from typing import Dict, Optional

import docx
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml.ns import qn

from .feature import StyleFeature, StyleIndex


class StyleAnalyzerError(Exception):
    """样式分析异常。"""


_COLOR_HEX_PATTERN = re.compile(r"^([0-9A-Fa-f]{6})$")


class StyleAnalyzer:
    """样式分析器。

    读取 Word 文档中所有段落的样式信息，建立段落 ID → StyleFeature 的样式索引。
    """

    def __init__(self, document: docx.Document):
        self._document = document

    def analyze(self) -> StyleIndex:
        """分析文档中的所有段落，返回样式索引。

        Returns:
            Dict[str, StyleFeature]: 段落 ID → StyleFeature 的映射。
        """
        index: StyleIndex = {}
        for i, paragraph in enumerate(self._document.paragraphs):
            elem_id = f"elem_{i:04d}"
            feature = self._extract_feature(paragraph, i)
            index[elem_id] = feature
        return index

    def _extract_feature(self, paragraph: docx.text.paragraph.Paragraph, index: int) -> StyleFeature:
        """提取单个段落的样式特征。"""
        feature = StyleFeature()

        # 样式名称
        style = paragraph.style
        if style and style.name:
            feature.style_name = style.name
            feature.style_name_raw = style.name

        # 段落文本统计
        text = paragraph.text
        feature.text_length = len(text)
        feature.word_count = len(text.split()) if text.strip() else 0

        # 运行级样式（取第一个 run 的字体信息为代表）
        runs = paragraph.runs
        if runs:
            first_run = runs[0]
            self._extract_run_font(feature, first_run)

        # 段落格式
        self._extract_paragraph_format(feature, paragraph)

        # 段落对齐方式
        feature.alignment = self._parse_alignment(paragraph.alignment)

        # 列表编号
        num_pr = paragraph._element.find(qn("w:pPr") + "/" + qn("w:numPr"))
        if num_pr is not None:
            feature.has_numbering = True
            num_id_elem = num_pr.find(qn("w:numId"))
            if num_id_elem is not None and num_id_elem.get(qn("w:val")):
                # 通过 numId 能判断列表类型，但这里简化为有编号
                pass

        return feature

    def _extract_run_font(self, feature: StyleFeature, run: docx.text.run.Run) -> None:
        """从 Run 对象提取字体信息。"""
        font = run.font

        # 字体名称
        if font.name:
            feature.font_name = font.name

        # 中文字体（从 XML 获取）
        rpr = run._element.find(qn("w:rPr"))
        if rpr is not None:
            ea_font = rpr.find(qn("w:rFonts"))
            if ea_font is not None:
                hint = ea_font.get(qn("w:eastAsia"))
                if hint:
                    feature.font_name_east_asia = hint

        # 字号（磅）
        if font.size:
            feature.font_size = font.size.pt

        # 加粗
        if font.bold is not None:
            feature.is_bold = font.bold

        # 斜体
        if font.italic is not None:
            feature.is_italic = font.italic

        # 颜色
        if font.color and font.color.rgb:
            try:
                feature.color_hex = str(font.color.rgb)
            except Exception:
                pass

    def _extract_paragraph_format(
        self, feature: StyleFeature, paragraph: docx.text.paragraph.Paragraph
    ) -> None:
        """从段落对象提取段落格式信息。"""
        pf = paragraph.paragraph_format

        # 缩进
        if pf.left_indent is not None:
            feature.indent_left = pf.left_indent.pt
        if pf.right_indent is not None:
            feature.indent_right = pf.right_indent.pt
        if pf.first_line_indent is not None:
            feature.indent_first_line = pf.first_line_indent.pt

        # 行距
        if pf.line_spacing is not None:
            feature.line_spacing = pf.line_spacing

        # 段间距
        if pf.space_before is not None:
            feature.space_before = pf.space_before.pt
        if pf.space_after is not None:
            feature.space_after = pf.space_after.pt

        # 大纲级别（从 XML 获取更准确）
        ppr = paragraph._element.find(qn("w:pPr"))
        if ppr is not None:
            outline = ppr.find(qn("w:outlineLvl"))
            if outline is not None:
                val = outline.get(qn("w:val"))
                if val is not None:
                    try:
                        feature.outline_level = int(val)
                    except (ValueError, TypeError):
                        pass
            # 列表信息
            num_pr = ppr.find(qn("w:numPr"))
            if num_pr is not None:
                feature.has_numbering = True
                num_fmt = num_pr.find(qn("w:numFmt"))
                if num_fmt is not None:
                    feature.numbering_format = num_fmt.get(qn("w:val"))

    @staticmethod
    def _parse_alignment(alignment: Optional[WD_ALIGN_PARAGRAPH]) -> Optional[str]:
        """将 WD_ALIGN_PARAGRAPH 枚举转为字符串。"""
        if alignment is None:
            return None
        mapping = {
            WD_ALIGN_PARAGRAPH.LEFT: "left",
            WD_ALIGN_PARAGRAPH.CENTER: "center",
            WD_ALIGN_PARAGRAPH.RIGHT: "right",
            WD_ALIGN_PARAGRAPH.JUSTIFY: "justify",
        }
        return mapping.get(alignment)

    @staticmethod
    def analyze_from_document(document: docx.Document) -> StyleIndex:
        """便捷方法：直接对 Document 对象进行分析。"""
        analyzer = StyleAnalyzer(document)
        return analyzer.analyze()
