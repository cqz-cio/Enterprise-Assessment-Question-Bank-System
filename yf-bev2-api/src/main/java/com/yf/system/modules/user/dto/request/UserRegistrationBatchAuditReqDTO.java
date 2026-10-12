package com.yf.system.modules.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class UserRegistrationBatchAuditReqDTO {
    @NotEmpty @Size(max = 100)
    private List<@NotBlank @Size(max = 64) String> ids;
    @NotBlank @Pattern(regexp = "APPROVE|REJECT")
    private String action;
    @Size(max = 500)
    private String remark;
}
