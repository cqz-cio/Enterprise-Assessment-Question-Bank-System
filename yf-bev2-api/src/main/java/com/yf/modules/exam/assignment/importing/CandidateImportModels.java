package com.yf.modules.exam.assignment.importing;

import lombok.Getter;
import lombok.Setter;
import java.util.*;

public final class CandidateImportModels {
    private CandidateImportModels() { }
    @Getter @Setter
    public static class ImportRow {
        private int rowNumber;
        private List<String> values = new ArrayList<>();
        private String candidateName, candidateNo, departName, positionName, examTitle, batchNo;
        private String validFrom, expireAt, status = "ERROR", message;
        private String assignmentId, accessCode;
    }
    @Getter @Setter
    public static class ImportView {
        private String taskId, fileName;
        private long expiresAt;
        private boolean committed;
        private int totalCount, validCount, successCount, failureCount, duplicateCount;
        private List<ImportRow> rows;
    }
}
