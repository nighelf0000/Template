# PDF 预览段落类型标记改造：从底色块改为下划线

## 1. 概述

**目标**：将 PDF 预览中段落类型的标记方式从"半透明底色矩形"改为"文本底部的彩色下划线"。

**变更范围**：仅改 `frontend/src/views/file/ParsePreview.vue`，不动后端和类型定义。

**涉及功能**：

| 功能点 | 当前实现 | 目标实现 |
|--------|----------|----------|
| PDF 高亮层 | `context.fillRect()` 绘制半透明彩色矩形 | `context.stroke()` 绘制彩色下划线 |
| 图例面板 | 彩色方块 `legend-color` | 彩色横线 |

---

## 2. 数据流分析（不变部分）

```
后端 WordParseService.determineBackgroundColor()
  → 返回 CSS 颜色字符串，如 "hsl(120, 60%, 85%)" 或 "#FF0000"
  → 存入 ParagraphItemDTO.backgroundColor / LegendItemDTO.color

前端接收 ParseResult
  → paragraphs[].backgroundColor 用于高亮渲染
  → legend[].color 用于图例展示
```

颜色生成逻辑不变，字段名不变，数据类型不变。

---

## 3. PDF 高亮层改造（核心变更）

### 3.1 当前 `renderHighlights()` 逻辑回顾

```javascript
// 当前逻辑（第496-517行）：合并所有文本项的包围盒，画一个矩形
let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity
for (const item of pageItems) {
  // 计算每个文本项的 x, y, w, h
  const x = item.transform[4] * effectiveScale
  const y = viewport.height - (item.transform[5] * effectiveScale) - item.height * effectiveScale
  const w = item.width * effectiveScale
  const h = item.height * effectiveScale
  minX = Math.min(minX, x)
  minY = Math.min(minY, y)
  maxX = Math.max(maxX, x + w)
  maxY = Math.max(maxY, y + h)
}
context.fillStyle = match.backgroundColor
context.globalAlpha = 0.35
context.fillRect(minX, minY, maxX - minX, maxY - minY)
```

### 3.2 下划线绘制设计

#### 3.2.1 核心问题：按行分组

`pageItems` 中可能包含一个段落内的多个文本项，这些文本项可能分布在不同的**视觉行**上（例如一个长段落换行后占据两行或更多行）。需要将同一行的文本项归为一组，每条线对应一行。

**分组依据**：pdf.js 中 `item.transform[5]` 表示该文本项的基线在 PDF 坐标系中的 Y 值。同一行文本项的基线 Y 值相同（或差值在 1px 以内）。

![坐标说明]
- PDF 坐标系：原点左下，Y 向上
- Canvas 坐标系：原点左上，Y 向下
- `transform[5]` = PDF 空间中的基线位置
- Canvas 空间中的基线位置 = `viewport.height - transform[5] * effectiveScale`

#### 3.2.2 分组算法

```
1. 遍历 pageItems，对每个 item：
   a. 计算 canvasBaselineY = viewport.height - item.transform[5] * effectiveScale
   b. 将 canvasBaselineY 四舍五入到整数（容忍 1px 内的浮点误差），作为分组 key

2. 按分组 key 聚合 items，得到 Map<int, item[]>

3. 对每组：
   a. 遍历组内 items，找到 minX 和 maxX（当前代码已计算 x 和 w）
   b. 取组内第一个 item 的 canvasBaselineY 作为该行的基线位置
   c. 在该基线下方 1~2px 处画线
```

#### 3.2.3 下划线参数

| 参数 | 值 | 理由 |
|------|-----|------|
| 线条颜色 | `match.backgroundColor`（不透明） | 与现有颜色一致，取消 alpha 混合以确保线条清晰可见 |
| 线条宽度 | `2.5`（`context.lineWidth = 2.5`） | 2px 太细不明显，3px 偏粗，2.5px 在大部分缩放比例下清晰 |
| 线条位置 | baselineY + 2px | 基线下方 2px，紧贴文字底部，不压字符 |
| lineCap | `'round'` | 圆角端点，视觉柔和，避免尖锐横切 |
| 跨行处理 | 每行独立画线 | 段落跨行时，每行的文字下方都有一条独立下划线 |
| 跨页处理 | 自然分页隔离 | `pageItems` 已按 `pageNum` 过滤，每页只画当前页的下划线 |

#### 3.2.4 伪代码

```javascript
async function renderHighlights() {
  // ... 前期逻辑不变：获取 canvas、context、allItems、viewport、effectiveScale 等 ...

  for (const match of allParagraphs) {
    if (!match.backgroundColor || !match.endOffset || match.endOffset <= match.startOffset) {
      continue
    }

    // 计算字符偏移（不变）
    const startCharPos = match.pdfStartPos != null ? match.pdfStartPos : ...
    const endCharPos = match.pdfEndPos != null ? match.pdfEndPos : ...
    const startItemIdx = ...
    const endItemIdx = ...

    // 收集当前页的文本项（不变）
    const pageItems = []
    for (let i = startItemIdx; i <= endItemIdx; i++) {
      if (allItems[i].pageNum === currentPage.value) {
        pageItems.push(allItems[i])
      }
    }
    if (pageItems.length === 0) continue

    // ==== 新增：按基线 Y 分组 ====
    const lineMap = new Map()

    for (const item of pageItems) {
      if (!item.transform || item.width == null || item.height == null) continue

      // 计算该文本项的基线在 Canvas 中的 Y 坐标
      const baselineY = viewport.height - item.transform[5] * effectiveScale
      const key = Math.round(baselineY)  // 四舍五入取整，容忍 1px 误差

      if (!lineMap.has(key)) {
        lineMap.set(key, [])
      }
      lineMap.get(key).push({ item, baselineY })
    }

    // ==== 新增：为每一行绘制下划线 ====
    context.strokeStyle = match.backgroundColor
    context.lineWidth = 2.5
    context.lineCap = 'round'

    for (const [, group] of lineMap) {
      let minX = Infinity
      let maxX = -Infinity
      let baselineY = 0

      for (const entry of group) {
        const item = entry.item
        baselineY = entry.baselineY
        const x = item.transform[4] * effectiveScale
        const w = item.width * effectiveScale
        minX = Math.min(minX, x)
        maxX = Math.max(maxX, x + w)
      }

      if (minX < Infinity) {
        const lineY = baselineY + 2  // 基线下方 2px
        context.beginPath()
        context.moveTo(minX, lineY)
        context.lineTo(maxX, lineY)
        context.stroke()
      }
    }
  }
}
```

### 3.3 关于透明度

之前底色使用 `globalAlpha = 0.35` 是为了让底色不遮挡文字。下划线本身是在文字下方绘制（实际上文字绘制在 PDF Canvas 上，下划线绘制在 Highlight Canvas 上，两个 Canvas 层叠），所以**下划线不需要透明度**，直接使用满色即可。下划线位于文字区域内部但紧贴底部，不会遮挡文字的可读性。

---

## 4. 图例面板改造

### 4.1 当前模板代码（第82-95行）

```html
<span class="legend-color" :style="{ backgroundColor: item.color }"></span>
```

```css
.legend-color {
  display: inline-block;
  width: 18px;
  height: 18px;
  border-radius: 4px;
  border: 1px solid #d9d9d9;
  flex-shrink: 0;
  margin-top: 2px;
}
```

### 4.2 改造方案

将 `legend-color` span 改为：一个窄长的矩形，视觉上呈现为一条彩色横线。

**HTML 不变**，只改 CSS：

```css
.legend-color {
  display: inline-block;
  width: 24px;          /* 适当加宽，更显眼 */
  height: 4px;          /* 窄高，呈现为线 */
  border-radius: 2px;   /* 两端圆角 */
  flex-shrink: 0;
  margin-top: 8px;      /* 垂直居中于文字 */
  vertical-align: middle;
}
```

**视觉效果**：24x4px 圆角矩形，类似一条彩色粗横线 `▬▬`。

---

## 5. 改动汇总

### 文件清单

| 文件 | 改动类型 | 改动内容 |
|------|---------|---------|
| `frontend/src/views/file/ParsePreview.vue` | 修改 | 见图 5.1 |

### 5.1 `renderHighlights()` 函数内改动（第432-519行）

| 位置 | 改动 |
|------|------|
| 第496-509行（遍历 pageItems 计算包围盒） | 删除：合并包围盒计算 |
| 第510-516行（fillRect 绘制） | 删除 |
| 新增（在 continue 之后） | 新增：按基线 Y 分组逻辑 + 逐行画下划线 |

### 5.2 图例 CSS 改动

| 位置 | 改动 |
|------|------|
| 第706-714行 `.legend-color` | 修改：width/height/border-radius |

### 5.3 无需改动

- 后端 `ParagraphItemDTO.java` / `LegendItemDTO.java` — 字段名和数据语义不变
- 前端 `api.ts` — 类型定义不变
- 后端 `WordParseService.java` — 颜色生成逻辑不变
- 其他 `*.vue` 文件 — 不涉及
- `renderHighlights()` 之外的 PDF 加载逻辑 — 不涉及

---

## 6. 边界情况分析

| 场景 | 行为 |
|------|------|
| 空段落（无文本项） | `pageItems` 为空，跳过 |
| 段落中某页无文本 | `pageItems` 为空，跳过，无下划线 |
| 同一行多个文本项（如加粗切换导致的拆分） | 基线 Y 相同，归入同一组；minX~maxX 覆盖整行 |
| 上标/下标文本 | 基线 Y 与正常文本不同，自成一组；下划线与正常行独立 |
| 首行缩进 | 第一行文本项的 minX 偏右，下划线从缩进后的位置开始 |
| PDF 缩放（scale 变化） | `effectiveScale` 参与所有坐标计算，下划线同步缩放 |
| 跨页段落 | 每页各自渲染，互不影响 |

---

## 7. 注意事项

1. **分组精度的选择**：`Math.round(baselineY)` 在 scale=2.0 时精度约为 0.5px，足以区分不同行。若发现同一行被误分为多组（罕见），可将 key 改为 `Math.floor(baselineY * 2)` 或使用基于 `transform[5]` 原始值的分组。

2. **性能**：新增的分组操作复杂度 O(n)，n 为当前页的文本项数，与现有 fillRect 方案同量级，无额外性能开销。

3. **下划线遮盖**：由于 Highlight Canvas 在 PDF Canvas 上方且 `pointer-events: none`，下划线绘制在透明层上，不与 PDF 文字产生像素竞争，无需担心遮盖问题。

4. **图例视觉一致性**：原来的彩色方块变为彩色横线后，图例整体风格从"色块图"变为"线条图"，建议后续保持这种视觉语言的一致性。
