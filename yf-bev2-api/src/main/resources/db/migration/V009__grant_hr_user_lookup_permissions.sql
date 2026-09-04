-- HR uses the shared personnel-management page. Its department and role
-- filters load read-only lookup data during page initialization.
-- Grant only those two lookup permissions; management mutations remain admin-only.

INSERT INTO `el_sys_role_menu`
    (`id`, `role_id`, `menu_id`, `create_time`, `update_time`, `create_by`, `update_by`, `data_flag`)
SELECT
    'hr_depart_view', 'HR', menu.id, NOW(), NOW(), '', '', 0
FROM `el_sys_menu` menu
WHERE menu.permission_tag = 'sys:depart:view'
  AND EXISTS (SELECT 1 FROM `el_sys_role` role WHERE role.id = 'HR')
  AND NOT EXISTS (
      SELECT 1
      FROM `el_sys_role_menu` existing
      WHERE existing.role_id = 'HR' AND existing.menu_id = menu.id
  );

INSERT INTO `el_sys_role_menu`
    (`id`, `role_id`, `menu_id`, `create_time`, `update_time`, `create_by`, `update_by`, `data_flag`)
SELECT
    'hr_role_paging', 'HR', menu.id, NOW(), NOW(), '', '', 0
FROM `el_sys_menu` menu
WHERE menu.permission_tag = 'sys:role:paging'
  AND EXISTS (SELECT 1 FROM `el_sys_role` role WHERE role.id = 'HR')
  AND NOT EXISTS (
      SELECT 1
      FROM `el_sys_role_menu` existing
      WHERE existing.role_id = 'HR' AND existing.menu_id = menu.id
  );
