package com.yf.system.modules.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@Schema(name = "员工注册审核请求", description = "管理员或 HR 审核员工注册申请")
public class UserRegistrationAuditReqDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "用户ID不能为空")
    @Schema(description = "待审核员工用户ID")
    private String userId;

    @NotBlank(message = "审核动作不能为空")
    @Pattern(regexp = "APPROVE|REJECT", message = "审核动作只允许 APPROVE 或 REJECT")
    @Schema(description = "审核动作：APPROVE 或 REJECT")
    private String action;

    @Size(max = 500, message = "审核意见不能超过500个字符")
    @Schema(description = "审核意见或驳回原因")
    private String remark;
}
