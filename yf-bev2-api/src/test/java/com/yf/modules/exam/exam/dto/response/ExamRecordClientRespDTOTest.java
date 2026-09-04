package com.yf.modules.exam.exam.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yf.modules.exam.exam.dto.ExamRecordDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExamRecordClientRespDTOTest {

    @Test
    void responseOnlyExposesExamAndPassResult() throws Exception {
        ExamRecordDTO source = new ExamRecordDTO();
        source.setExamId("exam-1");
        source.setPassed(true);
        source.setPaperId("paper-secret");
        source.setTryCount(3);
        source.setMaxScore(new BigDecimal("99"));
        source.setLastScore(new BigDecimal("88"));

        ExamRecordClientRespDTO response = ExamRecordClientRespDTO.from(source);
        String json = new ObjectMapper().writeValueAsString(response);

        assertEquals("exam-1", response.getExamId());
        assertTrue(response.getPassed());
        assertFalse(json.contains("paperId"));
        assertFalse(json.contains("tryCount"));
        assertFalse(json.contains("maxScore"));
        assertFalse(json.contains("lastScore"));
    }
}
