"""结构树数据模型 — 文档识别输出的核心数据结构。"""

from __future__ import annotations

import enum
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional


class ElementType(str, enum.Enum):
    """文档元素类型枚举。"""

    DOCUMENT = "document"
    HEADING = "heading"
    PARAGRAPH = "paragraph"
    TABLE = "table"
    IMAGE = "image"
    LIST_ORDERED = "list_ordered"
    LIST_UNORDERED = "list_unordered"
    CODE_BLOCK = "code_block"
    BLOCK_QUOTE = "block_quote"
    HEADER = "header"
    FOOTER = "footer"
    FOOTNOTE = "footnote"
    ENDNOTE = "endnote"
    TOC = "toc"
    TOC_ITEM = "toc_item"
    PAGE_BREAK = "page_break"
    SECTION_BREAK = "section_break"
    COMMENT = "comment"
    CAPTION = "caption"

    def __str__(self) -> str:
        return self.value


@dataclass
class ElementContent:
    """元素的内容负载。"""

    text: Optional[str] = None
    rich_text: Optional[List[Dict[str, Any]]] = None  # run 级富文本
    table_data: Optional[Dict[str, Any]] = None
    image_data: Optional[Dict[str, Any]] = None
    list_items: Optional[List[Dict[str, Any]]] = None

    def to_dict(self) -> Dict[str, Any]:
        d: Dict[str, Any] = {}
        if self.text is not None:
            d["text"] = self.text
        if self.rich_text is not None:
            d["rich_text"] = self.rich_text
        if self.table_data is not None:
            d["table_data"] = self.table_data
        if self.image_data is not None:
            d["image_data"] = self.image_data
        if self.list_items is not None:
            d["list_items"] = self.list_items
        return d


@dataclass
class ElementMetadata:
    """元素级元数据。"""

    position: Optional[Dict[str, Any]] = None  # {"index": int, "page_estimate": Optional[int]}
    style_name: Optional[str] = None
    style_features: Optional[Dict[str, Any]] = None  # debug 模式包含
    formatting: Optional[Dict[str, Any]] = None
    source: str = "body"

    def to_dict(self) -> Dict[str, Any]:
        d: Dict[str, Any] = {}
        if self.position is not None:
            d["position"] = self.position
        if self.style_name is not None:
            d["style_name"] = self.style_name
        if self.style_features is not None:
            d["style_features"] = self.style_features
        if self.formatting is not None:
            d["formatting"] = self.formatting
        d["source"] = self.source
        return d


@dataclass
class DocumentElement:
    """文档中的一个元素节点。"""

    id: str
    type: str  # ElementType value
    level: Optional[int] = None
    content: Optional[ElementContent] = None
    metadata: Optional[ElementMetadata] = None
    children: List[DocumentElement] = field(default_factory=list)
    confidence: float = 1.0

    def to_dict(self) -> Dict[str, Any]:
        d: Dict[str, Any] = {
            "id": self.id,
            "type": self.type,
            "confidence": self.confidence,
        }
        if self.level is not None:
            d["level"] = self.level
        if self.content is not None:
            d["content"] = self.content.to_dict()
        if self.metadata is not None:
            d["metadata"] = self.metadata.to_dict()
        if self.children:
            d["children"] = [c.to_dict() for c in self.children]
        return d


@dataclass
class DocumentMeta:
    """文档级元数据。"""

    page_count: Optional[int] = None
    paragraph_count: int = 0
    table_count: int = 0
    image_count: int = 0
    author: Optional[str] = None
    created_time: Optional[str] = None
    modified_time: Optional[str] = None
    title: Optional[str] = None
    word_count: int = 0
    language: Optional[str] = None

    extra: Dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        d: Dict[str, Any] = {}
        if self.page_count is not None:
            d["page_count"] = self.page_count
        d["paragraph_count"] = self.paragraph_count
        d["table_count"] = self.table_count
        d["image_count"] = self.image_count
        if self.author is not None:
            d["author"] = self.author
        if self.created_time is not None:
            d["created_time"] = self.created_time
        if self.modified_time is not None:
            d["modified_time"] = self.modified_time
        if self.title is not None:
            d["title"] = self.title
        d["word_count"] = self.word_count
        if self.language is not None:
            d["language"] = self.language
        d.update(self.extra)
        return d


@dataclass
class StructureTree:
    """文档结构树 — 识别的最终输出。"""

    version: str = "1.0.0"
    generated_at: str = ""
    source_file: str = ""
    document_meta: Optional[DocumentMeta] = None
    root: Optional[DocumentElement] = None
    flat_index: Dict[str, str] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        d: Dict[str, Any] = {
            "version": self.version,
            "generated_at": self.generated_at,
            "source_file": self.source_file,
        }
        if self.document_meta is not None:
            d["document_meta"] = self.document_meta.to_dict()
        if self.root is not None:
            d["root"] = self.root.to_dict()
        d["flat_index"] = self.flat_index
        return d
