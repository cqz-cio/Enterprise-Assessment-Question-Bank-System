package com.yf.modules.exam.paper.service;
import com.yf.base.api.exception.ServiceException;
import com.yf.modules.exam.exam.dto.ExamRuleDTO;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class ObjectivePaperPolicyTest {
    @Test void shortQuestionsUseManualGradingButUnknownTypesAreRejected() {
        assertDoesNotThrow(() -> ObjectivePaperPolicy.validateRules(List.of(rule("short",1))));
        assertThrows(ServiceException.class, () -> ObjectivePaperPolicy.validateRules(List.of(rule("unknown",1))));
        assertThrows(ServiceException.class, () -> ObjectivePaperPolicy.requireObjective("short"));
    }
    @Test void emptyOrInvalidRulesCannotCreateEmptyOrNegativeScorePapers() {
        assertThrows(ServiceException.class, () -> ObjectivePaperPolicy.validateRules(List.of()));
        assertThrows(ServiceException.class, () -> ObjectivePaperPolicy.validateRules(List.of(rule("radio",0))));
        assertThrows(ServiceException.class, () -> ObjectivePaperPolicy.validateRules(List.of(rule("radio",-1))));
        var r=rule("radio",1); r.setQuScore(BigDecimal.ZERO);
        assertThrows(ServiceException.class, () -> ObjectivePaperPolicy.validateRules(List.of(r)));
    }
    @Test void zeroCountShortRuleDoesNotBlockObjectiveExams() {
        assertDoesNotThrow(() -> ObjectivePaperPolicy.validateRules(List.of(rule("radio",1),rule("short",0))));
    }
    @Test void duplicateTypeRulesAreRejected() {
        assertThrows(ServiceException.class, () -> ObjectivePaperPolicy.validateRules(List.of(rule("radio",1),rule("radio",1))));
    }
    private ExamRuleDTO rule(String type,int count) {
        var r=new ExamRuleDTO();r.setQuType(type);r.setQuCount(count);r.setQuScore(BigDecimal.ONE);return r;
    }
}
