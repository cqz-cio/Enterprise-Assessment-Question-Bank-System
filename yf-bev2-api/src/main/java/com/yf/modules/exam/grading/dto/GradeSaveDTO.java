package com.yf.modules.exam.grading.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class GradeSaveDTO {
    @NotBlank @Size(max=64) private String paperQuId;
    @NotNull @DecimalMin("0") @Digits(integer=8,fraction=2) private BigDecimal score;
    @Size(max=2000) private String comment;
    @NotNull @Min(0) private Long expectedVersion;
}
