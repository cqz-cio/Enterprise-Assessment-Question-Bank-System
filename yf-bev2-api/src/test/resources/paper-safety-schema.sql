CREATE TABLE el_paper (
 id varchar(64) PRIMARY KEY, user_id varchar(64), exam_id varchar(64), assignment_id varchar(64) UNIQUE,
 title varchar(255), total_time int, user_time int, total_score decimal(10,2), qualify_score decimal(10,2),
 user_score decimal(10,2), limit_time timestamp, hand_time timestamp, hand_state int DEFAULT 0,
 passed boolean, create_time timestamp DEFAULT CURRENT_TIMESTAMP, update_time timestamp,
 create_by varchar(64), update_by varchar(64), hand_min_snapshot int DEFAULT 0,
 grading_state varchar(32) DEFAULT 'NOT_REQUIRED', snapshot_source varchar(32) DEFAULT 'CREATED', grading_version bigint DEFAULT 0, graded_by varchar(64), graded_at timestamp
);
CREATE TABLE el_paper_qu (
 id varchar(64) PRIMARY KEY, paper_id varchar(64), qu_id varchar(64), qu_type varchar(32),
 answered boolean, mark boolean, sort int, score decimal(10,2), actual_score decimal(10,2), is_right boolean,
 content_snapshot clob, analysis_snapshot clob, reference_answer_snapshot clob, grading_criteria_snapshot clob, text_answer clob, grading_state varchar(32) DEFAULT 'NOT_REQUIRED', grader_id varchar(64), grader_comment varchar(2000), graded_at timestamp
);
CREATE TABLE el_paper_qu_answer (
 id varchar(64) PRIMARY KEY, paper_id varchar(64), qu_id varchar(64), answer_id varchar(64),
 content_snapshot clob, is_right boolean, answer varchar(2000), checked boolean, sort int, abc varchar(8)
);
CREATE TABLE el_exam_assignment (
 id varchar(64) PRIMARY KEY, exam_id varchar(64), user_id varchar(64), subject_type varchar(32),
 subject_name varchar(128), candidate_no varchar(64), mobile varchar(32), email varchar(255),
 depart_id varchar(64), position_id varchar(64), target_grade_id varchar(64), batch_no varchar(64),
 access_code_lookup varchar(64), access_code_hash varchar(255), valid_from timestamp, expire_at timestamp,
 status varchar(32), paper_id varchar(64), activated_at timestamp, submitted_at timestamp,
 completed_at timestamp, disabled_reason varchar(500), create_time timestamp DEFAULT CURRENT_TIMESTAMP,
 update_time timestamp, create_by varchar(64), update_by varchar(64)
);
