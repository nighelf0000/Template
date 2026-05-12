"""分类器引擎 — 编排整个识别流程。"""

from __future__ import annotations

import datetime
from typing import Any, Dict, List, Optional, Set, Union

import docx

from ..loader.document_loader import DocumentLoader
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

        # 2. 加载规则集
        ruleset = self._get_ruleset()

        # 3. 样式分析
        features = StyleAnalyzer.analyze_from_document(document)

        # 4. XML 补充解析
        xml_parser = XmlParser(document)
        supplements = xml_parser.parse()

        # 5. 分类 + 提取
        elements = self._classify_and_extract(document, features, supplements, ruleset)

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
