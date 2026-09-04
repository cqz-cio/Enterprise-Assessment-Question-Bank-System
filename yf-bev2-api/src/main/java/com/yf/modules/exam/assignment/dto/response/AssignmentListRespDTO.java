package com.yf.modules.exam.assignment.dto.response;

import lombok.Data;

import java.util.Date;

@Data
public class AssignmentListRespDTO {
    private String id;
    private String examId;
    private String userId;
    private String subjectType;
    private String subjectName;
    private String candidateNo;
    private String mobile;
    private String email;
    private String departId;
    private String departName;
    private String positionId;
    private String positionName;
    private String targetGradeId;
    private String targetGradeName;
    private String batchNo;
    private Date validFrom;
    private Date expireAt;
    private String status;
    private String paperId;
    private Boolean passed;
    private String examTitle;
    private Date activatedAt;
    private Date submittedAt;
    private Date completedAt;
    private String disabledReason;
    private Date createTime;
}
