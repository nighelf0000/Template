"""结构树 JSON 序列化。"""

from __future__ import annotations

import json
from typing import Any, Dict, Optional

from .structure_tree import StructureTree


class StructureTreeSerializer:
    """StructureTree JSON 序列化/反序列化。"""

    @classmethod
    def to_json(
        cls,
        tree: StructureTree,
        indent: int = 2,
        ensure_ascii: bool = False,
        **kwargs: Any,
    ) -> str:
        """将结构树序列化为 JSON 字符串。"""
        return json.dumps(
            tree.to_dict(),
            indent=indent,
            ensure_ascii=ensure_ascii,
            **kwargs,
        )

    @classmethod
    def to_file(
        cls,
        tree: StructureTree,
        filepath: str,
        indent: int = 2,
        ensure_ascii: bool = False,
        **kwargs: Any,
    ) -> None:
        """将结构树序列化并写入文件。"""
        with open(filepath, "w", encoding="utf-8") as f:
            json.dump(
                tree.to_dict(),
                f,
                indent=indent,
                ensure_ascii=ensure_ascii,
                **kwargs,
            )

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> StructureTree:
        """从字典重建 StructureTree。"""
        from .structure_tree import DocumentElement, DocumentMeta, ElementContent, ElementMetadata

        tree = StructureTree(
            version=data.get("version", "1.0.0"),
            generated_at=data.get("generated_at", ""),
            source_file=data.get("source_file", ""),
            flat_index=data.get("flat_index", {}),
        )

        meta_data = data.get("document_meta")
        if meta_data:
            tree.document_meta = DocumentMeta(
                page_count=meta_data.get("page_count"),
                paragraph_count=meta_data.get("paragraph_count", 0),
                table_count=meta_data.get("table_count", 0),
                image_count=meta_data.get("image_count", 0),
                author=meta_data.get("author"),
                created_time=meta_data.get("created_time"),
                modified_time=meta_data.get("modified_time"),
                title=meta_data.get("title"),
                word_count=meta_data.get("word_count", 0),
                language=meta_data.get("language"),
            )

        root_data = data.get("root")
        if root_data:
            tree.root = cls._dict_to_element(root_data)

        return tree

    @classmethod
    def _dict_to_element(cls, data: Dict[str, Any]) -> DocumentElement:
        """递归将字典转换为 DocumentElement。"""
        from .structure_tree import DocumentElement, ElementContent, ElementMetadata

        content_data = data.get("content")
        content = None
        if content_data:
            content = ElementContent(
                text=content_data.get("text"),
                rich_text=content_data.get("rich_text"),
                table_data=content_data.get("table_data"),
                image_data=content_data.get("image_data"),
                list_items=content_data.get("list_items"),
            )

        meta_data = data.get("metadata")
        metadata = None
        if meta_data:
            metadata = ElementMetadata(
                position=meta_data.get("position"),
                style_name=meta_data.get("style_name"),
                style_features=meta_data.get("style_features"),
                formatting=meta_data.get("formatting"),
                source=meta_data.get("source", "body"),
            )

        elem = DocumentElement(
            id=data.get("id", ""),
            type=data.get("type", "paragraph"),
            level=data.get("level"),
            content=content,
            metadata=metadata,
            confidence=data.get("confidence", 1.0),
        )

        for child_data in data.get("children", []):
            elem.children.append(cls._dict_to_element(child_data))

        return elem
