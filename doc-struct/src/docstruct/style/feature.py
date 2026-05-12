"""样式特征数据结构 — StyleFeature 和 StyleIndex。"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional


@dataclass
class StyleFeature:
    """段落样式特征向量。"""

    style_name: str = "Normal"  # Word 样式名称
    font_name: str = ""  # 字体名称
    font_name_east_asia: str = ""  # 中文字体名称
    font_size: float = 10.5  # 字号（磅）
    is_bold: bool = False
    is_italic: bool = False
    color_hex: Optional[str] = None  # 颜色十六进制
    alignment: Optional[str] = None  # 对齐方式
    indent_left: float = 0.0  # 左缩进（磅）
    indent_right: float = 0.0  # 右缩进（磅）
    indent_first_line: float = 0.0  # 首行缩进
    line_spacing: float = 1.0  # 行距倍数
    space_before: float = 0.0  # 段前间距（磅）
    space_after: float = 0.0  # 段后间距（磅）
    outline_level: Optional[int] = None  # 大纲级别
    list_format: Optional[str] = None  # 列表格式信息
    has_numbering: bool = False  # 是否有编号
    numbering_format: Optional[str] = None  # 编号格式（如 "decimal", "bullet"）

    # 文本级统计
    text_length: int = 0
    word_count: int = 0
    has_image: bool = False
    has_table: bool = False

    # 原始样式名（可能为中文版 Word）
    style_name_raw: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        d: Dict[str, Any] = {}
        for k, v in self.__dict__.items():
            if v is not None:
                d[k] = v
        return d

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> StyleFeature:
        return cls(**{k: v for k, v in data.items() if k in cls.__dataclass_fields__})


# 样式索引：段落 ID → StyleFeature
StyleIndex = Dict[str, StyleFeature]
