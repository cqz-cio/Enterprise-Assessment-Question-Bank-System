package com.yf.modules.exam.assignment.dto.request;

import lombok.Data;

import java.util.Date;

@Data
public class AssignmentQueryReqDTO {
    private String subjectName;
    private String departId;
    private String positionId;
    private String batchNo;
    private String status;
    private Boolean passed;
    private Date validFrom;
    private Date expireTo;
}
