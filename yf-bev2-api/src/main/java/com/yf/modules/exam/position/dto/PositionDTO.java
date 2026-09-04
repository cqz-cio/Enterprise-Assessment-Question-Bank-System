package com.yf.modules.exam.position.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;

@Data
public class PositionDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;

    @NotBlank(message = "岗位编码不能为空")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$", message = "岗位编码必须以大写字母开头，仅包含大写字母、数字和下划线")
    private String code;

    @NotBlank(message = "岗位名称不能为空")
    @Size(max = 128, message = "岗位名称不能超过128个字符")
    private String name;

    @NotNull(message = "岗位状态不能为空")
    @Min(value = 0, message = "岗位状态无效")
    @Max(value = 1, message = "岗位状态无效")
    private Integer status;

    @NotNull(message = "岗位排序不能为空")
    private Integer sort;

    @Size(max = 500, message = "岗位备注不能超过500个字符")
    private String remark;
    @NotEmpty(message = "至少选择一个所属部门")
    private List<String> departmentIds = new ArrayList<>();
    private List<String> departmentNames = new ArrayList<>();
    @Valid
    private List<PositionGradeDTO> grades = new ArrayList<>();
    private Date createTime;
    private Date updateTime;
    private String createBy;
    private String updateBy;
}
