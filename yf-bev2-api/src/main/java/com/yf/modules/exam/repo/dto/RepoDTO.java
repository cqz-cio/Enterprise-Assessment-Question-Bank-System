package com.yf.modules.exam.repo.dto;

import com.yf.base.api.annon.Dict;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * <p>
 * 题库数据传输类
 * </p>
 *
 * @author 聪明笨狗
 * @since 2025-04-11 09:42
 */
@Data
@Schema(description = "题库")
public class RepoDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;


    @Schema(description = "ID")
    private String id;

    @Schema(description = "题库名称")
    private String title;

    @Dict(dicCode = "repo_catalog")
    @Schema(description = "分类ID")
    private String catId;

    @Dict(dictTable = "el_sys_depart", dicText = "dept_name", dicCode = "id")
    @Schema(description = "部门ID")
    private String departId;

    @Dict(dictTable = "el_position", dicText = "name", dicCode = "id")
    @Schema(description = "岗位ID")
    private String positionId;

    @Schema(description = "考核场景：INTERVIEW/REGULARIZATION/PROMOTION")
    private String sceneType;

    @Dict(dictTable = "el_position_grade", dicText = "name", dicCode = "id")
    @Schema(description = "晋升目标职级ID，可选")
    private String targetGradeId;

    @Schema(description = "启用状态：1启用，0停用")
    private Integer status;

    @Schema(description = "题库备注")
    private String remark;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新时间")
    private Date updateTime;

    @Dict(dictTable = "el_sys_user", dicText = "real_name", dicCode = "id")
    @Schema(description = "创建人")
    private String createBy;

    @Dict(dictTable = "el_sys_user", dicText = "real_name", dicCode = "id")
    @Schema(description = "修改人")
    private String updateBy;

}
