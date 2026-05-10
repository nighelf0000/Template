# PDF 预览图例底色与段落截断悬停提示修复设计文档

## 概述

本文档针对 Word 模板解析系统的两个前端展示问题进行排查分析与修复方案设计：一是左侧 PDF 预览中样式图例（Legend）的底色未能正确展示，二是段落调整列表中长文字被截断后缺少悬停查看完整内容的交互。

---

## 一、问题 1：PDF 预览图例底色没有正确展示

### 1.1 数据流概览

```
┌─────────────────────────────────────────────────────────────────┐
│ 后端 /api/word/{id}/preview                                     │
│                                                                 │
│  WordParseService.preview()                                     │
│   ├─ 从 parsedJson 反序列化 ParagraphMatch 列表                  │
│   ├─ 从 template_rule 表查询规则列表（含 highlightColor）        │
│   ├─ 调用 determineBackgroundColor() 确定每个段落的底色          │
│   │   ├─ 优先取 rule.highlightColor（十六进制，如 #FF0000）      │
│   │   └─ 否则按 ruleId 自动生成 hsl(hue, 60%, 85%)              │
│   ├─ 调用 generateLegend() 生成图例列表（含 color）              │
│   └─ 返回 PreviewResultDTO { pdfUrl, paragraphs, legend }       │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ 前端 ParsePreview.vue                                           │
│                                                                 │
│  getPreview(id) → ParseResult                                   │
│   ├─ legend[] → 右侧图例面板（颜色方块 + 规则名 + 类型标签）     │
│   ├─ paragraphs[] → 右侧段落调整列表（含 backgroundColor）       │
│   └─ pdfUrl → 独立 HTTP 请求，获取 PDF 文件渲染到 Canvas        │
│                                                                 │
│  renderHighlights()                                             │
│   ├─ 用 pdf.js 的 getTextContent() 提取 PDF 文本项              │
│   ├─ 遍历每个段落的 backgroundColor 和 text                     │
│   ├─ 在 PDF 文本项中搜索段落文本（前 50 字符）                   │
│   └─ 匹配成功后在半透明 Canvas 上绘制彩色矩形                    │
└─────────────────────────────────────────────────────────────────┘
```

### 1.2 涉及文件

| 层面 | 文件路径 | 关键逻辑 |
|------|----------|----------|
| 后端 DTO | `.../dto/PreviewResultDTO.java` | `legend`（`List<LegendItemDTO>`）和 `paragraphs`（`List<ParagraphItemDTO>`） |
| 后端 DTO | `.../dto/LegendItemDTO.java` | `color: String` 字段 |
| 后端 DTO | `.../dto/ParagraphItemDTO.java` | `backgroundColor: String` 字段 |
| 后端 Service | `.../service/WordParseService.java` | `determineBackgroundColor()`（第 347-356 行）、`generateLegend()`（第 319-341 行） |
| 后端 Service | `.../service/RecognitionEngine.java` | `ParagraphMatch.recognize()` 中 `text` 截断为 200 字符（第 43 行） |
| 后端 PDF 转换 | `.../service/pdf/PoiPdfConversionService.java` | `renderParagraphs()` 仅渲染文本，无背景色；`sanitizeText()` 清洗字符；`wrapText()` 重新分行 |
| 前端组件 | `frontend/src/views/file/ParsePreview.vue` | 图例渲染（第 82-96 行）、`renderHighlights()`（第 366-413 行） |
| 前端类型 | `frontend/src/types/api.ts` | `LegendItem` 接口（`color: string`）、`ParagraphItem` 接口（`backgroundColor?: string`） |

### 1.3 根因定位

**根因：`renderHighlights()` 的文本匹配策略不可靠，导致 PDF Canvas 高亮叠加层几乎无法正确绘制。**

详细分析：

```
renderHighlights() 核心匹配逻辑（第 390 行）：
  itemText.includes(searchText.substring(0, Math.min(searchText.length, 50)))

问题：
  1. PDF 文本项粒度 vs. 搜索串长度不匹配
     - pdf.js 的 getTextContent() 返回的 items 是 PDF 内部的文本块，
       每个 item 通常对应一个 XWPFRun（一个单词或短句）
     - 搜索串取段落前 50 个字符（约 25 个中文字或 50 个英文字母）
     - 单个 PDF 文本项几乎不可能包含长达 50 字符的内容
     - 结果：匹配率极低 -> 高亮几乎全部缺失

  2. 文本内容不一致
     - 段落文本来自 parsedJson（由 RecognitionEngine 生成），
       其中 text 被截断为 200 字符（RecognitionEngine.java 第 43 行）
     - PDF 由 PoiPdfConversionService 从 originalContent（原始 docx）生成，
       sanitizeText() 会移除控制字符和部分 Unicode 符号
     - 两端文本可能在控制字符、空格、特殊符号上存在差异

  3. 分行导致跨 item 匹配失败
     - PoiPdfConversionService 的 wrapText() 对文本做了重新换行
     - PDF 中的分行方式和原始 docx/Run 结构不同
     - 段落文本可能分布到多个 PDF 文本项中，但匹配只检查单个 item

  4. 图例颜色本身正确
     - 后端 determineBackgroundColor() 和 generateLegend() 逻辑无误
     - 图例面板（右侧）的 color 小方块渲染正常（使用 backgroundColor CSS）
     - 问题不是"图例颜色错了"，而是"图例颜色没能投射到 PDF 上"
```

**结论：问题出在前端 `renderHighlights()` 的文本匹配策略，而非后端颜色值或图例面板渲染。**

### 1.4 修复方案

#### 方案 A（推荐）：基于段落索引的有序位置匹配

**思路**：放弃"文本字符串匹配"，改用**段落顺序和文本项的累积位置**来推断每个段落对应的 PDF 文本项范围。

**具体做法**：

1. 在 `preview()` 接口返回中增加段落位置提示：
   - 在 `ParagraphItemDTO` 中增加 `pageIndex`（页码）和 `orderInPage`（页内顺序），由 PDF 生成服务在生成时记录。
   - 或更轻量：前端根据 PDF 文本项的顺序与段落数组的顺序对齐（段落数组保持 docx 原始顺序，PDF 文本项也按页面顺序排列）。

2. 前端 `renderHighlights()` 修改为：
   ```
   a. 获取 PDF 所有页面的 getTextContent()
   b. 将各页面文本项按顺序展开为一个大数组
   c. 按段落顺序逐段匹配：从上次结束位置开始累加文本项，
      直到累积文本长度接近当前段落长度为止
   d. 在这些文本项的 bounding box 范围上绘制高亮
   ```

**优点**：
- 不依赖字符串精确匹配，鲁棒性高
- 段落顺序与 docx 原始顺序一致，天然对齐
- 可处理分页、跨 item 等情况

**缺点**：
- 实现复杂度较高
- 如果段落顺序与 PDF 文本项顺序不一致（如表格、文本框），则可能偏移

---

#### 方案 B（较简单）：逐词/逐字符匹配

**思路**：将搜索粒度从"段落前 50 字符"降低到"分词后逐词匹配"。

**具体做法**：
```
a. 将段落文本按空格/标点分词
b. 对每个词，在 PDF 文本项中搜索
c. 统计匹配到的词占总词数的比例
d. 如果匹配比例 > 阈值（如 70%），则在这些词对应的位置绘制高亮
```

**优点**：
- 实现简单，改动量小
- 能处理部分文本差异

**缺点**：
- 仍然依赖字符串匹配，对文本差异敏感
- PDF 文本项的位置计算较复杂

---

#### 方案 C（大改动，长期最优）：服务端渲染带底色的 PDF

**思路**：在 PDF 生成阶段直接将背景色嵌入 PDF，前端无需再叠加高亮。

**具体做法**：
1. 修改 `PoiPdfConversionService`，在 `renderParagraphs()` 中接收段落匹配信息
2. 对每个段落，在绘制文本前先用 `PDPageContentStream.addRect()` + `fill()` 绘制底色矩形
3. 前端移除 `renderHighlights()` 逻辑

**优点**：
- PDF 本身即包含颜色，前端零工作量
- 不受文本匹配限制，100% 准确
- 导出/打印时颜色一并保留

**缺点**：
- 需要重构 PDF 生成服务的接口签名
- 需要将段落匹配数据传递到 PDF 生成层
- 改动范围大，涉及后端多个模块

---

### 1.5 方案推荐：方案 A（基于位置匹配）

推荐理由：
1. 方案 B 仍存在匹配失败的场景（分词后部分词汇被 sanitizeText 修改或 split 到不同文本项），可靠性不足
2. 方案 C 是最彻底的方案，但当前阶段成本过高，适合后续迭代
3. 方案 A 可在前端独立完成，不涉及后端接口变更，平衡了效果和成本

**问题（向用户提问）：**
> PDF 高亮渲染有两种改进方向。方案 A（推荐）基于段落顺序和 PDF 文本项位置对齐，不需要后端配合；
> 方案 C 是在服务端 PDF 生成时直接绘制底色，效果最准确但需要后端较大改动。
> 
> 请问您的倾向？或者是否有其他考虑？

---

## 二、问题 2：段落文字截断缺少悬停提示

### 2.1 当前实现

**文件**: `C:\AITeam\Projects\Template\frontend\src\views\file\ParsePreview.vue`

**HTML（第 121-123 行）：**
```html
<div v-if="paragraphStylePreview(para)" class="adjust-preview" 
     :style="paragraphInlineStyle(para.style)">
  {{ para.text?.substring(0, 60) }}{{ para.text?.length > 60 ? '...' : '' }}
</div>
```

**CSS（第 667-674 行）：**
```css
.adjust-preview {
  padding: 4px 10px 8px;
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
```

### 2.2 根因定位

存在两层截断，但均无展示完整内容的机制：

| 截断方式 | 实现位置 | 行为 |
|----------|----------|------|
| JS 截断 | Vue 模板: `para.text?.substring(0, 60)` | 硬截断 60 字符，手动拼接 `...` |
| CSS 截断 | `.adjust-preview`: `text-overflow: ellipsis` | 容器边界超出时自动省略号截断 |

两种截断都没有提供 `title` 属性或 `el-tooltip` 来显示完整文本。

### 2.3 修复方案

#### 方案 A（推荐）：`el-tooltip` 包裹文本

使用 Element Plus 的 `<el-tooltip>` 组件包裹文本内容，当鼠标悬停时展示完整文本。

```html
<el-tooltip :content="para.text" placement="top" :show-after="300">
  <div class="adjust-preview" :style="paragraphInlineStyle(para.style)">
    {{ para.text?.substring(0, 60) }}{{ para.text?.length > 60 ? '...' : '' }}
  </div>
</el-tooltip>
```

**优点**：
- Element Plus 原生组件，样式统一
- `show-after` 控制延迟，避免频繁弹出干扰操作
- 支持长文本自动换行展示

**缺点**：
- 需要额外组件嵌套

#### 方案 B（轻量）：原生 `title` 属性

在 div 上添加 `:title="para.text"`：

```html
<div class="adjust-preview" :style="paragraphInlineStyle(para.style)" :title="para.text">
  {{ para.text?.substring(0, 60) }}{{ para.text?.length > 60 ? '...' : '' }}
</div>
```

**优点**：
- 零依赖，实现最简单
- 浏览器原生支持

**缺点**：
- 无延迟控制，鼠标经过即显示
- 无样式定制能力
- 长文本不换行，超出视口部分不可见

### 2.4 方案推荐：方案 A（`el-tooltip`）

推荐理由：与项目中 Element Plus 生态系统一致，体验更好，支持延迟和样式定制。

---

## 三、改动范围汇总

### 问题 1：PDF 图例底色

| 文件 | 改动类型 | 说明 |
|------|----------|------|
| `frontend/src/views/file/ParsePreview.vue` | 修改 | 重写 `renderHighlights()` 方法，用位置对齐替代文本字符串匹配 |

### 问题 2：段落截断悬停提示

| 文件 | 改动类型 | 说明 |
|------|----------|------|
| `frontend/src/views/file/ParsePreview.vue` | 修改 | 在 `.adjust-preview` 外层包裹 `<el-tooltip>`，或添加 `title` 属性 |

---

## 四、约束与风险

| 风险项 | 说明 | 应对方案 |
|--------|------|----------|
| PDF 文本项顺序与段落顺序不一致 | 含表格、文本框、页眉页脚时，PDF 文本提取顺序可能改变 | 方案 A 仅适用于纯文本段落；如有复杂布局需降级为方案 C（服务端渲染） |
| 段落调整后文本变更 | parsedJson 被修改后，段落文本可能与原始 docx 不一致 | 方案 A 基于位置索引，不依赖文本内容，不受影响 |
| `el-tooltip` 与现有布局冲突 | `.adjust-preview` 父容器可能影响 tooltip 定位 | 设置 `placement="top"` 并确保父容器 `overflow` 正确 |

---

## 五、待确认问题（向用户提问）

### 问题 1：PDF 高亮实现路径

> PDF 预览中，图例颜色无法在 PDF 文档上正确显示，原因是前端 `renderHighlights()` 方法通过"文本内容匹配"来定位需要高亮的段落，但 PDF 文本提取的粒度很细（按单词/短句切分），而匹配代码使用段落前 50 个字符去搜索，导致几乎无法命中。
>
> **可选方案：**
>
> A（推荐）**前端按位置对齐**：利用段落数组保持 docx 原始顺序的特性，将 PDF 文本项按顺序与段落对齐绘制高亮。不依赖字符串匹配，不需要后端配合。
>
> B **仍用字符串匹配但降低粒度**：将段落分词后逐词匹配，统计匹配率。实现简单但仍有失败场景。
>
> C **服务端生成带底色的 PDF**：在 `PoiPdfConversionService` 中直接绘制背景色，前端不再需要叠加层。效果最准确但需要后端较大改动。
>
> **请问您倾向于哪个方案？**

### 问题 2：段落截断提示方式

> 段落调整列表中，文字被截断时没有悬停查看完整内容的交互。
>
> **可选方案：**
>
> A（推荐）**使用 `<el-tooltip>`**：Element Plus 原生组件，支持延迟显示，样式统一。
>
> B **使用原生 `title` 属性**：最简单快捷，但无延迟控制，体验较粗糙。
>
> **请问您倾向于哪个方案？**
