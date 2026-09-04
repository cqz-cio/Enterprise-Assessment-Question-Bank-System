-- Phase 2: associate repositories and assessment templates with a fixed position and scene.

ALTER TABLE `el_repo`
    ADD COLUMN `position_id` varchar(64) DEFAULT NULL COMMENT '岗位 ID' AFTER `cat_id`,
    ADD COLUMN `scene_type` varchar(32) DEFAULT NULL COMMENT 'ENTRY/PROMOTION/GENERAL' AFTER `position_id`,
    ADD COLUMN `status` tinyint NOT NULL DEFAULT 1 COMMENT '1 启用，0 停用' AFTER `scene_type`,
    ADD KEY `idx_repo_position_scene_status` (`position_id`, `scene_type`, `status`);

ALTER TABLE `el_exam`
    ADD COLUMN `position_id` varchar(64) DEFAULT NULL COMMENT '岗位 ID' AFTER `repo_id`,
    ADD COLUMN `scene_type` varchar(32) DEFAULT NULL COMMENT 'ENTRY/PROMOTION/GENERAL' AFTER `position_id`,
    ADD COLUMN `template_status` tinyint NOT NULL DEFAULT 1 COMMENT '1 启用，0 停用' AFTER `scene_type`,
    ADD COLUMN `option_shuffle` tinyint NOT NULL DEFAULT 1 COMMENT '1 随机选项，0 固定选项' AFTER `template_status`,
    ADD KEY `idx_exam_position_scene_status` (`position_id`, `scene_type`, `template_status`);
