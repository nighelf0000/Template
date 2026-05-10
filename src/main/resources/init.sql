-- Template 系统 — 数据库初始化脚本
-- 数据库名：template_db

CREATE DATABASE IF NOT EXISTS template_db
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_0900_ai_ci;

USE template_db;

-- 1. 模板信息配置表
CREATE TABLE IF NOT EXISTS template_config (
    id          BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    name        VARCHAR(200) NOT NULL                 COMMENT '规则模板名称',
    is_active   TINYINT      NOT NULL DEFAULT 1       COMMENT '是否有效：1=有效, 0=停用',
    created_at  DATETIME     NOT NULL DEFAULT NOW()   COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT NOW() ON UPDATE NOW() COMMENT '最后更新时间',
    PRIMARY KEY (id),
    INDEX idx_name (name),
    INDEX idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模板信息配置表';

-- 2. 模板规则样式配置表
CREATE TABLE IF NOT EXISTS template_rule (
    id              BIGINT        NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    template_id     BIGINT        NOT NULL                 COMMENT '所属模板ID',
    name            VARCHAR(200)  NOT NULL                 COMMENT '样式规则名称',
    font_name       VARCHAR(100)  DEFAULT NULL             COMMENT '字体名称',
    font_size       INT           DEFAULT NULL             COMMENT '字号(磅值)',
    font_bold       TINYINT       DEFAULT NULL             COMMENT '是否加粗：1=加粗, 0=不加粗, NULL=不修改',
    font_italic     TINYINT       DEFAULT NULL             COMMENT '是否斜体',
    font_underline  TINYINT       DEFAULT NULL             COMMENT '是否下划线',
    font_color      VARCHAR(20)   DEFAULT NULL             COMMENT '字体颜色(十六进制)',
    font_strike     TINYINT       DEFAULT NULL             COMMENT '是否删除线',
    text_align      VARCHAR(20)   DEFAULT NULL             COMMENT '对齐方式：LEFT/CENTER/RIGHT/JUSTIFY',
    text_indent     DECIMAL(10,2) DEFAULT NULL             COMMENT '首行缩进(字符数)',
    line_spacing    DECIMAL(10,2) DEFAULT NULL             COMMENT '行距(倍数)',
    space_before    DECIMAL(10,2) DEFAULT NULL             COMMENT '段前间距(磅值)',
    space_after     DECIMAL(10,2) DEFAULT NULL             COMMENT '段后间距(磅值)',
    highlight_color VARCHAR(20)   DEFAULT NULL             COMMENT '预览高亮底色(十六进制)',
    created_at      DATETIME      NOT NULL DEFAULT NOW()   COMMENT '创建时间',
    updated_at      DATETIME      NOT NULL DEFAULT NOW() ON UPDATE NOW() COMMENT '最后更新时间',
    PRIMARY KEY (id),
    INDEX idx_template_id (template_id),
    CONSTRAINT fk_rule_template FOREIGN KEY (template_id) REFERENCES template_config(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模板规则样式配置表';

-- 3. 内容识别引擎配置表（重构版：一对多，每条记录一条正则匹配规则）
CREATE TABLE IF NOT EXISTS template_engine_config (
    id              BIGINT        NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    template_id     BIGINT        NOT NULL                 COMMENT '关联模板ID',
    config_name     VARCHAR(200)  NOT NULL                 COMMENT '配置名称（用户自定义，如"封面匹配""一级标题"）',
    pattern         TEXT          NOT NULL                 COMMENT '正则表达式',
    match_type      VARCHAR(20)   NOT NULL DEFAULT 'TITLE' COMMENT '匹配类型：COVER=封面, TOC=目录, TITLE=标题, BODY=正文',
    match_level     INT           DEFAULT NULL             COMMENT '匹配级别（仅TITLE类型使用，如1=一级标题, 2=二级标题）',
    rule_id         BIGINT        DEFAULT NULL             COMMENT '关联的模板样式规则ID',
    sort_order      INT           NOT NULL DEFAULT 0       COMMENT '排序序号，同一类型内按此字段升序匹配',
    is_active       TINYINT       NOT NULL DEFAULT 1       COMMENT '是否启用：1=启用, 0=停用',
    created_at      DATETIME      NOT NULL DEFAULT NOW()   COMMENT '创建时间',
    updated_at      DATETIME      NOT NULL DEFAULT NOW() ON UPDATE NOW() COMMENT '最后更新时间',
    PRIMARY KEY (id),
    INDEX idx_template_id (template_id),
    INDEX idx_match_type (match_type),
    INDEX idx_template_type (template_id, match_type),
    CONSTRAINT fk_engine_config_template FOREIGN KEY (template_id) REFERENCES template_config(id) ON DELETE CASCADE,
    CONSTRAINT fk_engine_config_rule FOREIGN KEY (rule_id) REFERENCES template_rule(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='内容识别引擎配置表(重构版：一对多，每条记录一条正则匹配规则)';

-- 4. 用户上传文件表
CREATE TABLE IF NOT EXISTS user_upload_file (
    id                  BIGINT        NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    template_id         BIGINT        DEFAULT NULL             COMMENT '使用的模板ID',
    original_name       VARCHAR(500)  NOT NULL                 COMMENT '原始文件名',
    original_size       BIGINT        DEFAULT NULL             COMMENT '原始文件大小(字节)',
    original_content    LONGBLOB      NOT NULL                 COMMENT '原始Word文件内容',
    parsed_content      LONGBLOB      DEFAULT NULL             COMMENT '解析后预览文件内容',
    parsed_json         MEDIUMTEXT    DEFAULT NULL             COMMENT '解析结果JSON',
    parsed_at           DATETIME      DEFAULT NULL             COMMENT '解析完成时间',
    adjusted_at         DATETIME      DEFAULT NULL             COMMENT '调整保存时间',
    output_content      LONGBLOB      DEFAULT NULL             COMMENT '导出文件内容',
    output_name         VARCHAR(500)  DEFAULT NULL             COMMENT '导出文件名',
    exported_at         DATETIME      DEFAULT NULL             COMMENT '导出完成时间',
    status              VARCHAR(20)   NOT NULL DEFAULT 'UPLOADED' COMMENT '文件状态',
    error_message       VARCHAR(2000) DEFAULT NULL             COMMENT '错误信息',
    created_at          DATETIME      NOT NULL DEFAULT NOW()   COMMENT '上传时间',
    updated_at          DATETIME      NOT NULL DEFAULT NOW() ON UPDATE NOW() COMMENT '最后修改时间',
    PRIMARY KEY (id),
    INDEX idx_template_id (template_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at),
    CONSTRAINT fk_file_template FOREIGN KEY (template_id) REFERENCES template_config(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户上传文件表';
