-- V20260513__create_result_view_tables.sql
-- 训练结果查看功能 - 新增表与表结构变更

-- 1. parse_standard_answer 表（方案B：标准答案存储）
CREATE TABLE  parse_standard_answer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_id BIGINT NOT NULL,
    source_file VARCHAR(500) NOT NULL,
    answer_name VARCHAR(200) NOT NULL,
    structure_tree LONGTEXT NOT NULL,
    document_meta TEXT NULL,
    element_summary TEXT NULL,
    description VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_psa_template_id (template_id),
    INDEX idx_psa_source_file (source_file),
    CONSTRAINT fk_psa_template FOREIGN KEY (template_id) REFERENCES template_config(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. parse_diff_report 补充字段（diff_type 区分对比类型，standard_answer_id 支持方案B）
ALTER TABLE parse_diff_report
    ADD COLUMN  diff_type VARCHAR(20) NOT NULL DEFAULT 'TASK' AFTER id,
    ADD COLUMN  standard_answer_id BIGINT NULL AFTER record_id_b,
    ADD INDEX  idx_pdr_diff_type (diff_type),
    ADD INDEX  idx_pdr_created_at (created_at);

-- 3. parse_element 补充索引
ALTER TABLE parse_element
    ADD INDEX  idx_pe_record_type (record_id, element_type),
    ADD INDEX  idx_pe_confidence (confidence);
