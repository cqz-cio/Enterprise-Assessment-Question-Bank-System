package com.yf.modules.exam.assignment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AssignmentStatusReqDTO {

    @NotBlank(message = "分配ID不能为空")
    private String id;
    @NotBlank(message = "操作不能为空")
    private String action;
    @Size(max = 500, message = "原因不能超过500个字符")
    private String reason;
}
