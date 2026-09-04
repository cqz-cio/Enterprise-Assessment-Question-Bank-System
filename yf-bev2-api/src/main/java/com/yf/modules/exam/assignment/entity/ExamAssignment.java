package com.yf.modules.exam.assignment.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("el_exam_assignment")
public class ExamAssignment extends Model<ExamAssignment> {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;
    @TableField("exam_id")
    private String examId;
    @TableField("user_id")
    private String userId;
    @TableField("subject_type")
    private String subjectType;
    @TableField("subject_name")
    private String subjectName;
    @TableField("candidate_no")
    private String candidateNo;
    private String mobile;
    private String email;
    @TableField("depart_id")
    private String departId;
    @TableField("position_id")
    private String positionId;
    @TableField("target_grade_id")
    private String targetGradeId;
    @TableField("batch_no")
    private String batchNo;
    @TableField("access_code_lookup")
    private String accessCodeLookup;
    @TableField("access_code_hash")
    private String accessCodeHash;
    @TableField("valid_from")
    private Date validFrom;
    @TableField("expire_at")
    private Date expireAt;
    private String status;
    @TableField("paper_id")
    private String paperId;
    @TableField("activated_at")
    private Date activatedAt;
    @TableField("submitted_at")
    private Date submittedAt;
    @TableField("completed_at")
    private Date completedAt;
    @TableField("disabled_reason")
    private String disabledReason;
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Date createTime;
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    private Date updateTime;
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    private String updateBy;
}
