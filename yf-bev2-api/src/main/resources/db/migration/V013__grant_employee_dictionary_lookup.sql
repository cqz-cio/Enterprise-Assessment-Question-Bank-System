-- Employee-facing pages use the shared dictionary-value selector for filters.
-- Grant read-only dictionary lookup; dictionary mutations remain admin-only.

INSERT INTO `el_sys_role_menu`
    (`id`, `role_id`, `menu_id`, `create_time`, `update_time`, `create_by`, `update_by`, `data_flag`)
SELECT
    'employee_dict_lookup', 'EMPLOYEE', menu.id, NOW(), NOW(), '', '', 0
FROM `el_sys_menu` menu
WHERE menu.permission_tag = 'sys:dict:paging'
  AND EXISTS (SELECT 1 FROM `el_sys_role` role WHERE role.id = 'EMPLOYEE')
  AND NOT EXISTS (
      SELECT 1
      FROM `el_sys_role_menu` existing
      WHERE existing.role_id = 'EMPLOYEE' AND existing.menu_id = menu.id
  );
