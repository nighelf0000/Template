# PDF 底色渲染改造：从按行合并改为逐文本项独立绘制

## 1. 概述

**目标**：将 PDF 预览中段落底色高亮的渲染方式，从"按基线 Y 坐标分组合并整行矩形"改为"逐文本项（text item）独立绘制矩形"，使底色仅覆盖文本实际区域，空白间距（行间距、单词间距等）不渲染底色。

**变更范围**：仅修改 `frontend/src/views/file/ParsePreview.vue` 中 `renderHighlights()` 函数（约第496-533行），不动后端、类型定义和模板。

**涉及功能**：

| 功能点 | 当前实现 | 目标实现 |
|--------|----------|----------|
| 底色分组策略 | 按 `Math.round(baselineY)` 分组合并 | 不分组，每项独立绘制 |
| 底色矩形范围 | 行内所有项合并的包围盒（minX ~ maxX） | 每项自身的宽度和高度 |
| 底色覆盖区域 | 整行宽度，含单词间距 | 仅文本项自身区域 |
| fillRect 调用次数 | 每行 1 次 | 每个文本项 1 次 |

---

## 2. 当前实现分析（变更基线）

### 2.1 数据流

```
后端 WordParseService 确定段落颜色
  → ParseResult.paragraphs[].backgroundColor (CSS 颜色字符串)
  → renderHighlights() 中使用 match.backgroundColor

allPagesTextItems (PDF.js 解析出的扁平文本项数组)
  → pageItems (当前页面上的文本项子集)
  → lineMap (按 baselineY 分组)
  → fillRect 绘制整行包围盒
```

### 2.2 关键代码（第496-533行）

```javascript
// 当前逻辑：按基线分组 → 整行合并 → 一个 fillRect
const lineMap = new Map()
for (const item of pageItems) {
  const baselineY = viewport.height - item.transform[5] * effectiveScale
  const key = Math.round(baselineY)
  lineMap.get(key).push({ item, baselineY })
}

context.globalAlpha = 0.35
for (const [, group] of lineMap) {
  let minX = Infinity, maxX = -Infinity, baselineY = 0, maxH = 0
  for (const entry of group) {
    const item = entry.item; baselineY = entry.baselineY
    const x = item.transform[4] * effectiveScale
    const w = item.width * effectiveScale
    const h = item.height * effectiveScale
    minX = Math.min(minX, x)
    maxX = Math.max(maxX, x + w)
    maxH = Math.max(maxH, h)
  }
  if (minX < Infinity) {
    context.fillStyle = match.backgroundColor
    context.fillRect(minX, baselineY - maxH, maxX - minX, maxH)
  }
}
context.globalAlpha = 1.0
```

### 2.3 当前效果的问题

- 同一视觉行（相同 baselineY）的所有文本项被合并成一个矩形
- 矩形宽度为 `maxX - minX`，覆盖了从行首到行尾的全部范围
- 单词间距、字符侧边距等空白区域也被底色覆盖，视觉效果"过宽"
- 多行文本的垂直间距（行间距）区域也有底色，但这一问题在当前行级合并中不明显

---

## 3. 改造方案设计

### 3.1 核心思路

移除分组逻辑，直接遍历 `pageItems` 数组，对每个有效项独立绘制一个底色矩形。

### 3.2 每个文本项的底色矩形定位

基于 PDF.js 文本项的标准字段，转换到 Canvas 坐标：

| 矩形属性 | 计算公式 | 说明 |
|----------|----------|------|
| left (x) | `item.transform[4] * effectiveScale` | PDF tx 变换 → Canvas 像素 |
| top | `baselineY - h` | 基线上方为文字区域 |
| width (w) | `item.width * effectiveScale` | PDF 文本项宽度 → Canvas 像素 |
| height (h) | `item.height * effectiveScale` | PDF 文本项高度/字号 → Canvas 像素 |
| baselineY | `viewport.height - item.transform[5] * effectiveScale` | PDF ty 变换反转 → Canvas Y |

**坐标示意图**（单个文本项）：

```
  ┌──────────────────┐  ← top = baselineY - h
  │  文本底色矩形      │
  │  (fillRect)       │
  │  覆盖文字区域      │
  │                   │
  ├──────────────────┤  ← baseline（基线）
  │                  │
  └──────────────────┘
  ↑                  ↑
  x                  x + w
```

### 3.3 目标代码结构

```javascript
// 移除 lineMap 分组，直接遍历 pageItems
context.globalAlpha = 0.35

for (const item of pageItems) {
  if (!item.transform || item.width == null || item.height == null) continue

  const x = item.transform[4] * effectiveScale
  const baselineY = viewport.height - item.transform[5] * effectiveScale
  const w = item.width * effectiveScale
  const h = item.height * effectiveScale

  context.fillStyle = match.backgroundColor
  context.fillRect(x, baselineY - h, w, h)
}

context.globalAlpha = 1.0
```

### 3.4 视觉效果预期对比

```
当前效果（按行合并）:
  ┌──────────────────────────────────────────────┐
  │██ 这是第一行文本的底色███████████████████████████│  ← 整行覆盖，含单词间距
  │██ 这是第二行文本的底色███████████████████████████│
  └──────────────────────────────────────────────┘

目标效果（逐文本项）:
  ┌──────────────────────────────────────────────┐
  │██ 这是 ██ 第一行 ██ 文本的 ██ 底色              │  ← 仅文字区域
  │██ 这是 ██ 第二行 ██ 文本的 ██ 底色              │
  └──────────────────────────────────────────────┘
```

---

## 4. 关键问题分析与决策

### 4.1 相邻文本项是否合并

| 选项 | 方案 | 评估 |
|------|------|------|
| A（推荐） | **不合并**，每个 item 独立绘制 | 符合"空白不加底色"的需求；文本项间自然留出单词间距 |
| B | 检测相邻 item，若间距 < 1px 则合并 | 增加复杂度；与需求矛盾（应保留空白） |
| C | 添加 0.5px 重叠避免反走样缝隙 | 多数场景不需要；Alpha 0.35 下重叠区域不可见 |

**决策**：采用方案 A，不合并。两个文本项之间若有空白（单词间距等），正是需求要求的留白效果，不应覆盖。

### 4.2 反走样边缘缝隙问题

**现象**：当两个文本项的矩形在 Canvas 上紧邻时（item1.x + item1.w === item2.x），反走样可能产生约 0.5px 的透明天缝。

**影响评估**：
- 由于底色 Alpha = 0.35，即使出现微缝，视觉效果极其微弱
- 在正常缩放比例（100%-150%）下，人眼几乎不可察觉
- 只有在放大到 200%+ 时才可能注意到

**决策**：不特殊处理。如需修复，可考虑在 `fillRect` 时将宽度增加 0.5px（`w + 0.5`），产生微小重叠补偿反走样。

### 4.3 矩形重叠导致 Alpha 叠加

**现象**：若因浮点舍入导致相邻矩形重叠，重叠区域 Alpha = 0.35 + 0.35 = 0.7（叠加），颜色比预期深。

**决策**：
- 理想情况下 PDF 文本项不应重叠，此问题发生概率极低
- 如需最严谨处理，可先用 offscreen canvas 绘制纯色层再整体应用 alpha，但**不建议**（过度设计，且引入额外 canvas 复杂度）

### 4.4 性能影响评估

| 指标 | 当前（按行合并） | 改造后（逐项） | 倍率 |
|------|-----------------|---------------|------|
| fillRect 调用次数 | O(行数) | O(文本项数) | ~3~15x |
| 典型单页调用次数 | 5~20 次 | 50~200 次 | ~10x |
| 内存占用 | O(行数) Map | 无额外结构 | 减少 |
| 逐帧性能 | < 0.1ms | < 0.5ms | 可忽略 |

**结论**：性能影响极低。Canvas 2D `fillRect` 在 Chrome/V8 中使用 GPU 加速，单次调用开销约 0.001~0.005ms。200 次调用仍在亚毫秒级，对 60fps 渲染无影响。

**额外优化点**：可将 `context.fillStyle` 设置提取到 item 循环外部（`match.backgroundColor` 在一个段落内是常数），避免不必要的样式切换。

### 4.5 Canvas 变换矩阵精度

`item.transform[4]` (tx) 和 `item.transform[5]` (ty) 是 PDF 用户空间单位。经过 `effectiveScale` 缩放后，可能产生非整数像素值。

Canvas 在非整数坐标上会自动进行子像素渲染（anti-aliasing），这对底色矩形影响有限，无需额外取整处理。

---

## 5. 改动范围

### 5.1 文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `frontend/src/views/file/ParsePreview.vue` | 修改 `renderHighlights()` | 仅修改第496-533行 |

### 5.2 具体变更内容

**删除**（约第496-531行）：
- `lineMap = new Map()` 及分组循环（第496-508行）
- 内层循环的 minX/maxX/maxH 计算（第512-527行）

**保留**：
- `pageItems` 的收集逻辑（第484-490行）
- `context.globalAlpha = 0.35` 和 `context.globalAlpha = 1.0` 的 Alpha 控制
- `context.fillStyle = match.backgroundColor`（仅移到外层）

**新增**：
- 直接遍历 `pageItems` 的循环，每次绘制单项 `fillRect`

### 5.3 预计改动行数

- 删除约 30 行
- 新增约 10 行
- 净减少约 20 行

---

## 6. 边界情况与异常处理

| 场景 | 处理方式 |
|------|----------|
| `item.transform` 为空 | `continue` 跳过（已有） |
| `item.width` 或 `item.height` 为空 | `continue` 跳过（已有） |
| `item.width === 0` | `fillRect` 绘制零宽矩形，无视觉效果，可添加 `if (w <= 0) continue` 优化 |
| `item.height === 0` | 同上 |
| 跨页文本项 | 已在 `pageItems` 筛选中按 `item.pageNum === currentPage` 过滤 |
| 颜色无效 | `match.backgroundColor` 来自后端，由 `determineBackgroundColor()` 保证合法性 |

---

## 7. 测试要点（给 Agent 3 参考）

1. **视觉对比测试**：在相同文档上对比改前改后截图，确认底色仅覆盖文字区域
2. **中英文混排测试**：中英文混排文档，确认英文字母间距无底色覆盖
3. **大段落测试**：长段落文档，确认性能无明显下降（FPS 保持 60）
4. **零宽文本项测试**：确认空字符串或零宽项不产生异常
5. **缩放测试**：在不同缩放比例（75%, 100%, 150%, 200%）下验证矩形位置精确
6. **多色段落测试**：多个不同颜色的相邻段落，确认颜色边界正确
7. **反走样检查**：在 200% 放大下检查相邻 item 之间是否有明显缝隙

---

## 8. 风险与应对

| 风险 | 概率 | 影响 | 应对措施 |
|------|------|------|----------|
| 相邻 item 微缝可见 | 低 | 低 | 若用户反馈可见，增加 0.5px 右向重叠 |
| 渲染性能下降 | 极低 | 低 | 若页面文本项 > 2000，可退化回行合并模式（但预期不会出现） |
| 与下划线模式不兼容 | 无 | 无 | 下划线模式先于本改造上线，互不冲突 |

---

## 9. 设计确认

以上是完整的改造设计方案。如果确认以上设计，我将把任务交给 Agent 2（开发工程师）开始实现。
