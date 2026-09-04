-- Phase 2: fixed enterprise positions maintained by administrators.

CREATE TABLE `el_position` (
    `id` varchar(64) NOT NULL COMMENT '岗位 ID',
    `code` varchar(64) NOT NULL COMMENT '稳定岗位编码',
    `name` varchar(128) NOT NULL COMMENT '岗位名称',
    `status` tinyint NOT NULL DEFAULT 1 COMMENT '1 启用，0 停用',
    `sort` int NOT NULL DEFAULT 0 COMMENT '展示顺序',
    `remark` varchar(500) DEFAULT NULL COMMENT '备注',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `create_by` varchar(64) DEFAULT NULL,
    `update_by` varchar(64) DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_position_code` (`code`),
    KEY `idx_position_status_sort` (`status`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='企业岗位';
