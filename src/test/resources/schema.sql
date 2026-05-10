-- Template 系统 — H2 兼容测试用建表脚本
-- 已移除 MySQL 特有语法（ENGINE、COLLATE、COMMENT、ON UPDATE NOW() 等）

-- 1. 模板信息配置表
CREATE TABLE IF NOT EXISTS template_config (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(200) NOT NULL,
    is_active   TINYINT      NOT NULL DEFAULT 1,
    created_at  DATETIME     NOT NULL DEFAULT NOW(),
    updated_at  DATETIME     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id)
);

-- 2. 模板规则样式配置表
CREATE TABLE IF NOT EXISTS template_rule (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    template_id     BIGINT        NOT NULL,
    name            VARCHAR(200)  NOT NULL,
    font_name       VARCHAR(100)  DEFAULT NULL,
    font_size       INT           DEFAULT NULL,
    font_bold       TINYINT       DEFAULT NULL,
    font_italic     TINYINT       DEFAULT NULL,
    font_underline  TINYINT       DEFAULT NULL,
    font_color      VARCHAR(20)   DEFAULT NULL,
    font_strike     TINYINT       DEFAULT NULL,
    text_align      VARCHAR(20)   DEFAULT NULL,
    text_indent     DECIMAL(10,2) DEFAULT NULL,
    line_spacing    DECIMAL(10,2) DEFAULT NULL,
    space_before    DECIMAL(10,2) DEFAULT NULL,
    space_after     DECIMAL(10,2) DEFAULT NULL,
    highlight_color VARCHAR(20)   DEFAULT NULL,
    created_at      DATETIME      NOT NULL DEFAULT NOW(),
    updated_at      DATETIME      NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_template_id ON template_rule(template_id);

-- 3. 内容识别引擎配置表（重构版：一对多）
CREATE TABLE IF NOT EXISTS template_engine_config (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    template_id     BIGINT        NOT NULL,
    config_name     VARCHAR(200)  NOT NULL,
    pattern         TEXT          NOT NULL,
    match_type      VARCHAR(20)   NOT NULL DEFAULT 'TITLE',
    match_level     INT           DEFAULT NULL,
    rule_id         BIGINT        DEFAULT NULL,
    sort_order      INT           NOT NULL DEFAULT 0,
    is_active       TINYINT       NOT NULL DEFAULT 1,
    created_at      DATETIME      NOT NULL DEFAULT NOW(),
    updated_at      DATETIME      NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_template_id ON template_engine_config(template_id);
CREATE INDEX IF NOT EXISTS idx_match_type ON template_engine_config(match_type);
CREATE INDEX IF NOT EXISTS idx_template_type ON template_engine_config(template_id, match_type);

-- 4. 用户上传文件表
CREATE TABLE IF NOT EXISTS user_upload_file (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    template_id         BIGINT        DEFAULT NULL,
    original_name       VARCHAR(500)  NOT NULL,
    original_size       BIGINT        DEFAULT NULL,
    original_content    BLOB          NOT NULL,
    parsed_content      BLOB          DEFAULT NULL,
    parsed_json         TEXT          DEFAULT NULL,
    parsed_at           DATETIME      DEFAULT NULL,
    adjusted_at         DATETIME      DEFAULT NULL,
    output_content      BLOB          DEFAULT NULL,
    output_name         VARCHAR(500)  DEFAULT NULL,
    exported_at         DATETIME      DEFAULT NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'UPLOADED',
    error_message       VARCHAR(2000) DEFAULT NULL,
    created_at          DATETIME      NOT NULL DEFAULT NOW(),
    updated_at          DATETIME      NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_template_id ON user_upload_file(template_id);
CREATE INDEX IF NOT EXISTS idx_status ON user_upload_file(status);
CREATE INDEX IF NOT EXISTS idx_created_at ON user_upload_file(created_at);

-- 5. 智能匹配训练任务表
CREATE TABLE IF NOT EXISTS smart_match_task (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    template_id     BIGINT        NOT NULL,
    task_name       VARCHAR(200)  NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    progress        INT           NOT NULL DEFAULT 0,
    file_count      INT           NOT NULL DEFAULT 0,
    error_message   VARCHAR(2000) DEFAULT NULL,
    rule_count      INT           DEFAULT NULL,
    started_at      DATETIME      DEFAULT NULL,
    completed_at    DATETIME      DEFAULT NULL,
    created_at      DATETIME      NOT NULL DEFAULT NOW(),
    updated_at      DATETIME      NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_smt_template_id ON smart_match_task(template_id);
CREATE INDEX IF NOT EXISTS idx_smt_status ON smart_match_task(status);

-- 6. 智能匹配规则表
CREATE TABLE IF NOT EXISTS smart_match_rule (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    template_id     BIGINT        NOT NULL,
    task_id         BIGINT        DEFAULT NULL,
    rule_name       VARCHAR(200)  NOT NULL,
    match_type      VARCHAR(20)   NOT NULL DEFAULT 'TITLE',
    match_level     INT           DEFAULT NULL,
    keywords        TEXT          NOT NULL,
    feature_vector  TEXT          NOT NULL,
    style_rule_id   BIGINT        DEFAULT NULL,
    threshold       DECIMAL(5,4)  NOT NULL DEFAULT 0.3000,
    is_active       TINYINT       NOT NULL DEFAULT 1,
    match_order     INT           NOT NULL DEFAULT 0,
    created_at      DATETIME      NOT NULL DEFAULT NOW(),
    updated_at      DATETIME      NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_smr_template_id ON smart_match_rule(template_id);
CREATE INDEX IF NOT EXISTS idx_smr_task_id ON smart_match_rule(task_id);
CREATE INDEX IF NOT EXISTS idx_smr_match_type ON smart_match_rule(match_type);
CREATE INDEX IF NOT EXISTS idx_smr_is_active ON smart_match_rule(is_active);
