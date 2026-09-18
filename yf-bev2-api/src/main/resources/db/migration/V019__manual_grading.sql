ALTER TABLE el_paper_qu
    ADD COLUMN text_answer LONGTEXT NULL,
    ADD COLUMN grading_state VARCHAR(32) NOT NULL DEFAULT 'NOT_REQUIRED',
    ADD COLUMN grader_id VARCHAR(64) NULL,
    ADD COLUMN grader_comment VARCHAR(2000) NULL,
    ADD COLUMN graded_at DATETIME NULL;
UPDATE el_paper_qu SET grading_state='PENDING' WHERE qu_type='short';

ALTER TABLE el_paper
    ADD COLUMN grading_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN graded_by VARCHAR(64) NULL,
    ADD COLUMN graded_at DATETIME NULL;
CREATE INDEX idx_paper_grading_hand ON el_paper(grading_state,hand_state,hand_time);

CREATE TABLE el_paper_grading_log (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    paper_id VARCHAR(64) NOT NULL,
    paper_qu_id VARCHAR(64) NULL,
    grader_id VARCHAR(64) NOT NULL,
    score_before DECIMAL(10,2) NULL,
    score_after DECIMAL(10,2) NULL,
    comment_before VARCHAR(2000) NULL,
    comment_after VARCHAR(2000) NULL,
    action VARCHAR(32) NOT NULL,
    create_time DATETIME NOT NULL,
    KEY idx_grading_log_paper(paper_id,create_time,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO el_sys_menu
(id,parent_id,menu_type,permission_tag,path,component,name,meta_title,meta_no_cache,hidden,sort,create_time,update_time,create_by,update_by)
VALUES
('p1_grading_page','1911703533823365121',2,NULL,'/admin/exam/grading','views/Exam/Grading/Index','ExamGrading','人工阅卷',1,0,4,NOW(),NOW(),'',''),
('p1_grading_view','p1_grading_page',3,'exam:grading:view',NULL,NULL,'GradingView','查看阅卷',1,1,1,NOW(),NOW(),'',''),
('p1_grading_score','p1_grading_page',3,'exam:grading:score',NULL,NULL,'GradingScore','逐题评分',1,1,2,NOW(),NOW(),'',''),
('p1_grading_finalize','p1_grading_page',3,'exam:grading:finalize',NULL,NULL,'GradingFinalize','完成阅卷',1,1,3,NOW(),NOW(),'','');

-- HR does not receive grading privileges automatically. Administrators may explicitly grant them.
INSERT INTO el_sys_role_menu(id,role_id,menu_id,create_time,update_time,create_by,update_by,data_flag)
SELECT CONCAT('p1g_',LEFT(MD5(id),24)),'admin',id,NOW(),NOW(),'','',0
FROM el_sys_menu WHERE id IN ('p1_grading_page','p1_grading_view','p1_grading_score','p1_grading_finalize');
