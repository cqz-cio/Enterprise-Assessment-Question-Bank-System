package com.yf.modules.exam.assignment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CandidateVerifyReqDTO {

    @NotBlank(message = "请输入姓名")
    @Size(max = 128, message = "姓名格式不正确")
    private String candidateName;

    @NotBlank(message = "请输入6位考核码")
    @Size(min = 6, max = 6, message = "考核码必须为6位")
    private String accessCode;
}
