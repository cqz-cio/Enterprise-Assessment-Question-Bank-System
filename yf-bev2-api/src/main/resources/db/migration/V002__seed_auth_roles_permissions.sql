-- Phase 1: enterprise role baseline and minimum backend permissions.
-- Existing legacy `user` assignments are migrated to `EMPLOYEE`; the legacy
-- role itself is retained so older external integrations are not deleted.

INSERT INTO `el_sys_role`
    (`id`, `role_name`, `data_scope`, `role_level`, `remark`, `create_time`, `create_by`, `update_time`, `update_by`)
VALUES
    ('HR', 'HR/人事', 4, 100, '候选人与员工审核、考核发放及结果权限', NOW(), '', NOW(), ''),
    ('EMPLOYEE', '员工', 1, 10, '正式员工考核角色', NOW(), '', NOW(), ''),
    ('CANDIDATE', '候选人', 1, 1, '临时候选人，仅可访问本人考核', NOW(), '', NOW(), '')
ON DUPLICATE KEY UPDATE
    `role_name` = VALUES(`role_name`),
    `data_scope` = VALUES(`data_scope`),
    `role_level` = VALUES(`role_level`),
    `remark` = VALUES(`remark`),
    `update_time` = NOW();

-- 员工注册审核是后端权限，不依赖前端按钮隐藏。
INSERT INTO `el_sys_menu`
    (`id`, `parent_id`, `menu_type`, `permission_tag`, `path`, `component`, `redirect`, `name`,
     `meta_title`, `meta_icon`, `meta_active_menu`, `meta_no_cache`, `hidden`, `sort`,
     `create_time`, `update_time`, `create_by`, `update_by`)
VALUES
    ('auth_user_audit', '1556906844813914114', 3, 'sys:user:registration:audit', NULL, NULL, NULL,
     'SysUserRegistrationAudit', '审核员工注册', NULL, NULL, 1, 1, 6, NOW(), NOW(), '', '')
ON DUPLICATE KEY UPDATE
    `permission_tag` = VALUES(`permission_tag`),
    `meta_title` = VALUES(`meta_title`),
    `update_time` = NOW();

-- 复用原学员端菜单作为员工门户权限。
INSERT INTO `el_sys_role_menu`
    (`id`, `role_id`, `menu_id`, `create_time`, `update_time`, `create_by`, `update_by`, `data_flag`)
SELECT
    CONCAT('emp_', LEFT(MD5(rm.menu_id), 24)), 'EMPLOYEE', rm.menu_id, NOW(), NOW(), '', '', 0
FROM `el_sys_role_menu` rm
WHERE rm.role_id = 'user'
  AND NOT EXISTS (
      SELECT 1 FROM `el_sys_role_menu` existing
      WHERE existing.role_id = 'EMPLOYEE' AND existing.menu_id = rm.menu_id
  );

-- 候选人只获得本人已有试卷的进入/答题权限，不能浏览模板或创建任意考试。
INSERT INTO `el_sys_role_menu`
    (`id`, `role_id`, `menu_id`, `create_time`, `update_time`, `create_by`, `update_by`, `data_flag`)
SELECT 'cand_exam_enter', 'CANDIDATE', '1912452280306171906', NOW(), NOW(), '', '', 0
WHERE EXISTS (SELECT 1 FROM `el_sys_menu` WHERE id = '1912452280306171906')
  AND NOT EXISTS (
      SELECT 1 FROM `el_sys_role_menu`
      WHERE role_id = 'CANDIDATE' AND menu_id = '1912452280306171906'
  );

-- HR 只获得员工列表与注册审核权限；通用用户编辑、删除、角色授权不下放。
INSERT INTO `el_sys_role_menu`
    (`id`, `role_id`, `menu_id`, `create_time`, `update_time`, `create_by`, `update_by`, `data_flag`)
SELECT seed.id, 'HR', seed.menu_id, NOW(), NOW(), '', '', 0
FROM (
    SELECT 'hr_org_root' AS id, '1552547259067965442' AS menu_id
    UNION ALL SELECT 'hr_user_page', '1556906844813914114'
    UNION ALL SELECT 'hr_user_paging', '1556907994690744321'
    UNION ALL SELECT 'hr_user_audit', 'auth_user_audit'
) seed
WHERE EXISTS (SELECT 1 FROM `el_sys_menu` m WHERE m.id = seed.menu_id)
  AND NOT EXISTS (
      SELECT 1 FROM `el_sys_role_menu` existing
      WHERE existing.role_id = 'HR' AND existing.menu_id = seed.menu_id
  );

-- 管理员也需要新审核权限。
INSERT INTO `el_sys_role_menu`
    (`id`, `role_id`, `menu_id`, `create_time`, `update_time`, `create_by`, `update_by`, `data_flag`)
SELECT 'admin_user_audit', 'admin', 'auth_user_audit', NOW(), NOW(), '', '', 0
WHERE NOT EXISTS (
    SELECT 1 FROM `el_sys_role_menu`
    WHERE role_id = 'admin' AND menu_id = 'auth_user_audit'
);

-- 将现有学员身份平滑迁移为企业员工身份。
UPDATE `el_sys_user_role`
SET `role_id` = 'EMPLOYEE'
WHERE `role_id` = 'user';
