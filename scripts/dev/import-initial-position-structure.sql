-- 开发环境初始业务岗位结构，可重复执行。
-- 先执行 Flyway V008；职级尚未确认，因此暂不预设。

INSERT INTO `el_position`
    (`id`, `code`, `name`, `status`, `sort`, `remark`, `create_time`, `update_time`, `create_by`, `update_by`)
VALUES
    ('dev_pos_sales_development', 'SALES_DEVELOPMENT', '开发业务员', 1, 10, '初始业务岗位结构', NOW(), NOW(), '', ''),
    ('dev_pos_sales_growth', 'SALES_GROWTH', '增长业务员', 1, 20, '初始业务岗位结构', NOW(), NOW(), '', '')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `status` = VALUES(`status`), `sort` = VALUES(`sort`),
    `remark` = VALUES(`remark`), `update_time` = NOW();

INSERT INTO `el_depart_position` (`id`, `depart_id`, `position_id`, `create_time`)
SELECT CONCAT('dev_dp_', LEFT(MD5(CONCAT(d.id, p.id)), 24)), d.id, p.id, NOW()
FROM `el_sys_depart` d
CROSS JOIN `el_position` p
WHERE d.parent_id = '1441328268501381121'
  AND d.status = 1
  AND d.dept_name IN ('餐具部', '家具部')
  AND p.code IN ('SALES_DEVELOPMENT', 'SALES_GROWTH')
ON DUPLICATE KEY UPDATE `create_time` = `create_time`;
