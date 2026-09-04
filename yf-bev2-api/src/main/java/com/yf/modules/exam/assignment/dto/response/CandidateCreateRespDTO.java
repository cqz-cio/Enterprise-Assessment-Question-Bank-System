package com.yf.modules.exam.assignment.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CandidateCreateRespDTO {
    private String assignmentId;
    private String candidateName;
    private String positionName;
    private String examTitle;
    private String accessCode;
    private String entryUrl;
}
