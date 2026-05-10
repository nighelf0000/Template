# 样式图例与 PDF 底色高亮颜色修复设计文档

## 概述

修复样式规则中 `highlightColor`（高亮颜色/底色）设置未正确应用到前端图例面板和 PDF 预览段落底色的问题。用户配置 `highlightColor` 后，后端计算颜色时理应优先使用该值，但实际展示时未体现。

当前代码中 `determineBackgroundColor()` 方法的逻辑是"优先使用 `highlightColor`，否则按 `ruleId` 自动生成 HSL 颜色"，该逻辑本身正确。问题在于**数据传递链中的容错不足**和 **JSON 序列化/反序列化的字段命名不一致**，导致在某些场景下 `highlightColor` 无法被正确传递。

---

## 根因分析

### 1. JSON 序列化/反序列化字段命名不匹配（主要根因）

**问题位置**：
- `WordParseService.parse()` 方法（约第 127 行）

**分析**：
在 `parse()` 方法中有两段看似冗余的 JSON 构建逻辑：

```java
// 代码段 A — matchToMap 返回 snake_case Map（实际未使用）
Map<String, Object> json = new LinkedHashMap<>();
json.put("template_id", config.getId());
json.put("paragraphs", matches.stream().map(this::matchToMap)...);

// 代码段 B — Jackson 直接序列化 ParagraphMatch（使用 camelCase）
String parsedJsonStr = objectMapper.writeValueAsString(matches);
```

当前 `parsedJsonStr` 是**直接序列化 `List<ParagraphMatch>`**（代码段 B），使用 Jackson 默认的 `LOWER_CAMEL_CASE` 策略，字段名如 `ruleId`、`matchedType`。

当 `preview()` 方法反序列化时：

```java
matches = objectMapper.readValue(
    uf.getParsedJson(),
    objectMapper.getTypeFactory().constructCollectionType(List.class, RecognitionEngine.ParagraphMatch.class));
```

**Jackson 使用相同的 `LOWER_CAMEL_CASE` 策略，理论上序列化和反序列化一致**。

但在 `application.yml` 中：

```yaml
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
```

MyBatis-Plus 开启了 `map-underscore-to-camel-case`，这会影响**数据库字段到实体类的映射**，但**不影响 Jackson 的 JSON 处理**。Spring Boot 的 Jackson 配置（`application.yml` 第 26-28 行）**没有设置** `property-naming-strategy`，因此 JSON 字段名使用默认的 `LOWER_CAMEL_CASE`。

**结论**：JSON 层的序列化/反序列化应是一致的（均为 camelCase），这一层不是问题。

### 2. `ruleMap` 中规则查找失败时无降级（根本薄弱点）

**问题位置**：
- `WordParseService.preview()` 第 247 行
- `WordParseService.generateLegend()` 第 368 行

**分析**：

```java
// preview() 第 247 行
TemplateRule rule = match.getRuleId() != null ? ruleMap.get(match.getRuleId()) : null;

// generateLegend() 第 368 行
TemplateRule rule = ruleMap.get(match.getRuleId());
```

当 `match.getRuleId()` 有值（如 `5L`），但 `ruleMap.get(5L)` 返回 `null` 时（可能原因：规则被删除、模板切换、数据不一致），`determineBackgroundColor(5L, null)` 会跳过 `highlightColor` 判断，直接进入 `ruleId != null` 分支**生成自动 HSL 颜色**。

```java
private String determineBackgroundColor(Long ruleId, TemplateRule rule) {
    // 第一步：rule 为 null，跳过
    if (rule != null && rule.getHighlightColor() != null && !rule.getHighlightColor().isEmpty()) {
        return rule.getHighlightColor();
    }
    // 第二步：ruleId 不为 null，生成 HSL（highlightColor 被忽略！）
    if (ruleId != null) {
        int hue = (int) ((ruleId * 137.508) % 360);
        return String.format("hsl(%d, 60%%, 85%%)", hue);
    }
    return null;
}
```

**这是最核心的薄弱点**：`ruleMap.get(ruleId)` 返回 `null` 时，`highlightColor` 被静默丢弃，用户看到的是一条自动生成的颜色，而不是设置的 `highlightColor`。

### 3. 缺乏日志追踪，调试困难

**问题位置**：`determineBackgroundColor()` 全方法

**分析**：方法内部没有输出任何日志。当回退到自动生成时，开发者无法从日志中得知：
- 是 `rule` 为 `null`？
- 还是 `highlightColor` 本身为 `null`/空？

### 4. 颜色格式兼容性风险

**问题位置**：
- `PoiPdfConversionService.parseColor()` 第 736 行

**分析**：`parseColor()` 只处理 `#` 前缀的十六进制和 `hsl()` 格式。如果 `highlightColor` 保存为不带 `#` 的格式（如 `"FF0000"`）或带空格的格式（如 `" #FF0000"`），`Color.decode()` 会抛出异常，被 `catch` 后返回 `null`，**PDF 底色不会被绘制**。

但图例面板使用 CSS `background-color`，CSS 对颜色格式容忍度较高（支持 `#RRGGBB`、`#RGB`、`rgb()`、`rgba()`、命名颜色等），所以图例可能显示正常而 PDF 不显示。

### 5. `preview()` 被重复调用，效率低

**问题位置**：`WordController.previewPdf()` 第 89 行

**分析**：

```java
// 前端首先调用 GET /api/word/{id}/preview
// 然后独立调用 GET /api/word/{id}/preview/pdf

// previewPdf() 内部再次调用 preview()
PreviewResultDTO previewResult = wordParseService.preview(id);
List<ParagraphItemDTO> paragraphs = previewResult.getParagraphs();
byte[] pdfBytes = pdfConversionService.convertToPdfWithBackground(
        uf.getOriginalContent(), uf.getOriginalName(), paragraphs);
```

`previewPdf()` 内部调用 `preview()`，这会完整执行：
- 反序列化 parsedJson
- 提取段落原始样式（POI 操作）
- 生成图例（虽然 PDF 不需要图例）
- 记录 PDF 位置

其中大部分工作对 PDF 生成是冗余的，我们只需要 `paragraphs` 列表中的 `backgroundColor` 和 `index`。

---

## 修复方案

### 方案 A：`determineBackgroundColor()` 增加降级查询逻辑

**目标**：解决 `ruleMap.get(ruleId)` 返回 `null` 时 `highlightColor` 丢失的问题。

**修改方式**：

在 `determineBackgroundColor()` 中，当 `rule` 为 `null` 但 `ruleId` 不为 `null` 时，增加一次数据库查询：

```java
private String determineBackgroundColor(Long ruleId, TemplateRule rule) {
    if (rule != null && rule.getHighlightColor() != null && !rule.getHighlightColor().isEmpty()) {
        return rule.getHighlightColor();
    }
    // 降级：rule 为 null 但 ruleId 有值，尝试从数据库查询
    if (rule == null && ruleId != null) {
        TemplateRule dbRule = templateRuleMapper.selectById(ruleId);
        if (dbRule != null && dbRule.getHighlightColor() != null && !dbRule.getHighlightColor().isEmpty()) {
            log.info("determineBackgroundColor: ruleId={} 从数据库降级查询到 highlightColor={}", ruleId, dbRule.getHighlightColor());
            return dbRule.getHighlightColor();
        }
    }
    if (ruleId != null) {
        int hue = (int) ((ruleId * 137.508) % 360);
        return String.format("hsl(%d, 60%%, 85%%)", hue);
    }
    return null;
}
```

**优点**：解决规则查找不到时的根本问题，确保 `highlightColor` 在规则存在时一定能被使用。

**缺点**：增加了数据库查询次数（不过在正常场景下 `ruleMap` 能命中，不会触发降级查询）。

### 方案 B：`determineBackgroundColor()` 增加格式规范化与日志

**目标**：解决颜色格式兼容性问题，增加调试信息。

**修改方式**：

```java
private String determineBackgroundColor(Long ruleId, TemplateRule rule) {
    if (rule != null && rule.getHighlightColor() != null && !rule.getHighlightColor().isEmpty()) {
        String color = rule.getHighlightColor().trim();
        // 规范化十六进制颜色：确保有 # 前缀
        if (color.matches("[0-9a-fA-F]{6}")) {
            color = "#" + color;
        }
        log.debug("determineBackgroundColor: 使用 ruleId={} 的 highlightColor={}", ruleId, color);
        return color;
    }
    if (ruleId != null) {
        int hue = (int) ((ruleId * 137.508) % 360);
        String autoColor = String.format("hsl(%d, 60%%, 85%%)", hue);
        log.debug("determineBackgroundColor: ruleId={} 无 highlightColor，自动生成={}", ruleId, autoColor);
        return autoColor;
    }
    log.debug("determineBackgroundColor: ruleId 为 null，返回 null");
    return null;
}
```

**优点**：兼容无 `#` 前缀的颜色值，增加日志便于调试。

### 方案 C：`previewPdf()` 优化，避免重复调用 `preview()`

**目标**：减少 PDF 生成时的冗余计算。

**修改方式**：在 `WordParseService` 中新增一个轻量方法，只返回段落底色列表，不做完整预览：

```java
/**
 * 仅获取段落底色列表（供 PDF 生成使用，避免 preview() 的冗余计算）。
 */
public List<ParagraphItemDTO> getParagraphBackgrounds(Long fileId) {
    UploadFile uf = uploadFileMapper.selectById(fileId);
    if (uf == null || uf.getParsedJson() == null) {
        return Collections.emptyList();
    }

    // 反序列化匹配数据
    List<RecognitionEngine.ParagraphMatch> matches;
    try {
        matches = objectMapper.readValue(
            uf.getParsedJson(),
            objectMapper.getTypeFactory().constructCollectionType(List.class, RecognitionEngine.ParagraphMatch.class));
    } catch (Exception e) {
        log.error("parsedJson 反序列化失败: fileId={}", fileId, e);
        return Collections.emptyList();
    }

    // 获取规则列表
    List<TemplateRule> rules = templateRuleMapper.selectList(
        new LambdaQueryWrapper<TemplateRule>().eq(TemplateRule::getTemplateId, uf.getTemplateId()));
    Map<Long, TemplateRule> ruleMap = rules.stream()
        .collect(Collectors.toMap(TemplateRule::getId, r -> r));

    List<ParagraphItemDTO> items = new ArrayList<>();
    for (RecognitionEngine.ParagraphMatch match : matches) {
        ParagraphItemDTO item = new ParagraphItemDTO();
        item.setIndex(match.getIndex());
        item.setText(match.getText());

        TemplateRule rule = match.getRuleId() != null ? ruleMap.get(match.getRuleId()) : null;
        if (match.getMatchedType() == RecognitionEngine.MatchedType.UNKNOWN) {
            item.setBackgroundColor(null);
        } else {
            item.setBackgroundColor(determineBackgroundColor(match.getRuleId(), rule));
        }
        items.add(item);
    }
    return items;
}
```

然后 `WordController.previewPdf()` 调用此方法代替 `preview()`。

**优点**：避免 PDF 生成时的不必要计算（图例生成、样式提取、PDF 位置记录等）。

---

## 改动范围汇总

### 必须修改

| 文件 | 改动类型 | 说明 |
|------|----------|------|
| `WordParseService.java` | **修改** | `determineBackgroundColor()` 增加格式规范化、日志、数据库降级查询 |
| `WordParseService.java` | **新增方法** | `getParagraphBackgrounds()` 轻量方法（可选，用于方案 C） |
| `WordController.java` | **修改** | `previewPdf()` 改用新方法（如实施方案 C） |

### 建议修改

| 文件 | 改动类型 | 说明 |
|------|----------|------|
| `PoiPdfConversionService.java` | **修改** | `parseColor()` 增加对无 `#` 前缀的十六进制颜色的兼容处理 |

### 无需修改

| 文件 | 说明 |
|------|------|
| `LegendItemDTO.java` | 数据结构正确，无需改动 |
| `ParagraphItemDTO.java` | 数据结构正确，无需改动 |
| `PreviewResultDTO.java` | 数据结构正确，无需改动 |
| `TemplateRule.java` | 实体类正确，无需改动 |
| `TemplateRuleDTO.java` | DTO 正确，无需改动 |
| 前端文件 | 图例面板和 PDF 展示逻辑无需改动（数据端修复后自动生效） |

---

## 约束与风险

| 风险项 | 说明 | 应对方案 |
|--------|------|----------|
| 数据库查询开销 | `determineBackgroundColor` 新增的 `selectById` 降级查询仅在 `ruleMap` 未命中时触发 | 正常场景下 `ruleMap` 包含所有规则，不会触发降级查询；即使触发也是单条主键查询，性能影响微乎其微 |
| 颜色格式规范化副作用 | 自动加 `#` 可能影响原 HSL 格式的判断 | 规范化只针对 `[0-9a-fA-F]{6}` 格式（纯六位十六进制），和 HSL 前缀不冲突 |
| `getParagraphBackgrounds` 与 `preview()` 行为不一致 | 两个方法独立维护，可能导致未来修改不同步 | `getParagraphBackgrounds` 复用 `determineBackgroundColor` 方法，保持颜色逻辑一致；若 `preview()` 增加新逻辑，`getParagraphBackgrounds` 需同步评估 |
| 空文档/全部 UNKNOWN 段落 | 无匹配规则时所有段落 backgroundColor 为 null，PDF 完全没有底色 | 这是预期行为（无匹配自然无底色），不属于 bug |

---

## 测试验证要点

1. **规则存在且 highlightColor 已设置**：
   - 图例面板颜色方块应显示设置的 highlightColor
   - PDF 预览中对应的段落应显示该颜色的背景（35% 透明度）

2. **规则存在但 highlightColor 未设置**：
   - 图例面板应显示自动生成的 HSL 颜色
   - PDF 预览底色应为自动生成的颜色

3. **规则存在但 highlightColor 格式不规范**（如 `FF0000` 无 `#`）：
   - 图例面板颜色应正常显示
   - PDF 底色也应正常显示（解析时自动补 `#`）

4. **规则被删除后预览**：
   - 若 `parsedJson` 中仍有该 `ruleId`，规则不存在时自动生成 HSL 颜色作为兜底
   - PDF 和图例保持一致

5. **第一次打开和调整后刷新**：
   - 颜色状态在页面刷新后保持一致

---

## 结论

本次修复的核心是将 `determineBackgroundColor()` 的容错机制增强、增加颜色格式规范化和日志输出。用户设置 `highlightColor` 后，无论规则是否在 `ruleMap` 中（正常情况应在其中），系统都能正确地优先使用该值，并通过日志输出帮助定位问题。方案 C 的 `getParagraphBackgrounds()` 轻量方法可作为性能优化叠加实施。

---

## 附件：数据流全景图

```
[用户设置 highlightColor]
        │
        ▼
[TemplateDetail.vue] el-color-picker
        │  data.highlightColor = "#FF0000"
        ▼
[TemplateService.updateRule()]
        │  rule.setHighlightColor("#FF0000")
        ▼
[template_rule 表] highlightColor = "#FF0000"
        │
        ▼
[WordParseService.preview()] ─── 查询规则 → ruleMap
        │
        ├─ generateLegend()
        │     └─ determineBackgroundColor(ruleId, rule)
        │           ├─ ✔ rule.getHighlightColor() != null → "#FF0000"
        │           └─ ✗ ruleMap.get() 返回 null → 降级 selectById(ruleId)
        │
        ├─ PreviewResultDTO.legend → 前端图例面板
        │     └─ <span :style="{ backgroundColor: item.color }" />
        │
        └─ ParagraphItemDTO.backgroundColor
              │
              ▼
        [PoiPdfConversionService.renderParagraphsWithBackground()]
              │  bgColorMap.get(paraIdx) → parseColor("#FF0000") → Color.RED
              │  cs.addRect() + cs.fill()
              ▼
        PDF 渲染红色底色
```

---

如果确认以上设计，我会将任务交给 Agent 2（开发工程师）开始实现。
