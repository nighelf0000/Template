"""多来源内容加载器 — 从 .docx 文件的所有内容区域加载段落内容。"""

from __future__ import annotations

import logging
import zipfile
from dataclasses import dataclass
from typing import Dict, List, Optional

import docx
from lxml import etree

logger = logging.getLogger(__name__)

# XML 命名空间
NS_W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"


@dataclass
class SourceParagraph:
    """来自非正文来源的段落。

    Attributes:
        source: 来源类型 ("header"|"footer"|"footnote"|"endnote"|"comment")
        paragraph: python-docx Paragraph 对象（header/footer 来源）；XML 解析来源为 None
        text: 纯文本内容（用于 XML 解析场景：footnote/endnote/comment）
        section_index: 所属节索引（仅 header/footer）
        linked_id: 关联的 ID（如 footnote_id, comment_id）
        style_name: 段落样式名
    """

    source: str
    paragraph: Optional[docx.text.paragraph.Paragraph] = None
    text: str = ""
    section_index: Optional[int] = None
    linked_id: Optional[str] = None
    style_name: Optional[str] = None


class SourceLoader:
    """多来源内容加载器。

    从 .docx 文档的页眉、页脚、脚注、尾注、批注等区域加载内容，
    返回统一的 SourceParagraph 数据结构。
    """

    @staticmethod
    def load_headers_footers(document: docx.Document) -> List[SourceParagraph]:
        """遍历所有节，收集页眉和页脚的段落。

        Args:
            document: python-docx Document 对象。

        Returns:
            页眉/页脚的 SourceParagraph 列表。
        """
        result: List[SourceParagraph] = []

        for section_idx, section in enumerate(document.sections):
            for source_name in ("header", "footer"):
                source_obj = (
                    section.header if source_name == "header" else section.footer
                )
                if source_obj is None:
                    continue
                for p in source_obj.paragraphs:
                    if p.text.strip():
                        result.append(
                            SourceParagraph(
                                source=source_name,
                                paragraph=p,
                                section_index=section_idx,
                                style_name=p.style.name if p.style else None,
                            )
                        )

        return result

    @staticmethod
    def load_footnotes(filepath: str) -> List[SourceParagraph]:
        """解析 word/footnotes.xml，提取脚注段落。"""
        return SourceLoader._load_xml_elements(
            filepath, "word/footnotes.xml", "footnote", tag_type="separator"
        )

    @staticmethod
    def load_endnotes(filepath: str) -> List[SourceParagraph]:
        """解析 word/endnotes.xml，提取尾注段落。"""
        return SourceLoader._load_xml_elements(
            filepath, "word/endnotes.xml", "endnote"
        )

    @staticmethod
    def load_comments(filepath: str) -> List[SourceParagraph]:
        """解析 word/comments.xml，提取批注段落。"""
        return SourceLoader._load_xml_elements(
            filepath, "word/comments.xml", "comment"
        )

    @staticmethod
    def load_all(document: docx.Document, filepath: str = "") -> List[SourceParagraph]:
        """汇总加载所有非正文来源的内容。

        Args:
            document: python-docx Document 对象（用于 header/footer）。
            filepath: .docx 文件路径（用于读取 footnotes/endnotes/comments XML）。

        Returns:
            所有非正文来源的 SourceParagraph 列表。
        """
        result: List[SourceParagraph] = []
        result.extend(SourceLoader.load_headers_footers(document))
        if filepath:
            result.extend(SourceLoader.load_footnotes(filepath))
            result.extend(SourceLoader.load_endnotes(filepath))
            result.extend(SourceLoader.load_comments(filepath))
        else:
            logger.warning("未提供 filepath，跳过 footnote/endnote/comment 加载")
        return result

    # ------------------------------------------------------------------ #
    # 内部辅助方法
    # ------------------------------------------------------------------ #

    @staticmethod
    def _extract_text_from_paragraph(element: etree._Element) -> str:
        """从 lxml 的 w:p 元素中提取纯文本。"""
        texts: List[str] = []
        for t_elem in element.iter(f"{{{NS_W}}}t"):
            if t_elem.text:
                texts.append(t_elem.text)
        return "".join(texts)

    @staticmethod
    def _load_xml_elements(
        filepath: str,
        xml_path: str,
        source_type: str,
        tag_type: Optional[str] = None,
    ) -> List[SourceParagraph]:
        """从 .docx ZIP 中的 XML 文件加载元素。

        Args:
            filepath: .docx 文件路径。
            xml_path: ZIP 中的 XML 文件路径（如 "word/footnotes.xml"）。
            source_type: 来源类型字符串。
            tag_type: 需要过滤的 type 属性值（如 "separator"）。

        Returns:
            解析得到的 SourceParagraph 列表。
        """
        try:
            with zipfile.ZipFile(filepath, "r") as zf:
                if xml_path not in zf.namelist():
                    return []

                xml_bytes = zf.read(xml_path)
                root = etree.fromstring(xml_bytes)

                result: List[SourceParagraph] = []

                container_tag = {
                    "footnote": "footnote",
                    "endnote": "endnote",
                    "comment": "comment",
                }[source_type]

                for container in root.iter(f"{{{NS_W}}}{container_tag}"):
                    if tag_type:
                        type_attr = container.get(f"{{{NS_W}}}type", "")
                        if type_attr == tag_type:
                            continue

                    linked_id = container.get(f"{{{NS_W}}}id", None)

                    for p_element in container.iter(f"{{{NS_W}}}p"):
                        text = SourceLoader._extract_text_from_paragraph(p_element)
                        if text.strip():
                            result.append(
                                SourceParagraph(
                                    source=source_type,
                                    paragraph=None,
                                    text=text.strip(),
                                    linked_id=linked_id,
                                )
                            )

                return result

        except Exception as e:
            logger.warning("加载 %s 失败: %s", xml_path, e)
            return []
