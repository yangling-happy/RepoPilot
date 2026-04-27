-- Add GitLab username as the database isolation dimension.
-- Existing rows are marked as legacy so the migration can run on populated dev databases.

SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'doc_task' AND COLUMN_NAME = 'gitlab_username'
    ),
    'SELECT 1',
    'ALTER TABLE `doc_task` ADD COLUMN `gitlab_username` VARCHAR(128) NULL AFTER `id`'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `doc_task`
SET `gitlab_username` = 'legacy'
WHERE `gitlab_username` IS NULL OR `gitlab_username` = '';

ALTER TABLE `doc_task` MODIFY COLUMN `gitlab_username` VARCHAR(128) NOT NULL;

SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'doc_task' AND INDEX_NAME = 'idx_doc_task_user_project_branch_commit'
    ),
    'SELECT 1',
    'ALTER TABLE `doc_task` ADD KEY `idx_doc_task_user_project_branch_commit` (`gitlab_username`, `project`, `branch`, `commit_id`)'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'doc_file_dtl' AND COLUMN_NAME = 'gitlab_username'
    ),
    'SELECT 1',
    'ALTER TABLE `doc_file_dtl` ADD COLUMN `gitlab_username` VARCHAR(128) NULL AFTER `task_id`'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `doc_file_dtl`
SET `gitlab_username` = 'legacy'
WHERE `gitlab_username` IS NULL OR `gitlab_username` = '';

ALTER TABLE `doc_file_dtl` MODIFY COLUMN `gitlab_username` VARCHAR(128) NOT NULL;

SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'doc_file_dtl' AND INDEX_NAME = 'uk_project_branch_file_commit'
    ),
    'ALTER TABLE `doc_file_dtl` DROP INDEX `uk_project_branch_file_commit`',
    'SELECT 1'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'doc_file_dtl' AND INDEX_NAME = 'uk_user_project_branch_file_commit'
    ),
    'SELECT 1',
    'ALTER TABLE `doc_file_dtl` ADD UNIQUE KEY `uk_user_project_branch_file_commit` (`gitlab_username`, `project_name`, `branch_name`, `file_path_sha`, `commit_id`)'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'doc_file_dtl' AND INDEX_NAME = 'idx_doc_file_user_project_branch_update_time'
    ),
    'SELECT 1',
    'ALTER TABLE `doc_file_dtl` ADD KEY `idx_doc_file_user_project_branch_update_time` (`gitlab_username`, `project_name`, `branch_name`, `update_time`)'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'deploy_task' AND COLUMN_NAME = 'gitlab_username'
    ),
    'SELECT 1',
    'ALTER TABLE `deploy_task` ADD COLUMN `gitlab_username` VARCHAR(128) NULL AFTER `id`'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `deploy_task`
SET `gitlab_username` = 'legacy'
WHERE `gitlab_username` IS NULL OR `gitlab_username` = '';

ALTER TABLE `deploy_task` MODIFY COLUMN `gitlab_username` VARCHAR(128) NOT NULL;

SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'deploy_task' AND INDEX_NAME = 'idx_deploy_task_user_project_status_time'
    ),
    'SELECT 1',
    'ALTER TABLE `deploy_task` ADD KEY `idx_deploy_task_user_project_status_time` (`gitlab_username`, `project_name`, `run_status`, `update_time`)'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'build_task' AND COLUMN_NAME = 'gitlab_username'
    ),
    'SELECT 1',
    'ALTER TABLE `build_task` ADD COLUMN `gitlab_username` VARCHAR(128) NULL AFTER `id`'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `build_task`
SET `gitlab_username` = 'legacy'
WHERE `gitlab_username` IS NULL OR `gitlab_username` = '';

ALTER TABLE `build_task` MODIFY COLUMN `gitlab_username` VARCHAR(128) NOT NULL;

SET @sql = (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'build_task' AND INDEX_NAME = 'idx_build_task_user_project_status_time'
    ),
    'SELECT 1',
    'ALTER TABLE `build_task` ADD KEY `idx_build_task_user_project_status_time` (`gitlab_username`, `project_name`, `run_status`, `update_time`)'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
