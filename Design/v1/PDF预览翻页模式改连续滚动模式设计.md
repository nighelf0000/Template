# PDF 预览翻页模式改连续滚动模式 — 设计文档

## 1. 概述

### 1.1 改造目标

将 ParsePreview.vue 中的 PDF 预览从**单页翻页模式**改造为**连续滚动模式**：

- 移除翻页控件（上/下页按钮、页码输入框）
- 所有 PDF 页面在垂直方向连续堆叠
- 用户通过垂直滚动条浏览完整 PDF 内容
- 保留右侧图例与段落调整面板，不受影响

### 1.2 受影响的文件

| 文件 | 影响程度 | 说明 |
|------|---------|------|
| `frontend/src/views/file/ParsePreview.vue` | 大改 | 模板、脚本、样式均需改造 |
| `frontend/src/types/api.ts` | 无影响 | 类型定义不变 |
| 后端接口 | 无影响 | 接口和数据格式不变 |

---

## 2. 多页渲染策略分析

### 2.1 两种方案对比

| 维度 | 方案A：每页独立 `<canvas>` 对 | 方案B：单张超长 Canvas |
|------|-------------------------------|------------------------|
| **实现方式** | 每页一个 `div.pdf-page-container`，内含 `canvas.pdf-canvas` + `canvas.highlight-canvas` | 将所有页面像素数据依次写入同一个超长 canvas 的对应 y 偏移位置 |
| **高亮叠加** | 各页独立叠加，可复用现有 `renderHighlights()` 逻辑，每页单独调用 | 需要在渲染所有 PDF 内容后，在内存中统一计算高亮位置再绘制到超长 canvas |
| **内存 / GPU** | 按页分配 GPU 内存，支持按需创建和销毁 | 单张 canvas 总尺寸 = 宽 × (各页高之和)，大文档会超浏览器限额（多数浏览器上限 16384px） |
| **局部更新** | 更新/重绘一页不影响其他页面 | 更新单页内容需要整体重绘 |
| **懒加载支持** | 原生支持 Intersection Observer，未进入视口的页面可以完全不创建/渲染 canvas | 必须先渲染所有内容才能显示，无法懒加载 |
| **DOM 复杂度** | 页面数即 DOM 节点数（PDF Canvas + 高亮 Canvas × 页数） | 仅 2 个 DOM 节点 |
| **缩放调整** | 每页独立重新渲染，工作量随页数线性增长 | 整体缩放需要重新绘制整张 canvas |

### 2.2 推荐方案

**采用方案A（每页独立 `<canvas>` 对 + Intersection Observer）**。核心理由：

1. **避免浏览器 Canvas 尺寸限制**（超长 canvas 在 50+ 页时很容易突破 16384px 上限）
2. **高亮系统可直接复用既有逻辑**，仅需将 `renderHighlights()` 按页拆分
3. **内存控制更灵活**：仅渲染可见区域 ± 若干缓冲区页面，大文档可稳定运行
4. **缩放调整时不需整体冲刷**：仅重绘可见页面即可

---

## 3. 高亮适配方案

### 3.1 当前高亮绘制流程的问题

当前 `renderHighlights()` 假设只有一个高亮 Canvas，依赖 `currentPage.value` 作为过滤条件：

```typescript
// 当前代码 L490-496：在段落匹配循环内按 currentPage 过滤
for (let i = startItemIdx; i <= endItemIdx; i++) {
  if (allItems[i].pageNum === currentPage.value) {
    pageItems.push(allItems[i])
  }
}
```

### 3.2 改造后的高亮流程

1. **预取阶段优化** (`prefetchAllPageTexts`)
   - 当前已按页码标记每个文本项 (`pageNum: p`)
   - 增加：将 `allPagesTextItems` 按页索引组织为 `Map<number, TextItem[]>`
   - 同时预计算每页文本项的累计结束位置数组 `Map<number, number[]>`
   - 此优化避免高亮绘制时每次都走全局过滤 + 全局二分查找

2. **绘制阶段拆分**
   - 新增 `renderHighlightsForPage(pageNum, canvas, scale)` 函数
   - 接收单页的 canvas 引用和页号
   - 从预构建的 Map 中取出该页的文本项和位置数组
   - 复用现有段落匹配逻辑，但过滤条件改为仅匹配当前页

3. **调用时机**
   - 在每页的 PDF Canvas 渲染完成之后立即调用
   - 与原 `renderPage()` 中调用 `renderHighlights()` 的时序一致

### 3.3 关键代码变化

```typescript
// 新增数据结构
const pageTextItems = ref<Map<number, { items: any[], endPos: number[] }>>(new Map())

// prefetchAllPageTexts 改造：按页索引
async function prefetchAllPageTexts() {
  // ... 遍历各页获取 textContent ...
  const perPage = new Map<number, { items: any[], endPos: number[] }>()
  for (let p = 1; p <= totalPages; p++) {
    const page = await pdfDoc.value.getPage(p)
    const tc = await page.getTextContent()
    const items: any[] = []
    let acc = 0
    const endPos: number[] = []
    for (const item of tc.items as any[]) {
      items.push({ ...item, pageNum: p })
      acc += (item.str || '').length
      endPos.push(acc)
    }
    perPage.set(p, { items, endPos })
    page.cleanup()
  }
  // 合并 allPagesTextItems 保持向下兼容（可选）
  pageTextItems.value = perPage
}

// 新增：按页渲染高亮
async function renderHighlightsForPage(pageNum: number, canvas: HTMLCanvasElement, scale: number) {
  const context = canvas.getContext('2d')
  if (!context || !parseResult.value) return
  context.clearRect(0, 0, canvas.width, canvas.height)

  const pageData = pageTextItems.value.get(pageNum)
  if (!pageData || pageData.items.length === 0) return

  const page = await pdfDoc.value!.getPage(pageNum)
  const viewport = page.getViewport({ scale })
  // ... 复用现有段落匹配逻辑，使用 pageData.items 和 pageData.endPos ...
  page.cleanup()
}
```

---

## 4. 性能与内存策略

### 4.1 文本预取（必须保留并加强）

`prefetchAllPageTexts()` 在滚动模式下是**必需的**，理由：

- 高亮系统依赖全量文本位置信息来定位段落
- 文本数据仅占内存（每页 ~几 KB 到几十 KB JSON），非 GPU 资源
- 50 页文档的文本数据通常 < 2MB，可一次性全部加载

### 4.2 Canvas 内存分析

以 A4 页面、缩放比 1.2x 为例：

| 元素 | 尺寸 | 单页内存 | 50 页全部渲染 |
|------|------|---------|-------------|
| pdf-canvas | ~993 × 1404 px | ~5.3 MB (RGBA) | ~265 MB |
| highlight-canvas | 同上 | ~5.3 MB | ~265 MB |
| **合计** | | **~10.6 MB/页** | **~530 MB** |

530 MB 对于浏览器来说偏高，所以需要**视口懒渲染**策略。

### 4.3 视口懒渲染方案（Intersection Observer）

```
                      +------------------+
                      |  视口上方缓冲页    |  ← 保留 3 页已渲染
                      +------------------+
  可视区域 ──────────> +==================+
                      |   当前视口内页面   |  ← 必须渲染
                      +==================+
                      |  视口下方缓冲页    |  ← 保留 3 页已渲染
                      +------------------+
                      |  不可见页面（懒加载）|  ← 不渲染，保留 DOM 占位
                      +------------------+
```

实现要点：

1. **DOM 占位创建**：在 `v-for` 中为每页创建 `.pdf-page-wrapper` 结构（含 canvas 元素），所有页的 DOM 节点始终存在
2. **Intersection Observer 初始化**：在 `loadPdf()` 末尾创建 observer，观察所有 `.pdf-page-wrapper`
3. **进入视口**：触发 `renderSinglePage(pageNum)`，渲染 PDF 内容 + 高亮
4. **离开视口**：暂不销毁 Canvas（避免反复创建/销毁），仅当内存紧张时（通过 `Navigator.deviceMemory` 检测）回收远端页的 Canvas
5. **首次加载**：`loadPdf()` 完成后自动触发 `nextTick()` + 手动检查前 5 页可见性，确保首屏立即可见

### 4.4 内存回收策略（可选优化）

- 当页面数量 > 30 时启用：页面离开视口超过 10 页以上，清除其 Canvas `width = height = 0` 释放 GPU 内存
- 保留文本数据（`pageTextItems`）不受影响，重新进入视口时重新渲染
- 使用 `requestIdleCallback` 执行回收，避免阻塞主线程

---

## 5. 自动缩放策略

### 5.1 问题分析

当前 `calcFitScale()`：

```typescript
function calcFitScale(): number {
  const base = currentPageObj.value.getViewport({ scale: 1 })
  const vw = pdfViewportRef.value.clientWidth
  return vw / base.width   // 基于当前页宽度
}
```

滚动模式下没有"当前页"，且各页宽度通常相同（同一 PDF 文档的页面尺寸一致）。

### 5.2 方案

1. **统一缩放**：在 `loadPdf()` 时通过 `pdfDoc.getPage(1)` 获取首页的 `getViewport({ scale: 1 })` 宽度，除以视口宽度得到全局统一缩放比
2. **处理异宽页面**：极少数 PDF 包含不同宽度页面时，取所有页面宽度的**最大值**进行计算，确保所有页面都不会超出视口
3. **缩放调整时机**：
   - PDF 首次加载完成后计算
   - 窗口 resize 时（通过 `ResizeObserver` 监听 `pdfViewportRef`）
   - 用户手动缩放时（可选的附加功能，本次不实现）

### 5.3 关键代码变化

```typescript
const unifiedScale = ref(2.0)  // 替换 scale.value

async function calcUnifiedScale(): Promise<number> {
  if (!pdfDoc.value || !pdfViewportRef.value) return unifiedScale.value
  const vw = pdfViewportRef.value.clientWidth
  if (vw <= 0) return unifiedScale.value
  
  // 取所有页宽度最大值
  let maxWidth = 0
  for (let p = 1; p <= totalPages.value; p++) {
    const page = await pdfDoc.value.getPage(p)
    const vp = page.getViewport({ scale: 1 })
    maxWidth = Math.max(maxWidth, vp.width)
    page.cleanup()
  }
  return vw / maxWidth
}
```

> 注：遍历所有页面获取宽度在极端大文档下会增加加载时间。可优化为仅采样前 5 页和后 5 页，或要求 PDF 提供标准页面尺寸。当前实现取保守策略。

---

## 6. 模板结构设计

### 6.1 当前 DOM 结构

```html
<div class="pdf-viewport" ref="pdfViewportRef">
  <div class="pdf-page-container">
    <canvas ref="pdfCanvasRef" class="pdf-canvas" />
    <canvas ref="highlightCanvasRef" class="highlight-canvas" />
  </div>
</div>
<!-- 翻页控制 -->
<div class="page-controls">
  <el-button>上一页</el-button>
  <el-input-number v-model="currentPage" />
  <span>/ {{ totalPages }}</span>
  <el-button>下一页</el-button>
</div>
```

### 6.2 改造后的 DOM 结构

```html
<div class="pdf-viewport" ref="pdfViewportRef">
  <div class="pdf-scroll-container" ref="pdfScrollContainerRef">
    <div
      v-for="pageNum in totalPages"
      :key="pageNum"
      class="pdf-page-wrapper"
      :data-page-num="pageNum"
    >
      <div class="pdf-page-container">
        <!-- PDF 内容 Canvas -->
        <canvas
          :ref="(el) => setPageCanvasRef(pageNum, 'pdf', el as HTMLCanvasElement)"
          class="pdf-canvas"
        />
        <!-- 高亮叠加 Canvas -->
        <canvas
          :ref="(el) => setPageCanvasRef(pageNum, 'highlight', el as HTMLCanvasElement)"
          class="highlight-canvas"
        />
      </div>
    </div>
  </div>
</div>
<!-- 翻页控件已被完全移除 -->
```

### 6.3 新增 ref 管理

由于 `v-for` 中不能直接使用固定 ref 名称，需通过函数管理 canvas 引用：

```typescript
// 存储所有页面的 canvas 引用
const pageCanvases = reactive<Map<number, {
  pdf: HTMLCanvasElement | null
  highlight: HTMLCanvasElement | null
}>>(new Map())

function setPageCanvasRef(pageNum: number, type: 'pdf' | 'highlight', el: HTMLCanvasElement | null) {
  if (!pageCanvases.has(pageNum)) {
    pageCanvases.set(pageNum, { pdf: null, highlight: null })
  }
  pageCanvases.get(pageNum)![type] = el
}
```

### 6.4 新增 CSS 要点

```css
.pdf-viewport {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  background: #e0e0e0;
}

.pdf-scroll-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;              /* 页面之间的间距 */
  padding: 12px 0;        /* 上下边距 */
}

.pdf-page-wrapper {
  flex-shrink: 0;          /* 防止被压缩 */
}

.pdf-page-container {
  position: relative;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.15);
  background: #fff;
  line-height: 0;
}

.pdf-canvas {
  display: block;
}

.highlight-canvas {
  position: absolute;
  top: 0;
  left: 0;
  pointer-events: none;
}
```

---

## 7. 翻页控件处理

### 7.1 翻页按钮与页码输入

**完全移除**。需求明确要求移除：

- 删除 `<div class="page-controls">` 及其内部的所有元素
- 删除 `goToPage()` 函数
- 删除 `currentPage` ref（或废弃，改为其他用途）

### 7.2 顶部"当前页：X/Y"显示

**保留但改造为纯显示**：

```html
<!-- 顶栏中间 -->
<div class="top-bar-center" v-if="parseResult">
  <span>模板：<strong>{{ parseResult.templateName }}</strong></span>
  <el-divider direction="vertical" />
  <span>段落总数：<strong>{{ parseResult.paragraphs.length }}</strong></span>
  <el-divider direction="vertical" />
  <span>总页数：<strong>{{ totalPages }}</strong></span>  <!-- 仅显示总页数 -->
</div>
```

- 移除 `currentPage` 的响应式显示
- 改为静态显示 `总页数：N`（或完全移除该字段）
- 不提供任何翻页交互元素

### 7.3 保留的变量

| 原始变量 | 处理方式 | 说明 |
|---------|---------|------|
| `currentPage` | **移除** | 翻页模式的核心变量，滚动模式下无意义 |
| `totalPages` | **保留** | 用于 `v-for` 循环和总页数显示 |
| `currentPageObj` | **移除** | 单页对象，改为按页获取后立即 cleanup |
| `scale` | **替换** | 改为 `unifiedScale`，全局统一缩放比 |

---

## 8. 完整改造清单

### 8.1 新增函数

| 函数 | 职责 |
|------|------|
| `calcUnifiedScale(): Promise<number>` | 计算全局统一缩放比，遍历各页取最大宽度 |
| `renderSinglePage(pageNum: number): Promise<void>` | 渲染单页 PDF + 高亮 |
| `renderHighlightsForPage(pageNum, canvas, scale)` | 按页渲染高亮 |
| `setupIntersectionObserver(): void` | 初始化 Intersection Observer 监听页面可见性 |
| `setPageCanvasRef(pageNum, type, el): void` | 管理各页 canvas 引用 |
| `releasePageCanvas(pageNum: number): void` | 释放远离视口的页面的 GPU 内存（可选） |

### 8.2 改造函数

| 原函数 | 改造要点 |
|--------|---------|
| `loadPdf()` | 移除 `renderPage(1)` 调用；改为计算 `unifiedScale` + 创建 DOM + 启动 Intersection Observer |
| `prefetchAllPageTexts()` | 增加按页索引的数据结构 `pageTextItems` |
| `renderPage(pageNum)` | 替换为 `renderSinglePage(pageNum)`，不再依赖 `currentPageObj` |
| `renderHighlights()` | 替换为 `renderHighlightsForPage(pageNum, canvas, scale)` |
| `calcFitScale()` | 替换为 `calcUnifiedScale()` |
| `goToPage(page)` | **删除** |

### 8.3 生命周期变化

```typescript
onMounted(() => {
  // 不变：获取文件 ID，加载文件列表，触发预览
})

onUnmounted(() => {
  isMounted.value = false
  if (pdfDoc.value) {
    pdfDoc.value.destroy()
    pdfDoc.value = null
  }
  if (intersectionObserver.value) {
    intersectionObserver.value.disconnect()  // 新增：断开 Observer
  }
})

// 新增：ResizeObserver 监听视口宽度变化
let resizeObserver: ResizeObserver | null = null
onMounted(() => {
  // 在 loadPdf 中创建，或在独立的 setup 中监听
  resizeObserver = new ResizeObserver(() => {
    if (pdfDoc.value) {
      recalcAndRerender()  // 重新计算缩放比并重渲染可见页面
    }
  })
  if (pdfViewportRef.value) {
    resizeObserver.observe(pdfViewportRef.value)
  }
})
```

---

## 9. 风险与注意事项

### 9.1 风险

| 风险 | 等级 | 缓解措施 |
|------|------|---------|
| 大文档（100+ 页）DOM 节点过多 | 中 | Intersection Observer 控制 Canvas 内容渲染，DOM 结构轻量（仅 div） |
| 首屏加载变慢（需计算所有页宽度） | 低 | `calcUnifiedScale()` 可改为采样前 N 页取最大宽度 |
| 高亮偏移（各页缩放独立可能不一致） | 低 | 统一缩放比确保所有页使用相同的 scale |
| `v-for` ref 获取时序问题 | 中 | 用 `nextTick()` + 函数 ref 确保 canvas 元素已挂载 |

### 9.2 注意事项

1. **canvas 的 `willReadFrequently`**：Canvas 2D 默认使用 GPU 加速，但在连续滚动场景下频繁的 getContext 调用可能需要设置 `{ willReadFrequently: true }`（如果需要读取像素数据）
2. **打印功能**：当前未涉及打印功能，无需考虑
3. **段落滚动联动**：右侧段落面板点击后如有需要可联动滚动到对应页面，本次不纳入改造范围
4. **`zoom` 缩放控制**：当前仅保留自动适配宽度，不增加手动缩放控件

---

## 10. 实现顺序建议

| 步骤 | 内容 | 预估工时 |
|------|------|---------|
| 1 | 改造 `prefetchAllPageTexts` 增加 `pageTextItems` 按页索引 | 0.5h |
| 2 | 新增 `calcUnifiedScale()` 替代 `calcFitScale()` | 0.5h |
| 3 | 改造 DOM 模板为 `v-for` 多页结构 + canvas ref 管理 | 1h |
| 4 | 新增 `renderSinglePage()` 和 `renderHighlightsForPage()` | 1h |
| 5 | 新增 Intersection Observer 懒加载逻辑 | 1h |
| 6 | 移除翻页控件相关代码（模板 + 脚本 + 样式） | 0.5h |
| 7 | 安装 Intersection Observer polyfill（如需兼容旧浏览器） | 0.5h |
| 8 | 窗口 resize 重渲染逻辑 | 0.5h |
| 9 | 测试与调试（重点：高亮偏移、大文档性能、resize 缩放） | 2h |
| **合计** | | **~7.5h** |
