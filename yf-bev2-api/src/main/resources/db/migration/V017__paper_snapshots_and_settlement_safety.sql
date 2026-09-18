-- Deploy with the old application stopped: old writers do not populate snapshots.
ALTER TABLE el_paper
    MODIFY COLUMN passed tinyint NULL DEFAULT NULL COMMENT 'Final result; NULL while pending',
    ADD COLUMN hand_min_snapshot int NOT NULL DEFAULT 0,
    ADD COLUMN grading_state varchar(32) NOT NULL DEFAULT 'NOT_REQUIRED',
    ADD COLUMN snapshot_source varchar(32) NOT NULL DEFAULT 'CREATED',
    ADD KEY idx_paper_overdue (hand_state, limit_time);

ALTER TABLE el_paper_qu
    ADD COLUMN content_snapshot longtext NULL,
    ADD COLUMN analysis_snapshot longtext NULL,
    ADD COLUMN reference_answer_snapshot longtext NULL,
    ADD COLUMN grading_criteria_snapshot longtext NULL;

ALTER TABLE el_paper_qu_answer
    ADD COLUMN content_snapshot longtext NULL;

-- This freezes currently available content, not necessarily the original content.
UPDATE el_paper p LEFT JOIN el_exam e ON e.id = p.exam_id
SET p.hand_min_snapshot = COALESCE(e.hand_min, 0), p.snapshot_source = 'LEGACY_BACKFILL';
UPDATE el_paper_qu pq LEFT JOIN el_repo_qu q ON q.id = pq.qu_id
SET pq.content_snapshot = q.content, pq.analysis_snapshot = q.analysis,
    pq.reference_answer_snapshot = q.reference_answer,
    pq.grading_criteria_snapshot = q.grading_criteria;
UPDATE el_paper_qu_answer pa LEFT JOIN el_repo_qu_answer a ON a.id = pa.answer_id
SET pa.content_snapshot = a.content;
-- pa.is_right is the original saved scoring key: NEVER overwrite it from the bank.
UPDATE el_paper p SET p.snapshot_source = 'LEGACY_INCOMPLETE'
WHERE EXISTS (SELECT 1 FROM el_paper_qu q WHERE q.paper_id = p.id AND q.content_snapshot IS NULL)
   OR EXISTS (SELECT 1 FROM el_paper_qu_answer a WHERE a.paper_id = p.id AND a.content_snapshot IS NULL);

-- Quarantine provisional legacy subjective results without deleting answers/scores.
UPDATE el_paper p SET p.grading_state = 'PENDING', p.passed = NULL
WHERE EXISTS (SELECT 1 FROM el_paper_qu q WHERE q.paper_id = p.id
              AND q.qu_type NOT IN ('radio', 'multi', 'judge'));
UPDATE el_exam_assignment a JOIN el_paper p ON p.id = a.paper_id
SET a.status = 'PENDING_REVIEW', a.completed_at = NULL
WHERE p.grading_state = 'PENDING' AND p.hand_state <> 0
  AND a.status IN ('STARTED', 'COMPLETED');
