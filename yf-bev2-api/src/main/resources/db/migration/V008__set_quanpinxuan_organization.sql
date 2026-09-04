-- Confirmed visible organization: 全品轩 -> 餐具部 / 家具部.
-- Legacy demo departments are hidden instead of deleted so historical references remain valid.

ALTER TABLE `el_sys_depart`
    ADD COLUMN `status` tinyint NOT NULL DEFAULT 1 COMMENT '1启用，0停用' AFTER `sort`,
    ADD KEY `idx_depart_parent_status_sort` (`parent_id`, `status`, `sort`);

UPDATE `el_sys_depart`
SET `dept_name` = '全品轩', `status` = 1, `update_time` = NOW()
WHERE `id` = '1441328268501381121';

INSERT INTO `el_sys_depart`
    (`id`, `dept_type`, `parent_id`, `dept_name`, `dept_code`, `dept_level`, `sort`, `status`,
     `create_time`, `update_time`, `create_by`, `update_by`)
SELECT 'dev_dept_tableware', 2, '1441328268501381121', '餐具部', 'A01A04', 0, 1, 1,
       NOW(), NOW(), '', ''
WHERE NOT EXISTS (
    SELECT 1 FROM `el_sys_depart`
    WHERE `parent_id` = '1441328268501381121' AND `dept_name` = '餐具部'
);

INSERT INTO `el_sys_depart`
    (`id`, `dept_type`, `parent_id`, `dept_name`, `dept_code`, `dept_level`, `sort`, `status`,
     `create_time`, `update_time`, `create_by`, `update_by`)
SELECT 'dev_dept_furniture', 2, '1441328268501381121', '家具部', 'A01A05', 0, 2, 1,
       NOW(), NOW(), '', ''
WHERE NOT EXISTS (
    SELECT 1 FROM `el_sys_depart`
    WHERE `parent_id` = '1441328268501381121' AND `dept_name` = '家具部'
);

UPDATE `el_sys_depart`
SET `dept_type` = 2,
    `status` = 1,
    `sort` = CASE `dept_name` WHEN '餐具部' THEN 1 ELSE 2 END,
    `update_time` = NOW()
WHERE `parent_id` = '1441328268501381121'
  AND `dept_name` IN ('餐具部', '家具部');

UPDATE `el_sys_depart`
SET `status` = 0, `update_time` = NOW()
WHERE `id` IN ('1447829893265002498', '1447829920330846210', '1447829960050905090');
