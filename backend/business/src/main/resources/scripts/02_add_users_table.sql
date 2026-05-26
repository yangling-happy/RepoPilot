CREATE TABLE IF NOT EXISTS `users` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `gitlab_id` INT NOT NULL COMMENT 'GitLab 用户 ID',
  `username` VARCHAR(128) NOT NULL COMMENT 'GitLab 用户名',
  `name` VARCHAR(256) NULL COMMENT 'GitLab 显示名',
  `avatar_url` VARCHAR(512) NULL,
  `email` VARCHAR(256) NULL,
  `access_token` VARCHAR(512) NOT NULL COMMENT 'GitLab OAuth access_token',
  `last_login_at` DATETIME(3) NULL,
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_gitlab_id` (`gitlab_id`),
  UNIQUE KEY `uk_users_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
