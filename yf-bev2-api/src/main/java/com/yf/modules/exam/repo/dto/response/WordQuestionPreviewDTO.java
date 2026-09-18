package com.yf.modules.exam.repo.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class WordQuestionPreviewDTO {
    private Integer paragraph;
    private String questionCode;
    private String questionType;
    private String content;
    private List<String> options;
    private String answer;
    private String difficulty;
    private String explanation;
    private String gradingCriteria;
    private String status;
}
