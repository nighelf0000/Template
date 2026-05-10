# highlightColor 与 HSL 格式统一修复设计

## 概述

本文档在已有设计（`样式图例与PDF底色高亮颜色修复设计.md`）的基础上，**重点针对用户提出的「格式不一致」假设进行验证分析**，并给出最终的修复方案。

已有设计文档已在代码中部分实施（数据库降级查询已添加），但用户仍然反馈 `highlightColor` 不生效。本文档将确认格式问题是否是根因，并补充遗漏的修复点。

---

## 1. 颜色消费端分析

### 消费者一：前端图例面板（CSS background-color）

**位置**：`ParsePreview.vue:86`

```html
<span class="legend-color" :style="{ backgroundColor: item.color }"></span>
```

`item.color` 来自后端 `LegendItemDTO.color`，即 `determineBackgroundColor()` 的返回值。

**CSS 对两种格式的支持情况**：

| 格式 | 示例 | CSS 是否支持 |
|------|------|-------------|
| `#RRGGBB` | `#FF0000` | 完全支持（CSS 原生） |
| `hsl(H, S%, L%)` | `hsl(45, 60%, 85%)` | 完全支持（CSS3+ 原生） |

**结论**：前端图例面板可正确处理两种格式，格式差异不是前端问题的根因。

---

### 消费者二：PDF 渲染（PoiPdfConversionService.parseColor）

**位置**：`PoiPdfConversionService.java:736-751`

```java
private Color parseColor(String colorStr) {
    if (colorStr == null || colorStr.isEmpty()) return null;
    try {
        if (colorStr.startsWith("#")) {
            return Color.decode(colorStr);              // 处理 #RRGGBB
        } else if (colorStr.startsWith("hsl")) {
            return parseHsl(colorStr);                   // 处理 hsl(H, S%, L%)
        } else if (colorStr.matches("[0-9a-fA-F]{6}")) {
            return Color.decode("#" + colorStr);         // 处理无#前缀的六位十六进制
        }
    } catch (Exception e) {
        log.warn("颜色解析失败: {}", colorStr, e);
    }
    return null;
}
```

**两种格式的分支分析**：

| 输入格式 | 分支 | 内部处理 | 结果 |
|----------|------|----------|------|
| `#FF0000` | `startsWith("#")` | `Color.decode("#FF0000")` | AWT Color 对象 |
| `hsl(45, 60%, 85%)` | `startsWith("hsl")` | `parseHsl()` → `hslToRgb()` | AWT Color 对象 |

**结论**：PDF 渲染端也正确处理两种格式，格式差异不是 PDF 问题的根因。

**但注意**：`parseColor` 对输入字符串质量敏感——如果颜色字符串包含前后空白字符（如 ` #FF0000`），`startsWith("#")` 会返回 `false`，导致解析失败返回 `null`。详见下文根因分析。

---

### 消费者三：前端段落预览（ParsePreview.vue:152）

```html
<div class="adjust-preview" :style="paragraphStylePreview(para) ? paragraphInlineStyle(para.style) : {}">
```

该处仅使用 `style` 字段（字体样式），**并未使用** `backgroundColor`。段落底色只在 PDF 渲染中使用。

**结论**：段落列表中底色不可见是正常行为，非 Bug。

---

## 2. 根因确认

### 2.1 格式差异是否根因？**不是**

通过上述分析证明：`#RRGGBB` 和 `hsl(H, S%, L%)` 两种颜色格式在**两个消费端均能正确渲染**。

| 消费端 | `#RRGGBB` | `hsl()` |
|--------|-----------|---------|
| 前端 CSS `background-color` | 支持 | 支持 |
| PDF `parseColor` | `Color.decode()` | `parseHsl()` → `hslToRgb()` |

因此，**格式不一致不是底色不生效的根因**。

### 2.2 真实根因：ruleMap 查找失败导致 highlightColor 丢失

当前 `determineBackgroundColor` 已包含三层逻辑：

```
Branch 1: rule != null && highlightColor 非空 → 返回 highlightColor（#RRGGBB 格式）
Branch 2: rule == null && ruleId != null → DB 降级查询（已有的修复）
Branch 3: ruleId != null → 自动生成 HSL
```

**问题场景**：

1. `ruleMap.get(match.getRuleId())` 返回 `null`
   - 可能原因：规则被删除、规则关联的模板与文件模板不一致、数据不一致
   - 此时 `rule` 参数为 `null`，Branch 1 被跳过
2. Branch 2（DB 降级）尝试 `selectById(ruleId)`：
   - 如果规则已被删除 → `selectById` 返回 `null` → 无法获取 `highlightColor`
   - 如果规则存在且 `highlightColor` 非空 → 可以获取，**这个场景已被修复**
3. 无论如何落到了 Branch 3 → 返回自动生成的 HSL 颜色
4. **用户看到的是自动生成的 HSL 颜色，而非自己设置的 `highlightColor`**

**关键结论**：用户观察到的「格式不一致」现象，本质上是 `highlightColor` 被静默丢弃导致的「颜色值不同」，而不是格式本身不可渲染。用户设置的颜色（`#RRGGBB`）被自动生成的颜色（`hsl`）替代了。

### 2.3 次要根因：颜色字符串未 trim

当前 `determineBackgroundColor` 没有对 `highlightColor` 做 `.trim()`：

```java
String color = rule.getHighlightColor(); // 未 trim！若数据库存储 " #FF0000"（带空格）则有问题
```

如果数据库中的 `highlightColor` 包含前导/后置空白字符（如 ` #FF0000`）：

1. **`determineBackgroundColor` 返回值**：`" #FF0000"`（含前导空格）
2. **CSS 渲染**：`background-color:  #FF0000` → CSS 容错性高，仍可正常显示
3. **PDF 渲染**：`parseColor(" #FF0000")`：
   - `" #FF0000".startsWith("#")` → **false**（首字符是空格）
   - `" #FF0000".startsWith("hsl")` → false
   - `" #FF0000".matches("[0-9a-fA-F]{6}")` → false
   - 所有分支均不匹配，try 块正常结束（无异常抛出）
   - 返回 `null`
   - **PDF 段落底色不会被绘制**

但此场景罕见，仅在数据库被直接修改或 API 传入不规范值时才会触发。

---

## 3. 修复方案

### 方案一：完善 `determineBackgroundColor`（核心修复）

在已有的三层逻辑基础上，做以下改进：

**1. 添加 `.trim()` 规范化处理**

```java
if (rule != null && rule.getHighlightColor() != null && !rule.getHighlightColor().isEmpty()) {
    String color = rule.getHighlightColor().trim();  // 添加 .trim()
    // 规范化十六进制颜色：确保有 # 前缀
    if (color.matches("[0-9a-fA-F]{6}")) {
        color = "#" + color;
    }
    return color;
}
```

DB 降级查询分支（Branch 2）同样需要 `.trim()`。

**2. 添加日志输出**

```java
if (rule != null && rule.getHighlightColor() != null && !rule.getHighlightColor().isEmpty()) {
    String color = rule.getHighlightColor().trim();
    if (color.matches("[0-9a-fA-F]{6}")) {
        color = "#" + color;
    }
    log.debug("determineBackgroundColor: ruleId={} 使用 highlightColor={}", ruleId, color);
    return color;
}
if (rule == null && ruleId != null) {
    TemplateRule dbRule = templateRuleMapper.selectById(ruleId);
    if (dbRule != null && dbRule.getHighlightColor() != null && !dbRule.getHighlightColor().isEmpty()) {
        String color = dbRule.getHighlightColor().trim();
        if (color.matches("[0-9a-fA-F]{6}")) {
            color = "#" + color;
        }
        log.debug("determineBackgroundColor: ruleId={} 从数据库降级查到 highlightColor={}", ruleId, color);
        return color;
    }
}
if (ruleId != null) {
    int hue = (int) ((ruleId * 137.508) % 360);
    String autoColor = String.format("hsl(%d, 60%%, 85%%)", hue);
    log.debug("determineBackgroundColor: ruleId={} 无 highlightColor，自动生成 HSL={}", ruleId, autoColor);
    return autoColor;
}
log.debug("determineBackgroundColor: ruleId 为 null，返回 null");
return null;
```

### 方案二：增强 `parseColor` 的健壮性（防御性修复）

在 `PoiPdfConversionService.parseColor()` 中对输入字符串做 `.trim()`：

```java
private Color parseColor(String colorStr) {
    if (colorStr == null || colorStr.isEmpty()) return null;
    colorStr = colorStr.trim();  // 防御性 trim
    try {
        ...
    }
}
```

### 方案三：前端段落预览增加底色显示（可选增强）

目前 `ParsePreview.vue:152` 段落预览不展示 `backgroundColor`。如果需要在前端段落列表中看到底色，可添加：

```html
<div class="adjust-preview"
     :style="{
       ...(paragraphStylePreview(para) ? paragraphInlineStyle(para.style) : {}),
       ...(para.backgroundColor ? { backgroundColor: para.backgroundColor } : {})
     }">
```

这可以帮助用户在前端直接验证底色设置是否生效。但需注意：`ParagraphItemDTO.backgroundColor` 的透明度（PDF 渲染中为 35%）与前端 CSS 的表现可能存在视觉差异。

---

## 4. 变更范围汇总

### 必须修改

| 文件 | 改动 | 说明 |
|------|------|------|
| `WordParseService.java` | `determineBackgroundColor()` 增加 `.trim()` 和日志 | 修复空白字符问题，增强可调试性 |

### 建议修改

| 文件 | 改动 | 说明 |
|------|------|------|
| `PoiPdfConversionService.java` | `parseColor()` 增加 `.trim()` | 防御性修复 |

### 可选修改

| 文件 | 改动 | 说明 |
|------|------|------|
| `ParsePreview.vue` | 段落预览增加 `backgroundColor` 显示 | 前端体验增强 |

---

## 5. 改动量评估

本次修复改动量极小：

- `WordParseService.java`：仅修改 `determineBackgroundColor()` 方法内部，添加 `.trim()`（2处）和 `log.debug`（4处），**无新增方法**
- `PoiPdfConversionService.java`：`parseColor()` 入口添加一行 `colorStr = colorStr.trim()`
- `ParsePreview.vue`（可选）：修改段落预览的 `:style` 绑定

**不涉及**：
- 数据库表结构变更
- DTO 类变更
- API 接口变更
- 前端组件结构变更

---

## 6. 测试验证要点

1. **正常场景**：设置 `highlightColor = "#FF0000"`，确认图例面板颜色为红色，PDF 段落底色为红色（35% 透明度）
2. **无 `#` 前缀**：设置 `highlightColor = "FF0000"`，确认两端均正常渲染
3. **带空白字符**：手动修改数据库为 `" #FF0000"`（前导空格），确认两端均正常渲染
4. **不设置 highlightColor**：确认自动生成 HSL 颜色正常显示
5. **规则被删除后预览**：确认降级到 Branch 3（自动 HSL），不抛异常
6. **日志验证**：确认在 debug 级别可以看到 `determineBackgroundColor` 的决策日志
