"""XML 解析器 — 直接访问 docx 底层 XML 获取 python-docx 未暴露的元信息。"""

from __future__ import annotations

import zipfile
from dataclasses import dataclass, field
from io import BytesIO
from typing import Any, Dict, List, Optional

import docx
from lxml import etree

# 常用的 Office Open XML 命名空间
NSMAP = {
    "w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main",
    "wp": "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing",
    "a": "http://schemas.openxmlformats.org/drawingml/2006/main",
    "r": "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
    "pic": "http://schemas.openxmlformats.org/drawingml/2006/picture",
    "mc": "http://schemas.openxmlformats.org/markup-compatibility/2006",
    "ct": "http://schemas.openxmlformats.org/package/2006/content-types",
    "rel": "http://schemas.openxmlformats.org/package/2006/relationships",
    "wp14": "http://schemas.microsoft.com/office/word/2010/wordprocessingDrawing",
    "wps": "http://schemas.microsoft.com/office/word/2010/wordprocessingShape",
}

# 部分 XPath 查询用到的 qname 缓存
W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"


@dataclass
class SupplementElement:
    """补充元素 — XML 解析器发现的特殊元素。"""

    type: str  # page_break | section_break | comment_ref | footnote_ref | endnote_ref | image_ref
    position_index: int  # 在段落列表中的插入位置
    data: Dict[str, Any] = field(default_factory=dict)


class XmlParserError(Exception):
    """XML 解析异常。"""


class XmlParser:
    """XML 解析器。

    访问 docx 底层 XML，获取 python-docx 未暴露的元信息（分页符、分节符、批注引用等）。
    """

    def __init__(self, document: docx.Document):
        self._document = document
        self._xml_tree: Optional[etree.ElementTree] = None
        self._zip_file: Optional[zipfile.ZipFile] = None

    def parse(self) -> List[SupplementElement]:
        """解析文档的底层 XML，返回补充元素列表。"""
        supplements: List[SupplementElement] = []

        body = self._get_body()
        if body is None:
            return supplements

        # 收集所有子元素并按文档顺序遍历
        children = list(body)
        paragraph_index = 0

        for child in children:
            tag = etree.QName(child).localname

            if tag == "p":
                paragraph_index += 1
            elif tag == "tbl":
                # 表格 — 记录位置
                supplements.append(SupplementElement(
                    type="table",
                    position_index=paragraph_index,
                    data={"table_index": self._get_table_index(paragraph_index)},
                ))
            elif tag == "sdt":
                # 结构化文档标签（目录等）
                sdt_type = self._detect_sdt_type(child)
                if sdt_type:
                    supplements.append(SupplementElement(
                        type=sdt_type,
                        position_index=paragraph_index,
                    ))
            elif tag == "bookmarkStart":
                # 书签
                pass

        # 检查段落内的分页符和分节符
        self._find_inline_supplements(body, supplements)

        return supplements

    def get_document_xml(self) -> str:
        """获取原始 document.xml 字符串。"""
        return self._get_document_xml_str()

    def get_body_xml(self) -> Optional[str]:
        """获取 body 元素 XML 字符串。"""
        body = self._get_body()
        if body is not None:
            return etree.tostring(body, pretty_print=True, encoding="unicode")
        return None

    def xpath(self, path: str, namespaces: Optional[Dict[str, str]] = None) -> List[etree.Element]:
        """对文档 XML 执行 XPath 查询。"""
        tree = self._get_or_create_tree()
        ns = namespaces or NSMAP
        return tree.xpath(path, namespaces=ns)

    def _get_or_create_tree(self) -> etree.ElementTree:
        if self._xml_tree is None:
            xml_str = self._get_document_xml_str()
            self._xml_tree = etree.fromstring(xml_str.encode("utf-8"))
        return self._xml_tree  # type: ignore[return-value]

    def _get_document_xml_str(self) -> str:
        if self._document.part is not None and self._document.part.blob is not None:
            # 直接访问 docx 的 ZIP 包
            if self._zip_file is not None:
                pass  # already opened
            # 通过 python-docx 内部获取
            doc_part = self._document.part
            blob = doc_part.blob
            # 解压 document.xml
            from io import BytesIO
            import zipfile

            try:
                with zipfile.ZipFile(BytesIO(blob)) as zf:
                    return zf.read("word/document.xml").decode("utf-8")
            except Exception as e:
                raise XmlParserError(f"无法读取 document.xml: {e}")
        raise XmlParserError("无法获取文档 XML")

    def _get_body(self) -> Optional[etree.Element]:
        try:
            tree = self._get_or_create_tree()
        except Exception:
            return None

        if hasattr(tree, "find"):
            return tree.find(f"{{{W}}}body")
        return None

    def _get_table_index(self, paragraph_index: int) -> int:
        """估算表格在段落列表中的索引。"""
        tables = self._document.tables
        for i, table in enumerate(tables):
            # 通过表格的起始段落位置粗略匹配
            try:
                if table._tbl is not None:
                    # 查找表格在 body 中的位置
                    pass
            except Exception:
                continue
        return 0

    def _detect_sdt_type(self, sdt_element: etree.Element) -> Optional[str]:
        """检测结构化文档标签的类型。"""
        # 检查是否为目录
        sdt_pr = sdt_element.find(f"{{{W}}}sdtPr")
        if sdt_pr is None:
            return None

        # 查找 docPartObj / docPartGallery
        doc_part = sdt_pr.find(f"{{{W}}}docPartObj")
        if doc_part is not None:
            gallery = doc_part.find(f"{{{W}}}docPartGallery")
            if gallery is not None and gallery.get(f"{{{W}}}val") == "Table of Contents":
                return "toc"

        return None

    def _find_inline_supplements(
        self, body: etree.Element, supplements: List[SupplementElement]
    ) -> None:
        """在段落内查找内联元素（分页符、批注引用等）。"""
        for p_idx, p in enumerate(body.findall(f"{{{W}}}p")):
            # 检查段落内是否有分页符
            for br in p.iter(f"{{{W}}}br"):
                br_type = br.get(f"{{{W}}}type")
                if br_type == "page":
                    supplements.append(SupplementElement(
                        type="page_break",
                        position_index=p_idx,
                    ))

            # 检查分节符（lastRenderedPageBreak）
            for lrpb in p.iter(f"{{{W}}}lastRenderedPageBreak"):
                supplements.append(SupplementElement(
                    type="page_break",
                    position_index=p_idx,
                ))

            # 检查脚注/尾注引用
            for footnote_ref in p.iter(f"{{{W}}}footnoteReference"):
                footnote_id = footnote_ref.get(f"{{{W}}}id")
                supplements.append(SupplementElement(
                    type="footnote_ref",
                    position_index=p_idx,
                    data={"footnote_id": int(footnote_id) if footnote_id else 0},
                ))

            for endnote_ref in p.iter(f"{{{W}}}endnoteReference"):
                endnote_id = endnote_ref.get(f"{{{W}}}id")
                supplements.append(SupplementElement(
                    type="endnote_ref",
                    position_index=p_idx,
                    data={"endnote_id": int(endnote_id) if endnote_id else 0},
                ))

            # 检查批注引用
            for comment_ref in p.iter(f"{{{W}}}commentReference"):
                comment_id = comment_ref.get(f"{{{W}}}id")
                supplements.append(SupplementElement(
                    type="comment_ref",
                    position_index=p_idx,
                    data={"comment_id": int(comment_id) if comment_id else 0},
                ))

        # 分节符（sectPr 元素通常出现在 body 最后一个段落 after 或在段落内的 pPr 中）
        sect_prs = body.findall(f"{{{W}}}sectPr")
        for sect_pr in sect_prs:
            # 查找所属段落
            parent = sect_pr.getparent()
            if parent is not None and etree.QName(parent).localname == "p":
                # 段落内的分节符（常见）
                # 找到这个段落的索引
                ppr = sect_pr.getparent()
                all_ps = body.findall(f"{{{W}}}p")
                for i, p in enumerate(all_ps):
                    if p == ppr:
                        supplements.append(SupplementElement(
                            type="section_break",
                            position_index=i,
                        ))
                        break

    def close(self) -> None:
        """释放资源。"""
        self._xml_tree = None
        self._zip_file = None
