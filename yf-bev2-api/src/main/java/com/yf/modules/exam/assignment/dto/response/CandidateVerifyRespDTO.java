package com.yf.modules.exam.assignment.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class CandidateVerifyRespDTO {
    private String token;
    private String assignmentId;
    private String examId;
    private String paperId;
    private String candidateName;
    private String positionName;
    private String examTitle;
    private Date validFrom;
    private Date expireAt;
    private String assignmentStatus;
}
