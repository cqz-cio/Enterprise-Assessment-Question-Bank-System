package com.yf.modules.exam.common;

import java.util.Set;

public final class AssessmentScene {

    public static final String INTERVIEW = "INTERVIEW";
    public static final String REGULARIZATION = "REGULARIZATION";
    public static final String PROMOTION = "PROMOTION";
    private static final Set<String> VALUES = Set.of(INTERVIEW, REGULARIZATION, PROMOTION);

    private AssessmentScene() {
    }

    public static boolean isValid(String value) {
        return VALUES.contains(value);
    }
}
