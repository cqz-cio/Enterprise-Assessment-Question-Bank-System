package com.yf.modules.exam.repo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
@Schema(description = "试题导入逐行问题")
public class QuestionImportIssueDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "Excel 原始行号或 Word 题目起始段落号")
    private Integer rowNumber;

    @Schema(description = "题目编号")
    private String questionCode;

    @Schema(description = "题干摘要")
    private String content;

    @Schema(description = "问题字段")
    private String field;

    @Schema(description = "问题原因")
    private String message;

    @Schema(description = "ERROR 或 DUPLICATE")
    private String issueType;
}
