-- V20260512__create_train_tables.sql
-- 模板训练功能 - 建表脚本
-- 创建训练文件表和训练任务表

CREATE TABLE train_file (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_id BIGINT NOT NULL,
    original_name VARCHAR(500) NOT NULL,
    original_size BIGINT NOT NULL,
    original_content LONGBLOB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'UPLOADED',
    error_message VARCHAR(2000) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tf_template_id (template_id),
    INDEX idx_tf_status (status),
    INDEX idx_tf_created_at (created_at),
    CONSTRAINT fk_tf_template FOREIGN KEY (template_id) REFERENCES template_config(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE train_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_id BIGINT NOT NULL,
    task_name VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    progress INT NOT NULL DEFAULT 0,
    total_files INT NOT NULL DEFAULT 0,
    file_count INT NOT NULL DEFAULT 0,
    parse_record_count INT NULL,
    error_message VARCHAR(2000) NULL,
    started_at DATETIME NULL,
    completed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tt_template_id (template_id),
    INDEX idx_tt_status (status),
    INDEX idx_tt_created_at (created_at),
    INDEX idx_tt_template_status (template_id, status),
    CONSTRAINT fk_tt_template FOREIGN KEY (template_id) REFERENCES template_config(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
