-- ============================================================
-- 论文模板2025.11 - 初始化数据脚本
-- 生成日期: 2026-05-11
-- 说明:
--   1. template_config: 1条
--   2. template_rule: 79条
--   3. template_engine_config: 73条
-- ============================================================

-- ---------------------
-- 0. 表结构变更（如需）
-- ---------------------
ALTER TABLE template_rule MODIFY COLUMN font_size DECIMAL(5,1) DEFAULT NULL COMMENT '字号(磅值)';

-- ---------------------
-- 1. template_config
-- ---------------------
--INSERT INTO template_config (id, name, is_active) VALUES (1, '论文模板2025.11', 1);

-- ---------------------
-- 2. template_rule (79条)
-- ---------------------

INSERT INTO template_rule (template_id, name, font_name, font_size, text_align, line_spacing) VALUES (1, 'Normal', '宋体', 12.0, 'JUSTIFY', 1.50);
INSERT INTO template_rule (template_id, name, font_name, font_size, font_bold, text_align, line_spacing, space_after) VALUES (1, 'heading 1', '黑体', 15.0, 1, 'CENTER', 1.00, 100);
INSERT INTO template_rule (template_id, name, font_name, font_size, font_bold, text_align, line_spacing, space_after) VALUES (1, 'heading 2', '黑体', 14.0, 1, 'CENTER', 1.00, 50);
INSERT INTO template_rule (template_id, name, font_name, font_bold, space_before) VALUES (1, 'heading 3', '黑体', 1, 50);
INSERT INTO template_rule (template_id, name, font_name, font_bold) VALUES (1, 'heading 4', '黑体', 1);
INSERT INTO template_rule (template_id, name) VALUES (1, 'heading 5');
INSERT INTO template_rule (template_id, name) VALUES (1, 'heading 6');
INSERT INTO template_rule (template_id, name, font_name, font_bold, line_spacing) VALUES (1, '封面学号', '宋体', 1, 1.67);
INSERT INTO template_rule (template_id, name, font_name, line_spacing) VALUES (1, '封面空行', '宋体', 1.42);
INSERT INTO template_rule (template_id, name, font_name, font_size, font_bold, text_align, line_spacing) VALUES (1, '封面类型', '宋体', 22.0, 1, 'CENTER', 2.08);
INSERT INTO template_rule (template_id, name, font_name, font_size, font_bold, text_align, line_spacing, space_before, space_after) VALUES (1, '封面标题（中）', '黑体', 18.0, 1, 'CENTER', 2.50, 50, 100);
INSERT INTO template_rule (template_id, name, font_size, font_bold, text_align, line_spacing) VALUES (1, '封面标题（英）', 16.0, 1, 'CENTER', 2.17);
INSERT INTO template_rule (template_id, name, font_name, font_size, font_bold, text_align) VALUES (1, '封面姓名', '楷体', 18.0, 1, 'CENTER');
INSERT INTO template_rule (template_id, name, font_name, font_size, font_bold, text_align, line_spacing, space_before) VALUES (1, '封面日期', '宋体', 14.0, 1, 'CENTER', 1.42, 100);
INSERT INTO template_rule (template_id, name, font_name, font_size, line_spacing) VALUES (1, '封面信息', '楷体', 15.0, 2.58);
INSERT INTO template_rule (template_id, name, font_name, font_size, font_bold, text_align, line_spacing, space_after) VALUES (1, '声明标题', '黑体', 18.0, 1, 'CENTER', 1.00, 80);
INSERT INTO template_rule (template_id, name, font_size, line_spacing) VALUES (1, '声明正文', 14.0, 0.90);
INSERT INTO template_rule (template_id, name, font_size, text_align, line_spacing) VALUES (1, 'header', 10.5, 'CENTER', 1.00);
INSERT INTO template_rule (template_id, name, font_size, text_align, line_spacing) VALUES (1, 'footer', 10.5, 'CENTER', 1.00);
INSERT INTO template_rule (template_id, name, font_name, font_size, font_bold, text_align, space_before, space_after) VALUES (1, '摘要目录标题', '黑体', 16.0, 1, 'CENTER', 100, 100);
INSERT INTO template_rule (template_id, name, text_align) VALUES (1, '关键词', 'CENTER');
INSERT INTO template_rule (template_id, name, font_size, font_bold, text_align, space_before, space_after) VALUES (1, 'Title', 16.0, 1, 'CENTER', 240, 60);
INSERT INTO template_rule (template_id, name, font_size, text_align) VALUES (1, '附录正文', 10.5, 'CENTER');
INSERT INTO template_rule (template_id, name, font_bold, text_align) VALUES (1, 'caption', 1, 'CENTER');
INSERT INTO template_rule (template_id, name, font_size, font_bold, text_align, line_spacing, space_before, space_after) VALUES (1, 'Subtitle', 16.0, 1, 'CENTER', 1.30, 240, 60);
INSERT INTO template_rule (template_id, name, font_size, text_align) VALUES (1, '图表资料来源', 9.0, 'CENTER');
INSERT INTO template_rule (template_id, name, font_size, text_align, line_spacing) VALUES (1, '图表正文', 10.5, 'CENTER', 1.00);
INSERT INTO template_rule (template_id, name, font_size, text_align) VALUES (1, '图表单位', 10.5, 'RIGHT');
INSERT INTO template_rule (template_id, name) VALUES (1, 'List Paragraph');
INSERT INTO template_rule (template_id, name, font_size, line_spacing, space_before) VALUES (1, 'endnote text', 10.5, 1.33, 60);
INSERT INTO template_rule (template_id, name, line_spacing) VALUES (1, 'table of figures', 1.15);
INSERT INTO template_rule (template_id, name, font_name, text_align) VALUES (1, '公式', 'Cambria Math', 'CENTER');
INSERT INTO template_rule (template_id, name) VALUES (1, '正文-缩进');
INSERT INTO template_rule (template_id, name, font_name, font_size) VALUES (1, 'Revision', '宋体', 12.0);
INSERT INTO template_rule (template_id, name, font_size, line_spacing) VALUES (1, 'annotation text', 10.0, 1.00);
INSERT INTO template_rule (template_id, name, font_bold) VALUES (1, 'annotation subject', 1);
INSERT INTO template_rule (template_id, name, font_size, text_align) VALUES (1, 'footnote text', 9.0, 'LEFT');
INSERT INTO template_rule (template_id, name) VALUES (1, '附录正文-缩进');
INSERT INTO template_rule (template_id, name) VALUES (1, 'Date');
INSERT INTO template_rule (template_id, name, font_name, font_size, font_bold, text_align) VALUES (1, 'toc 1', '黑体', 14.0, 1, 'LEFT');
INSERT INTO template_rule (template_id, name, font_name, font_bold, text_align) VALUES (1, 'toc 2', '黑体', 1, 'LEFT');
INSERT INTO template_rule (template_id, name, text_align) VALUES (1, 'toc 3', 'LEFT');
INSERT INTO template_rule (template_id, name, font_size, text_align) VALUES (1, 'toc 4', 9.0, 'LEFT');
INSERT INTO template_rule (template_id, name, font_size, text_align) VALUES (1, 'toc 5', 9.0, 'LEFT');
INSERT INTO template_rule (template_id, name, font_size, text_align) VALUES (1, 'toc 6', 9.0, 'LEFT');
INSERT INTO template_rule (template_id, name, font_size, text_align) VALUES (1, 'toc 7', 9.0, 'LEFT');
INSERT INTO template_rule (template_id, name, font_size, text_align) VALUES (1, 'toc 8', 9.0, 'LEFT');
INSERT INTO template_rule (template_id, name, font_size, text_align) VALUES (1, 'toc 9', 9.0, 'LEFT');
INSERT INTO template_rule (template_id, name) VALUES (1, 'Default Paragraph Font');
INSERT INTO template_rule (template_id, name, font_name, text_align) VALUES (1, '页眉 字符', '宋体', 'CENTER');
INSERT INTO template_rule (template_id, name, font_name, text_align) VALUES (1, '页脚 字符', '宋体', 'CENTER');
INSERT INTO template_rule (template_id, name, text_align) VALUES (1, 'page number', 'CENTER');
INSERT INTO template_rule (template_id, name, font_name, font_size, font_bold, text_align) VALUES (1, '标题 1 字符', '黑体', 15.0, 1, 'CENTER');
INSERT INTO template_rule (template_id, name, font_name, font_size, font_bold, text_align) VALUES (1, '标题 2 字符', '黑体', 14.0, 1, 'CENTER');
INSERT INTO template_rule (template_id, name, font_name, font_size, font_bold, text_align) VALUES (1, '标题 3 字符', '黑体', 12.0, 1, 'CENTER');
INSERT INTO template_rule (template_id, name, font_name, font_size, font_bold, text_align) VALUES (1, '标题 4 字符', '黑体', 12.0, 1, 'RIGHT');
INSERT INTO template_rule (template_id, name, font_name, font_size, text_align) VALUES (1, '标题 5 字符', '宋体', 12.0, 'CENTER');
INSERT INTO template_rule (template_id, name, font_name, font_size, text_align) VALUES (1, '标题 6 字符', '宋体', 12.0, 'CENTER');
INSERT INTO template_rule (template_id, name, font_size, font_bold, text_align) VALUES (1, '标题 字符', 16.0, 1, 'CENTER');
INSERT INTO template_rule (template_id, name, text_align) VALUES (1, 'Hyperlink', 'LEFT');
INSERT INTO template_rule (template_id, name, font_size, font_bold, text_align) VALUES (1, '副标题 字符', 16.0, 1, 'CENTER');
INSERT INTO template_rule (template_id, name, font_name) VALUES (1, '尾注文本 字符', '宋体');
INSERT INTO template_rule (template_id, name) VALUES (1, 'endnote reference');
INSERT INTO template_rule (template_id, name) VALUES (1, 'Unresolved Mention');
INSERT INTO template_rule (template_id, name) VALUES (1, 'Placeholder Text');
INSERT INTO template_rule (template_id, name, font_size) VALUES (1, 'annotation reference', 8.0);
INSERT INTO template_rule (template_id, name, font_name, font_size) VALUES (1, '批注文字 字符', '宋体', 10.0);
INSERT INTO template_rule (template_id, name, font_name, font_size, font_bold) VALUES (1, '批注主题 字符', '宋体', 10.0, 1);
INSERT INTO template_rule (template_id, name, font_name, font_size, text_align) VALUES (1, '脚注文本 字符', '宋体', 9.0, 'LEFT');
INSERT INTO template_rule (template_id, name, text_align) VALUES (1, 'footnote reference', 'LEFT');
INSERT INTO template_rule (template_id, name, font_name, font_size) VALUES (1, '日期 字符', '宋体', 12.0);
INSERT INTO template_rule (template_id, name) VALUES (1, 'Normal Table');
INSERT INTO template_rule (template_id, name, text_align) VALUES (1, 'Table Grid', 'CENTER');
INSERT INTO template_rule (template_id, name, font_name, text_align) VALUES (1, '样式1-无间隔线', '宋体', 'CENTER');
INSERT INTO template_rule (template_id, name, font_name, text_align) VALUES (1, '样式2-有间隔线', '宋体', 'CENTER');
INSERT INTO template_rule (template_id, name) VALUES (1, 'No List');
INSERT INTO template_rule (template_id, name) VALUES (1, '当前列表1');
INSERT INTO template_rule (template_id, name) VALUES (1, '当前列表2');
INSERT INTO template_rule (template_id, name) VALUES (1, '当前列表3');

-- ---------------------
-- 3. template_engine_config (73条)
-- ---------------------

SET @rule_Normal = (SELECT id FROM template_rule WHERE template_id = 1 AND name = 'Normal');
SET @rule_heading_1 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = 'heading 1');
SET @rule_heading_2 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = 'heading 2');
SET @rule_heading_3 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = 'heading 3');
SET @rule_heading_4 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = 'heading 4');
SET @rule_heading_5 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = 'heading 5');
SET @rule_heading_6 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = 'heading 6');
SET @rule_封面学号 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = '封面学号');
SET @rule_封面空行 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = '封面空行');
SET @rule_封面类型 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = '封面类型');
SET @rule_封面标题_中 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = '封面标题（中）');
SET @rule_封面标题_英 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = '封面标题（英）');
SET @rule_封面姓名 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = '封面姓名');
SET @rule_封面日期 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = '封面日期');
SET @rule_封面信息 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = '封面信息');
SET @rule_声明标题 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = '声明标题');
SET @rule_声明正文 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = '声明正文');
SET @rule_摘要目录标题 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = '摘要目录标题');
SET @rule_关键词 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = '关键词');
SET @rule_Title = (SELECT id FROM template_rule WHERE template_id = 1 AND name = 'Title');
SET @rule_附录正文 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = '附录正文');
SET @rule_caption = (SELECT id FROM template_rule WHERE template_id = 1 AND name = 'caption');
SET @rule_Subtitle = (SELECT id FROM template_rule WHERE template_id = 1 AND name = 'Subtitle');
SET @rule_图表资料来源 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = '图表资料来源');
SET @rule_图表正文 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = '图表正文');
SET @rule_图表单位 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = '图表单位');
SET @rule_List_Paragraph = (SELECT id FROM template_rule WHERE template_id = 1 AND name = 'List Paragraph');
SET @rule_endnote_text = (SELECT id FROM template_rule WHERE template_id = 1 AND name = 'endnote text');
SET @rule_table_of_figures = (SELECT id FROM template_rule WHERE template_id = 1 AND name = 'table of figures');
SET @rule_公式 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = '公式');
SET @rule_正文_缩进 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = '正文-缩进');
SET @rule_Revision = (SELECT id FROM template_rule WHERE template_id = 1 AND name = 'Revision');
SET @rule_annotation_text = (SELECT id FROM template_rule WHERE template_id = 1 AND name = 'annotation text');
SET @rule_annotation_subject = (SELECT id FROM template_rule WHERE template_id = 1 AND name = 'annotation subject');
SET @rule_footnote_text = (SELECT id FROM template_rule WHERE template_id = 1 AND name = 'footnote text');
SET @rule_附录正文_缩进 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = '附录正文-缩进');
SET @rule_Date = (SELECT id FROM template_rule WHERE template_id = 1 AND name = 'Date');
SET @rule_toc_1 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = 'toc 1');
SET @rule_toc_2 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = 'toc 2');
SET @rule_toc_3 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = 'toc 3');
SET @rule_toc_4 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = 'toc 4');
SET @rule_toc_5 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = 'toc 5');
SET @rule_toc_6 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = 'toc 6');
SET @rule_toc_7 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = 'toc 7');
SET @rule_toc_8 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = 'toc 8');
SET @rule_toc_9 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = 'toc 9');
SET @rule_Default_Paragraph_Font = (SELECT id FROM template_rule WHERE template_id = 1 AND name = 'Default Paragraph Font');
SET @rule_页眉_字符 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = '页眉 字符');
SET @rule_页脚_字符 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = '页脚 字符');
SET @rule_page_number = (SELECT id FROM template_rule WHERE template_id = 1 AND name = 'page number');
SET @rule_标题_1_字符 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = '标题 1 字符');
SET @rule_标题_2_字符 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = '标题 2 字符');
SET @rule_标题_3_字符 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = '标题 3 字符');
SET @rule_标题_4_字符 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = '标题 4 字符');
SET @rule_标题_5_字符 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = '标题 5 字符');
SET @rule_标题_6_字符 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = '标题 6 字符');
SET @rule_标题_字符 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = '标题 字符');
SET @rule_Hyperlink = (SELECT id FROM template_rule WHERE template_id = 1 AND name = 'Hyperlink');
SET @rule_副标题_字符 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = '副标题 字符');
SET @rule_尾注文本_字符 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = '尾注文本 字符');
SET @rule_endnote_reference = (SELECT id FROM template_rule WHERE template_id = 1 AND name = 'endnote reference');
SET @rule_Unresolved_Mention = (SELECT id FROM template_rule WHERE template_id = 1 AND name = 'Unresolved Mention');
SET @rule_Placeholder_Text = (SELECT id FROM template_rule WHERE template_id = 1 AND name = 'Placeholder Text');
SET @rule_annotation_reference = (SELECT id FROM template_rule WHERE template_id = 1 AND name = 'annotation reference');
SET @rule_批注文字_字符 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = '批注文字 字符');
SET @rule_批注主题_字符 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = '批注主题 字符');
SET @rule_脚注文本_字符 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = '脚注文本 字符');
SET @rule_footnote_reference = (SELECT id FROM template_rule WHERE template_id = 1 AND name = 'footnote reference');
SET @rule_日期_字符 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = '日期 字符');
SET @rule_No_List = (SELECT id FROM template_rule WHERE template_id = 1 AND name = 'No List');
SET @rule_当前列表1 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = '当前列表1');
SET @rule_当前列表2 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = '当前列表2');
SET @rule_当前列表3 = (SELECT id FROM template_rule WHERE template_id = 1 AND name = '当前列表3');

-- ---- COVER (8条) ----
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('封面类型', '^(硕士|博士)学位论文(开题报告)?|（专\\s*业\\s*学\\s*位）', 'COVER', NULL, 10, 1, 1, @rule_封面类型);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('封面标题（中）', '^[\\u4e00-\\u9fffA-Za-z（）()\\s]+(?:模板|论文|报告|研究)$', 'COVER', NULL, 20, 1, 1, @rule_封面标题_中);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('封面标题（英）', '^[A-Z][A-Za-z\\s]+(?:Template|Dissertation|Thesis|Paper|Report)$', 'COVER', NULL, 30, 1, 1, @rule_封面标题_英);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('封面姓名', '^[\\u4e00-\\u9fff]{2,4}$', 'COVER', NULL, 40, 1, 1, @rule_封面姓名);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('封面学号', '^(学校编码：\\d+|学\\s*号：\\s*\\w+)$', 'COVER', NULL, 50, 1, 1, @rule_封面学号);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('封面日期', '^\\d{4}年\\d{1,2}月$', 'COVER', NULL, 60, 1, 1, @rule_封面日期);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('封面信息', '^(指导教师姓名|专\\s*业\\s*名\\s*称|论文提交日期|论文答辩日期|学位授予日期)[：:].+$', 'COVER', NULL, 70, 1, 1, @rule_封面信息);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('封面空行', '^\\s*$', 'COVER', NULL, 80, 1, 1, @rule_封面空行);

-- ---- TOC (9条) ----
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('toc 1', '^(?:第[一二三四五六七八九十百千]+章\\s+.+\\d+|Chapter\\s*\\d+\\s+.+\\d+|附\\s*录\\d+|参考文献\\d+|致\\s*谢\\d+|Acknowledgments\\d+|Appendix\\d+|References\\d+)$', 'TOC', 1, 10, 1, 1, @rule_toc_1);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('toc 2', '^(?:第[一二三四五六七八九十百千]+节\\s+.+\\d+|Section\\s*\\d+\\s+.+\\d+)$', 'TOC', 2, 20, 1, 1, @rule_toc_2);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('toc 3', '^[一二三四五六七八九十]+[、]\\s*.+\\d+$', 'TOC', 3, 30, 1, 1, @rule_toc_3);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('toc 4', '^.+\\d+$', 'TOC', 4, 40, 1, 1, @rule_toc_4);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('toc 5', '^.+\\d+$', 'TOC', 5, 50, 1, 1, @rule_toc_5);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('toc 6', '^.+\\d+$', 'TOC', 6, 60, 1, 1, @rule_toc_6);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('toc 7', '^.+\\d+$', 'TOC', 7, 70, 1, 1, @rule_toc_7);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('toc 8', '^.+\\d+$', 'TOC', 8, 80, 1, 1, @rule_toc_8);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('toc 9', '^.+\\d+$', 'TOC', 9, 90, 1, 1, @rule_toc_9);

-- ---- TITLE (18条) ----
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('Title', '^[\\u4e00-\\u9fffA-Za-z\\s（）()，。；：、]+$', 'TITLE', 0, 10, 1, 1, @rule_Title);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('摘要目录标题', '^(摘\\s*要|Abstract|目\\s*录|Contents)$', 'TITLE', 0, 20, 1, 1, @rule_摘要目录标题);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('标题 字符', '^[\\u4e00-\\u9fffA-Za-z\\s，。；：、]+$', 'TITLE', 0, 30, 1, 1, @rule_标题_字符);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('heading 1', '^(第[一二三四五六七八九十百千]+章\\s+.+|附\\s*录|参考文献|致\\s*谢|Chapter\\s+\\d+\\s+.+)$', 'TITLE', 1, 10, 1, 1, @rule_heading_1);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('标题 1 字符', '^第[一二三四五六七八九十百千]+章\\s+.+$', 'TITLE', 1, 20, 1, 1, @rule_标题_1_字符);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('heading 2', '^(第[一二三四五六七八九十百千]+节\\s+.+|Section\\s+\\d+\\s+.+)$', 'TITLE', 2, 10, 1, 1, @rule_heading_2);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('标题 2 字符', '^第[一二三四五六七八九十百千]+节\\s+.+$', 'TITLE', 2, 20, 1, 1, @rule_标题_2_字符);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('heading 3', '^[一二三四五六七八九十]+[、,，]\\s*.+$', 'TITLE', 3, 10, 1, 1, @rule_heading_3);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('标题 3 字符', '^[一二三四五六七八九十]+[、]\\s*.+$', 'TITLE', 3, 20, 1, 1, @rule_标题_3_字符);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('heading 4', '^[\\u4e00-\\u9fffA-Za-z\\d\\s\\-()（）./，。]+$', 'TITLE', 4, 10, 1, 1, @rule_heading_4);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('标题 4 字符', '^[\\u4e00-\\u9fffA-Za-z\\d\\s，。；：、/()（）]+$', 'TITLE', 4, 20, 1, 1, @rule_标题_4_字符);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('heading 5', '^[\\u4e00-\\u9fffA-Za-z]+$', 'TITLE', 5, 10, 1, 1, @rule_heading_5);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('标题 5 字符', '^[\\u4e00-\\u9fffA-Za-z]+$', 'TITLE', 5, 20, 1, 1, @rule_标题_5_字符);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('heading 6', '^[\\u4e00-\\u9fffA-Za-z]+$', 'TITLE', 6, 10, 1, 1, @rule_heading_6);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('标题 6 字符', '^[\\u4e00-\\u9fffA-Za-z]+$', 'TITLE', 6, 20, 1, 1, @rule_标题_6_字符);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('Subtitle', '^[\\u4e00-\\u9fffA-Za-z\\s，。；：、\\u3000-\\u303f\\uff00-\\uffef]+$', 'TITLE', 7, 10, 1, 1, @rule_Subtitle);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('副标题 字符', '^[\\u4e00-\\u9fffA-Za-z\\s，。；：、\\u3000-\\u303f\\uff00-\\uffef]+$', 'TITLE', 7, 20, 1, 1, @rule_副标题_字符);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('声明标题', '^厦门大学[\\u4e00-\\u9fff（）()]+(?:原创性声明|著作权使用声明)$', 'TITLE', 7, 30, 1, 1, @rule_声明标题);

-- ---- BODY (38条) ----
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('Normal', '[\\u4e00-\\u9fff\\w\\s\\u3000-\\u303f\\uff00-\\uffef\\-.,;:!?\\[\\]()&%@#]+', 'BODY', NULL, 10, 1, 1, @rule_Normal);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('正文-缩进', '^[\\u4e00-\\u9fff\\w\\s，。；：、\\u3000-\\u303f\\uff00-\\uffef\\-.,;:!?\\[\\]/&=%\\.@#\\$%+]+$', 'BODY', NULL, 20, 1, 1, @rule_正文_缩进);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('附录正文', '^[\\u4e00-\\u9fff\\w\\s，。；：、：:\\-.,;:!?\\[\\]&%@#\\u3000-\\u303f\\uff00-\\uffef]+$', 'BODY', NULL, 30, 1, 1, @rule_附录正文);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('附录正文-缩进', '^[\\u4e00-\\u9fff\\w\\s，。；：、\\u3000-\\u303f\\uff00-\\uffef\\-.,;:!?\\[\\]&%@#\\$]+$', 'BODY', NULL, 40, 1, 1, @rule_附录正文_缩进);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('声明正文', '^(本人[\\u4e00-\\u9fff，。；：、\\s\\d]+。|声明人|指导教师|[（(]\\s*[）)]|\\s*\\d{4}\\s*年\\s*\\d{1,2}\\s*月\\s*\\d{1,2}\\s*日)', 'BODY', NULL, 50, 1, 1, @rule_声明正文);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('关键词', '^(关键词[：:]\\s*.+|Keywords[：:]\\s*.+)$', 'BODY', NULL, 60, 1, 1, @rule_关键词);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('caption', '^(图|表)\\s*\\d+[\\s　]+[\\u4e00-\\u9fff\\w\\s（）()\\-/，。；：、]+$', 'BODY', NULL, 70, 1, 1, @rule_caption);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('图表资料来源', '^资料来源[：:][\\u4e00-\\u9fffA-Za-z\\s，。；：、\\-\\d.]{2,}。?$', 'BODY', NULL, 80, 1, 1, @rule_图表资料来源);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('图表正文', '^[\\u4e00-\\u9fffA-Za-z\\d\\s，。；：、\\-./()（）%&]+$', 'BODY', NULL, 90, 1, 1, @rule_图表正文);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('图表单位', '^单位[：:]\\s*[\\u4e00-\\u9fffA-Za-z\\s/]+$', 'BODY', NULL, 100, 1, 1, @rule_图表单位);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('List Paragraph', '^[\\u4e00-\\u9fff\\w\\s，。；：、\\u3000-\\u303f\\uff00-\\uffef\\-.,;:!?\\[\\]()&%@#]+$', 'BODY', NULL, 110, 1, 1, @rule_List_Paragraph);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('endnote text', '^\\[\\d+\\][\\u4e00-\\u9fffA-Za-z\\s，。；：、\\u3000-\\u303f\\uff00-\\uffef\\-.,;:!?\\[\\]/&=.\\d@#\\$%+]+$', 'BODY', NULL, 120, 1, 1, @rule_endnote_text);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('table of figures', '^(图|表)\\s*\\d+[\\s　]+.+\\d+$', 'BODY', NULL, 130, 1, 1, @rule_table_of_figures);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('公式', '^（\\d+）$', 'BODY', NULL, 140, 1, 1, @rule_公式);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('Revision', '^[\\u4e00-\\u9fff\\w\\s，。；：、\\u3000-\\u303f\\uff00-\\uffef\\-.,;:!?\\[\\]&%@#\\$]+$', 'BODY', NULL, 150, 1, 1, @rule_Revision);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('annotation text', '^[\\u4e00-\\u9fff\\w\\s，。；：、\\u3000-\\u303f\\uff00-\\uffef\\-.,;:!?\\[\\]&%@#\\$]+$', 'BODY', NULL, 160, 1, 1, @rule_annotation_text);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('annotation subject', '^[\\u4e00-\\u9fff\\w\\s，。；：、\\u3000-\\u303f\\uff00-\\uffef\\-.,;:!?]+$', 'BODY', NULL, 170, 1, 1, @rule_annotation_subject);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('footnote text', '^[\\u4e00-\\u9fff\\w\\s，。；：、\\u3000-\\u303f\\uff00-\\uffef\\-.,;:!?\\[\\]&%@#\\$]+$', 'BODY', NULL, 180, 1, 1, @rule_footnote_text);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('Date', '^[\\u4e00-\\u9fff\\d\\s年月日\\-/]+$', 'BODY', NULL, 190, 1, 1, @rule_Date);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('Default Paragraph Font', '^.+$', 'BODY', NULL, 200, 1, 1, @rule_Default_Paragraph_Font);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('页眉 字符', '^[\\u4e00-\\u9fffA-Za-z\\d\\s，。；：、\\u3000-\\u303f\\uff00-\\uffef]+$', 'BODY', NULL, 210, 1, 1, @rule_页眉_字符);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('页脚 字符', '^[\\u4e00-\\u9fff\\d\\s\\-/]+$', 'BODY', NULL, 220, 1, 1, @rule_页脚_字符);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('page number', '^\\d+$', 'BODY', NULL, 230, 1, 1, @rule_page_number);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('Hyperlink', '^https?://[\\w\\-.]+(\\.[a-zA-Z]{2,})+(/\\S*)?$', 'BODY', NULL, 240, 1, 1, @rule_Hyperlink);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('尾注文本 字符', '^[\\u4e00-\\u9fffA-Za-z\\s，。；：、\\u3000-\\u303f\\uff00-\\uffef\\-.,;:!?\\[\\]/&=.\\d@#\\$%+]+$', 'BODY', NULL, 250, 1, 1, @rule_尾注文本_字符);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('endnote reference', '^\\[\\d+\\]$', 'BODY', NULL, 260, 1, 1, @rule_endnote_reference);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('Unresolved Mention', '^[\\u4e00-\\u9fffA-Za-z\\d\\s]+$', 'BODY', NULL, 270, 1, 1, @rule_Unresolved_Mention);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('Placeholder Text', '^[\\u4e00-\\u9fffA-Za-z\\d\\s\\-.,;]+$', 'BODY', NULL, 280, 1, 1, @rule_Placeholder_Text);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('annotation reference', '^\\d+$', 'BODY', NULL, 290, 1, 1, @rule_annotation_reference);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('批注文字 字符', '^[\\u4e00-\\u9fff\\w\\s，。；：、\\u3000-\\u303f\\uff00-\\uffef\\-.,;:!?\\[\\]&%@#\\$]+$', 'BODY', NULL, 300, 1, 1, @rule_批注文字_字符);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('批注主题 字符', '^[\\u4e00-\\u9fff\\w\\s，。；：、\\u3000-\\u303f\\uff00-\\uffef\\-.,;:!?]+$', 'BODY', NULL, 310, 1, 1, @rule_批注主题_字符);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('脚注文本 字符', '^[\\u4e00-\\u9fff\\w\\s，。；：、\\u3000-\\u303f\\uff00-\\uffef\\-.,;:!?\\[\\]&%@#\\$]+$', 'BODY', NULL, 320, 1, 1, @rule_脚注文本_字符);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('footnote reference', '^\\d+$', 'BODY', NULL, 330, 1, 1, @rule_footnote_reference);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('日期 字符', '^[\\u4e00-\\u9fff\\d\\s年月日\\-/]+$', 'BODY', NULL, 340, 1, 1, @rule_日期_字符);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('No List', '', 'BODY', NULL, 350, 1, 1, @rule_No_List);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('当前列表1', '^\\d+[\\.、)）]\\s*.+$', 'BODY', NULL, 360, 1, 1, @rule_当前列表1);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('当前列表2', '^[①-⑳]+\\s*.+$', 'BODY', NULL, 370, 1, 1, @rule_当前列表2);
INSERT INTO template_engine_config (config_name, pattern, match_type, match_level, sort_order, is_active, template_id, rule_id) VALUES ('当前列表3', '^[a-zA-Z][\\.、)）]?\\s*.+$', 'BODY', NULL, 380, 1, 1, @rule_当前列表3);

-- ============================================================
-- 脚本结束
-- ============================================================