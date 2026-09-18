package com.yf.modules.exam.repo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
@Builder
@Schema(description = "试题导入结果")
public class QuestionImportResultRespDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Integer totalCount;
    private Integer successCount;
    private Integer duplicateCount;
    private Integer failureCount;
    private String templateVersion;
    private List<QuestionImportIssueDTO> issues;
    @Schema(description = "本次导入错误报告 XLSX 的 Base64，无问题行时为空")
    private String errorReportBase64;
}
