-- RepoPilot business-service schema
-- MySQL 8.0+

CREATE TABLE IF NOT EXISTS doc_task (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(128) NOT NULL,
    project_name VARCHAR(128) NOT NULL,
    branch_name VARCHAR(128) NOT NULL,
    commit_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    duration INT UNSIGNED DEFAULT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_doc_task_event_id (event_id),
    KEY idx_doc_task_project_branch_commit (project_name, branch_name, commit_id),
    KEY idx_doc_task_status_update_time (status, update_time),
    CONSTRAINT chk_doc_task_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'SKIPPED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS doc_file_dtl (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    project_name VARCHAR(128) NOT NULL,
    branch_name VARCHAR(128) NOT NULL,
    file_path VARCHAR(1024) NOT NULL,
    commit_id VARCHAR(64) NOT NULL,
    doc_json JSON DEFAULT NULL,
    doc_markdown MEDIUMTEXT,
    -- Use full-path hash to keep a deterministic unique constraint without hitting index length limits.
    file_path_sha BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(file_path, 256))) STORED,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_branch_file_commit (project_name, branch_name, file_path_sha, commit_id),
    KEY idx_doc_file_dtl_project_branch_update_time (project_name, branch_name, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS deploy_task (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    task_id VARCHAR(64) NOT NULL,
    project_name VARCHAR(128) NOT NULL,
    branch_name VARCHAR(128) NOT NULL,
    commit_id VARCHAR(64) NOT NULL,
    script_name VARCHAR(255) NOT NULL,
    args TEXT,
    run_status VARCHAR(32) NOT NULL,
    operator VARCHAR(128) DEFAULT NULL,
    result MEDIUMTEXT,
    start_time DATETIME(3) DEFAULT NULL,
    end_time DATETIME(3) DEFAULT NULL,
    -- Keep commit_id only for RUNNING rows so one (project, branch, commit_id) can have at most one running task.
    running_commit_id VARCHAR(64) GENERATED ALWAYS AS (
        CASE
            WHEN run_status = 'RUNNING' THEN commit_id
            ELSE NULL
        END
    ) STORED,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_deploy_task_task_id (task_id),
    UNIQUE KEY uk_deploy_task_running_commit (project_name, branch_name, running_commit_id),
    KEY idx_project_status_time (project_name, run_status, update_time),
    KEY idx_deploy_task_commit_status (commit_id, run_status),
    CONSTRAINT chk_deploy_task_run_status CHECK (run_status IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'CANCELLED', 'TIMEOUT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS build_task (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    build_id VARCHAR(64) NOT NULL,
    deploy_task_id VARCHAR(64) DEFAULT NULL,
    project_name VARCHAR(128) NOT NULL,
    branch_name VARCHAR(128) NOT NULL,
    commit_id VARCHAR(64) NOT NULL,
    build_type VARCHAR(32) NOT NULL DEFAULT 'PACKAGE',
    build_tool VARCHAR(32) DEFAULT NULL,
    run_status VARCHAR(32) NOT NULL,
    operator VARCHAR(128) DEFAULT NULL,
    result MEDIUMTEXT,
    artifact_url VARCHAR(512) DEFAULT NULL,
    start_time DATETIME(3) DEFAULT NULL,
    end_time DATETIME(3) DEFAULT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_build_task_build_id (build_id),
    KEY idx_build_task_project_status_time (project_name, run_status, update_time),
    KEY idx_build_task_deploy_task_id (deploy_task_id),
    CONSTRAINT chk_build_task_run_status CHECK (run_status IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'CANCELLED', 'TIMEOUT')),
    CONSTRAINT fk_build_task_deploy_task_id FOREIGN KEY (deploy_task_id)
        REFERENCES deploy_task (task_id)
        ON DELETE SET NULL
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
