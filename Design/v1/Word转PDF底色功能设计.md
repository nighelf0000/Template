# Word 转 PDF 底色功能设计

## 1. 概述

在 PDF 预览场景下，将 Word 文档段落按照匹配的样式规则（模板规则）所配置的底色（highlightColor），以 35% 透明度从左到右整行填充的方式绘制到 PDF 页面上，使预览时用户能直观看到各段落的样式归属；导出/下载 Word 文档时不含 PDF 底色。

## 2. 模块划分

| 模块 | 职责 | 说明 |
|------|------|------|
| **底色数据准备** | 从模板规则中提取各段落的底色信息，通过 PreviewResultDTO 传递给前端 | 现有 WordParseService.preview() 中已实现底色计算（determineBackgroundColor），无需改动 |
| **PDF 底色绘制** | 在 POI+PDFBox 生成 PDF 时，为带底色的段落绘制整行宽度矩形底色 | 本次核心改动模块，在 PoiPdfConversionService 中新增底色绘制逻辑 |
| **PDF 预览接口** | 提供 `/api/word/{id}/preview/pdf` 接口，返回带底色的 PDF | 现有 WordController.previewPdf()，接口不改，内部实现变化 |
| **前端 PDF 展示** | 移除前端高亮叠加 Canvas，改为直接展示服务端生成的带底色 PDF | 简化前端渲染逻辑，去除 renderHighlights() 及相关代码 |
| **转换引擎配置** | 移除 LibreOffice 相关配置，统一使用 POI+PDFBox | 简化系统依赖，去除 LibreOffice 的外部进程调用 |

## 3. 接口定义

### 3.1 后端内部接口

#### PdfConversionService.convertToPdfWithBackground()
```
输入: byte[] docxContent, String originalFilename, List<ParagraphItemDTO> paragraphs
输出: byte[] (带底色的 PDF 字节)
错误: PdfConversionException

说明:
- docxContent: 原始 Word 文档字节
- originalFilename: 原始文件名（用于日志）
- paragraphs: 段落列表，包含 backgroundColor、startOffset、endOffset 等
- 返回值: 已绘制底色的 PDF 字节
```

#### 设计决策：新建方法 vs 修改现有方法
选择在 `PoiPdfConversionService` 中新增 `convertToPdfWithBackground()` 方法，而非修改现有的 `convertToPdf()`，原因：
- 保持原有无底色生成能力（若有其他调用方）
- 避免破坏 `convertToPdfWithPositions()` 的现有契约
- 新增方法在生成 PDF 文本的同时，根据传入的段落底色信息绘制底色

### 3.2 HTTP 接口（不变）

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/word/{id}/preview` | GET | 返回 PreviewResultDTO（含 pdfUrl、paragraphs 等），**不变** |
| `/api/word/{id}/preview/pdf` | GET | 返回带底色的 PDF 字节（Content-Type: application/pdf），**内部实现变化** |
| `/api/word/{id}/export` | POST | 导出 Word 文档，**不含 PDF 底色，不变** |
| `/api/word/{id}/download` | GET | 下载 Word 文档，**不含 PDF 底色，不变** |

### 3.3 PreviewResultDTO（不变）

```java
public class PreviewResultDTO {
    private Long templateId;
    private String templateName;
    private String pdfUrl;              // → /api/word/{id}/preview/pdf
    private List<ParagraphItemDTO> paragraphs;  // 含 backgroundColor、pdfStartPos、pdfEndPos
    private List<LegendItemDTO> legend;
    private Integer totalPdfChars;      // PDF 总渲染字符数
}
```

`ParagraphItemDTO` 中的 `backgroundColor` 字段已用于前端 Canvas 高亮，本次改动后此字段仍保留，但作用变为**仅在服务端绘制底色时使用**，不再传递到前端用于 Canvas 渲染。

## 4. 数据模型

### 4.1 核心数据流

```
Word 上传 → 解析 → 段落匹配规则（含 highlightColor）→ 预览请求
                                                          │
                                                          ▼
                                              ┌─────────────────────┐
                                              │ WordParseService    │
                                              │ .preview()          │
                                              │                     │
                                              │ 1. 计算底色         │
                                              │ 2. 获取段落样式      │
                                              │ 3. 调用 PDF 生成    │
                                              └─────────┬───────────┘
                                                        │
                                                        ▼
                                              ┌─────────────────────┐
                                              │ PoiPdfConversion    │
                                              │ Service             │
                                              │                     │
                                              │ 1. POI 读取 docx    │
                                              │ 2. 逐段落绘制文本   │
                                              │ 3. 整行绘制底色     │
                                              │ 4. 返回 PDF 字节    │
                                              └─────────┬───────────┘
                                                        │
                                                        ▼
                                              ┌─────────────────────┐
                                              │ 前端 PDF.js 展示    │
                                              │ (无叠加 Canvas)     │
                                              └─────────────────────┘
```

### 4.2 底色数据来源

底色来源于 `TemplateRule.highlightColor`，取值逻辑在 `WordParseService.determineBackgroundColor()` 中已有实现：
1. 优先使用规则的 `highlightColor`
2. 若未配置，按 `ruleId * 137.508 % 360` 的 HSL 色相自动生成
3. UNKNOWN 类型段落返回 `null`（无底色）

此逻辑不变，数据从 `paragraphs` 列表传递给 PDF 生成服务。

## 5. 技术选型

| 组件 | 选型 | 理由 |
|------|------|------|
| PDF 生成引擎 | **Apache POI + Apache PDFBox** | 用户选择；纯 Java 实现，无需外部进程，可精确控制每行绘制 |
| 底色绘制 | **PDFBox PDPageContentStream** | 原生支持矩形绘制（addRect + fill），可设置透明度 |
| 透明度控制 | **PDFBox 图形状态** | 使用 `setGraphicsStateParameters` + 预定义的透明度扩展图形状态 |
| 前端 PDF 展示 | **PDF.js**（不变） | 仅展示 PDF，不再叠加 Canvas 高亮 |
| 字体 | **系统/配置中文字体**（不变） | 现有字体加载逻辑 |

### 新增依赖（无需额外引入）

- Apache PDFBox 原生支持矩形绘制和透明度，当前项目已有 PDFBox 依赖
- 无需新增 Maven/Gradle 依赖

## 6. 约束与风险

### 6.1 已知限制

| 限制 | 说明 |
|------|------|
| **底色仅展示在 PDF 预览** | 导出/下载的 Word 文档不含底色，这是用户明确要求的 |
| **底色不支持透明度渐变** | 使用固定 35% 透明度，不支持渐变或多种透明度 |
| **整行填充** | 底色从左边距到右边距覆盖整行，不跟随文字缩进变化 |
| **仅 POI+PDFBox 模式** | 移除了 LibreOffice 支持，若未来需要 LibreOffice 的复杂排版能力则需重新评估 |
| **分页边界底色截断** | 底色跨页时会被截断，每页独立绘制底色 |

### 6.2 潜在风险与应对

| 风险 | 应对方案 |
|------|----------|
| **底色覆盖文字** | 底色在文字绘制之前绘制（底层），文字在上层，不遮挡文字 |
| **性能影响** | 底色的矩形绘制开销极小（每段落每行一个矩形），对 PDF 生成时间影响可忽略 |
| **PDF 文件体积增大** | 底色为简单矢量矩形，几乎不增加 PDF 体积 |
| **底色与原有 Canvas 高亮冲突** | 完全移除前端 Canvas 高亮，消除双重绘制 |
| **灰度/黑白打印可见底色** | 35% 透明度在黑白打印下仍可见，影响较小（预览场景为主） |

## 7. 改动范围

### 7.1 后端改动

| 文件 | 改动类型 | 改动说明 |
|------|----------|----------|
| `PoiPdfConversionService.java` | **新增方法** | 新增 `convertToPdfWithBackground()` 方法，接收段落底色列表，在逐行绘制文字前绘制整行底色矩形 |
| `PoiPdfConversionService.java` | **新增方法** | 新增 `renderBackgrounds()` 内部方法，遍历段落逐行绘制底色 |
| `WordController.java` | **修改** | `previewPdf()` 方法改为调用带底色的 PDF 生成方法（`convertToPdfWithBackground`） |
| `PdfConversionService.java` | **新增接口方法** | 新增 `convertToPdfWithBackground()` 接口定义（默认实现为无底色兜底） |
| `PdfConverterConfig.java` | **修改** | 移除 `converter` 配置和 LibreOffice Bean，仅保留 POI+PDFBox 的 Bean |
| `LibreOfficePdfConversionService.java` | **标记废弃** | 标注 @Deprecated，保留代码但不启用 |

### 7.2 配置改动

| 文件 | 改动说明 |
|------|----------|
| `application.yml` | 移除 `template.pdf.converter=libreoffice` 配置，移除 `template.pdf.libreoffice` 相关配置 |

### 7.3 前端改动

| 文件 | 改动类型 | 改动说明 |
|------|----------|----------|
| `ParsePreview.vue` | **移除代码** | 移除 `highlightCanvasRef` 引用和 `<canvas ref="highlightCanvasRef">` DOM 元素 |
| `ParsePreview.vue` | **移除代码** | 移除 `renderHighlights()` 方法及所有相关调用 |
| `ParsePreview.vue` | **移除代码** | 移除 `binarySearchItemIndex()`、`binarySearchItemIndexGT()`、`prefetchAllPageTexts()` 方法及 `allPagesTextItems`、`allPagesTotalChars` 状态变量 |
| `ParsePreview.vue` | **简化** | 简化 `renderPage()` 方法，移除高亮渲染调用 |
| `types/api.ts` | **移除字段（可选）** | 可移除 `ParseResult` 和 `ParagraphItem` 中与前端高亮相关的 `totalPdfChars`、`pdfStartPos`、`pdfEndPos` 字段（若后端不再返回） |

### 7.4 无需改动的文件

| 文件 | 说明 |
|------|------|
| `WordParseService.java` | 底色计算逻辑不变，PDF 位置记录逻辑可移除（若前端不再需要 pdfStartPos/pdfEndPos）或保留（作为降级信息） |
| `WordController.java`（preview 接口） | PreviewResultDTO 结构不变 |
| `PreviewResultDTO.java` | 数据结构不变 |
| `ParagraphItemDTO.java` | 数据结构不变（backgroundColor 由后端使用） |
| `PdfParagraphPosition.java` | 若前端不再需要 pdfStartPos/pdfEndPos，可从 `ParagraphItemDTO` 中移除这两个字段 |

---

## 8. 详细实现方案

### 8.1 底色绘制逻辑（PoiPdfConversionService）

在 `PoiPdfConversionService` 中新增方法，接收段落底色列表，在 `renderText()` 之前绘制底色矩形：

```
新增方法 convertToPdfWithBackground(docxContent, filename, paragraphs):
  1. 调用现有 convertToPdfWithPositions() 生成基础 PDF 和段落位置
  2. 返回带底色的 PDF 字节（将底色直接绘制在基础 PDF 上？还是在过程中绘制？）

更优方案：在 renderParagraphs() 过程中同步绘制底色
```

**推荐方案**：在 `renderParagraphs()` 过程中，为每个段落中的每行文字，在绘制文字之前先绘制底色矩形。这样确保底色在文字底层。

具体实现：

```java
// renderText() 方法中，在每行文字绘制前添加底色矩形绘制
private float renderTextWithBackground(...) throws IOException {
    PDPageContentStream cs = csHolder[0];
    
    // 设置底色（若有）
    if (backgroundColor != null) {
        cs.saveGraphicsState();
        // 设置 35% 透明度
        setTransparency(cs, 0.35f);
        // 设置填充色
        cs.setNonStrokingColor(parseColor(backgroundColor));
    }
    
    for (int i = 0; i < lines.size(); i++) {
        // 绘制整行底色矩形（从左边距到右边距）
        if (backgroundColor != null) {
            cs.addRect(MARGIN_LEFT, cursorY - lineHeight, CONTENT_WIDTH, lineHeight);
            cs.fill();
        }
        
        // 绘制文字（现有逻辑）
        cs.beginText();
        cs.newLineAtOffset(cursorX, cursorY - lineHeight);
        cs.showText(line);
        cs.endText();
        
        cursorY -= lineHeight;
    }
    
    if (backgroundColor != null) {
        cs.restoreGraphicsState();
    }
    
    return cursorY;
}
```

### 8.2 透明度设置

PDFBox 中设置透明度的标准方式：

```java
private void setTransparency(PDPageContentStream cs, float alpha) throws IOException {
    PDDeviceRGBColorSpace colorSpace = new PDDeviceRGBColorSpace();
    // 使用 PDGraphicsState 的 alpha constant
    PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
    gs.setNonStrokingAlphaConstant(alpha);
    gs.setStrokingAlphaConstant(alpha);
    cs.setGraphicsStateParameters(gs);
}
```

### 8.3 颜色解析

将 CSS 颜色值（如 `#ff0000` 或 `hsl(0, 60%, 85%)`）转换为 PDFBox 可用的颜色：

```java
private Color parseColor(String colorStr) {
    if (colorStr == null) return null;
    if (colorStr.startsWith("#")) {
        // 解析十六进制颜色
        return Color.decode(colorStr);
    } else if (colorStr.startsWith("hsl")) {
        // 解析 HSL 颜色（当前项目使用固定格式 hsl(h, 60%, 85%)）
        return parseHsl(colorStr);
    }
    return null;
}
```

### 8.4 接口适配（WordController）

```java
@GetMapping("/{id}/preview/pdf")
public ResponseEntity<byte[]> previewPdf(@PathVariable Long id) {
    UploadFile uf = uploadFileMapper.selectById(id);
    if (uf == null || uf.getOriginalContent() == null) {
        return ResponseEntity.notFound().build();
    }
    try {
        // 获取段落底色信息
        PreviewResultDTO previewResult = wordParseService.preview(id);
        List<ParagraphItemDTO> paragraphs = previewResult.getParagraphs();
        
        // 调用带底色的 PDF 生成
        byte[] pdfBytes = pdfConversionService.convertToPdfWithBackground(
            uf.getOriginalContent(), uf.getOriginalName(), paragraphs);
        
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=\"preview.pdf\"")
                .body(pdfBytes);
    } catch (PdfConversionException e) {
        log.error("PDF 转换失败: id={}, filename={}", id, uf.getOriginalName(), e);
        throw e;
    }
}
```

### 8.5 前端改动详情

**移除内容**：
1. `<canvas ref="highlightCanvasRef" class="highlight-canvas" />` — 整个 DOM 元素
2. `.highlight-canvas` 相关 CSS 样式
3. `highlightCanvasRef` 的 ref 定义
4. `renderHighlights()` 方法（约 100 行）
5. `prefetchAllPageTexts()` 方法（约 30 行）
6. `binarySearchItemIndex()` 和 `binarySearchItemIndexGT()` 辅助方法
7. `allPagesTextItems` 和 `allPagesTotalChars` 状态变量
8. `renderPage()` 中对 `highlightCanvasRef` 的宽度/高度设置
9. `renderPage()` 中调用 `renderHighlights()` 的代码行

**保留内容**：
- PDF Canvas 渲染逻辑不变
- 翻页控制不变
- 右侧图例面板和段落调整不变

### 8.6 pdfStartPos/pdfEndPos 字段处置

根据用户决策（问题4 — 完全移除 renderHighlights 和叠加 Canvas），前端不再需要 `pdfStartPos` 和 `pdfEndPos`。但是：

- 后端 `WordParseService.preview()` 中生成 PDF 位置信息的逻辑（调用 `convertToPdfWithPositions`）可以移除，简化 preview 流程
- `ParagraphItemDTO` 中的 `pdfStartPos`、`pdfEndPos` 字段可以移除
- `PdfParagraphPosition` DTO 类可以保留或标记废弃

**建议**：为减少改动量，暂时保留后端字段生成逻辑，仅在前端类型定义中标记为可选。后续清理时再移除。

## 9. 回退方案

若带底色 PDF 生成出现严重问题，可快速回退：

1. **前端降级**：保留原有 Canvas 高亮代码（用条件编译或开关控制），当底色 PDF 生成失败时切换回前端高亮模式
2. **后端降级**：`convertToPdfWithBackground()` 中若底色绘制异常，捕获异常后回退到无底色 PDF 生成，并输出警告日志

建议在实现中增加配置开关 `template.pdf.background.enabled`，默认为 true，可动态关闭底色功能。
