-- Only rename the label of unassigned papers from the known legacy demo exam.
-- Question/option snapshots, answers, grading, ownership and timestamps are untouched.
UPDATE el_paper SET title = '示例考核'
WHERE exam_id = '1915227067167539202'
  AND assignment_id IS NULL
  AND title = '云帆演示考试';
