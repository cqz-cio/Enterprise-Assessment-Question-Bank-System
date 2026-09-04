package com.yf.modules.exam.position.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class PositionGradeDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private String id;
    @NotBlank(message = "职级编码不能为空")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$", message = "职级编码必须以大写字母开头，仅包含大写字母、数字和下划线")
    private String code;
    @NotBlank(message = "职级名称不能为空")
    @Size(max = 128, message = "职级名称不能超过128个字符")
    private String name;
    private Integer levelNo;
    private Integer sort;
    private Integer status;
}
