package com.yf.modules.exam.assignment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AssignmentStartRespDTO {
    private String paperId;
    private boolean resumed;
}
