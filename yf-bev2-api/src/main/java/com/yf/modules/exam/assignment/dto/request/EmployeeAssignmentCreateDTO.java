package com.yf.modules.exam.assignment.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.Date;
import java.util.List;

@Data
public class EmployeeAssignmentCreateDTO {
    @NotBlank @Size(max = 64) private String examId;
    @NotEmpty @Size(max = 500) private List<@NotBlank @Size(max = 64) String> userIds;
    @NotBlank @Size(max = 64) private String batchNo;
    private Date validFrom;
    private Date expireAt;
}
