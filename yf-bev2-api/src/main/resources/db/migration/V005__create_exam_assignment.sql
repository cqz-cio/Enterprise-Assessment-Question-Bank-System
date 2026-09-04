-- Phase 2: candidate assessment assignments and single-paper lifecycle.

CREATE TABLE `el_exam_assignment` (
    `id` varchar(64) NOT NULL COMMENT '分配 ID',
    `exam_id` varchar(64) NOT NULL COMMENT '考核模板 ID',
    `user_id` varchar(64) NOT NULL COMMENT '内部用户 ID',
    `subject_type` varchar(32) NOT NULL COMMENT 'CANDIDATE/EMPLOYEE',
    `subject_name` varchar(128) NOT NULL COMMENT '姓名快照',
    `candidate_no` varchar(64) DEFAULT NULL COMMENT '候选人编号',
    `mobile` varchar(32) DEFAULT NULL COMMENT '手机号快照',
    `email` varchar(255) DEFAULT NULL COMMENT '邮箱快照',
    `position_id` varchar(64) NOT NULL COMMENT '岗位 ID',
    `batch_no` varchar(64) DEFAULT NULL COMMENT '招聘/考核批次',
    `access_code_lookup` char(64) DEFAULT NULL COMMENT '带服务端 pepper 的 HMAC-SHA256 查询摘要',
    `access_code_hash` varchar(255) DEFAULT NULL COMMENT 'PBKDF2 验证摘要',
    `valid_from` datetime DEFAULT NULL COMMENT '可进入时间',
    `expire_at` datetime NOT NULL COMMENT '截止时间',
    `status` varchar(32) NOT NULL DEFAULT 'ASSIGNED' COMMENT '分配状态',
    `paper_id` varchar(64) DEFAULT NULL COMMENT '唯一试卷 ID',
    `activated_at` datetime DEFAULT NULL COMMENT '首次验证时间',
    `submitted_at` datetime DEFAULT NULL COMMENT '交卷时间',
    `completed_at` datetime DEFAULT NULL COMMENT '最终完成时间',
    `disabled_reason` varchar(500) DEFAULT NULL COMMENT '停用原因',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `create_by` varchar(64) DEFAULT NULL,
    `update_by` varchar(64) DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_assignment_user_exam_batch` (`user_id`, `exam_id`, `batch_no`),
    UNIQUE KEY `uk_assignment_code_lookup` (`access_code_lookup`),
    KEY `idx_assignment_exam_status` (`exam_id`, `status`),
    KEY `idx_assignment_position_batch` (`position_id`, `batch_no`),
    KEY `idx_assignment_expire` (`expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='考核分配';

ALTER TABLE `el_paper`
    ADD COLUMN `assignment_id` varchar(64) DEFAULT NULL COMMENT '考核分配 ID' AFTER `exam_id`,
    ADD UNIQUE KEY `uk_paper_assignment` (`assignment_id`);

INSERT INTO `el_sys_menu`
    (`id`, `parent_id`, `menu_type`, `permission_tag`, `path`, `component`, `redirect`, `name`,
     `meta_title`, `meta_icon`, `meta_active_menu`, `meta_no_cache`, `hidden`, `sort`,
     `create_time`, `update_time`, `create_by`, `update_by`)
VALUES
    ('p2_position_page', '1911703533823365121', 2, NULL, '/admin/exam/position', 'views/Exam/Position/Position', NULL, 'ExamPosition', '岗位管理', NULL, NULL, 1, 0, 1, NOW(), NOW(), '', ''),
    ('p2_position_view', 'p2_position_page', 3, 'exam:position:view', NULL, NULL, NULL, 'ExamPositionView', '查看', NULL, NULL, 1, 1, 1, NOW(), NOW(), '', ''),
    ('p2_position_add', 'p2_position_page', 3, 'exam:position:add', NULL, NULL, NULL, 'ExamPositionAdd', '添加', NULL, NULL, 1, 1, 2, NOW(), NOW(), '', ''),
    ('p2_position_edit', 'p2_position_page', 3, 'exam:position:edit', NULL, NULL, NULL, 'ExamPositionEdit', '修改', NULL, NULL, 1, 1, 3, NOW(), NOW(), '', ''),
    ('p2_position_delete', 'p2_position_page', 3, 'exam:position:delete', NULL, NULL, NULL, 'ExamPositionDelete', '删除', NULL, NULL, 1, 1, 4, NOW(), NOW(), '', ''),
    ('p2_candidate_page', '1911703533823365121', 2, NULL, '/admin/exam/candidate', 'views/Exam/Assignment/Candidate', NULL, 'ExamCandidate', '候选人考核', NULL, NULL, 1, 0, 2, NOW(), NOW(), '', ''),
    ('p2_candidate_view', 'p2_candidate_page', 3, 'exam:assignment:candidate:view', NULL, NULL, NULL, 'ExamCandidateView', '查看', NULL, NULL, 1, 1, 1, NOW(), NOW(), '', ''),
    ('p2_candidate_add', 'p2_candidate_page', 3, 'exam:assignment:candidate:add', NULL, NULL, NULL, 'ExamCandidateAdd', '发放', NULL, NULL, 1, 1, 2, NOW(), NOW(), '', ''),
    ('p2_candidate_code', 'p2_candidate_page', 3, 'exam:assignment:candidate:code', NULL, NULL, NULL, 'ExamCandidateCode', '重置考核码', NULL, NULL, 1, 1, 3, NOW(), NOW(), '', ''),
    ('p2_assignment_edit', 'p2_candidate_page', 3, 'exam:assignment:edit', NULL, NULL, NULL, 'ExamAssignmentEdit', '停用/恢复', NULL, NULL, 1, 1, 4, NOW(), NOW(), '', '')
ON DUPLICATE KEY UPDATE
    `permission_tag` = VALUES(`permission_tag`), `path` = VALUES(`path`),
    `component` = VALUES(`component`), `meta_title` = VALUES(`meta_title`),
    `hidden` = VALUES(`hidden`), `sort` = VALUES(`sort`), `update_time` = NOW();

INSERT INTO `el_sys_role_menu`
    (`id`, `role_id`, `menu_id`, `create_time`, `update_time`, `create_by`, `update_by`, `data_flag`)
SELECT CONCAT('p2a_', LEFT(MD5(m.id), 24)), 'admin', m.id, NOW(), NOW(), '', '', 0
FROM `el_sys_menu` m
WHERE m.id IN ('p2_position_page', 'p2_position_view', 'p2_position_add', 'p2_position_edit',
               'p2_position_delete', 'p2_candidate_page', 'p2_candidate_view', 'p2_candidate_add',
               'p2_candidate_code', 'p2_assignment_edit')
  AND NOT EXISTS (SELECT 1 FROM `el_sys_role_menu` rm WHERE rm.role_id = 'admin' AND rm.menu_id = m.id);

INSERT INTO `el_sys_role_menu`
    (`id`, `role_id`, `menu_id`, `create_time`, `update_time`, `create_by`, `update_by`, `data_flag`)
SELECT CONCAT('p2h_', LEFT(MD5(m.id), 24)), 'HR', m.id, NOW(), NOW(), '', '', 0
FROM `el_sys_menu` m
WHERE m.id IN ('1911703533823365121', 'p2_candidate_page', 'p2_candidate_view',
               'p2_candidate_add', 'p2_candidate_code', 'p2_assignment_edit')
  AND NOT EXISTS (SELECT 1 FROM `el_sys_role_menu` rm WHERE rm.role_id = 'HR' AND rm.menu_id = m.id);
