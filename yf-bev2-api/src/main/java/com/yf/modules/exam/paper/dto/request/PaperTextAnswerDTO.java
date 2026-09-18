package com.yf.modules.exam.paper.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class PaperTextAnswerDTO {
    @NotBlank @Size(max=64) private String paperId;
    @NotBlank @Size(max=64) private String quId;
    @NotNull @Size(max=5000) private String answerText;
}
