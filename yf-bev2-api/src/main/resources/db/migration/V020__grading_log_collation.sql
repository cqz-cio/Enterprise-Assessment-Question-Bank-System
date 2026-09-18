-- Match the existing paper/user ID columns. MySQL 8 database defaults may be 0900_ai_ci.
ALTER TABLE el_paper_grading_log CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
