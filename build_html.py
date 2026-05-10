# -*- coding: utf-8 -*-
import re, os, markdown

MD_FILE = r"C:\AITeam\Projects\Template\Design\Template系统操作手册.md"
HTML_OUT = r"C:\AITeam\Projects\Template\Design\Template系统操作手册.html"
SS_DIR = "screenshots"

with open(MD_FILE, "r", encoding="utf-8") as f:
    md_content = f.read()

# ===== 截图映射 (关键词, 文件, 标题, 说明) =====
screen_map = [
    ("系统首页.*模板列表页面", "01-模板列表.png",
     "系统首页 — 模板列表",
     "左侧导航菜单（模板列表、引擎配置、文件上传、解析预览），右侧内容区显示模板表格和分页控件。"),

    ("新建模板对话框.*标注名称", "02-新建模板对话框.png",
     "新建模板对话框",
     "在模板名称输入框中填写名称后点击确定。"),

    ("新建模板对话框$", "02-新建模板对话框.png",
     "新建模板对话框",
     "弹窗包含模板名称输入框和取消/确定按钮。"),

    ("新建.*本科论文模板", "03-新建模板-填写名称.png",
     "新建模板 — 填写名称",
     "输入本科论文模板后点击确定完成创建。"),

    ("模板详情页.*基本信息", "04-模板详情-基本信息.png",
     "模板详情页 — 基本信息",
     "展示模板名称编辑框和保存按钮，下方为规则列表区域。"),

    ("规则列表卡片", "05-模板详情-规则列表.png",
     "模板详情页 — 规则列表",
     "表格展示规则 ID、名称、字体、字号、加粗、斜体，每行有编辑和删除按钮。"),

    ("规则列表.*5条规则", "05-模板详情-规则列表.png",
     "规则配置完成 — 5 条规则",
     "封面样式、目录样式、一级标题样式、二级标题样式、正文样式已全部配置。"),

    ("规则编辑对话框.*标注各字段", "06-规则编辑对话框.png",
     "规则编辑对话框",
     "包含 14 个样式字段：规则名称、字体、字号、加粗/斜体/下划线/删除线开关、字体颜色、文本对齐、首行缩进、行距、段前/段后距、高亮颜色。"),

    ("引擎配置页.*选择模板下拉框.*配置列表", "08-引擎配置列表.png",
     "引擎配置页 — 选择模板后",
     "顶部下拉框选择模板，下方表格显示该模板的引擎配置列表。"),

    ("引擎配置列表.*5条配置", "08-引擎配置列表.png",
     "引擎配置完成 — 5 条配置",
     "封面内容匹配(COVER)、目录行匹配(TOC)、一级标题匹配(TITLE-1)、二级标题匹配(TITLE-2)、正文兜底匹配(BODY)。"),

    ("引擎配置页.*选择模板下拉框", "07-引擎配置-选择模板前.png",
     "引擎配置页 — 选择模板前",
     "下拉框未选择模板时的初始状态。"),

    ("引擎配置编辑对话框.*标注各字段", "09-引擎配置编辑对话框.png",
     "引擎配置编辑对话框",
     "包含配置名称、正则表达式、匹配类型、标题级别、关联样式、排序序号、启用状态。"),

    ("文件上传页.*拖拽区域.*关联模板", "10-文件上传页.png",
     "文件上传页面",
     "顶部关联模板下拉选择框（可选），中间是 Element Plus 拖拽上传组件（虚线边框+上传图标），选中文件后出现开始上传按钮。"),

    ("文件列表页.*标注各操作按钮和状态标签", "11-文件列表-完整.png",
     "文件列表 — 操作按钮与状态",
     "表格列：文件名、大小、关联模板、状态（彩色标签）、上传时间、操作（解析/预览/导出/下载/修改/删除）。"),

    ("文件列表.*解析成功后状态为 PARSED", "11-文件列表-完整.png",
     "文件列表 — 解析完成",
     "文件状态列显示绿色 PARSED 标签，预览和导出按钮变为可用状态。"),

    ("上传论文文件", "10-文件上传页.png",
     "上传论文文件",
     "关联模板选中本科论文模板，拖拽或选择论文 .docx 文件后点击开始上传。"),

    ("修改关联模板对话框", "12-修改关联模板对话框.png",
     "修改关联模板对话框",
     "弹窗显示当前文件名（只读），下方为关联模板下拉选择框（可清空），底部取消/确定按钮。"),

    ("预览页全貌.*标注三大区域", "13-解析预览-全貌.png",
     "解析预览页全貌",
     "三栏布局：顶栏（模板名+段落数+页码+操作按钮）、左侧 PDF 画布（彩色高亮叠加）、右侧面板（上图例+下段落调整列表）。"),

    ("解析预览页面全貌", "13-解析预览-全貌.png",
     "解析预览页全貌",
     "三栏布局：顶栏（模板名+段落数+页码）、左侧 PDF 渲染区（高亮底色覆盖）、右侧面板（上图例+下段落调整折叠列表）。"),

    ("PDF 预览区.*标注高亮底色和翻页控件", "13-解析预览-全貌.png",
     "PDF 预览区",
     "左侧 PDF 页面以 Canvas 渲染，匹配段落覆盖半透明彩色高亮。底部翻页控件：上一页按钮、页码输入框、/总页数、下一页按钮。"),

    ("PDF 预览.*标注不同颜色的高亮区域", "13-解析预览-全貌.png",
     "PDF 预览 — 高亮效果",
     "各段落按匹配类型显示不同底色：封面(橙)、目录/标题(蓝)、正文(绿)，透明度约 35%，叠加在 PDF 文字上。"),

    ("样式图例面板", "13-解析预览-全貌.png",
     "样式图例面板",
     "右侧面板上半部分，每项显示颜色横条、规则名称和匹配类型标签。"),

    ("段落调整.*展开状态.*标注规则下拉框", "14-段落调整-展开状态.png",
     "段落调整面板 — 展开状态",
     "段落卡片展开后显示文本预览（前60字+省略号），选择样式规则下拉框列出当前模板所有规则，以及字号字体等样式信息。"),

    ("段落调整.*UNKNOWN 段落展开并选择规则", "14-段落调整-展开状态.png",
     "调整 UNKNOWN 段落",
     "红色 UNKNOWN 标签的段落展开后，从选择样式规则下拉框中选择正确规则。保存后自动创建 SPECIAL 配置。"),

    ("保存确认", "14-段落调整-展开状态.png",
     "保存调整确认",
     "点击顶栏保存调整按钮后，显示 loading 动画，成功后顶部弹出绿色保存成功提示，页面自动刷新。"),

    ("导出成功提示", "11-文件列表-完整.png",
     "导出成功提示",
     "在文件列表点击导出后，顶部弹出绿色导出成功提示，文件状态变为灰色 EXPORTED。"),
]

# ===== 替换截图标记 =====
def replace_screenshot(match):
    desc = match.group(1) or match.group(2)
    for keyword, filename, title, caption in screen_map:
        if re.search(keyword, desc):
            return (
                '<figure class="screenshot-figure">\n'
                f'  <img src="{SS_DIR}/{filename}" alt="{title}" loading="lazy" />\n'
                f'  <figcaption>图：{caption}</figcaption>\n'
                '</figure>'
            )
    # 无匹配时保留文字说明
    return (
        '<div class="screen-desc"><span class="screen-desc-icon">'
        f'</span><div class="screen-desc-body"><strong>页面示意：</strong>{desc}</div></div>'
    )

pattern = r'\*\*\[截图：(.+?)\]\*\*|\[截图：(.+?)\]'
md_content = re.sub(pattern, replace_screenshot, md_content)

# ===== 转换提示/注意/警告块 =====
def convert_blocks(text):
    # > **注意**：...
    text = re.sub(
        r'> \*\*注意\*\*[：:]\s*(.+?)(?=\n\n|\n(?:>|\*\*|[A-Z])|\Z)',
        r'<blockquote class="warning-block"><strong>注意：</strong>\1</blockquote>\n\n',
        text, flags=re.DOTALL
    )
    # > **警告**：...
    text = re.sub(
        r'> \*\*警告\*\*[：:]\s*(.+?)(?=\n\n|\n(?:>|\*\*|[A-Z])|\Z)',
        r'<blockquote class="warning-block"><strong>警告：</strong>\1</blockquote>\n\n',
        text, flags=re.DOTALL
    )
    # > **提示**：...
    text = re.sub(
        r'> \*\*提示\*\*[：:]\s*(.+?)(?=\n\n|\n(?:>|\*\*|[A-Z])|\Z)',
        r'<blockquote class="tip-block"><strong>提示：</strong>\1</blockquote>\n\n',
        text, flags=re.DOTALL
    )
    # > **说明**：...
    text = re.sub(
        r'> \*\*说明\*\*[：:]\s*(.+?)(?=\n\n|\n(?:>|\*\*|[A-Z])|\Z)',
        r'<blockquote class="tip-block"><strong>说明：</strong>\1</blockquote>\n\n',
        text, flags=re.DOTALL
    )
    return text

md_content = convert_blocks(md_content)

# ===== Markdown to HTML =====
md = markdown.Markdown(extensions=['tables', 'fenced_code', 'codehilite', 'toc', 'nl2br'])
html_body = md.convert(md_content)

# 表格 class
html_body = html_body.replace('<table>', '<table class="data-table">')

# 状态标签
tag_colors = {
    'UPLOADED': 'blue', 'PARSED': 'green', 'ADJUSTED': 'orange',
    'EXPORTED': 'gray', 'PARSE_FAILED': 'red', 'EXPORT_FAILED': 'red',
    'UNKNOWN': 'red', 'SPECIAL': 'gray',
    'COVER': 'orange', 'TOC': 'info', 'TITLE': 'primary', 'BODY': 'green',
}
for text, css_cls in tag_colors.items():
    html_body = re.sub(
        rf'(?<!["\w>-])({text})(?!["<\w])',
        rf'<span class="status-tag status-{css_cls}">\1</span>',
        html_body
    )

# ===== 输出 =====
css = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Template 系统 — 操作手册</title>
<style>
  :root {
    --primary: #1a73e8; --bg: #f8f9fa; --card: #ffffff;
    --border: #e0e0e0; --text: #333; --text-secondary: #666;
    --code-bg: #f1f3f4; --table-stripe: #f5f7fa;
    --success: #1e8e3e; --warn: #f9ab00; --danger: #ea4335;
  }
  * { margin: 0; padding: 0; box-sizing: border-box; }
  html { scroll-behavior: smooth; }
  body {
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "Microsoft YaHei", sans-serif;
    background: var(--bg); color: var(--text); line-height: 1.8; padding: 20px;
  }
  .container {
    max-width: 1100px; margin: 0 auto; background: var(--card);
    border-radius: 12px; box-shadow: 0 2px 12px rgba(0,0,0,0.08);
    padding: 48px 56px; position: relative;
  }
  h1 { font-size: 30px; border-bottom: 3px solid var(--primary); padding-bottom: 16px; margin-bottom: 12px; color: #111; }
  h2 { font-size: 22px; margin: 44px 0 18px; padding-left: 14px; border-left: 5px solid var(--primary); color: #1a1a1a; }
  h3 { font-size: 18px; margin: 28px 0 14px; color: #333; padding-bottom: 6px; border-bottom: 1px solid #eee; }
  h4 { font-size: 16px; margin: 20px 0 10px; color: #444; }
  p { margin: 10px 0; }

  /* 表格 */
  .data-table { width: 100%; border-collapse: collapse; margin: 14px 0 22px; font-size: 14px; }
  .data-table th {
    background: #e8f0fe; color: #1a56b9; font-weight: 600; padding: 10px 14px;
    text-align: left; border: 1px solid var(--border); white-space: nowrap;
  }
  .data-table td { padding: 8px 14px; border: 1px solid var(--border); vertical-align: top; }
  .data-table tr:nth-child(even) td { background: var(--table-stripe); }

  /* 代码 */
  code {
    font-family: "Cascadia Code", "Fira Code", "Consolas", monospace;
    background: var(--code-bg); padding: 2px 7px; border-radius: 4px; font-size: 13px; color: #c7254e;
  }
  pre {
    background: #1e1e1e; color: #d4d4d4; padding: 18px 22px; border-radius: 8px;
    overflow-x: auto; font-size: 13px; line-height: 1.6; margin: 14px 0 22px;
  }
  pre code { background: transparent; padding: 0; color: #d4d4d4; font-size: 13px; }
  pre.diagram { background: #2d2d2d; color: #c0c0c0; line-height: 1.4; }

  /* 状态标签 */
  .status-tag {
    display: inline-block; padding: 2px 10px; border-radius: 4px;
    font-size: 12px; font-weight: 600; white-space: nowrap; vertical-align: middle;
  }
  .status-blue    { background: #e8f0fe; color: #1a56b9; }
  .status-green   { background: #e6f4ea; color: #137333; }
  .status-orange  { background: #fef7e0; color: #b06000; }
  .status-gray    { background: #f1f3f4; color: #5f6368; }
  .status-red     { background: #fce8e6; color: #c5221f; }
  .status-info    { background: #e8f0fe; color: #1a56b9; }
  .status-primary { background: #e8f0fe; color: #1a56b9; }

  /* 提示块 */
  blockquote.tip-block {
    background: #e8f0fe; border-left: 4px solid var(--primary);
    margin: 14px 0; padding: 12px 18px; border-radius: 0 8px 8px 0;
    color: #1a56b9; font-size: 14px;
  }
  blockquote.tip-block p { margin: 4px 0; }
  blockquote.warning-block {
    background: #fef7e0; border-left: 4px solid var(--warn);
    margin: 14px 0; padding: 12px 18px; border-radius: 0 8px 8px 0;
    color: #b06000; font-size: 14px;
  }

  /* 截图 */
  .screenshot-figure {
    margin: 20px 0; text-align: center; background: #fafafa;
    border: 1px solid #e8e8e8; border-radius: 10px; padding: 16px 16px 8px; overflow: hidden;
  }
  .screenshot-figure img {
    max-width: 100%; height: auto; border-radius: 6px;
    box-shadow: 0 2px 8px rgba(0,0,0,0.1); border: 1px solid #e0e0e0;
  }
  .screenshot-figure figcaption { margin-top: 10px; font-size: 13px; color: #888; font-style: italic; }

  /* 文字描述（无截图降级） */
  .screen-desc {
    display: flex; align-items: flex-start; gap: 14px; background: #f8fafc;
    border: 1px solid #dde4ed; border-left: 4px solid var(--primary);
    border-radius: 8px; padding: 16px 20px; margin: 18px 0;
  }
  .screen-desc-icon { font-size: 28px; flex-shrink: 0; line-height: 1.4; }
  .screen-desc-body { font-size: 14px; color: var(--text-secondary); line-height: 1.7; }
  .screen-desc-body strong { color: #1a56b9; font-size: 15px; }

  ul, ol { margin: 8px 0 8px 24px; }
  li { margin: 4px 0; }
  a { color: var(--primary); text-decoration: none; }
  a:hover { text-decoration: underline; }
  strong { color: #111; }
  hr { border: none; border-top: 1px solid var(--border); margin: 32px 0; }

  @media (max-width: 768px) {
    .container { padding: 24px 18px; border-radius: 0; }
    h1 { font-size: 22px; } h2 { font-size: 18px; }
    .data-table { font-size: 12px; }
    .data-table th, .data-table td { padding: 6px 8px; }
  }
  @media print {
    body { background: white; padding: 0; }
    .container { box-shadow: none; border-radius: 0; max-width: 100%; }
    pre { background: #f5f5f5; color: #333; border: 1px solid #ddd; }
    pre code { color: #333; }
  }
</style>
</head>
<body>
<div class="container">
"""

html_output = css + html_body + "\n</div>\n</body>\n</html>"

with open(HTML_OUT, "w", encoding="utf-8") as f:
    f.write(html_output)

img_count = html_output.count('<img src="screenshots/')
desc_count = html_output.count('screen-desc-icon')
print(f"HTML 已生成: {HTML_OUT}")
print(f"大小: {len(html_output):,} 字符")
print(f"实际截图: {img_count} 张")
print(f"文字说明(无截图): {desc_count} 处")
