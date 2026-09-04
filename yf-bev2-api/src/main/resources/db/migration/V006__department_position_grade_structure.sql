-- Department -> position -> assessment scene -> optional promotion target grade.

CREATE TABLE `el_depart_position` (
    `id` varchar(64) NOT NULL COMMENT '关联 ID',
    `depart_id` varchar(64) NOT NULL COMMENT '部门 ID',
    `position_id` varchar(64) NOT NULL COMMENT '岗位 ID',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_depart_position` (`depart_id`, `position_id`),
    KEY `idx_depart_position_position` (`position_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='部门岗位关联';

CREATE TABLE `el_position_grade` (
    `id` varchar(64) NOT NULL COMMENT '职级 ID',
    `position_id` varchar(64) NOT NULL COMMENT '岗位 ID',
    `code` varchar(64) NOT NULL COMMENT '职级编码',
    `name` varchar(128) NOT NULL COMMENT '职级名称',
    `level_no` int DEFAULT NULL COMMENT '职级序号',
    `sort` int NOT NULL DEFAULT 0 COMMENT '展示顺序',
    `status` tinyint NOT NULL DEFAULT 1 COMMENT '1 启用，0 停用',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_position_grade_code` (`position_id`, `code`),
    KEY `idx_position_grade_status_sort` (`position_id`, `status`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='岗位职级';

ALTER TABLE `el_repo`
    ADD COLUMN `depart_id` varchar(64) DEFAULT NULL COMMENT '部门 ID' AFTER `cat_id`,
    ADD COLUMN `target_grade_id` varchar(64) DEFAULT NULL COMMENT '晋升目标职级 ID' AFTER `scene_type`,
    ADD KEY `idx_repo_depart_position_scene` (`depart_id`, `position_id`, `scene_type`, `target_grade_id`);

ALTER TABLE `el_exam`
    ADD COLUMN `depart_id` varchar(64) DEFAULT NULL COMMENT '部门 ID' AFTER `repo_id`,
    ADD COLUMN `target_grade_id` varchar(64) DEFAULT NULL COMMENT '晋升目标职级 ID' AFTER `scene_type`,
    ADD KEY `idx_exam_depart_position_scene` (`depart_id`, `position_id`, `scene_type`, `target_grade_id`);

ALTER TABLE `el_exam_assignment`
    ADD COLUMN `depart_id` varchar(64) DEFAULT NULL COMMENT '部门 ID' AFTER `email`,
    ADD COLUMN `target_grade_id` varchar(64) DEFAULT NULL COMMENT '目标职级 ID' AFTER `position_id`,
    ADD KEY `idx_assignment_depart_position` (`depart_id`, `position_id`);

UPDATE `el_repo` SET `scene_type` = 'INTERVIEW' WHERE `scene_type` = 'ENTRY';
UPDATE `el_repo` SET `scene_type` = 'REGULARIZATION' WHERE `scene_type` = 'GENERAL';
UPDATE `el_exam` SET `scene_type` = 'INTERVIEW' WHERE `scene_type` = 'ENTRY';
UPDATE `el_exam` SET `scene_type` = 'REGULARIZATION' WHERE `scene_type` = 'GENERAL';
