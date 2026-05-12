"""图片提取 — 提取图片（尺寸、路径、类型）。"""

from __future__ import annotations

import io
from typing import Any, Dict, List, Optional

import docx
from docx.oxml.ns import qn


class ImageExtractorError(Exception):
    """图片提取异常。"""


class ImageExtractor:
    """图片提取器。

    从 Word 文档中提取内嵌图片的信息（尺寸、路径、类型）。
    """

    def __init__(self, document: docx.Document):
        self._document = document

    def extract_from_paragraph(
        self, paragraph: docx.text.paragraph.Paragraph
    ) -> List[Dict[str, Any]]:
        """从段落中提取所有图片信息。

        Args:
            paragraph: 段落对象。

        Returns:
            图片信息列表。
        """
        images: List[Dict[str, Any]] = []

        for run in paragraph.runs:
            img_info = self._extract_from_run(run)
            if img_info:
                images.append(img_info)

        return images

    def extract_by_rel_id(self, rId: str) -> Optional[Dict[str, Any]]:
        """通过关系 ID 提取图片信息。"""
        try:
            rel = self._document.part.rels[rId]
            if rel is not None and hasattr(rel, "target_part"):
                part = rel.target_part
                content_type = getattr(part, "content_type", "") or ""
                blob = part.blob

                return {
                    "rId": rId,
                    "content_type": content_type,
                    "size_bytes": len(blob),
                    "extension": self._guess_extension(content_type),
                    "width": None,  # 需要解析 image 的 XML 属性
                    "height": None,
                }
        except (KeyError, Exception):
            pass
        return None

    def _extract_from_run(
        self, run: docx.text.run.Run
    ) -> Optional[Dict[str, Any]]:
        """从 Run 中提取图片。"""
        try:
            drawings = run._element.findall(qn("w:drawing"))
            for drawing in drawings:
                # 查找 wp:inline 或 wp:anchor
                inline = drawing.find(qn("wp:inline"))
                anchor = drawing.find(qn("wp:anchor"))

                container = inline or anchor
                if container is None:
                    continue

                # 获取 extent（尺寸）
                extent = container.find(qn("wp:extent"))
                width = None
                height = None
                if extent is not None:
                    cx = extent.get("cx")
                    cy = extent.get("cy")
                    if cx:
                        width = round(int(cx) / 914400, 2)  # EMU 到英寸
                    if cy:
                        height = round(int(cy) / 914400, 2)

                # 获取图片引用
                blip_fill = container.find(qn("a:graphic") + "/" + qn("a:graphicData") + "/" + qn("pic:pic") + "/" + qn("pic:blipFill") + "/" + qn("a:blip"))
                if blip_fill is None:
                    # 尝试其他路径
                    blip_fill = container.find(f".//{{{qn('a:blip').split('}')[0].strip('{')}}}blip")

                r_embed = None
                if blip_fill is not None:
                    r_embed = blip_fill.get(qn("r:embed"))

                if r_embed:
                    try:
                        rel = self._document.part.rels[r_embed]
                        if rel is not None and hasattr(rel, "target_part"):
                            part = rel.target_part
                            content_type = getattr(part, "content_type", "") or ""
                            blob = part.blob
                            return {
                                "rId": r_embed,
                                "content_type": content_type,
                                "size_bytes": len(blob),
                                "extension": self._guess_extension(content_type),
                                "width": width,
                                "height": height,
                                "raw_size": f"{width}\" x {height}\"",
                            }
                    except (KeyError, Exception):
                        pass

            return None
        except Exception:
            return None

    def _guess_extension(self, content_type: str) -> str:
        """根据 MIME 类型猜测图片扩展名。"""
        mapping = {
            "image/png": "png",
            "image/jpeg": "jpg",
            "image/gif": "gif",
            "image/bmp": "bmp",
            "image/tiff": "tiff",
            "image/svg+xml": "svg",
            "image/x-emf": "emf",
            "image/x-wmf": "wmf",
        }
        return mapping.get(content_type, "bin")
