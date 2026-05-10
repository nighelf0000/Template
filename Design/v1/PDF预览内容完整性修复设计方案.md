# PDF 预览内容完整性修复 -- 架构设计文档

## 1. 概述

**一句话描述**：引入纯 Java 的 PDFBox 文本位置提取增强组件，配合 LibreOffice 高质量 PDF 生成，在不改动前端的情况下同时解决内容缺失和段落高亮两个问题。

## 2. 问题根因

| 层级 | 原因 |
|------|------|
| **直接原因** | `PoiPdfConversionService.renderParagraphs()` 仅调用 `paragraph.getText()` 提取纯文本，完全忽略 `XWPFParagraph` 中的图片(`XWPFPicture`)、公式、表格等非文本元素 |
| **架构原因** | `PdfConversionService` 接口将 PDF 生成和位置记录耦合，导致 POI 和 LibreOffice 两种实现各有取舍，无法兼得 |

## 3. 解决方案详解

### 3.1 核心思路

```
上传 .docx
   │
   ├──→ LibreOfficePdfConversionService
   │       └── convertToPdf()
   │            └── soffice --headless --convert-to pdf
   │                 └── 完整PDF（含图片/公式/表格）
   │
   └──→ LibreOfficePdfConversionService（★新增实现）
           └── convertToPdfWithPositions()
                ├── 1. soffice 生成完整PDF
                ├── 2. PDFBox PDFTextStripper 文字位置提取
                └── 3. 段落文本匹配 → PdfParagraphPosition[]
```

### 3.2 关键设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| PDF 生成引擎 | LibreOffice（统一使用） | 唯一能完整处理图片/公式/OLE对象的方案 |
| 段落位置获取 | PDFBox 从生成后PDF提取文字位置 | POI文本布局与LibreOffice不一致，必须基于实际PDF提取 |
| 前端改动 | 零改动 | pdfStartPos/pdfEndPos字段不变，仍为字符索引 |
| 转换器选择 | 默认LibreOffice，POI作为降级方案 | 减少维护成本，兼容无LibreOffice环境 |
| 缓存策略 | 复用已有 template.pdf.cache 配置 | 避免同一文件重复转换 |

## 4. 模块划分与改动清单

### 4.1 新增模块

#### A. PdfTextStripperWithIndices（辅助类）

**文件**：`src/main/java/com/template/service/pdf/PdfTextStripperWithIndices.java` (~120行)

**职责**：扩展 PDFBox 的 `PDFTextStripper`，在提取文本时记录每个字符在PDF中的字符流索引。

核心字段：
- `List<CharInfo> charInfos` — 字符信息列表（字符、页码、坐标、索引）

#### B. PdfTextPositionExtractor（核心新增类）

**文件**：`src/main/java/com/template/service/pdf/PdfTextPositionExtractor.java` (~250行)

**职责**：从已生成的PDF中提取段落文本位置信息，构建段落-字符位置的映射。

核心方法：
```java
public List<PdfParagraphPosition> extractParagraphPositions(
        byte[] pdfBytes, 
        List<XWPFParagraph> paragraphs) throws IOException
```

**段落文本匹配算法**：
1. 使用 PdfTextStripperWithIndices 从PDF提取所有字符及其索引
2. 拼接为PDF全文文本
3. 标准化处理（合并空格、Unicode NFC归一化）
4. 对每个docx段落使用滑动窗口算法在PDF文本中匹配
5. 允许1-2个字符偏移容差
6. 对无法精确匹配的段落使用插值估算
7. 匹配结果合理性校验（长度偏差超过20%降级为估算）

### 4.2 修改模块

#### A. LibreOfficePdfConversionService.java（修改 ~50行增量）

**改动**：新增 `convertToPdfWithPositions()` 的实现（覆盖接口默认方法）。

```java
@Override
public byte[] convertToPdfWithPositions(byte[] docxContent, String originalFilename,
                                         List<PdfParagraphPosition> positions) throws PdfConversionException {
    // Step 1: 生成完整PDF
    byte[] pdfBytes = convertToPdf(docxContent, originalFilename);
    // Step 2: 从docx读取段落列表
    try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxContent))) {
        // Step 3: 使用PdfTextPositionExtractor从PDF提取位置
        PdfTextPositionExtractor extractor = new PdfTextPositionExtractor();
        positions.addAll(extractor.extractParagraphPositions(pdfBytes, doc.getParagraphs()));
    }
    return pdfBytes;
}
```

#### B. PdfConverterConfig.java（修改 ~15行）

**改动**：将默认转换器改为 `"libreoffice"`。

```java
// POI 降级为备选（显式配置时启用）
@Bean
@ConditionalOnProperty(name = "template.pdf.converter", havingValue = "poi")
public PdfConversionService pdfConversionServicePoi() { ... }

// LibreOffice 成为默认（matchIfMissing = true）
@Bean
@ConditionalOnProperty(name = "template.pdf.converter", havingValue = "libreoffice", matchIfMissing = true)
public PdfConversionService pdfConversionServiceLibreOffice() { ... }
```

#### C. application.yml（配置更新 ~5行）

```yaml
template:
  pdf:
    converter: libreoffice   # 改为默认使用 LibreOffice
    libreoffice:
      path: soffice
      timeout-seconds: 60    # 适当增加超时
    cache:
      enabled: true
      ttl-minutes: 10        # 缓存10分钟
```

### 4.3 无需改动

| 文件 | 理由 |
|------|------|
| `WordController.java` | 接口不变，响应语义不变 |
| `ParsePreview.vue` | 前端接口不变，pdfStartPos/pdfEndPos语义不变 |
| `PoiPdfConversionService.java` | 保留作为降级方案 |
| `PdfParagraphPosition.java` | 数据模型不变 |
| `ParagraphItemDTO.java` / `PreviewResultDTO.java` | DTO不变 |
| `pom.xml` | 无新增依赖 |

## 5. 新增文件汇总

| 文件 | 行数 | 说明 |
|------|------|------|
| `PdfTextStripperWithIndices.java` | ~120 | PDFBox 自定义文本+索引提取器 |
| `PdfTextPositionExtractor.java` | ~250 | 段落文本位置匹配引擎 |

## 6. 修改文件汇总

| 文件 | 改动量 | 说明 |
|------|--------|------|
| `LibreOfficePdfConversionService.java` | +50行 | 新增 `convertToPdfWithPositions()` |
| `PdfConverterConfig.java` | ~15行 | 切换默认转换器为LibreOffice |
| `application.yml` | ~5行 | 默认配置调整 |

## 7. 约束与风险

| 风险 | 概率 | 应对方案 |
|------|------|----------|
| LibreOffice渲染与Word有细微差异 | 中 | 文本匹配时允许字符级容差 |
| PDF文本提取不完整（特殊字体） | 低 | 前端已有比例估算fallback |
| LibreOffice冷启动慢(2-5秒) | 高 | 使用PDF缓存（已有配置） |
| 段落文本匹配偏移 | 中 | 模糊匹配+合理性校验+降级机制 |
| 无LibreOffice环境 | 中 | POI降级方案保留，前端回退比例估算 |

## 8. 实施步骤

```
Step 1: PdfTextStripperWithIndices（基础组件，无外部依赖）
  └── 实现 PDF 文本 + 索引的提取
Step 2: PdfTextPositionExtractor（依赖 Step 1 + POI）
  └── 实现段落文本匹配算法
Step 3: LibreOfficePdfConversionService 增强（依赖 Step 2）
  └── 实现 convertToPdfWithPositions()
Step 4: PdfConverterConfig + application.yml 调整
  └── 切换默认转换器为 LibreOffice
Step 5: 全链路集成测试
  └── 上传含图片/公式的docx → 验证PDF预览完整性 + 高亮准确性
```

## 9. 验收标准

| 验收项 | 标准 |
|--------|------|
| 图片展示 | PDF中图片与Word一致（位置、大小、比例）|
| 公式展示 | PDF中公式正常渲染（OMML和图片型公式）|
| 表格展示 | PDF中表格结构与Word一致 |
| 段落高亮 | 高亮覆盖范围与段落文本匹配，无显著漂移 |
| 回退兼容 | 无LibreOffice时降级到POI+比例估算高亮 |
| 性能 | 首次预览<10秒，缓存命中时<1秒 |
