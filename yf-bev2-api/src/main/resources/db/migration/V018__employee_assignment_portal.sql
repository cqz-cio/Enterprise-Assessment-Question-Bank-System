-- Employee management permissions are separate from template editing and candidate access.
INSERT INTO el_sys_menu
(id,parent_id,menu_type,permission_tag,path,component,name,meta_title,meta_no_cache,hidden,sort,create_time,update_time,create_by,update_by)
VALUES
('p1_employee_page','1911703533823365121',2,NULL,'/admin/exam/employee','views/Exam/Assignment/Employee','ExamEmployee','员工考核',1,0,3,NOW(),NOW(),'',''),
('p1_employee_view','p1_employee_page',3,'exam:assignment:employee:view',NULL,NULL,'EmployeeAssignmentView','查看',1,1,1,NOW(),NOW(),'',''),
('p1_employee_add','p1_employee_page',3,'exam:assignment:employee:add',NULL,NULL,'EmployeeAssignmentAdd','发放',1,1,2,NOW(),NOW(),'',''),
('p1_employee_edit','p1_employee_page',3,'exam:assignment:employee:edit',NULL,NULL,'EmployeeAssignmentEdit','停用/恢复',1,1,3,NOW(),NOW(),'',''),
('p1_employee_result','p1_employee_page',3,'exam:assignment:employee:result',NULL,NULL,'EmployeeAssignmentResult','查看结果',1,1,4,NOW(),NOW(),'',''),
('p1_my_assignment','1912389902866522113',3,'exam:assignment:my:view',NULL,NULL,'MyAssignmentView','我的考核列表',1,1,5,NOW(),NOW(),'','');

INSERT INTO el_sys_role_menu (id,role_id,menu_id,create_time,update_time,create_by,update_by,data_flag)
SELECT CONCAT('p1e_',LEFT(MD5(CONCAT(r.role_id,m.id)),24)),r.role_id,m.id,NOW(),NOW(),'','',0
FROM (SELECT 'admin' AS role_id UNION ALL SELECT 'HR') r
CROSS JOIN el_sys_menu m WHERE m.id IN ('p1_employee_page','p1_employee_view','p1_employee_add','p1_employee_edit','p1_employee_result');

INSERT INTO el_sys_role_menu (id,role_id,menu_id,create_time,update_time,create_by,update_by,data_flag)
SELECT CONCAT('p1m_',LEFT(MD5(r.role_id),24)),r.role_id,'p1_my_assignment',NOW(),NOW(),'','',0
FROM (SELECT 'admin' AS role_id UNION ALL SELECT 'EMPLOYEE') r;

UPDATE el_sys_menu SET meta_title='我的考核',meta_no_cache=1,update_time=NOW() WHERE id='1912389902866522113';
CREATE INDEX idx_assignment_employee_created ON el_exam_assignment(subject_type,user_id,create_time);
