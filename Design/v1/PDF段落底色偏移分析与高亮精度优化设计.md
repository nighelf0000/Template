# PDF 段落底色偏移分析与高亮精度优化设计

## 1. 概述

本文档针对 PDF 预览中段落底色高亮位置偏移问题（startOffset/endOffset 不准确导致底色覆盖到非目标区域）进行分析，并提出修复方案；同时对"是否引入后端字体/字符尺寸参数"进行论证评估。

**涉及范围**：

| 层级 | 文件 | 关键函数/方法 |
|------|------|--------------|
| 后端 | `RecognitionEngine.java` L34-47 | `recognize()` — offset 累计计算 |
| 后端 | `WordParseService.java` L260-278 | `preview()` — pdfStartPos/pdfEndPos 填充 |
| 后端 | `PoiPdfConversionService.java` L72-207 | `convertToPdfWithPositions()` — PDF 生成及字符位置记录 |
| 后端 | `PdfTextPositionExtractor.java` L55-153 | `extractParagraphPositions()` — 从 PDF 提取精确位置 |
| 后端 | `PdfParagraphPosition.java` | DTO：paragraphIndex/pdfStartPos/pdfEndPos |
| 后端 | `ParagraphItemDTO.java` | DTO：startOffset/endOffset/pdfStartPos/pdfEndPos |
| 前端 | `ParsePreview.vue` L432-513 | `renderHighlights()` — Canvas 底色渲染 |
| 前端 | `ParsePreview.vue` L383-409 | `prefetchAllPageTexts()` — PDF 文本项预提取 |

---

## 2. 关键数据流回顾

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                   后端 parse 阶段                                            │
│                                                                             │
│  docx → RecognitionEngine.recognize()                                       │
│           ↓                                                                 │
│   ParagraphMatch(startOffset, endOffset)     ← 基于 trim() 后文本累计       │
│           ↓                                                                 │
│   序列化为 JSON → 存入 DB(parsedJson)                                       │
└─────────────────────────────────────────────────────────────────────────────┘
                                      ↓
┌─────────────────────────────────────────────────────────────────────────────┐
│                   后端 preview 阶段                                          │
│                                                                             │
│   1) 读取 parsedJson → ParagraphItemDTO(startOffset, endOffset)              │
│   2) PoiPdfConversionService.convertToPdfWithPositions()                     │
│        ↓                                                                    │
│      PdfParagraphPosition(pdfStartPos, pdfEndPos)    ← 基于实际渲染字符累计 │
│        ↓                                                                    │
│      set to ParagraphItemDTO                                                │
│   3) 返回 PreviewResultDTO(paragraphs + pdfUrl)                              │
└─────────────────────────────────────────────────────────────────────────────┘
                                      ↓
┌─────────────────────────────────────────────────────────────────────────────┐
│                   前端 PDF 加载 & 渲染                                       │
│                                                                             │
│   1) 加载 PDF → PDF.js getTextContent() → allPagesTextItems[]               │
│      allPagesTotalChars = Σ(item.str.length)                                │
│                                                                             │
│   2) renderHighlights():                                                    │
│      优先 pdfStartPos/pdfEndPos → 直接映射到 itemEndPos 数组                │
│      降级 (startOffset / docTotalLen) * allPagesTotalChars  → 比例映射     │
│      然后逐 item 绘制 fillRect                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. 问题 1 分析：offset 计算精度

### 3.1 当前实现

`RecognitionEngine.recognize()` (L34-47)：

```java
int cumulativeOffset = 0;
for (int i = 0; i < paragraphs.size(); i++) {
    String text = paragraph.getText().trim();  // ① trim() 丢弃首尾空格
    if (text.isEmpty()) { continue; }           // ② 空段落完全跳过
    match.setStartOffset(cumulativeOffset);
    match.setEndOffset(cumulativeOffset + text.length());
    cumulativeOffset += text.length();
}
```

### 3.2 根因分析

存在三个独立的精度问题：

| 编号 | 问题 | 影响 |
|------|------|------|
| **Q1** | `trim()` 删除了段落首尾空格，offset 基于"缩短后"文本 | docTotalLen（trim后总和）< allPagesTotalChars（PDF实际字符总数），比例失真 |
| **Q2** | 空段落 `continue`，不参与 offset 累计 | 跳过的空段落不贡献字符数，后续段落的 offset 整体前移 |
| **Q3** | offset 基于 docx 原始文本，与 PDF 渲染文本存在固有差异 | PDF 渲染可能有换行插入、字体 fallback、字符替换等，docx 原文与 PDF 文本不完全相同 |

**前端的降级映射逻辑** (L471-477)：

```javascript
const docTotalLen = allParagraphs[last].endOffset;           // trim 后总和
const startCharPos = Math.floor((match.startOffset / docTotalLen) * allPagesTotalChars);
```

由于 Q1+Q2，`docTotalLen` 可能远小于 `allPagesTotalChars`，导致比例放大失真。

**示例**：

```
原始 docx 段落：
  Para 0: "  Hello  "  (8 chars raw)
  Para 1: ""            (空段落)
  Para 2: "World"       (5 chars)

RecognitionEngine offset 计算结果：
  Para 0: trim="Hello", start=0, end=5  (loss: 3 spaces)
  Para 1: skipped (continue)
  Para 2: trim="World", start=5, end=10

docTotalLen = 10

PDF 渲染文本（PoiPdfConversionService）：
  Para 0: sanitize("  Hello  ") → "  Hello  " (8 chars rendered)
  Para 1: empty → no chars rendered (但 totalRenderedChars 不变)
  Para 2: "World" (5 chars rendered)

allPagesTotalChars ≈ 13

比例映射：(0/10)*13 = 0  ← 正确
            (5/10)*13 = 6.5  ← 但理论上第6个字符已是 "World" 的第二个字符
```

### 3.3 是否真正影响了用户？

需要区分两条路径：

| 路径 | 使用场景 | 是否受影响 |
|------|---------|-----------|
| **主路径** `pdfStartPos/pdfEndPos` | `convertToPdfWithPositions()` 成功时 | **基本不受影响** — PoiPdfConversionService 基于实际渲染字符计数 |
| **降级路径** offset 比例映射 | PDF 转换失败或位置生成异常时 | **严重受影响** — 比例完全失真 |

当前代码中 `WordParseService.preview()` L265-281 的异常处理为：
```java
} catch (Exception e) {
    log.warn("PDF 位置生成失败，高亮将使用比例估算");
}
```

异常时会完全降级到 offset 比例映射，此时问题暴露。即使正常路径，若 `PoiPdfConversionService` 的字符计数与 PDF.js 提取存在不一致，也会导致高亮偏移。

### 3.4 方案评估

#### 方案 A：改用原始文本（不 trim）计算 offset

**做法**：
```java
String text = paragraph.getText();  // 不 trim，保留原始空格
// 仍跳过空段落（getText() 返回 "" 或 null）
if (text == null || text.isEmpty()) { continue; }
match.setStartOffset(cumulativeOffset);
match.setEndOffset(cumulativeOffset + text.length());
cumulativeOffset += text.length();
```

**优点**：
- 改动极小，风险低，仅修改 RecognitionEngine 内 2 行
- docTotalLen 更接近实际字符数

**缺点**：
- 空段落仍被跳过（Q2 问题未解决）
- offset 基于 docx 原文，与 PDF 渲染文本仍有差距（Q3 问题未解决）
- `matchPattern()` 仍对 trim 后的文本匹配，但 offset 改用原始文本，两者脱钩（但不影响功能，offset 仅用于前端映射）

**结论**：部分修复，治标不治本，不能完全消除降级路径的偏移。

---

#### 方案 B：完全依赖 pdfStartPos/pdfEndPos，放弃 offset 降级路径

**做法**：
1. 去除前端的 offset 降级分支
2. 当 `pdfStartPos/pdfEndPos` 缺失时，该段落不渲染高亮（跳过）
3. 增强 `convertToPdfWithPositions()` 的可靠性

**优点**：
- 彻底消除 offset 不准确导致的偏移问题
- 架构更清晰：位置信息统一由 PDF 生成环节提供

**缺点**：
- 失去容错能力：PDF 生成失败（如 LibreOffice 路径、PoiPdfConversionService 异常）时，所有高亮不可用
- `PoiPdfConversionService` 的 `totalRenderedChars` 与 PDF.js 的 `allPagesTotalChars` 可能存在不一致，需要验证对齐

**关于字符计数一致性的分析**：

`PoiPdfConversionService` 写入 PDF 的字符数与 PDF.js 提取的字符数**理论上应当一致**，因为：
- `cs.showText(line)` 写入 `line.length()` 个字符
- PDF.js `getTextContent()` 读取这些字符并合并为 text items

但在以下场景可能出现偏差：
- `sanitizeText()` 替换控制字符为空格，改变了字符内容但不改变计数
- 字体不支持某些字符时，PDFBox 可能跳过或替换（通常不影响计数）
- PDF.js 文本提取可能因字体编码问题跳过部分字符（概率低）

**结论**：高风险方案。失去降级能力可能导致功能完全不可用，且字符计数一致性需要更严格的验证。

---

#### 方案 C（推荐）：主路径加固 + 降级路径修复

综合方案：

**C1 — 修复降级路径的 offset 计算**（对应 Q1+Q2）：

```java
int cumulativeOffset = 0;
for (int i = 0; i < paragraphs.size(); i++) {
    XWPFParagraph paragraph = paragraphs.get(i);
    String text = paragraph.getText();
    if (text == null) { text = ""; }

    ParagraphMatch match = new ParagraphMatch();
    match.setIndex(i);
    String matchText = text.trim();  // 仅用于正则匹配和显示
    match.setText(matchText.length() > 200 ? matchText.substring(0, 200) : matchText);
    match.setStartOffset(cumulativeOffset);
    match.setEndOffset(cumulativeOffset + text.length());  // 原始文本长度
    cumulativeOffset += text.length();  // 原始文本长度累加（含空格）
    // 注意：空段落也累加 0 → 不影响 offset 但保留了位置
    // 但空段落仍然可以正常参与匹配处理
    ...
}
```

改动要点：
- offset 基于 `paragraph.getText()` 原始文本（不 trim）
- 不跳过空段落，空段落的 `text.length() = 0`，不影响累计
- trim 后的文本仅用于正则匹配和前端显示截断

**C2 — 对齐前端 docTotalLen 计算**：

```javascript
// 优先使用后端的 pdf 总字符数
const docTotalLen = backendTotalChars || 
  (allParagraphs.length > 0 ? allParagraphs[allParagraphs.length - 1].endOffset || 0 : 0);
```

增加后端返回字段 `totalPdfChars`：PoiPdfConversionService 渲染结束后 `totalRenderedChars` 的总值，作为前端降级映射的准确基数。

**C3 — 主路径增加字符计数校验**：

在 `PoiPdfConversionService.renderParagraphs()` 结束时，记录 `totalRenderedChars`：
- 存入 `PreviewResultDTO` 新增字段 `totalPdfChars`
- 前端收到后与 PDF.js 的 `allPagesTotalChars` 对比
- 偏差超过阈值（如 5%）时主动降级并记录警告

**C4 — 处理极端情况下的段落索引不匹配**：

当 `PdfParagraphPosition.paragraphIndex` 与 `RecognitionEngine` 的段落索引不一致时（如某些段落在 PDF 渲染中被跳过），前端通过文本内容二次确认映射。

---

### 3.5 方案对比总结

| 维度 | 方案 A（不 trim） | 方案 B（纯 pdfPos） | 方案 C（推荐） |
|------|------------------|-------------------|--------------|
| 修复完整性 | 部分（Q1） | 全部（无降级） | 全部（含降级） |
| 风险 | 低 | 高 | 低 |
| 改动量 | 极小（2行） | 中等（前后端） | 中等（前后端） |
| 容错能力 | 有 | 无 | 有 |
| 一致性验证 | 无 | 需单独验证 | 有内置校验 |
| 未来可维护性 | 低 | 中 | 高 |

---

## 4. 问题 2 分析：字体/字符尺寸参数

### 4.1 当前前端实现

`renderHighlights()` (L499-508)：

```javascript
const x = item.transform[4] * effectiveScale
const baselineY = viewport.height - item.transform[5] * effectiveScale
const w = item.width * effectiveScale
const h = item.height * effectiveScale
context.fillRect(x, baselineY - h * 0.7, w, h)
```

PDF.js text item 提供的关键字段：

| 字段 | 含义 | 单位 | 来源 |
|------|------|------|------|
| `item.transform[4]` (tx) | 文本起始 X 坐标 | PDF 用户空间 pt | PDF.js 解析 |
| `item.transform[5]` (ty) | 文本基线 Y 坐标 | PDF 用户空间 pt | PDF.js 解析 |
| `item.width` | 文本项渲染宽度 | PDF 用户空间 pt | PDF.js 解析 |
| `item.height` | 文本项高度（近似字号） | PDF 用户空间 pt | PDF.js 解析 |

### 4.2 问题评估

当前实现中有两个独立问题：

| 问题 | 影响 |
|------|------|
| **垂直定位常量 `0.7`** | 假设基线到顶部的比例为 70%，但不同字体的 ascent/descent 比例不同 |
| **无后端字体参数** | 缺失字体度量（ascent/descent/leading）信息 |

### 4.3 是否需要后端提供字体参数？

**不需要。** 理由如下：

**（1）PDF.js 已提供精确的字体度量**

PDF.js `getTextContent()` 不仅返回 `item.width/height`，还返回 `item.fontName` 和字体对象（可通过 `page.getTextContent()` 的 detail 级别获取）。更关键的是：

- `item.height` 是 PDF 渲染引擎直接提供的字号信息，精度高于任何后端推导
- `item.width` 是实际渲染宽度，已包含字距、kerning 等排版细节

**（2）后端无法提供比 PDF.js 更精确的字体尺寸**

后端数据来源（Docx POI）：
- 可以获取 `paragraph.getRuns().get(0).getFontSizeAsDouble()` → 字号
- 无法直接获取字符实际渲染宽度（取决于字体、字符、字距、PDF 渲染引擎）

这些数据在 PDF 渲染后 PDF.js 已经能精确提取，后端再传递一份数据重复且精度更低。

**（3）真实问题在垂直定位逻辑**

常量 `0.7` 是对字体 ascent/descent 比例的粗糙估计：

```
字体度量示意：
  ┌──────────────────┐  ← top (ascent line)
  │                  │
  │    text          │  ← h * 0.7 位置（当前高亮顶部）
  │                  │
  ├──────────────────┤  ← baseline (transform[5])
  │                  │  ← h * 0.3 位置
  └──────────────────┘  ← bottom (descent line)
```

当前 `baselineY - h * 0.7` 假设 ascent 占 70%、descent 占 30%。对于：
- **CJK 字体**：通常 ascent ~80-85%，descent ~15-20%，当前 70% 偏低，导致高亮矩形上边界偏低、下边界偏高
- **Latin 字体**：ascent ~70-80%，descent ~20-30%，当前 70% 大致合理

解决方案应为：前端直接处理垂直定位，无需后端介入。

### 4.4 推荐方案：前端垂直定位优化

针对 CJK 文本为主的场景，将垂直定位改为：

```javascript
// 方案一：针对 CJK 优化（推荐）
const topOffset = isLikelyCjk(item.str) ? h * 0.85 : h * 0.75;
context.fillRect(x, baselineY - topOffset, w, h);

// 辅助判断函数
function isLikelyCjk(str) {
    return /[一-鿿㐀-䶿豈-﫿]/.test(str);
}
```

**更精确的做法（可选）**：

利用 PDF.js 提供的 `textItem` 的附加属性：

```javascript
// PDF.js 的 getTextContent() 可以返回更详细的信息
// 在 prefetchAllPageTexts() 中同时提取 font 信息
const tc = await page.getTextContent()
for (const item of tc.items) {
    // item.fontFamily → 字体名称
    // 可以缓存主流 CJK 字体的 ascent/descent 比例
}
```

或者使用 Canvas 度量：

```javascript
function estimateFontMetrics(fontFamily, fontSize) {
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d');
    ctx.font = `${fontSize}px ${fontFamily}`;
    const metrics = ctx.measureText('中文');
    // metrics.fontBoundingBoxAscent / fontBoundingBoxDescent
    return metrics;
}
```

但更好的做法是直接采用 `h * 0.85`（针对 CJK 场景），因为：
- PDF.js 的 `item.height` 已接近 font-size
- CJK 字体的 ascent 通常在 80-85%
- 简化实现，无需额外 Canvas 操作

### 4.5 后端可提供的可选增强（非必须）

如果希望进一步精确，后端可以提供一行额外的元数据（在 `PreviewResultDTO` 级别，而非每段落）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `totalPdfChars` | int | PDF 渲染总字符数（用于降级比例验证） |
| `pdfFontName` | String | 渲染时使用的主字体名称（用于前端字体度量选择） |

这两个字段是轻量级的全局信息，不影响逐段落数据结构。

### 4.6 问题 2 方案结论

| 方案 | 评估 | 决策 |
|------|------|------|
| 后端提供逐段落实你尺寸 | 冗余、精度低、增加数据量 | ❌ 不采纳 |
| 后端提供全局字体元数据 | 轻量、可用于前端参考 | ✅ 可采纳（可选） |
| 前端优化垂直定位常量 | 直接解决因果关系、低风险 | ✅ 推荐 |

---

## 5. 总体推荐方案

### 5.1 方案组合

```
问题 1  → 方案 C（主路径加固 + 降级路径修复）
问题 2  → 前端垂直定位优化（后端不做改动）
```

### 5.2 预期效果

| 场景 | 改造前 | 改造后 |
|------|--------|--------|
| pdfStartPos/pdfEndPos 正常 | 主路径基本准确（可能有微小偏移） | 主路径准确 + 有字符计数校验保障 |
| pdfStartPos/pdfEndPos 缺失（降级） | 比例严重偏移 | 比例接近准确（仍不如主路径，但已大幅改善） |
| 高亮垂直位置 | `0.7` 常量偏低 | CJK 文本使用 `0.85`，贴合实际 |
| 空段落处理 | offset 跳过，后续段落偏移 | offset 不跳过（length=0），不影响后续 |
| 调试能力 | 无一致性校验 | 内置字符计数校验 + 日志警告 |

---

## 6. 实施计划

### 6.1 实施顺序

```
第1步：RecognitionEngine offset 计算修复（后端，低风险）
第2步：PreviewResultDTO 增加 totalPdfChars（后端，低风险）
第3步：前端垂直定位优化（前端，低风险）
第4步：前端 docTotalLen 计算对齐 + 字符计数校验（前端，中风险）
```

### 6.2 具体改动清单

#### 第1步：`RecognitionEngine.java` 修复

| 位置 | 当前 | 改为 |
|------|------|------|
| L37 | `paragraph.getText().trim()` | `paragraph.getText()`（不 trim） |
| L38-40 | `if (text.isEmpty()) { continue; }` | `if (text == null) text = "";`（不跳过） |
| L44 | `match.setText(text...` | `match.setText(text.trim()...`（仅显示用 trim） |
| L45-47 | offset 基于 text.length() | offset 基于 `text.length()`（但 text 已是原始文本） |

```java
// 修复后代码（约 L34-48）
int cumulativeOffset = 0;
for (int i = 0; i < paragraphs.size(); i++) {
    XWPFParagraph paragraph = paragraphs.get(i);
    String rawText = paragraph.getText();
    if (rawText == null) { rawText = ""; }

    String displayText = rawText.trim();
    
    ParagraphMatch match = new ParagraphMatch();
    match.setIndex(i);
    match.setText(displayText.length() > 200 ? displayText.substring(0, 200) : displayText);
    match.setStartOffset(cumulativeOffset);
    match.setEndOffset(cumulativeOffset + rawText.length());
    cumulativeOffset += rawText.length();
    // ... 后续匹配逻辑不变（仍基于 displayText 做正则匹配）
```

#### 第2步：`PreviewResultDTO.java` 增加字段

```java
@Data
public class PreviewResultDTO {
    private Long templateId;
    private String templateName;
    private String pdfUrl;
    private List<ParagraphItemDTO> paragraphs;
    private List<LegendItemDTO> legend;
    /** PDF 渲染总字符数（用于前端降级映射时比例对齐） */
    private Integer totalPdfChars;
}
```

#### 第2步续：`PoiPdfConversionService.java` 返回 totalRenderedChars

`renderParagraphs` 方法返回 `totalRenderedChars` 值，`convertToPdfWithPositions` 接口扩展或通过 positions list 的累加获取。

#### 第2步续：`WordParseService.java` 填充 totalPdfChars

preview 方法中获取 `totalRenderedChars` 并设置到 `PreviewResultDTO`。

#### 第3步：`ParsePreview.vue` 垂直定位优化

```javascript
// L504 附近
const isCjk = /[一-鿿㐀-䶿豈-﫿]/.test(item.str);
const topRatio = isCjk ? 0.85 : 0.75;
context.fillRect(x, baselineY - h * topRatio, w, h);
```

或者更精确（使用 baselineY 直接定位）：
```javascript
// 对于 CJK 字符，typographic 的 ascent 约为 font-size 的 0.85
// 高亮矩形顶部 = baseline - actualAscent
const adjustedTop = baselineY - h * 0.85;
context.fillRect(x, adjustedTop, w, h * 1.05);  // 留 5% 底部余量
```

#### 第4步：`ParsePreview.vue` 降级比例对齐 + 字符计数校验

```javascript
// renderHighlights() L450-477 附近

// 使用后端提供的 totalPdfChars 作为降级基数
const docTotalLen = parseResult.totalPdfChars || 
  (allParagraphs.length > 0 ? allParagraphs[allParagraphs.length - 1].endOffset || 0 : 0);

// 校验后端字符数与 PDF.js 提取数的偏差
if (parseResult.totalPdfChars && allPagesTotalChars.value > 0) {
    const deviation = Math.abs(parseResult.totalPdfChars - allPagesTotalChars.value) 
                     / allPagesTotalChars.value;
    if (deviation > 0.05) {
        console.warn(`字符计数偏差 ${(deviation * 100).toFixed(1)}%，高亮可能有偏移`);
    }
}
```

### 6.3 不改动的部分

以下部分**不做修改**：

| 文件 | 原因 |
|------|------|
| `PdfTextPositionExtractor.java` | 文本匹配逻辑正确，与偏移问题无关 |
| `PdfParagraphPosition.java` | DTO 结构合理，无需修改 |
| `ParagraphItemDTO.java` | 现有字段足够，无需增加 |
| `PoiPdfConversionService.java` | 字符计数逻辑正确，只需暴露 totalRenderedChars |
| `api.ts` / 类型定义 | 前端类型仅需在 ParsePreview.vue 内扩展 |

---

## 7. 风险与应对

| 风险 | 概率 | 影响 | 应对 |
|------|------|------|------|
| offset 改为原始文本后，显示用的 text 字段含多余空格 | 高 | 低 | `matchText` 保留 trim，仅 offset 用原始文本 |
| totalPdfChars 与 PDF.js 计数偏差超过预期 | 中 | 中 | 日志警告但功能不中断；偏差阈值可配置 |
| CJK 垂直比例 0.85 不适用于部分字体 | 中 | 低 | 用户可反馈调整；预留优化空间 |
| 空段落 offset 为 0 但前端仍尝试渲染 | 低 | 低 | 前端已有 `match.matchedType === 'UNKNOWN'` 等过滤条件 |

---

## 8. 测试要点

1. **含首尾空格的段落**：确认底色准确覆盖文字区域，不偏移
2. **连续空段落**：确认空段落后的非空段落底色位置正确
3. **纯中文段落**：验证垂直高亮顶部与文字顶部对齐（0.85 比例）
4. **中英文混排**：验证垂直高亮在切换字体时无跳跃
5. **PDF 生成失败场景**：降级到 offset 比例映射，验证精度较改造前有改善
6. **字符计数校验触发**：人为制造字符计数偏差，验证日志告警正常
7. **大文档回归**：验证修改后不影响正常文档的解析和预览流程

---

## 9. 设计确认

以上为完整的分析和优化设计方案。核心结论：

1. **Problem 1**：采用方案 C（主路径加固 + 降级路径修复），修复 RecognitionEngine 的 offset 计算逻辑，同时增加字符计数校验机制
2. **Problem 2**：不增加后端字体参数，前端直接优化垂直定位比例（CJK 使用 0.85）
3. **总改动量**：后端约 10 行（RecognitionEngine + PreviewResultDTO），前端约 20 行（ParsePreview.vue）

如果确认以上设计，将进入阶段 2（开发工程师编码实现）。
