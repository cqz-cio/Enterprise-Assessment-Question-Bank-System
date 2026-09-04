package com.yf.system.modules.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>
 *
 * </p>
 *
 * @author 聪明笨狗
 * @since 2020-04-13 16:57
 */
@Data
@Schema(name = "用户注册请求类", description = "用户注册请求类")
public class UserRegReqDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "帐号")
    @NotBlank(message = "账号不能为空")
    @Size(max = 255, message = "账号长度不能超过255个字符")
    private String userName;

    @Schema(description = "密码")
    @NotBlank(message = "密码不能为空")
    private String password;

    @Schema(description = "姓名")
    @NotBlank(message = "姓名不能为空")
    @Size(max = 255, message = "姓名长度不能超过255个字符")
    private String realName;

    @Schema(description = "部门")
    @NotBlank(message = "部门不能为空")
    private String deptCode;

    @Schema(description = "员工工号")
    @NotBlank(message = "员工工号不能为空")
    @Size(max = 64, message = "员工工号长度不能超过64个字符")
    private String employeeNo;

    @Schema(description = "手机号（选填）")
    @Pattern(regexp = "^$|^[0-9+() -]{6,32}$", message = "手机号格式不正确")
    private String mobile;

    @Schema(description = "邮箱（选填）")
    @Email(message = "邮箱格式不正确")
    @Size(max = 255, message = "邮箱长度不能超过255个字符")
    private String email;

    @Schema(description = "验证码KEY")
    @NotBlank(message = "验证码KEY不能为空")
    private String captchaKey;

    @Schema(description = "验证码值")
    @NotBlank(message = "验证码不能为空")
    private String captchaValue;


}
