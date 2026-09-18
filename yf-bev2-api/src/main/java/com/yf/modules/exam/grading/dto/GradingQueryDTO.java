package com.yf.modules.exam.grading.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

@Data
public class GradingQueryDTO {
    @Min(1) @Max(100000) private int current=1;
    @Min(1) @Max(100) private int size=10;
    @Size(max=128) private String keyword;
    @Size(max=64) private String positionId;
    @Size(max=64) private String departId;
    @Size(max=64) private String batchNo;
    @Size(max=64) private String examId;
    @Size(max=64) private String graderId;
    @Pattern(regexp="PENDING|GRADED") private String state="PENDING";
    @Pattern(regexp="INTERVIEW|REGULARIZATION|PROMOTION|^$") private String sceneType;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss",timezone="Asia/Shanghai") private Date submittedFrom;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss",timezone="Asia/Shanghai") private Date submittedTo;
}
