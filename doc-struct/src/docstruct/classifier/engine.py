"""分类器引擎 — 编排整个识别流程。"""

from __future__ import annotations

import datetime
from typing import Any, Dict, List, Optional, Set, Union

import docx

from ..loader.document_loader import DocumentLoader
from ..loader.source_loader import SourceLoader, SourceParagraph
from ..style.style_analyzer import StyleAnalyzer
from ..style.feature import StyleFeature, StyleIndex
from ..xml_parser.xml_parser import XmlParser, SupplementElement
from ..rule.models import ClassificationResult, RuleSet
from ..rule.rule_executor import RuleExecutor
from ..rule.rule_repository import RuleRepository
from ..extractor.content_extractor import ContentExtractor
from ..builder.hierarchy_builder import HierarchyBuilder
from ..builder.metadata_extractor import MetadataExtractor
from ..output.structure_tree import (
    DocumentElement,
    DocumentMeta,
    ElementContent,
    ElementMetadata,
    StructureTree,
)
from .context import ContextCorrector


class ClassifierEngineError(Exception):
    """分类器引擎异常。"""


class ClassifierEngine:
    """分类器引擎。

    编排整个识别流程：文档加载 → 样式分析 → 规则匹配 → 内容提取 → 上下文矫正 → 层级构建。
    """

    def __init__(
        self,
        ruleset: Optional[RuleSet] = None,
        ruleset_name: str = "default",
        debug: bool = False,
    ):
        self._ruleset = ruleset
        self._ruleset_name = ruleset_name
        self._debug = debug
        self._rule_repository: Optional[RuleRepository] = None
        self._rule_executor: Optional[RuleExecutor] = None

    def recognize(self, filepath: str, **kwargs: Any) -> StructureTree:
        """识别文档结构。

        Args:
            filepath: .docx 文件路径。

        Returns:
            StructureTree 结构树。
        """
        # 1. 加载文档
        document = DocumentLoader.load(filepath)
        self._document = document  # 供 _process_source_paragraphs 使用

        # 2. 加载规则集
        ruleset = self._get_ruleset()

        # 3. 样式分析
        features = StyleAnalyzer.analyze_from_document(document)

        # 4. XML 补充解析
        xml_parser = XmlParser(document)
        supplements = xml_parser.parse()

        # 5. 分类 + 提取
        elements = self._classify_and_extract(document, features, supplements, ruleset)

        # 5b. 加载并处理非 body 内容
        source_paragraphs = SourceLoader.load_all(document, filepath=filepath)
        supplement_elements = self._process_source_paragraphs(
            source_paragraphs, supplements
        )

        # 5c. 按文档自然顺序插入非 body 元素
        elements = self._interleave_elements(elements, supplement_elements)

        # 6. 上下文矫正
        corrector = ContextCorrector(features)
        corrected = corrector.correct(elements)

        # 7. 提取元数据
        meta_extractor = MetadataExtractor(document)
        doc_meta = meta_extractor.extract_document_meta(corrected)

        # 8. 构建层级树
        builder = HierarchyBuilder()
        tree = builder.build(corrected, doc_meta, source_file=filepath)

        return tree

    def _get_ruleset(self) -> RuleSet:
        """获取规则集。"""
        if self._ruleset is not None:
            return self._ruleset

        if self._rule_repository is None:
            self._rule_repository = RuleRepository()

        return self._rule_repository.load(self._ruleset_name)

    def _classify_and_extract(
        self,
        document: docx.Document,
        features: StyleIndex,
        supplements: List[SupplementElement],
        ruleset: RuleSet,
    ) -> List[DocumentElement]:
        """执行分类和内容提取。"""
        self._rule_executor = RuleExecutor(ruleset)
        extractor = ContentExtractor(document)

        elements: List[DocumentElement] = []
        supplement_indices: Set[int] = set()
        supplement_map: Dict[int, List[SupplementElement]] = {}

        # 构建补充元素位置映射
        for supp in supplements:
            idx = supp.position_index
            if idx not in supplement_map:
                supplement_map[idx] = []
            supplement_map[idx].append(supp)
            supplement_indices.add(idx)

        paragraph_idx = 0
        for p_idx, paragraph in enumerate(document.paragraphs):
            elem_id = f"elem_{p_idx:04d}"
            feature_key = f"elem_{p_idx:04d}"

            # 获取样式特征
            feature = features.get(feature_key, StyleFeature())
            if not feature.style_name:
                feature.style_name = paragraph.style.name if paragraph.style else "Normal"

            # 规则匹配
            result = self._rule_executor.classify(feature)

            # 内容提取
            content = extractor.extract(result.element_type, paragraph, p_idx)

            # 构建位置信息
            position = {"index": p_idx}

            # 构建元数据
            metadata = ElementMetadata(
                position=position,
                style_name=feature.style_name,
                source="body",
            )

            if self._debug:
                metadata.style_features = feature.to_dict()
                metadata.formatting = {
                    "alignment": feature.alignment,
                    "indent": {
                        "left": feature.indent_left,
                        "first_line": feature.indent_first_line,
                    },
                    "line_spacing": feature.line_spacing,
                }

            # 构建文档元素
            elem = DocumentElement(
                id=elem_id,
                type=result.element_type,
                level=result.level,
                content=content,
                metadata=metadata,
                confidence=result.confidence,
            )

            elements.append(elem)
            paragraph_idx += 1

        # 处理表格（表格在段落之后，额外添加）
        for t_idx, table in enumerate(document.tables):
            table_elem_id = f"table_{t_idx:04d}"
            table_content = extractor.extract_table(table)
            elements.append(DocumentElement(
                id=table_elem_id,
                type="table",
                content=table_content,
                metadata=ElementMetadata(
                    position={"index": paragraph_idx + t_idx},
                    source="body",
                ),
            ))

        return elements

    def _process_source_paragraphs(
        self,
        source_paragraphs: List["SourceParagraph"],
        supplements: List[SupplementElement],
    ) -> List[DocumentElement]:
        """处理非正文来源的段落，构建 DocumentElement 列表。

        为每个非 body 段落动态分配相应的 source 值，
        并确定每个元素在文档中的自然插入位置。

        Args:
            source_paragraphs: SourceLoader 加载的非正文段落列表。
            supplements: XML 补充解析结果（用于引用位置映射）。

        Returns:
            非 body 来源的 DocumentElement 列表。
        """
        elements: List[DocumentElement] = []
        extractor = ContentExtractor(self._document) if hasattr(self, '_document') else None

        # 构建引用位置映射：footnote_ref / endnote_ref / comment_ref → position_index
        ref_positions: Dict[str, Dict[str, int]] = {}
        for supp in supplements:
            if supp.type in ("footnote_ref", "endnote_ref", "comment_ref"):
                ref_type = supp.type.replace("_ref", "")
                if ref_type not in ref_positions:
                    ref_positions[ref_type] = {}
                ref_id = str(
                    supp.data.get("footnote_id")
                    or supp.data.get("endnote_id")
                    or supp.data.get("comment_id", "")
                )
                ref_positions[ref_type][ref_id] = supp.position_index

        # 节边界映射：section_index → body 段落前的插入位置
        section_boundaries = self._find_section_boundaries(supplements)

        for sp_idx, sp in enumerate(source_paragraphs):
            elem_id = f"{sp.source}_{sp_idx:04d}"

            # 根据 source 确定 element_type
            elem_type = {
                "header": "header",
                "footer": "footer",
                "footnote": "footnote",
                "endnote": "endnote",
                "comment": "comment",
            }[sp.source]

            # 内容提取
            if sp.source in ("header", "footer") and sp.paragraph is not None:
                # header/footer 通过 ContentExtractor 提取（保留富文本）
                if extractor:
                    content = extractor.extract(elem_type, sp.paragraph, sp_idx)
                else:
                    content = ElementContent(text=sp.paragraph.text.strip())
            else:
                # footnote/endnote/comment 使用已提取的纯文本
                content = ElementContent(text=sp.text)

            # 确定自然插入位置（body_insert_at）
            insert_position: Optional[int] = None
            if sp.source in ("header", "footer"):
                # header/footer 插入到所属 section 的 body 区域前方
                if sp.section_index is not None:
                    insert_position = section_boundaries.get(sp.section_index)
                else:
                    insert_position = 0
            elif sp.source in ("footnote", "endnote", "comment"):
                # 根据引用位置确定插入点（插入到引用标记所在的 body 段落之后）
                ref_map = ref_positions.get(sp.source, {})
                ref_id = sp.linked_id or ""
                ref_pos = ref_map.get(ref_id)
                if ref_pos is not None:
                    insert_position = ref_pos + 1

            # 位置元数据
            position: Dict[str, Any] = {"index": sp_idx}
            if sp.section_index is not None:
                position["section_index"] = sp.section_index
            if insert_position is not None:
                position["body_insert_at"] = insert_position

            metadata = ElementMetadata(
                position=position,
                source=sp.source,  # ← 动态分配 source
                style_name=sp.style_name if sp.style_name else None,
            )

            elem = DocumentElement(
                id=elem_id,
                type=elem_type,
                level=0 if elem_type == "header" else None,
                content=content,
                metadata=metadata,
                confidence=1.0,
            )
            elements.append(elem)

        return elements

    @staticmethod
    def _interleave_elements(
        body_elements: List[DocumentElement],
        supplement_elements: List[DocumentElement],
    ) -> List[DocumentElement]:
        """将非 body 元素按文档自然顺序插入 body 元素列表中。

        - header/footer: 插入到对应 section 的 body 区域前方
        - footnote/endnote/comment: 插入到引用标记所在 body 段落之后
        - 无引用位置信息的元素追加到末尾

        Args:
            body_elements: body 来源的元素列表（现有识别结果）。
            supplement_elements: 非 body 来源的元素列表。

        Returns:
            按自然顺序排列的完整元素列表。
        """
        result = list(body_elements)

        # 按 body_insert_at 升序排列，确保插入顺序稳定
        for supp in sorted(
            supplement_elements,
            key=lambda e: (
                e.metadata.position.get("body_insert_at", float("inf"))
                if e.metadata and e.metadata.position
                else float("inf")
            ),
        ):
            insert_at = (
                supp.metadata.position.get("body_insert_at")
                if supp.metadata and supp.metadata.position
                else None
            )
            if insert_at is not None and 0 <= insert_at <= len(result):
                result.insert(insert_at, supp)
            else:
                result.append(supp)

        return result

    @staticmethod
    def _find_section_boundaries(
        supplements: List[SupplementElement],
    ) -> Dict[int, int]:
        """根据补充元素中的分节符确定各节的边界位置。

        每个分节符标记当前节结束，下一节开始。
        section_index → body 段落插入位置 的映射。

        Args:
            supplements: XML 补充解析结果。

        Returns:
            字典，key 为 section_index，value 为 body 元素列表中的插入位置。
        """
        boundaries: Dict[int, int] = {0: 0}
        section_break_count = 0

        for supp in supplements:
            if supp.type == "section_break":
                section_break_count += 1
                # 分节符后的位置作为下一节的起始
                boundaries[section_break_count] = supp.position_index + 1

        return boundaries
