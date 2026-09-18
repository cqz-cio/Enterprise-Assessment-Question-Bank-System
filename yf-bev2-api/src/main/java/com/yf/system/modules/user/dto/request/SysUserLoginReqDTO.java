package com.yf.system.modules.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.*;

import java.io.Serializable;

/**
 * <p>
 * 管理员登录请求类
 * </p>
 *
 * @author 聪明笨狗
 * @since 2020-04-13 16:57
 */
@Data
@Schema(name = "管理员登录请求类", description = "管理员登录请求类")
public class SysUserLoginReqDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "用户名")
    @NotBlank(message="账号不能为空") @Size(max=255, message="账号过长")
    private String userName;

    @Schema(description = "密码")
    @NotBlank(message="密码不能为空") @Size(max=1024, message="密码过长")
    private String password;

    @Schema(description = "验证码key")
    @NotBlank(message="请刷新验证码") @Pattern(regexp="[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}", message="请刷新验证码")
    private String captchaKey;

    @Schema(description = "用户输入的验证码值")
    @NotBlank(message="请输入验证码") @Size(max=16, message="验证码格式不正确")
    private String captchaValue;

}
