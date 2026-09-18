package com.yf.modules.exam.repo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
@Builder
@Schema(description = "试题导入校验预览")
public class QuestionImportPreviewRespDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Integer totalCount;
    private Integer validCount;
    private Integer duplicateCount;
    private Integer failureCount;
    private String templateVersion;
    private List<QuestionImportIssueDTO> issues;
    private List<WordQuestionPreviewDTO> questions;
}
