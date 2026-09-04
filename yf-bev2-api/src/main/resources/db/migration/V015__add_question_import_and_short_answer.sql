-- Add question import metadata, short-answer fields, enabled state and import permission.

ALTER TABLE `el_repo_qu`
    ADD COLUMN `external_code` varchar(64) DEFAULT NULL COMMENT '外部题目编号，仅用于题库维护和导入追溯' AFTER `chapter_id`,
    ADD COLUMN `status` tinyint NOT NULL DEFAULT 1 COMMENT '1 启用，0 停用' AFTER `analysis`,
    ADD COLUMN `reference_answer` text DEFAULT NULL COMMENT '简答题参考答案' AFTER `status`,
    ADD COLUMN `grading_criteria` text DEFAULT NULL COMMENT '简答题评分要点' AFTER `reference_answer`,
    ADD COLUMN `tags` varchar(500) DEFAULT NULL COMMENT '题目标签，多个标签使用英文逗号分隔' AFTER `grading_criteria`,
    ADD KEY `idx_repo_qu_repo_type_status` (`repo_id`, `qu_type`, `status`),
    ADD KEY `idx_repo_qu_external_code` (`repo_id`, `external_code`);

UPDATE `el_repo_qu` SET `status` = 1 WHERE `status` IS NULL;

INSERT INTO `el_sys_dic_value`
    (`id`, `dic_code`, `dic_value`, `title`, `parent_id`, `remark`, `create_time`, `update_time`, `create_by`, `update_by`)
VALUES
    ('qu_type_short', 'qu_type', 'short', '简答题', '0', '需要人工阅卷的文本题', NOW(), NOW(), '', '')
ON DUPLICATE KEY UPDATE
    `title` = VALUES(`title`), `remark` = VALUES(`remark`), `update_time` = NOW();

INSERT INTO `el_sys_menu`
    (`id`, `parent_id`, `menu_type`, `permission_tag`, `path`, `component`, `redirect`, `name`,
     `meta_title`, `meta_icon`, `meta_active_menu`, `meta_no_cache`, `hidden`, `sort`,
     `create_time`, `update_time`, `create_by`, `update_by`)
VALUES
    ('repo_qu_import', '1910540102659358722', 3, 'repo:qu:import', NULL, NULL, NULL,
     'QuImport', 'Excel 导入', NULL, NULL, 1, 1, 5, NOW(), NOW(), '', '')
ON DUPLICATE KEY UPDATE
    `permission_tag` = VALUES(`permission_tag`),
    `meta_title` = VALUES(`meta_title`),
    `update_time` = NOW();

INSERT INTO `el_sys_role_menu`
    (`id`, `role_id`, `menu_id`, `create_time`, `update_time`, `create_by`, `update_by`, `data_flag`)
SELECT
    'admin_repo_qu_import', 'admin', 'repo_qu_import', NOW(), NOW(), '', '', 0
WHERE EXISTS (SELECT 1 FROM `el_sys_role` WHERE `id` = 'admin')
  AND NOT EXISTS (
      SELECT 1 FROM `el_sys_role_menu`
      WHERE `role_id` = 'admin' AND `menu_id` = 'repo_qu_import'
  );
