"""层级构建器 — 将扁平的元素列表构建为嵌套的树结构。"""

from __future__ import annotations

import datetime
from typing import Any, Dict, List, Optional

from ..output.structure_tree import (
    DocumentElement,
    DocumentMeta,
    ElementContent,
    ElementMetadata,
    StructureTree,
)


class HierarchyBuilderError(Exception):
    """层级构建异常。"""


class HierarchyBuilder:
    """层级构建器。

    将扁平的元素列表按标题层级构建为嵌套的 JSON 树结构。
    - 标题级别驱动的树构建
    - 正文段落归属到最近标题
    """

    def __init__(self) -> None:
        self._element_counter = 0

    def build(
        self,
        elements: List[DocumentElement],
        doc_meta: DocumentMeta,
        source_file: str = "",
    ) -> StructureTree:
        """将元素列表构建为结构树。

        Args:
            elements: 文档元素列表。
            doc_meta: 文档级元数据。
            source_file: 源文件名。

        Returns:
            嵌套的结构树。
        """
        # 创建根节点
        root = DocumentElement(
            id="root",
            type="document",
            children=[],
        )

        # 构建层级树
        self._build_hierarchy(root, elements)

        # 生成 flat_index
        flat_index: Dict[str, str] = {}
        self._build_flat_index(root, flat_index)

        # 构建最终结构树
        tree = StructureTree(
            version="1.0.0",
            generated_at=datetime.datetime.now().isoformat(),
            source_file=source_file,
            document_meta=doc_meta,
            root=root,
            flat_index=flat_index,
        )

        return tree

    def _build_hierarchy(
        self, parent: DocumentElement, elements: List[DocumentElement]
    ) -> None:
        """递归构建层级树。

        将扁平的元素列表按标题层级组织成嵌套结构。
        """
        # 维护一个标题栈：(level, parent_element)
        heading_stack: List[DocumentElement] = [parent]

        for elem in elements:
            if elem.type == "heading":
                level = elem.level or 1

                # 弹出比当前标题级别 >= 的栈顶（相同或更大 level 的标题）
                while len(heading_stack) > 1:
                    top = heading_stack[-1]
                    if top.type == "heading" and (top.level or 1) >= level:
                        heading_stack.pop()
                    else:
                        break

                # 当前标题作为栈顶的孩子
                heading_stack[-1].children.append(elem)
                heading_stack.append(elem)

            else:
                # 非标题元素：作为栈顶标题的孩子
                heading_stack[-1].children.append(elem)

    def _build_flat_index(
        self, element: DocumentElement, index: Dict[str, str]
    ) -> None:
        """递归构建 flat_index。"""
        index[element.id] = element.type
        for child in element.children:
            self._build_flat_index(child, index)
