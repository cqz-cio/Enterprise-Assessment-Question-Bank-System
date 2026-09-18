package com.yf.modules.exam.assignment.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class EmployeeAssignmentQueryDTO {
    @Min(1) @Max(100000) private int current = 1;
    @Min(1) @Max(500) private int size = 10;
    @Size(max = 128) private String keyword;
    @Size(max = 64) private String departId;
    @Size(max = 64) private String positionId;
    @Size(max = 32) private String sceneType;
    @Size(max = 64) private String batchNo;
    @Size(max = 32) private String status;
}
