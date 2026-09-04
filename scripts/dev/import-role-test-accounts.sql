-- Local verification data only. This file is intentionally outside Flyway's
-- classpath:db/migration directory and must never be used in production.
-- Import after Flyway V001-V005 have completed.
-- Passwords are not stored in plaintext; distribute local credentials out of band.

INSERT INTO `el_sys_user`
    (`id`, `user_name`, `real_name`, `avatar`, `password`, `salt`, `state`, `id_card`,
     `mobile`, `email`, `employee_no`, `dept_code`, `create_time`, `create_by`, `update_time`, `update_by`)
VALUES
    ('dev_role_hr_001', 'hr_test', '本地验收HR', '',
     'a9e91579ee64435b79e7ecc14d9246e4', 'HrT026', 0, '', NULL, NULL,
     'HR-TEST-001', 'A01', NOW(), '', NOW(), ''),
    ('dev_role_employee_001', 'employee_test', '本地验收员工', '',
     'b07b210a849c39c0c21eccc7676d000b', 'EmT026', 0, '', NULL, NULL,
     'EMP-TEST-001', 'A01', NOW(), '', NOW(), '')
ON DUPLICATE KEY UPDATE
    `user_name` = VALUES(`user_name`),
    `real_name` = VALUES(`real_name`),
    `password` = VALUES(`password`),
    `salt` = VALUES(`salt`),
    `state` = 0,
    `employee_no` = VALUES(`employee_no`),
    `dept_code` = VALUES(`dept_code`),
    `update_time` = NOW();

DELETE FROM `el_sys_user_role`
WHERE `user_id` IN ('dev_role_hr_001', 'dev_role_employee_001');

INSERT INTO `el_sys_user_role` (`id`, `user_id`, `role_id`) VALUES
    ('dev_role_map_hr_001', 'dev_role_hr_001', 'HR'),
    ('dev_role_map_employee_001', 'dev_role_employee_001', 'EMPLOYEE');

SELECT u.user_name, u.real_name, u.state, ur.role_id
FROM el_sys_user u
JOIN el_sys_user_role ur ON ur.user_id = u.id
WHERE u.id IN ('1000000000000000001', 'dev_role_hr_001', 'dev_role_employee_001')
ORDER BY ur.role_id;
