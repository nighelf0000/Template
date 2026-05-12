-- V20260512__create_parse_tables.sql
-- Python doc-struct 引擎解析结果存储表

CREATE TABLE parse_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_id BIGINT NOT NULL,
    upload_file_id BIGINT NULL,
    source_file VARCHAR(500) NOT NULL,
    source_checksum VARCHAR(64) NOT NULL,
    file_size BIGINT NOT NULL,
    parsed_at DATETIME NOT NULL,
    engine_version VARCHAR(50) NOT NULL,
    ruleset_name VARCHAR(100) NOT NULL,
    ruleset_version VARCHAR(50) NOT NULL,
    processing_time_ms INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'success',
    error_message TEXT NULL,
    structure_tree LONGTEXT NULL,
    document_meta TEXT NULL,
    element_summary TEXT NULL,
    tags VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_pr_template_id (template_id),
    INDEX idx_pr_source_checksum (source_checksum),
    INDEX idx_pr_parsed_at (parsed_at),
    INDEX idx_pr_status (status),
    INDEX idx_pr_template_status (template_id, status),
    CONSTRAINT fk_pr_template FOREIGN KEY (template_id) REFERENCES template_config(id)
);

CREATE TABLE parse_element (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id BIGINT NOT NULL,
    element_id VARCHAR(50) NOT NULL,
    element_type VARCHAR(30) NOT NULL,
    level INT DEFAULT 0,
    content_text LONGTEXT NULL,
    confidence DECIMAL(5,4) DEFAULT 0,
    parent_element_id VARCHAR(50) NULL,
    sort_order INT DEFAULT 0,
    metadata TEXT NULL,
    style_features TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_pe_record_id (record_id),
    INDEX idx_pe_element_type (element_type),
    INDEX idx_pe_record_parent (record_id, parent_element_id),
    CONSTRAINT fk_pe_record FOREIGN KEY (record_id) REFERENCES parse_record(id) ON DELETE CASCADE
);

CREATE TABLE parse_diff_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id_a BIGINT NOT NULL,
    record_id_b BIGINT NOT NULL,
    summary TEXT NOT NULL,
    structural_changes LONGTEXT NOT NULL,
    statistics_diff TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_pdr_record_pair (record_id_a, record_id_b),
    CONSTRAINT fk_pdr_a FOREIGN KEY (record_id_a) REFERENCES parse_record(id) ON DELETE CASCADE,
    CONSTRAINT fk_pdr_b FOREIGN KEY (record_id_b) REFERENCES parse_record(id) ON DELETE CASCADE
);
