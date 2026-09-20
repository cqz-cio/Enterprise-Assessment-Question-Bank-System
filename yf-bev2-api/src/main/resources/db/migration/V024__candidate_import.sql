-- Preserve all historical assignments, including any pre-existing duplicate natural keys.
CREATE TABLE el_candidate_issue_key (
    candidate_no VARCHAR(64) NOT NULL,
    batch_no VARCHAR(64) NOT NULL,
    PRIMARY KEY (candidate_no,batch_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO el_candidate_issue_key(candidate_no,batch_no)
SELECT DISTINCT TRIM(candidate_no) COLLATE utf8mb4_unicode_ci,TRIM(batch_no) COLLATE utf8mb4_unicode_ci
FROM el_exam_assignment
WHERE subject_type='CANDIDATE' AND candidate_no IS NOT NULL AND batch_no IS NOT NULL;

INSERT INTO el_sys_menu
(id,parent_id,menu_type,permission_tag,name,meta_title,meta_no_cache,hidden,sort,create_time,update_time,create_by,update_by)
SELECT 'p1_candidate_import',parent_id,3,'exam:assignment:candidate:import','CandidateImport','批量导入候选人',1,1,10,NOW(),NOW(),'',''
FROM el_sys_menu WHERE permission_tag='exam:assignment:candidate:add' LIMIT 1;

INSERT INTO el_sys_role_menu(id,role_id,menu_id,create_time,update_time,create_by,update_by,data_flag)
SELECT CONCAT('p1ci_',r.role_id),r.role_id,'p1_candidate_import',NOW(),NOW(),'','',0
FROM (SELECT 'admin' AS role_id UNION ALL SELECT 'HR') r
WHERE EXISTS (SELECT 1 FROM el_sys_menu WHERE id='p1_candidate_import');
