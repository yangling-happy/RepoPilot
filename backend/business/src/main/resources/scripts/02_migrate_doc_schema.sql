-- Align existing development databases with the current doc pipeline schema.
-- Run this after 01_init_tables.sql when the database was created from an older version.

-- doc_task: older local databases may have project_name/branch_name instead of project/branch.
SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'doc_task' AND COLUMN_NAME = 'event_id'
    ),
    'SELECT 1',
    'ALTER TABLE `doc_task` ADD COLUMN `event_id` VARCHAR(128) NULL AFTER `id`'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `doc_task`
SET `event_id` = CONCAT('legacy-doc-task-', `id`)
WHERE `event_id` IS NULL OR `event_id` = '';

ALTER TABLE `doc_task` MODIFY COLUMN `event_id` VARCHAR(128) NOT NULL;

SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'doc_task' AND COLUMN_NAME = 'project_name'
    )
    AND NOT EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'doc_task' AND COLUMN_NAME = 'project'
    ),
    'ALTER TABLE `doc_task` CHANGE COLUMN `project_name` `project` VARCHAR(128) NULL',
    'SELECT 1'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'doc_task' AND COLUMN_NAME = 'project'
    ),
    'SELECT 1',
    'ALTER TABLE `doc_task` ADD COLUMN `project` VARCHAR(128) NULL AFTER `event_id`'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `doc_task`
SET `project` = 'unknown'
WHERE `project` IS NULL OR `project` = '';

ALTER TABLE `doc_task` MODIFY COLUMN `project` VARCHAR(128) NOT NULL;

SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'doc_task' AND COLUMN_NAME = 'branch_name'
    )
    AND NOT EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'doc_task' AND COLUMN_NAME = 'branch'
    ),
    'ALTER TABLE `doc_task` CHANGE COLUMN `branch_name` `branch` VARCHAR(128) NULL',
    'SELECT 1'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'doc_task' AND COLUMN_NAME = 'branch'
    ),
    'SELECT 1',
    'ALTER TABLE `doc_task` ADD COLUMN `branch` VARCHAR(128) NULL AFTER `project`'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `doc_task`
SET `branch` = 'unknown'
WHERE `branch` IS NULL OR `branch` = '';

ALTER TABLE `doc_task` MODIFY COLUMN `branch` VARCHAR(128) NOT NULL;

SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'doc_task' AND INDEX_NAME = 'uk_doc_task_event_id'
    ),
    'SELECT 1',
    'ALTER TABLE `doc_task` ADD UNIQUE KEY `uk_doc_task_event_id` (`event_id`)'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'doc_task' AND INDEX_NAME = 'idx_doc_task_project_branch_commit'
    ),
    'SELECT 1',
    'ALTER TABLE `doc_task` ADD KEY `idx_doc_task_project_branch_commit` (`project`, `branch`, `commit_id`)'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- doc_file_dtl: keep older local tables compatible with doc_file_path based storage.
SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'doc_file_dtl' AND COLUMN_NAME = 'project'
    )
    AND NOT EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'doc_file_dtl' AND COLUMN_NAME = 'project_name'
    ),
    'ALTER TABLE `doc_file_dtl` CHANGE COLUMN `project` `project_name` VARCHAR(128) NOT NULL',
    'SELECT 1'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'doc_file_dtl' AND COLUMN_NAME = 'branch'
    )
    AND NOT EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'doc_file_dtl' AND COLUMN_NAME = 'branch_name'
    ),
    'ALTER TABLE `doc_file_dtl` CHANGE COLUMN `branch` `branch_name` VARCHAR(128) NOT NULL',
    'SELECT 1'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'doc_file_dtl' AND COLUMN_NAME = 'task_id'
    ),
    'SELECT 1',
    'ALTER TABLE `doc_file_dtl` ADD COLUMN `task_id` BIGINT UNSIGNED NULL AFTER `id`'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'doc_file_dtl' AND COLUMN_NAME = 'doc_file_path'
    ),
    'SELECT 1',
    'ALTER TABLE `doc_file_dtl` ADD COLUMN `doc_file_path` VARCHAR(1024) NULL AFTER `file_path`'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'doc_file_dtl' AND COLUMN_NAME = 'parse_status'
    ),
    'SELECT 1',
    'ALTER TABLE `doc_file_dtl` ADD COLUMN `parse_status` VARCHAR(32) NOT NULL DEFAULT ''PENDING'' AFTER `doc_file_path`'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'doc_file_dtl' AND COLUMN_NAME = 'parse_error_msg'
    ),
    'SELECT 1',
    'ALTER TABLE `doc_file_dtl` ADD COLUMN `parse_error_msg` TEXT NULL AFTER `parse_status`'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
