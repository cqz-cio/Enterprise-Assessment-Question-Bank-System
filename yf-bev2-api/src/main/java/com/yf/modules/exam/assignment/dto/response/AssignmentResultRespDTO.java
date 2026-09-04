package com.yf.modules.exam.assignment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AssignmentResultRespDTO {
    private String assignmentId;
    private boolean resultAvailable;
    private Boolean passed;
}
