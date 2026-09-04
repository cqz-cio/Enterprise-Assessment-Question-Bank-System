-- Allow HR and administrators to view completed candidate results without
-- granting HR the broad exam:record:list permission used by exam management.

INSERT INTO `el_sys_menu`
    (`id`, `parent_id`, `menu_type`, `permission_tag`, `path`, `component`, `redirect`, `name`,
     `meta_title`, `meta_icon`, `meta_active_menu`, `meta_no_cache`, `hidden`, `sort`,
     `create_time`, `update_time`, `create_by`, `update_by`)
VALUES
    ('p2_candidate_result', 'p2_candidate_page', 3, 'exam:assignment:candidate:result',
     NULL, NULL, NULL, 'ExamCandidateResult', '查看结果', NULL, NULL, 1, 1, 5,
     NOW(), NOW(), '', '')
ON DUPLICATE KEY UPDATE
    `permission_tag` = VALUES(`permission_tag`),
    `meta_title` = VALUES(`meta_title`),
    `update_time` = NOW();

INSERT INTO `el_sys_role_menu`
    (`id`, `role_id`, `menu_id`, `create_time`, `update_time`, `create_by`, `update_by`, `data_flag`)
SELECT
    CONCAT('p2_result_', LOWER(role.id)), role.id, 'p2_candidate_result',
    NOW(), NOW(), '', '', 0
FROM `el_sys_role` role
WHERE role.id IN ('admin', 'HR')
  AND NOT EXISTS (
      SELECT 1
      FROM `el_sys_role_menu` existing
      WHERE existing.role_id = role.id
        AND existing.menu_id = 'p2_candidate_result'
  );
