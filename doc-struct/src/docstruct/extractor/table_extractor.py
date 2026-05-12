"""表格提取 — 提取表格数据（行列、合并单元格处理）。"""

from __future__ import annotations

from typing import Any, Dict, List, Optional, Tuple

import docx
from docx.oxml.ns import qn


class TableExtractorError(Exception):
    """表格提取异常。"""


class TableExtractor:
    """表格提取器。

    提取 Word 表格的行列数据、合并单元格处理。
    """

    def __init__(self, document: docx.Document):
        self._document = document

    def extract(self, table: docx.table.Table) -> Dict[str, Any]:
        """提取表格数据。

        Args:
            table: 表格对象。

        Returns:
            包含表格数据的字典：{rows, cols, data, merged_cells, ...}
        """
        rows = len(table.rows)
        cols = 0
        if table.columns:
            cols = len(table.columns)

        # 解析合并单元格信息
        grid = self._build_grid(table, rows, cols)

        data = []
        for r_idx, row in enumerate(table.rows):
            row_data = []
            for c_idx in range(cols):
                cell = None
                try:
                    # 在非合并区域获取单元格
                    if r_idx < len(grid) and c_idx < len(grid[r_idx]) and grid[r_idx][c_idx]:
                        cell = row.cells[c_idx] if c_idx < len(row.cells) else None
                except IndexError:
                    cell = None

                cell_text = ""
                if cell is not None:
                    cell_text = cell.text.strip()

                row_data.append({
                    "row": r_idx,
                    "col": c_idx,
                    "text": cell_text,
                })
            data.append(row_data)

        # 获取合并单元格信息
        merged_cells = self._find_merged_cells(table)

        return {
            "rows": rows,
            "cols": cols,
            "data": data,
            "merged_cells": merged_cells,
        }

    def table_to_text(self, table_data: Dict[str, Any]) -> str:
        """将表格数据转为纯文本表示（供 content.text 使用）。"""
        lines = []
        for row in table_data.get("data", []):
            cells = [cell.get("text", "") for cell in row]
            lines.append(" | ".join(cells))
        return "\n".join(lines)

    def _build_grid(
        self, table: docx.table.Table, rows: int, cols: int
    ) -> List[List[bool]]:
        """构建单元格网格以识别合并区域。"""
        grid = [[True for _ in range(cols)] for _ in range(rows)]

        tbl = table._tbl
        if tbl is None:
            return grid

        for r_idx, tr in enumerate(tbl.findall(qn("w:tr"))):
            if r_idx >= rows:
                break
            for c_idx, tc in enumerate(tr.findall(qn("w:tc"))):
                if c_idx >= cols:
                    break
                # 检查水平合并
                tc_pr = tc.find(qn("w:tcPr"))
                if tc_pr is not None:
                    grid_span = tc_pr.find(qn("w:gridSpan"))
                    v_merge = tc_pr.find(qn("w:vMerge"))
                    h_merge = tc_pr.find(qn("w:hMerge"))

                    # 处理水平合并
                    if grid_span is not None:
                        span_val = grid_span.get(qn("w:val"))
                        if span_val:
                            span = int(span_val)
                            for merge_c in range(1, span):
                                if c_idx + merge_c < cols:
                                    grid[r_idx][c_idx + merge_c] = False

                    # 处理垂直合并
                    if v_merge is not None:
                        val = v_merge.get(qn("w:val"))
                        if val and val == "restart":
                            # 合并起始，查找合并结束
                            for merge_r in range(r_idx + 1, rows):
                                try:
                                    # 检查下一行相同位置的单元格
                                    pass  # 简化处理
                                except (IndexError, Exception):
                                    break

        return grid

    def _find_merged_cells(
        self, table: docx.table.Table
    ) -> List[Dict[str, Any]]:
        """查找表格中的合并单元格。"""
        merged = []

        tbl = table._tbl
        if tbl is None:
            return merged

        for r_idx, tr in enumerate(tbl.findall(qn("w:tr"))):
            for tc in tr.findall(qn("w:tc")):
                tc_pr = tc.find(qn("w:tcPr"))
                if tc_pr is None:
                    continue

                grid_span = tc_pr.find(qn("w:gridSpan"))
                v_merge = tc_pr.find(qn("w:vMerge"))

                span = 1
                if grid_span is not None:
                    val = grid_span.get(qn("w:val"))
                    if val:
                        span = int(val)

                if span > 1 or v_merge is not None:
                    cell_text = tc.text.strip() if tc.text else ""
                    merged.append({
                        "row": r_idx,
                        "col": 0,  # approximate
                        "colspan": span if span > 1 else None,
                        "rowspan": None,
                        "text": cell_text,
                    })

        return merged
