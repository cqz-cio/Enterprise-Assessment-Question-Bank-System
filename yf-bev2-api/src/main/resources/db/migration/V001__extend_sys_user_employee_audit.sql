-- Phase 1: employee registration and audit fields.
-- Apply once to a database initialized from yf_boot_exam.sql.

ALTER TABLE `el_sys_user`
    ADD COLUMN `employee_no` varchar(64) DEFAULT NULL COMMENT '员工工号；候选人内部用户为空' AFTER `email`,
    ADD COLUMN `audit_by` varchar(64) DEFAULT NULL COMMENT '最近审核人' AFTER `dept_code`,
    ADD COLUMN `audit_time` datetime DEFAULT NULL COMMENT '最近审核时间' AFTER `audit_by`,
    ADD COLUMN `audit_remark` varchar(500) DEFAULT NULL COMMENT '审核意见或驳回原因' AFTER `audit_time`,
    ADD UNIQUE KEY `uk_sys_user_employee_no` (`employee_no`);
