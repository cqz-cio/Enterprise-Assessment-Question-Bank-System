package com.yf.modules.exam.grading.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class GradeFinalizeDTO {
    @NotBlank @Size(max=64) private String paperId;
    @NotNull @Min(0) private Long expectedVersion;
}
