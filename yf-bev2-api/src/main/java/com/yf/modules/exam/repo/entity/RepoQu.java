package com.yf.modules.exam.repo.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.Date;

/**
 * <p>
 * 问题题目实体类
 * </p>
 *
 * @author 聪明笨狗
 * @since 2025-04-11 09:42
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("el_repo_qu")
public class RepoQu extends Model<RepoQu> {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 所属题库
     */
    @TableField("repo_id")
    private String repoId;

    /**
     * 所属章节
     */
    @TableField("chapter_id")
    private String chapterId;

    /**
     * 外部题目编号
     */
    @TableField("external_code")
    private String externalCode;

    /**
     * 题目类型
     */
    @TableField("qu_type")
    private String quType;

    /**
     * 难度等级
     */
    @TableField("difficulty_level")
    private String difficultyLevel;

    /**
     * 题目内容
     */
    private String content;

    /**
     * 整题解析
     */
    private String analysis;

    /**
     * 启用状态
     */
    private Integer status;

    /**
     * 简答题参考答案
     */
    @TableField("reference_answer")
    private String referenceAnswer;

    /**
     * 简答题评分要点
     */
    @TableField("grading_criteria")
    private String gradingCriteria;

    /**
     * 题目标签
     */
    private String tags;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    /**
     * 创建人
     */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;

    /**
     * 修改人
     */
    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

}
