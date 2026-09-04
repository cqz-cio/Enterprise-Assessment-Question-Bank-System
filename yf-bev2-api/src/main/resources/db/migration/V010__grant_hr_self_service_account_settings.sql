-- HR accounts need the same self-service profile and password pages as employees.
-- Candidate accounts remain excluded because candidate identity data is managed by HR.
INSERT INTO `el_sys_role_menu`
    (`id`, `role_id`, `menu_id`, `create_time`, `update_time`, `create_by`, `update_by`, `data_flag`)
SELECT seed.id, 'HR', seed.menu_id, NOW(), NOW(), '', '', 0
FROM (
    SELECT 'hr_user_settings_root' AS id, '1914244224937299969' AS menu_id
    UNION ALL SELECT 'hr_user_settings_info', '1914244884357386242'
    UNION ALL SELECT 'hr_user_settings_pass', '1914245167460323330'
) seed
WHERE EXISTS (SELECT 1 FROM `el_sys_menu` menu WHERE menu.id = seed.menu_id)
  AND NOT EXISTS (
      SELECT 1 FROM `el_sys_role_menu` existing
      WHERE existing.role_id = 'HR' AND existing.menu_id = seed.menu_id
  );
