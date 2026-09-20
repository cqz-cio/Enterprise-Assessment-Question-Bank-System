package com.yf.modules.exam.assignment.importing;

import com.yf.base.api.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Inserted in the same transaction as the candidate, including manual issuance. */
@Component
@RequiredArgsConstructor
public class CandidateIdentityGuard {
    private final JdbcTemplate db;

    public boolean exists(String number, String batch) {
        return db.queryForObject("SELECT COUNT(*) FROM el_candidate_issue_key WHERE candidate_no=? AND batch_no=?",
                Integer.class, number.trim(), batch.trim()) > 0;
    }

    public void reserve(String number, String batch) {
        try {
            db.update("INSERT INTO el_candidate_issue_key(candidate_no,batch_no) VALUES(?,?)", number.trim(), batch.trim());
        } catch (DuplicateKeyException ex) {
            throw new DuplicateCandidateException();
        }
    }

    public static class DuplicateCandidateException extends ServiceException {
        public DuplicateCandidateException() { super("该候选人编号在本批次已有考核，将跳过，不覆盖原考核"); }
    }
}
