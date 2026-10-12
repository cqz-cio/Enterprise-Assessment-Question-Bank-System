-- D-043: a separate department manager. No shared/default credentials or employee conversion.
INSERT INTO el_sys_role
(id,role_name,data_scope,role_level,remark,create_time,create_by,update_time,update_by)
VALUES ('DEPT_MANAGER','部门主管',2,90,'仅本部门：发放考核、审核员工、查询结果及人工阅卷',NOW(),'',NOW(),'');

-- Reuse existing pages and explicitly enumerate capabilities; do not copy future HR/admin grants.
INSERT INTO el_sys_role_menu(id,role_id,menu_id,create_time,update_time,create_by,update_by,data_flag)
SELECT CONCAT('dmgr_',LEFT(MD5(m.id),24)), 'DEPT_MANAGER', m.id, NOW(), NOW(), '', '', 0
FROM el_sys_menu m
WHERE m.id IN (
    '1552547259067965442','1556906844813914114',
    '1911703533823365121','p2_candidate_page','p1_employee_page','p1_results_page','p1_grading_page',
    '1914244224937299969','1914244884357386242','1914245167460323330'
) OR m.permission_tag IN (
    'sys:user:paging','sys:user:registration:audit','sys:depart:view','sys:role:paging','sys:dict:paging',
    'exam:assignment:candidate:view','exam:assignment:candidate:add','exam:assignment:candidate:code',
    'exam:assignment:candidate:result','exam:assignment:edit',
    'exam:assignment:employee:view','exam:assignment:employee:add','exam:assignment:employee:edit','exam:assignment:employee:result',
    'exam:results:view','exam:results:export','exam:grading:view','exam:grading:score','exam:grading:finalize'
);
