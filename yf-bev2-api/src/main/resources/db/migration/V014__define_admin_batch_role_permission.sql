-- Define the existing batch-role API permission and keep it admin-only.

INSERT INTO `el_sys_menu`
    (`id`, `parent_id`, `menu_type`, `permission_tag`, `path`, `component`, `redirect`, `name`,
     `meta_title`, `meta_icon`, `meta_active_menu`, `meta_no_cache`, `hidden`, `sort`,
     `create_time`, `update_time`, `create_by`, `update_by`)
VALUES
    ('auth_user_batch_role', '1556906844813914114', 3, 'sys:user:batch-role', NULL, NULL, NULL,
     'SysUserBatchRole', '批量分配角色', NULL, NULL, 1, 1, 7, NOW(), NOW(), '', '')
ON DUPLICATE KEY UPDATE
    `permission_tag` = VALUES(`permission_tag`),
    `meta_title` = VALUES(`meta_title`),
    `update_time` = NOW();

INSERT INTO `el_sys_role_menu`
    (`id`, `role_id`, `menu_id`, `create_time`, `update_time`, `create_by`, `update_by`, `data_flag`)
SELECT
    'admin_user_batch_role', 'admin', 'auth_user_batch_role', NOW(), NOW(), '', '', 0
WHERE EXISTS (SELECT 1 FROM `el_sys_role` WHERE id = 'admin')
  AND NOT EXISTS (
      SELECT 1
      FROM `el_sys_role_menu`
      WHERE role_id = 'admin' AND menu_id = 'auth_user_batch_role'
  );
