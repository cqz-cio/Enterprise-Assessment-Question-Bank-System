package com.yf.modules.exam.paper.service;

import com.yf.base.api.exception.ServiceException;
import com.yf.modules.exam.exam.dto.ExamRuleDTO;
import java.util.List;
import java.util.Set;

/** Supported paper rules and the separate objective-answer boundary. */
public final class ObjectivePaperPolicy {
    private static final Set<String> TYPES = Set.of("radio", "multi", "judge");
    private ObjectivePaperPolicy() { }

    public static boolean isObjective(String type) {
        return type != null && TYPES.contains(type);
    }

    public static void requireObjective(String type) {
        if (!isObjective(type)) {
            throw new ServiceException("此接口只接受单选、多选和判断题！");
        }
    }

    public static void requireSupported(String type) {
        if (!isObjective(type) && !"short".equals(type)) throw new ServiceException("不支持的题型！");
    }

    public static void validateRules(List<ExamRuleDTO> rules) {
        if (rules == null || rules.isEmpty()) {
            throw new ServiceException("请先配置有效的组卷规则！");
        }
        boolean hasQuestions = false;
        Set<String> types = new java.util.HashSet<>();
        for (ExamRuleDTO rule : rules) {
            if (rule == null || rule.getQuCount() == null || rule.getQuCount() < 0) {
                throw new ServiceException("组卷题量必须为非负整数！");
            }
            if (rule.getQuCount() == 0) continue;
            requireSupported(rule.getQuType());
            if (!types.add(rule.getQuType())) throw new ServiceException("同一题型不能重复配置组卷规则！");
            if (rule.getQuScore() == null || rule.getQuScore().signum() <= 0) {
                throw new ServiceException("每题分值必须大于零！");
            }
            hasQuestions = true;
        }
        if (!hasQuestions) throw new ServiceException("试卷至少需要一道试题！");
    }
}
