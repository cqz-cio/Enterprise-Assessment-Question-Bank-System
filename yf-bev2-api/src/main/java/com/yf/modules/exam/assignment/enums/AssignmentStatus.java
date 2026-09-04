package com.yf.modules.exam.assignment.enums;

import java.util.Set;

public final class AssignmentStatus {

    public static final String ASSIGNED = "ASSIGNED";
    public static final String STARTED = "STARTED";
    public static final String PENDING_REVIEW = "PENDING_REVIEW";
    public static final String COMPLETED = "COMPLETED";
    public static final String EXPIRED = "EXPIRED";
    public static final String DISABLED = "DISABLED";

    private static final Set<String> ENTERABLE = Set.of(ASSIGNED, STARTED);

    private AssignmentStatus() {
    }

    public static boolean isEnterable(String status) {
        return ENTERABLE.contains(status);
    }
}
