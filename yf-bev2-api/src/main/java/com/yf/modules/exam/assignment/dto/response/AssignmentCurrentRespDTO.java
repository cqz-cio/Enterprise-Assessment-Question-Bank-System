package com.yf.modules.exam.assignment.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class AssignmentCurrentRespDTO {
    private String assignmentId;
    private String examId;
    private String paperId;
    private String subjectName;
    private String positionName;
    private String examTitle;
    private Date validFrom;
    private Date expireAt;
    private String status;
}
