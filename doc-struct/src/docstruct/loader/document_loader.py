"""文档加载器 — 接收 .docx 文件路径或字节流，使用 python-docx 加载并校验。"""

from __future__ import annotations

import io
import os
import zipfile
from pathlib import Path
from typing import Optional, Union

import docx


class DocumentLoadError(Exception):
    """文档加载过程中的异常基类。"""


class FileNotFoundError_(DocumentLoadError):
    """文件不存在。"""


class InvalidFormatError(DocumentLoadError):
    """文件不是有效的 .docx 格式。"""


class FileCorruptedError(DocumentLoadError):
    """文件损坏。"""


class DocumentLoader:
    """文档加载器。

    接收 .docx 文件路径或字节流，校验文件有效性，返回 python-docx Document 对象。
    """

    @staticmethod
    def load(source: Union[str, Path, bytes]) -> docx.Document:
        """加载 .docx 文件。

        Args:
            source: 文件路径（str 或 Path）或文件字节流（bytes）。

        Returns:
            python-docx Document 对象。

        Raises:
            FileNotFoundError_: 文件不存在。
            InvalidFormatError: 文件不是有效的 .docx 格式。
            FileCorruptedError: 文件损坏无法打开。
        """
        if isinstance(source, (str, Path)):
            return DocumentLoader._load_from_path(str(source))
        elif isinstance(source, bytes):
            return DocumentLoader._load_from_bytes(source)
        else:
            raise InvalidFormatError(f"不支持的输入类型: {type(source)}")

    @staticmethod
    def load_bytes(source: Union[str, Path, bytes]) -> bytes:
        """以字节流形式读取 .docx 文件内容。"""
        if isinstance(source, bytes):
            return source
        path = Path(source)
        if not path.exists():
            raise FileNotFoundError_(f"文件不存在: {path}")
        return path.read_bytes()

    @staticmethod
    def validate(source: Union[str, Path, bytes]) -> bool:
        """校验文件是否为有效的 .docx 格式。"""
        try:
            if isinstance(source, (str, Path)):
                path = Path(source)
                if not path.exists():
                    return False
                with zipfile.ZipFile(str(path), "r") as zf:
                    return "word/document.xml" in zf.namelist()
            elif isinstance(source, bytes):
                with zipfile.ZipFile(io.BytesIO(source), "r") as zf:
                    return "word/document.xml" in zf.namelist()
            return False
        except (zipfile.BadZipFile, Exception):
            return False

    @staticmethod
    def _load_from_path(filepath: str) -> docx.Document:
        path = Path(filepath)
        if not path.exists():
            raise FileNotFoundError_(f"文件不存在: {path}")

        # 检查是否为 .docx 后缀
        if path.suffix.lower() != ".docx":
            raise InvalidFormatError(f"不支持的文件格式: {path.suffix}，仅支持 .docx")

        # 检查 zip 结构
        try:
            with zipfile.ZipFile(str(path), "r") as zf:
                if "word/document.xml" not in zf.namelist():
                    raise InvalidFormatError("文件不是有效的 .docx 格式：缺少 word/document.xml")
        except zipfile.BadZipFile:
            raise InvalidFormatError("文件不是有效的 ZIP 存档")

        # 尝试加载
        try:
            return docx.Document(str(path))
        except Exception as e:
            raise FileCorruptedError(f"文件损坏无法打开: {e}")

    @staticmethod
    def _load_from_bytes(data: bytes) -> docx.Document:
        if not data:
            raise InvalidFormatError("字节流为空")

        try:
            with zipfile.ZipFile(io.BytesIO(data), "r") as zf:
                if "word/document.xml" not in zf.namelist():
                    raise InvalidFormatError("字节流不是有效的 .docx 格式：缺少 word/document.xml")
        except zipfile.BadZipFile:
            raise InvalidFormatError("字节流不是有效的 ZIP 存档")

        try:
            return docx.Document(io.BytesIO(data))
        except Exception as e:
            raise FileCorruptedError(f"字节流数据损坏: {e}")
