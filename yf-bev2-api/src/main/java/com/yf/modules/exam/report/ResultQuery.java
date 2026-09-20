package com.yf.modules.exam.report;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class ResultQuery {
    @Min(1) @Max(100000) private int current=1;
    @Min(1) @Max(100) private int size=10;
    @Size(max=128) private String keyword;
    @Size(max=128) private String title;
    @Size(max=64) private String departId;
    @Size(max=64) private String positionId;
    @Size(max=64) private String batchNo;
    @Pattern(regexp="CANDIDATE|EMPLOYEE|^$") private String subjectType;
    @Pattern(regexp="INTERVIEW|REGULARIZATION|PROMOTION|^$") private String sceneType;
    @Pattern(regexp="ALL|COMPLETED|PENDING") private String state="ALL";
    private Boolean passed;
    @DecimalMin("0") @DecimalMax("99999999") @Digits(integer=8,fraction=2) private BigDecimal scoreMin;
    @DecimalMin("0") @DecimalMax("99999999") @Digits(integer=8,fraction=2) private BigDecimal scoreMax;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss",timezone="Asia/Shanghai") private Date submittedFrom;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss",timezone="Asia/Shanghai") private Date submittedTo;
}
