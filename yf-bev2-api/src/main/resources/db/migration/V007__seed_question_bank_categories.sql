-- New question banks use two business categories. Historical dictionary values are retained for display compatibility.
INSERT INTO `el_sys_dic_value`
    (`id`, `dic_code`, `dic_value`, `title`, `parent_id`, `remark`, `create_time`, `update_time`, `create_by`, `update_by`)
VALUES
    ('repo_recruitment', 'repo_catalog', 'RECRUITMENT', '入职招聘', '0', '面试及转正相关题库', NOW(), NOW(), '', ''),
    ('repo_promotion', 'repo_catalog', 'EMPLOYEE_PROMOTION', '员工晋升', '0', '员工晋升相关题库', NOW(), NOW(), '', '')
ON DUPLICATE KEY UPDATE
    `title` = VALUES(`title`), `remark` = VALUES(`remark`), `update_time` = NOW();
