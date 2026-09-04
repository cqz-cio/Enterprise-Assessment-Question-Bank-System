package com.yf.modules.exam.assignment.controller;

import com.yf.base.api.api.dto.BaseIdReqDTO;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExamAssignmentPermissionAnnotationTest {

    @Test
    void candidateResultDetailRequiresDedicatedPermission() throws Exception {
        Method method = ExamAssignmentController.class
                .getMethod("candidateResultDetail", BaseIdReqDTO.class);
        RequiresPermissions annotation = method.getAnnotation(RequiresPermissions.class);

        assertNotNull(annotation);
        assertArrayEquals(new String[]{"exam:assignment:candidate:result"}, annotation.value());
    }
}
