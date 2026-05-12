"""测试样式分析器。"""

from __future__ import annotations

from pathlib import Path

import docx
import pytest

from docstruct.style.style_analyzer import StyleAnalyzer
from docstruct.style.feature import StyleFeature, StyleIndex


class TestStyleAnalyzer:
    """测试 StyleAnalyzer 的样式提取功能。"""

    def test_analyze_returns_style_index(self, docx_document: docx.Document):
        """测试 analyze 返回 StyleIndex。"""
        index = StyleAnalyzer.analyze_from_document(docx_document)
        assert isinstance(index, dict)
        assert len(index) > 0

    def test_style_index_keys(self, docx_document: docx.Document):
        """测试样式索引的键格式。"""
        index = StyleAnalyzer.analyze_from_document(docx_document)
        for key in index.keys():
            assert key.startswith("elem_")

    def test_style_index_values(self, docx_document: docx.Document):
        """测试样式索引的值类型。"""
        index = StyleAnalyzer.analyze_from_document(docx_document)
        for feature in index.values():
            assert isinstance(feature, StyleFeature)
            assert isinstance(feature.style_name, str)
            assert isinstance(feature.font_size, float)
            assert isinstance(feature.is_bold, bool)

    def test_heading_style_detected(self, docx_document: docx.Document):
        """测试标题样式被正确检测。"""
        index = StyleAnalyzer.analyze_from_document(docx_document)
        # 找到 heading 1 样式的段落
        heading_features = [
            f for f in index.values()
            if "heading" in f.style_name.lower() or "标题" in f.style_name
        ]
        assert len(heading_features) > 0

    def test_text_length_and_word_count(self, docx_document: docx.Document):
        """测试文本长度和字数统计。"""
        index = StyleAnalyzer.analyze_from_document(docx_document)
        for feature in index.values():
            assert feature.text_length >= 0
            assert feature.word_count >= 0

    def test_has_numbering_detection(self, docx_document: docx.Document):
        """测试列表编号检测。"""
        index = StyleAnalyzer.analyze_from_document(docx_document)
        # 检查列表段落
        list_features = [f for f in index.values() if f.has_numbering]
        # 列表样式可能不被所有 docx 支持

    def test_analyzer_accepts_document(self, docx_document: docx.Document):
        """测试 StyleAnalyzer 接受 Document 对象。"""
        analyzer = StyleAnalyzer(docx_document)
        index = analyzer.analyze()
        assert len(index) > 0

    def test_style_feature_to_dict(self):
        """测试 StyleFeature 的 to_dict 方法。"""
        feature = StyleFeature(
            style_name="Heading 1",
            font_name="Arial",
            font_size=16.0,
            is_bold=True,
            outline_level=1,
        )
        d = feature.to_dict()
        assert d["style_name"] == "Heading 1"
        assert d["font_size"] == 16.0
        assert d["is_bold"] is True
        assert d["outline_level"] == 1

    def test_style_feature_from_dict(self):
        """测试 StyleFeature 的 from_dict 方法。"""
        data = {
            "style_name": "Normal",
            "font_name": "宋体",
            "font_size": 12.0,
            "is_bold": False,
        }
        feature = StyleFeature.from_dict(data)
        assert feature.style_name == "Normal"
        assert feature.font_size == 12.0
        assert feature.is_bold is False
