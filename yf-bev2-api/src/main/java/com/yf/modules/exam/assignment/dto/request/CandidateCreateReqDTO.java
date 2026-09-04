package com.yf.modules.exam.assignment.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Date;

@Data
public class CandidateCreateReqDTO {

    @NotBlank(message = "候选人姓名不能为空")
    @Size(max = 128, message = "候选人姓名不能超过128个字符")
    private String candidateName;
    @NotBlank(message = "候选人编号不能为空")
    @Size(max = 64, message = "候选人编号不能超过64个字符")
    private String candidateNo;
    @NotBlank(message = "手机号不能为空")
    @Size(max = 32, message = "手机号不能超过32个字符")
    private String mobile;
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;
    @NotBlank(message = "部门不能为空")
    private String departId;
    @NotBlank(message = "岗位不能为空")
    private String positionId;
    @NotBlank(message = "招聘批次不能为空")
    @Size(max = 64, message = "招聘批次不能超过64个字符")
    private String batchNo;
    private Date validFrom;
    private Date expireAt;
}
