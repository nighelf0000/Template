# PDF 生成内容损坏 — 根因分析与修复设计

## 一、问题概述

用户反馈：调用 `POST /api/word/{id}/parse` 后，通过 `GET /api/word/{id}/preview/pdf` 获取的 PDF 文件"内容损坏"。

系统环境概要：
- Spring Boot 3.2.5 后端（端口 8080）
- Vue 3 + Vite 前端 dev server（端口 3000），通过 proxy 代理 `/api` 到后端
- PDF 转换当前配置为 `template.pdf.converter: libreoffice`（LibreOffice 方案）
- 备选方案为 `PoiPdfConversionService`（纯 Java POI+PDFBox）

---

## 二、关键架构分析

### 2.1 PDF 生成的数据流

```
preview/pdf 端点 (GET /api/word/{id}/preview/pdf)
  → uploadFileMapper.selectById(id) 从 DB 读取
  → 使用 uf.getOriginalContent() —— 原始上传的 .docx 字节
  → pdfConversionService.convertToPdf(originalContent, originalName)
  → 返回 PDF bytes → ResponseEntity<byte[]>(contentType=application/pdf)
```

**重要发现：PDF 预览端点使用的是 `originalContent`（原始上传的 .docx），而非 `parsedContent`（解析后的内容）。** 因此 `/api/word/{id}/parse` 操作本身理论上不会影响 PDF 生成结果。

### 2.2 PDF 转换策略（当前默认：LibreOffice）

`LibreOfficePdfConversionService.convertToPdf()`：
1. 将 `.docx` 字节写入临时文件
2. 调用 `soffice --headless --convert-to pdf --outdir <临时目录> <临时文件>`
3. `process.waitFor(60, TimeUnit.SECONDS)` 等待完成
4. 检查退出码（非 0 抛异常）
5. 读取生成的 PDF 文件并返回字节

配置：
- `template.pdf.libreoffice.path: soffice`（仅命令名，无完整路径）
- `template.pdf.libreoffice.timeout-seconds: 60`

### 2.3 备选方案：POI + PDFBox

`PoiPdfConversionService.convertToPdf()`：
1. 使用 POI `XWPFDocument` 读取 `.docx`
2. 使用 PDFBox `PDDocument` 逐段绘制文本
3. `sanitizeText()` 过滤字符（仅保留特定 Unicode 范围）
4. `loadCjkFont()` 尝试加载中文字体（配置路径：`C:\Windows\Fonts\simhei.ttf`）

### 2.4 异常处理链

```
PdfConversionService.convertToPdf() 抛出 PdfConversionException (extends RuntimeException)
  → WordController.previewPdf() 无 try-catch，异常向上传播
  → GlobalExceptionHandler.@ExceptionHandler(RuntimeException.class)
  → 返回 ApiResponse<Void> (JSON)，HTTP 500
```

### 2.5 前端代理

Vite 配置：`/api` → proxy → `http://localhost:8080`，无超时/大小限制配置。

---

## 三、可能根因分析

### 根因 A：【高概率】LibreOffice 不可用导致异常降级为 JSON，前端误解析

这是最可能的原因。Windows 上 `soffice` 命令需要 LibreOffice 安装且加入 PATH。如果不可用：

1. `ProcessBuilder.start()` 抛出 `IOException`（CreateProcess error=2）
2. `LibreOfficePdfConversionService` 将其包装为 `PdfConversionException`
3. 异常传播到 `GlobalExceptionHandler`，后者返回 JSON 格式的错误响应（`ApiResponse<Void>`）
4. Vite 代理将 JSON 以 `application/json` 转发
5. 前端 Vue 应用未检查 Content-Type 或 HTTP 状态码，直接将响应体传递给 PDF 渲染器（如 `pdfjs-dist`）
6. PDF 渲染器收到 JSON 字符串而非有效 PDF 二进制 → 显示"PDF 内容损坏"

**佐证：**
- `application.yml` 中 `template.pdf.libreoffice.path: soffice` —— 没有 .exe 后缀，也没有完整路径
- 前端 `vite.config.ts` 中 `optimizeDeps.exclude: ['pdfjs-dist']` —— 确认前端使用了 PDF.js 渲染 PDF
- 后端 `GlobalExceptionHandler` 对所有 RuntimeException 统一返回 JSON

### 根因 B：【中概率】LibreOffice 转换生成了空或无效的 PDF 文件

即使 LibreOffice 可用且退出码为 0，仍可能生成无效 PDF：

1. `.docx` 文件包含 LibreOffice 无法正确渲染的内容（如特定图表、嵌入式对象）
2. LibreOffice 生成空白 PDF（退出码仍为 0）
3. 代码未做 PDF 有效性校验（未检查文件大小、未尝试打开验证）
4. 无效的 PDF 字节被直接返回并传递到前端

**佐证：**
- `LibreOfficePdfConversionService` 没有对生成的 PDF 做正确性校验（仅检查文件存在性）
- 没有检查 PDF 文件大小是否为 0

### 根因 C：【中概率】PoiPdfConversionService 中文字体缺失

如果配置已切换为 `template.pdf.converter: poi`，但中文字体不可用：

1. `loadCjkFont()` 尝试加载 `C:\Windows\Fonts\simhei.ttf`
2. 如果字体文件不存在或 PDFBox 无法解析，返回 null
3. `renderParagraphs()` 降级使用 `FALLBACK_FONT`（HELVETICA，不支持中文）
4. `showText()` 遇到 CJK 字符时可能抛出 `IllegalArgumentException` 或渲染为空白
5. 异常类型为 `IllegalArgumentException`（非 IOException）→ 未被 `PoiPdfConversionService` 捕获
6. 异常传播到全局处理器 → 同根因 A 的 JSON 降级路径

**佐证：**
- `application.yml` 中包含 `template.font.path: 'C:\Windows\Fonts\simhei.ttf'` —— 说明系统在 Windows 上运行
- `PoiPdfConversionService` 的 `renderParagraphs()` 只声明 throws IOException
- `showText()` 调用 PDFBox API，遇到不支持字符可能抛 `IllegalArgumentException`

### 根因 D：【低概率】Vite 代理对大 PDF 响应的截断

1. 如果生成的 PDF 体积较大（>5MB），Node.js http-proxy 默认可能触发超时或缓冲区限制
2. Vite dev server 的 proxy 未配置 `proxyTimeout`、`timeout` 或 `buffer` 选项
3. 响应在传输过程中被截断，前端收到不完整的 PDF 字节

**佐证：**
- `vite.config.ts` 中 proxy 配置极为简单，无任何超时或缓冲区参数

### 根因 E：【低概率】MyBatis-Plus 查询中 originalContent 被错误更新

1. `parse()` 方法中通过 `uploadFileMapper.updateById(uf)` 更新实体
2. 如果 MyBatis-Plus 的 `updateById` 策略是更新全部字段（而非仅非空字段），可能错误地将 `originalContent` 置空或更新

**佐证：**
- 实体 `UploadFile` 中 `originalContent` 为 `byte[]` 类型
- `parse()` 方法中 `uf.setParsedContent(...)` 但未设置 `uf.setOriginalContent(...)`
- MyBatis-Plus 默认 `updateById` 策略为 `NOT_NULL`，不应更新 null 字段，但不排除配置变更

---

## 四、需要向用户确认的问题

### 问题 1：当前服务器上 LibreOffice 的安装状态

> 服务器操作系统是什么？是否安装了 LibreOffice？
> 
> **可选方案：**
> - A) 确认已安装 LibreOffice，`soffice` 命令在 PATH 中可用 —— 推荐，如果已安装可排除根因 A
> - B) 未安装 LibreOffice，需安装或切换到 POI 方案
> - C) 不确定安装状态，请检查

### 问题 2："PDF 内容损坏"的具体表现

> 请描述"损坏"的具体现象：
>
> **可选方案：**
> - A) PDF 完全无法打开，浏览器显示"无法加载 PDF"或"PDF 文件已损坏" —— 推荐，这提示是无效的 PDF 字节
> - B) PDF 可以打开但内容全部空白（无文字）
> - C) PDF 可以打开但中文显示为乱码/方块

### 问题 3：该问题是否在所有文档上都可复现？

> 是所有上传的 .docx 文件都无法正常预览 PDF，还是仅特定文件？
>
> **可选方案：**
> - A) 所有文件都出现问题 —— 推荐，说明是系统级配置问题（根因 A 或 C）
> - B) 仅特定复杂格式的文件有问题 —— 说明可能是文档兼容性问题（根因 B）
> - C) 未在其他文档上测试

### 问题 4：在调用 `/api/word/{id}/parse` 之前，PDF 预览是否正常？

> 请尝试先调用 `GET /api/word/{id}/preview/pdf`（不调用 parse），确认 PDF 是否正常？
>
> **可选方案：**
> - A) 不调用 parse 时 PDF 正常，调用 parse 后 PDF 损坏 —— 推荐，指向数据流异常（根因 E 或其他未预期行为）
> - B) 无论是否调用 parse，PDF 都不正常 —— 说明问题在 PDF 转换本身
> - C) 尚未测试过此场景

### 问题 5：浏览器 DevTools Network 面板显示的响应信息

> 请在浏览器中打开 DevTools → Network 面板，重新请求 PDF 预览，确认：
>
> **可选方案：**
> - A) 响应状态码为 200，Content-Type 为 `application/pdf`，响应体是 PDF 二进制 —— 推荐，说明后端正常，需检查 PDF 内容
> - B) 响应状态码为 500，Content-Type 为 `application/json`，响应体是 JSON 错误消息 —— 说明后端 PDF 转换抛出了异常
> - C) 响应状态码为 200，但响应体明显是 JSON（以 `{` 开头）—— 说明后端正常但返回了错误的数据类型

---

## 五、推荐的诊断步骤（按优先级排序）

在获取用户确认之前，建议按以下顺序排查：

### 步骤 1：确认 LibreOffice 可用性（对应根因 A）

在生产服务器上执行以下命令：

```bash
soffice --headless --convert-to pdf --outdir C:\temp C:\path\to\test.docx
```

- 如果命令找不到：需安装 LibreOffice 或配置完整路径
- 如果命令可以执行且生成有效 PDF：说明问题不在 LibreOffice 本身

### 步骤 2：查看后端日志（对应根因 A/B/C）

检查 PDF 请求时后端的日志输出：

```
请求 GET /api/word/2/preview/pdf 发生异常: ...
```

如果出现此类日志，说明异常被 GlobalExceptionHandler 捕获，PDF 预览失败。

### 步骤 3：检查浏览器网络请求（对应根因 A）

浏览器 DevTools → Network → 找到 `/api/word/2/preview/pdf` 请求：

- **Status Code**: 200 表示后端转换成功；500 表示异常
- **Content-Type**: `application/pdf` 表示正常；`application/json` 表示异常
- **Response Body**: 查看实际返回的内容

### 步骤 4：尝试切换到 PoiPdfConversionService（对应根因 A/B）

在 `application.yml` 中临时切换：

```yaml
template:
  pdf:
    converter: poi
```

测试 PDF 预览是否正常。如果 POI 方案正常而 LibreOffice 异常，说明问题在 LibreOffice 配置。

### 步骤 5：检查字体文件（对应根因 C）

确认 `C:\Windows\Fonts\simhei.ttf` 文件存在且可读：

```bash
dir C:\Windows\Fonts\simhei.ttf
```

---

## 六、推荐的修复方案（待确认后实施）

### 方案 1：修复 LibreOffice 配置（推荐 — 如果确认使用 LibreOffice）

- 修改 `application.yml` 中 `template.pdf.libreoffice.path` 为 LibreOffice 的完整路径
- Windows 典型路径：`C:\Program Files\LibreOffice\program\soffice.exe`
- 或者在服务器上安装 LibreOffice 并将安装目录加入系统 PATH

### 方案 2：增加 PDF 转换安全性校验

在 `LibreOfficePdfConversionService.convertToPdf()` 返回前增加校验：

- 检查生成的 PDF 文件大小，0 字节或过小时抛异常
- 尝试使用 PDFBox 打开并读取 PDF 的元数据，确认文件格式正确

### 方案 3：增加 PDF 端点的异常保护

在 `WordController.previewPdf()` 中增加 try-catch，确保即使 PDF 转换失败：

- 仍返回 `application/json` 内容类型的错误响应（避免前端误解析）
- 或者在 Response Header 中添加 `X-PDF-Status: error` 等自定义标记

### 方案 4：配置 Vite 代理参数

在 `vite.config.ts` 中增加代理超时和大文件支持配置：

```typescript
proxy: {
  '/api': {
    target: 'http://localhost:8080',
    changeOrigin: true,
    proxyTimeout: 120000,
    timeout: 120000
  }
}
```

### 方案 5：切换到 PoiPdfConversionService（备选）

如果 LibreOffice 安装困难，可切换为纯 Java 的 POI+PDFBox 方案：

```yaml
template:
  pdf:
    converter: poi
    font:
      path: 'C:\Windows\Fonts\simhei.ttf'
```

需要确保：
- 中文字体文件存在且可读
- `PoiPdfConversionService` 中的异常处理覆盖非 IOException 的异常

---

## 七、总结

| 根因 | 概率 | 影响范围 | 验证方法 |
|------|------|----------|----------|
| A: LibreOffice 不可用 → JSON 降级 | **高** | 所有 PDF 预览 | 命令行执行 soffice + 浏览器检查响应类型 |
| B: LibreOffice 生成无效 PDF | **中** | 特定复杂文档 | 检查 PDF 文件完整性 |
| C: POI 中文字体缺失 | **中** | 所有中文 PDF | 确认字体文件存在 |
| D: Vite 代理截断大文件 | **低** | 大文档 | 检查响应体大小 |
| E: MyBatis 字段错误更新 | **低** | 调用 parse 后 | 对比 parse 前后 originalContent 数据 |

**最推荐的快速排查路径：** 步骤 2（后端日志）→ 步骤 3（浏览器网络检查）→ 步骤 1（LibreOffice 命令行验证），预期 10 分钟内可定位根因。

---

*文档版本：v1.0*
*作者：Agent 1（架构师）*
*状态：待用户确认后进入开发阶段*
