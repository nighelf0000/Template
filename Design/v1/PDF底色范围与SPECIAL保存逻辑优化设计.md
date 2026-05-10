# PDF 底色展示范围与 SPECIAL 保存逻辑优化设计文档

## 概述

本文档针对 Word 模板解析系统的两个问题进行排查分析与修复方案设计：一是左侧 PDF 预览中底色（高亮矩形）展示范围超出应覆盖的内容区域；二是保存调整时 SPECIAL 类型段落的引擎配置重复创建/缺乏更新机制的问题。

---

## 一、问题 1：PDF 底色展示范围超出内容区域

### 1.1 当前实现

当前 `renderHighlights()` 采用**基于累积文本长度比例的位置匹配算法**，流程如下：

```
1. prefetchAllPageTexts()
   → 遍历 PDF 所有页面，调用 page.getTextContent()
   → 收集每个文本项 { str, transform, width, height, pageNum }
   → 存入 allPagesTextItems[]

2. renderHighlights()
   a. 计算 docTotalLen = sum(所有段落 text.trim().length)
   b. 计算 allPagesTotalChars = sum(所有 PDF 文本项 str.length)
   c. 构建 itemEndPos[] = 每个 PDF 文本项的累积字符结束位置
   d. 遍历每个有 backgroundColor 的段落：
      i.    startCharPos = floor((paraCumLen / docTotalLen) * allPagesTotalChars)
      ii.   endCharPos   = ceil(((paraCumLen + paraLen) / docTotalLen) * allPagesTotalChars)
      iii.  在 itemEndPos 上二分查找，得到 startItemIdx ~ endItemIdx
      iv.   筛选 pageNum === currentPage 的文本项
      v.    计算这些文本项的合并包围盒（minX, minY, maxX, maxY）
      vi.   在 highlight Canvas 上绘制半透明底色矩形
```

### 1.2 数据流全景

```
原始 docx
  ├──→ PoiPdfConversionService.convertToPdf() 生成 PDF
  │       ├─ sanitizeText(): 移除控制字符、替换不支持符号
  │       ├─ wrapText(): 重新换行（CJK 宽字符感知）
  │       └─ renderText(): 逐行绘制 → PDF
  │
  └──→ RecognitionEngine.recognize() 生成 parsedJson
          ├─ paragraph.text 截断为 200 字符
          ├─ paragraph.text 经过 trim()
          └─ 按模板规则匹配 → JSON 序列化

PDF → pdfjsLib.getDocument() → page.getTextContent()
       └─ text items: 由 PDF 内部结构决定，粒度 ≈ 原始 docx 的 XWPFRun
```

### 1.3 根因分析

**核心根因：累积文本长度比例映射存在多重误差叠加，导致选中的 PDF 文本项跨越了原始段落边界，从而使高亮矩形的包围盒扩展到相邻段落区域。**

具体误差来源：

| # | 误差源 | 位置 | 影响 |
|---|--------|------|------|
| 1 | **段落文本截断（200 字符）** | `RecognitionEngine.java:43` | `docTotalLen` 基于被截断的文本计算，小于实际长度。后段段落的 `paraCumLen` 比例随之偏移 |
| 2 | **trim() 去除首尾空白** | `RecognitionEngine.java:36` | 后端段落文本与 PDF 原文长度不一致 |
| 3 | **PDF 生成时的文本清洗** | `PoiPdfConversionService.java:367-381` | `sanitizeText()` 移除控制字符和部分 Unicode 符号，`wrapText()` 改变文本分行 |
| 4 | **编码/字体差异** | PDFBox 字体映射 | 部分字符在 PDF 中可能以不同方式编码，影响文本提取结果 |
| 5 | **误差累积效应** | 前端 `renderHighlights()` | 早期段落的微小误差会累积放大，越是靠后的段落，映射偏差越大 |

**误差传导路径：**

```
后端 text.trim().length（已截断 200）
  → docTotalLen 偏小
  → paraCumLen / docTotalLen 比例偏大（对后段段落）
  → startCharPos / endCharPos 映射到错误的 PDF 文本项
  → startItemIdx ~ endItemIdx 包含了相邻段落的文本项
  → 合并包围盒 minY/maxY 扩展到相邻区域
  → 高亮矩形超出应覆盖范围
```

**直接导致"范围超出"的现象：**

当 `startItemIdx` 选取了上一段落的尾部文本项，或 `endItemIdx` 选取了下一段落的头部文本项时，这些额外文本项的 `y` 坐标与原段落文本项的 `y` 坐标存在差距，合并后的 `minY` 更小（更高）或 `maxY` 更大（更低），导致矩形在垂直方向上延伸超出段落应有的区域。

### 1.4 修复方案

#### 方案 A（推荐）：在预览响应中返回段落原始字符偏移

**思路**：在 `parsedJson` 生成阶段记录每个段落相对于 docx 文档的原始字符偏移（startOffset / endOffset），而非在运行时基于截断文本重新计算。前端用这些准确的偏移来定位 PDF 文本项。

**具体步骤：**

**后端改动（WordParseService.preview()）：**

1. 在 `RecognitionEngine.ParagraphMatch` 中增加 `startOffset` 和 `endOffset` 字段
2. 在 `RecognitionEngine.recognize()` 中遍历 docx 段落时，累计原始文本（非截断、非 trim）的字符长度：
   ```java
   String rawText = paragraph.getText();  // 原始文本，不截断
   match.setStartOffset(currentOffset);
   match.setEndOffset(currentOffset + rawText.length());
   currentOffset += rawText.length();
   ```
3. `ParagraphItemDTO` 增加 `startOffset` 和 `endOffset` 字段
4. `preview()` 中透传这些偏移到前端

**前端改动（ParsePreview.vue）：**

1. `renderHighlights()` 不再使用 `(paraCumLen / docTotalLen) * allPagesTotalChars` 的比例公式
2. 改为直接使用段落的 `startOffset` 和 `endOffset`：
   ```javascript
   const startCharPos = match.startOffset
   const endCharPos = match.endOffset
   ```
3. 其余逻辑（二分查找、包围盒计算、Canvas 绘制）保持不变

**优点：**
- 字符偏移在源端（docx）计算，不受后端截断/trim 影响
- PDF 文本提取基于相同原始内容，字符位置天然对齐
- 消除了主要误差源（文本截断、trim 不一致）
- 对后段段落无累积误差

**缺点：**
- 需要修改 `ParagraphMatch`、`ParagraphItemDTO` 两个数据类
- 需要修改 `preview()` 方法透传新字段
- PDF 中的文本清洗（sanitizeText）仍可能导致少量偏移

---

#### 方案 B（备选）：前端基于文本内容的近似匹配

**思路**：放弃比例映射，改用段落文本的前 N 个和后 N 个字符，在 PDF 文本项中进行近似匹配，确定起止范围。

**具体步骤：**

1. 对每个段落，取前 10 字符作为"开头签名"，后 10 字符作为"结尾签名"
2. 在 `allPagesTextItems` 中拼接每个页面的完整文本内容
3. 在当前页面文本中搜索开头签名和结尾签名的位置
4. 将找到的字符位置映射到文本项索引
5. 在这些文本项的包围盒上绘制高亮

**优点：**
- 不需要后端改动
- 文本签名匹配精度高

**缺点：**
- 当文本内容在 PDF 生成过程中被 sanitizeText 修改时（控制字符、特殊符号），签名可能不匹配
- 性能开销较大（需要对每个页面拼接文本、多次搜索）
- 跨页段落的匹配逻辑复杂

---

#### 方案 C（长期）：服务端渲染带底色的 PDF

**思路**：在 `PoiPdfConversionService.generatePdf()` 阶段，接收段落匹配信息，直接在 PDF 上绘制底色矩形，前端不再需要 `renderHighlights()`。

**优点：**
- 100% 精确，不受前端匹配逻辑限制
- PDF 下载/打印时颜色一并保留

**缺点：**
- 需要大幅重构 PDF 生成接口
- 需要将规则和匹配信息传递到 PDF 层
- LibreOffice 实现无法支持（仅 `PoiPdfConversionService` 可行）

### 1.5 方案推荐

**推荐方案 A。** 理由：
1. 改动集中在后端数据传递和前端使用方式，逻辑清晰
2. 字符偏移在源端计算，消除了最主要的误差源（200 字符截断 + trim）
3. 前端核心逻辑（二分查找、包围盒计算）无需重写，只需替换比例公式为直接偏移
4. 对 PDF 文本项的选择更准确，高亮矩形范围自然收敛到正确区域

---

## 二、问题 2：SPECIAL 类型保存逻辑优化

### 2.1 当前实现

**saveAdjust() 第 379-386 行：**
```java
for (RecognitionEngine.ParagraphMatch match : matches) {
    if (match.getRuleId() != null && match.getText() != null && !match.getText().isEmpty()
            && match.getMatchedType() == RecognitionEngine.MatchedType.UNKNOWN) {
        autoCreateSpecialConfig(uf.getTemplateId(), match.getText(), match.getRuleId());
        match.setMatchedType(RecognitionEngine.MatchedType.SPECIAL);
    }
}
```

**autoCreateSpecialConfig() 第 403-434 行：**
```java
private void autoCreateSpecialConfig(Long templateId, String text, Long ruleId) {
    String pattern = "^" + Pattern.quote(text) + "$";
    // 检查同 pattern + ruleId 是否存在
    LambdaQueryWrapper<EngineConfig> check = ...;
    long count = engineConfigMapper.selectCount(check);
    if (count > 0) { return; }  // 存在则跳过
    // 不存在则创建新记录
    EngineConfig config = new EngineConfig();
    config.setPattern(pattern);
    config.setRuleId(ruleId);
    // ...
    engineConfigMapper.insert(config);
}
```

### 2.2 数据流

```
前端 handleSave()
  → 发送 { index, text, matchedType, ruleId, ruleName }
  → 后端 saveAdjust()
      ├─ backfillFromManualAdjust()  → 回填 ruleId
      ├─ 遍历 matches:
      │   匹配 UNKNOWN + ruleId != null
      │    → autoCreateSpecialConfig() → 创建/跳过
      │    → setMatchedType(SPECIAL)
      └─ 更新 parsedJson
```

### 2.3 根因分析

**核心根因：ParagraphMatch 缺少到 EngineConfig 的关联（engine_config.id），导致 SPECIAL 段落在保存调整时既无法定位已有配置进行更新，也无法识别重复创建。**

具体问题场景：

#### 场景 A：调整已匹配 SPECIAL 段落的规则

```
初始解析：
  paragraph "技术方案概述"
    → 匹配 EngineConfig#5: pattern="^技术方案概述$", ruleId=3
    → matchedType=SPECIAL, ruleId=3

用户操作：在段落调整面板中，将 ruleId 从 3 改为 4
保存调整：
  matchedType=SPECIAL（来自已保存的 parsedJson）
  → 条件 matchedType == UNKNOWN 不满足
  → autoCreateSpecialConfig() 不执行
  → EngineConfig#5 的 ruleId 仍为 3
  → 调整未实际写入引擎配置
  → 下次重新解析时，段落仍按 ruleId=3 匹配
```

#### 场景 B：重复调整 UNKNOWN 段落（文本变化后）

```
第 1 次调整：
  paragraph "版本记录" (matchedType=UNKNOWN, ruleId=5)
  → 创建 EngineConfig#10: pattern="^版本记录$", ruleId=5
  → parsedJson 中 matchedType 更新为 SPECIAL

文档修改后重新上传解析：
  paragraph "版本记录（修订版）" (文本变化)
  → 不匹配 EngineConfig#10 的 "^版本记录$"
  → matchedType=UNKNOWN

第 2 次调整：
  paragraph "版本记录（修订版）" (matchedType=UNKNOWN, ruleId=5)
  → 创建 EngineConfig#11: pattern="^版本记录（修订版）$", ruleId=5
  → EngineConfig#10 成为孤儿记录
```

#### 场景 C：同一规则下累积冗余配置

多次调整同一条规则的不同文本段落时，会不断产生新的 SPECIAL engine_config 记录，而旧记录不会被清理。

### 2.4 修复方案

#### 方案 A（推荐）：在 ParagraphMatch 中记录 engine_config_id

**后端改动——RecognitionEngine：**

1. `ParagraphMatch` 类增加 `matchedEngineConfigId` 字段
2. 在 `recognize()` 匹配 SPECIAL 类型时，记录匹配到的 `EngineConfig.id`：
   ```java
   if ("SPECIAL".equals(type)) {
       match.setMatchedEngineConfigId(cfg.getId());
   }
   ```

**后端改动——WordParseService.saveAdjust()：**

3. 修改 SPECIAL 段落保存逻辑，区分三种情况：

```java
for (RecognitionEngine.ParagraphMatch match : matches) {
    if (match.getRuleId() == null || match.getText() == null || match.getText().isEmpty()) {
        continue;
    }

    if (match.getMatchedType() == RecognitionEngine.MatchedType.SPECIAL) {
        // 情况 1：已经是 SPECIAL → 更新已有配置
        // 需要追溯该段落对应的 engine_config
        // 方案 1a：前端传回 matchedEngineConfigId
        // 方案 1b：按 templateId + 当前文本模糊匹配查找原有配置
        updateSpecialConfig(uf.getTemplateId(), match.getText(),
                            match.getRuleId(), match.getMatchedEngineConfigId());
    } else if (match.getMatchedType() == RecognitionEngine.MatchedType.UNKNOWN) {
        // 情况 2：原来未匹配 → 创建新配置
        autoCreateOrUpdateSpecialConfig(uf.getTemplateId(), match.getText(),
                                       match.getRuleId());
    }
}
```

4. 新增 `updateSpecialConfig()` 方法：
   ```java
   private void updateSpecialConfig(Long templateId, String text, Long ruleId, Long configId) {
       if (configId == null) return;
       EngineConfig config = engineConfigMapper.selectById(configId);
       if (config == null) return;
       String newPattern = "^" + Pattern.quote(text) + "$";
       config.setPattern(newPattern);
       config.setRuleId(ruleId);
       engineConfigMapper.updateById(config);
   }
   ```

5. 改造 `autoCreateSpecialConfig()` → `autoCreateOrUpdateSpecialConfig()`：
   保留原有"检查重复→创建"逻辑，但在发现已有同 pattern+ruleId 记录时，不直接跳过，而是更新其 `updatedAt` 等时间戳。

**后端改动——DTO 透传：**

6. `ParagraphItemDTO` 增加 `matchedEngineConfigId` 字段
7. `WordParseService.preview()` 中透传此字段

**前端改动（ParsePreview.vue）：**

8. `handleSave()` 的发送数据中增加 `matchedEngineConfigId` 字段：
   ```javascript
   manualAdjustJson: JSON.stringify(
       parseResult.value.paragraphs.map((p) => ({
           index: p.index,
           text: p.text,
           matchedType: p.matchedType,
           matchedLevel: p.matchedLevel,
           ruleId: p.ruleId,
           ruleName: p.ruleName,
           matchedEngineConfigId: p.matchedEngineConfigId  // 新增
       }))
   )
   ```

**优点：**
- 完整追踪段落→引擎配置的映射关系
- SPECIAL 段落的规则变更、文本变更都能正确更新对应配置
- 不产生孤儿记录

**缺点：**
- 需要修改后端 3 个 Java 类、前端 1 个 Vue 文件
- 新增字段需验证序列化/反序列化兼容性

---

#### 方案 B（轻量）：在 autoCreateSpecialConfig 中增强查重逻辑

不引入 `engineConfigId` 关联，而是改进查重和更新逻辑。

**改动点：**

1. 当前查重条件为 `pattern + ruleId` 精确匹配，改为查 `ruleId + matchType`：
   ```java
   check.eq(EngineConfig::getTemplateId, templateId)
        .eq(EngineConfig::getMatchType, "SPECIAL")
        .eq(EngineConfig::getRuleId, ruleId);
   ```
2. 如果查到记录，不是直接跳过，而是更新 pattern 为最新文本：
   ```java
   List<EngineConfig> existing = engineConfigMapper.selectList(check);
   if (!existing.isEmpty()) {
       EngineConfig cfg = existing.get(0); // 取第一条
       cfg.setPattern("^" + Pattern.quote(text) + "$");
       engineConfigMapper.updateById(cfg);
       return;
   }
   ```

**优点：**
- 改动量小，不涉及数据模型变更
- 对同一 ruleId 下的 SPECIAL 配置自动合并

**缺点：**
- 无法处理"同一 ruleId 下有多条不同文本的 SPECIAL 配置"的场景（会覆盖）
- 未解决"段落已为 SPECIAL 但修改规则"的场景（仍然被 `matchedType != UNKNOWN` 条件阻止）
- 方案 B 必须结合方案 A 的 `matchedEngineConfigId` 追踪才能真正定位到哪个配置

---

#### 方案 C：结合方案 A+B

在方案 A 的基础上，对于无法获取 `matchedEngineConfigId` 的情况（如旧数据没有该字段），退化到方案 B 的 ruleId 查重逻辑，确保向下兼容。

1. 优先使用 `matchedEngineConfigId` 精确定位
2. 如果 `matchedEngineConfigId` 为 null（旧数据），则按 `ruleId + matchType` 查找并更新第一条匹配记录

### 2.5 方案推荐

**推荐方案 C（方案 A+B 结合）。** 理由：
1. `matchedEngineConfigId` 提供了最准确的段落→配置关联
2. 向后兼容性：旧数据在无 `matchedEngineConfigId` 时自动降级为 ruleId 匹配
3. 消除所有三种不良场景：规则变更不生效、孤儿记录、冗余配置积累

---

## 三、改动范围汇总

### 问题 1：PDF 底色展示范围

| 文件 | 改动内容 | 改动类型 |
|------|----------|----------|
| `RecognitionEngine.java` | `ParagraphMatch` 增加 `startOffset`/`endOffset`；`recognize()` 中记录原始偏移 | 修改 |
| `ParagraphItemDTO.java` | 增加 `startOffset`/`endOffset` 字段 | 修改 |
| `WordParseService.java` | `preview()` 中透传偏移字段到 DTO | 修改 |
| `ParsePreview.vue` | `renderHighlights()` 使用前端传入的偏移替换比例公式 | 修改 |

### 问题 2：SPECIAL 保存逻辑

| 文件 | 改动内容 | 改动类型 |
|------|----------|----------|
| `RecognitionEngine.java` | `ParagraphMatch` 增加 `matchedEngineConfigId`；`recognize()` 中记录匹配的 ID | 修改 |
| `ParagraphItemDTO.java` | 增加 `matchedEngineConfigId` 字段 | 修改 |
| `WordParseService.java` | `saveAdjust()` 区分 SPECIAL/UNKNOWN 分别处理；新增 `updateSpecialConfig()`；改造 `autoCreateSpecialConfig()` | 修改 |
| `ParsePreview.vue` | `handleSave()` 发送的数据中增加 `matchedEngineConfigId` | 修改 |

---

## 四、约束与风险

| 风险项 | 说明 | 应对方案 |
|--------|------|----------|
| PDF 文本提取顺序与 docx 段落顺序不一致 | 含表格、文本框、页眉页脚时可能打乱顺序 | 方案 A 的偏移映射基于纯文本顺序，不受影响；极端复杂布局时需结合页面号验证 |
| sanitizeText 修改了 PDF 文本内容 | 控制字符、Unicode 符号被移除，改变实际长度 | 该类字符通常占比极小，对偏移影响可忽略；必要时可在偏移计算中做相同清洗 |
| 旧 parsedJson 数据无 matchedEngineConfigId | 升级前已保存的数据缺少新字段 | 方案 C 降级逻辑：无 matchedEngineConfigId 时按 ruleId 查重 |
| 并发保存导致引擎配置竞争条件 | 多人同时调整同一模板 | 当前系统暂未引入并发控制；后续可考虑乐观锁或模板级锁 |

---

## 五、待确认问题（向用户提问）

### 问题 1：PDF 底色的"超出范围"的具体表现

> 您提到的"底色展示范围超出应覆盖区域"，具体是：
> A. 高亮矩形在垂直方向上比段落实际高度更长（覆盖了上下相邻段落的部分区域）
> B. 高亮矩形在水平方向上比段落实际宽度更宽
> C. 高亮矩形的位置完全偏移到了错误的段落上
>
> 根据代码分析，最可能的是 **A**（垂直方向延伸），因为比例映射误差导致选中的文本项包含了相邻段落的文本项，合并包围盒后在垂直方向上被拉长。

### 问题 2：SPECIAL 段落调整的典型操作流程

> 用户调整 SPECIAL 段落时，典型的操作场景是：
> A. 修改段落所属的规则（ruleId 改变）
> B. 修改段落文本内容（需重新上传 docx 并解析）
> C. 以上两种都有
>
> 这会影响方案的设计——如果主要是规则变更（A），`matchedEngineConfigId` 追踪是必需的；如果主要是文本变更（B），则需要在重新解析后也能追溯旧配置。

### 问题 3：关于已有数据的兼容性

> 系统中是否存在已保存的 parsedJson 数据？如果存在，升级后旧数据缺少 `matchedEngineConfigId` 字段是否可接受（将自动降级为按 ruleId 模糊匹配）？
