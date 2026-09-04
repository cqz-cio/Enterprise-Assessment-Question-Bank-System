package com.yf.modules.exam.exam.dto.response;

import com.yf.base.api.annon.Dict;
import com.yf.modules.exam.exam.dto.ExamRecordDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@Schema(description = "员工端考试记录")
public class ExamRecordClientRespDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Dict(dictTable = "el_exam", dicText = "title", dicCode = "id")
    @Schema(description = "考试ID")
    private String examId;

    @Schema(description = "是否通过")
    private Boolean passed;

    public static ExamRecordClientRespDTO from(ExamRecordDTO source) {
        ExamRecordClientRespDTO response = new ExamRecordClientRespDTO();
        response.setExamId(source.getExamId());
        response.setPassed(source.getPassed());
        return response;
    }
}
