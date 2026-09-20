INSERT INTO el_sys_menu
(id,parent_id,menu_type,permission_tag,path,component,name,meta_title,meta_no_cache,hidden,sort,create_time,update_time,create_by,update_by)
VALUES
('p1_results_page','1911703533823365121',2,NULL,'/admin/exam/results','views/Exam/Report/Index','ExamResults','成绩查询',1,0,5,NOW(),NOW(),'',''),
('p1_results_view','p1_results_page',3,'exam:results:view',NULL,NULL,'ResultView','查看成绩',1,1,1,NOW(),NOW(),'',''),
('p1_results_export','p1_results_page',3,'exam:results:export',NULL,NULL,'ResultExport','导出成绩',1,1,2,NOW(),NOW(),'','');
INSERT INTO el_sys_role_menu(id,role_id,menu_id,create_time,update_time,create_by,update_by,data_flag)
SELECT CONCAT('p1r_',LEFT(MD5(CONCAT(r.role_id,m.id)),24)),r.role_id,m.id,NOW(),NOW(),'','',0
FROM (SELECT 'admin' AS role_id UNION ALL SELECT 'HR') r CROSS JOIN el_sys_menu m
WHERE m.id IN ('p1_results_page','p1_results_view','p1_results_export');
