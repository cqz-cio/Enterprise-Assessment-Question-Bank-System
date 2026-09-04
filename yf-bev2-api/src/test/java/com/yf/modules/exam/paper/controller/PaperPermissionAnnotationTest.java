package com.yf.modules.exam.paper.controller;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PaperPermissionAnnotationTest {

    @Test
    void managementPaperEndpointsRequireRecordPermission() throws Exception {
        assertPermission(PaperController.class.getMethod("paging", com.yf.base.api.api.dto.PagingReqDTO.class),
                "exam:record:list");
        assertPermission(PaperController.class.getMethod("fullDetail", com.yf.base.api.api.dto.BaseIdReqDTO.class),
                "exam:record:list");
    }

    @Test
    void candidateFacingPaperEndpointsRequireEnterPermission() throws Exception {
        assertPermission(PaperController.class.getMethod("detail", com.yf.base.api.api.dto.BaseIdReqDTO.class),
                "exam:client:enter");
        assertPermission(PaperController.class.getMethod("hand", com.yf.base.api.api.dto.BaseIdReqDTO.class),
                "exam:client:enter");
        assertPermission(PaperQuController.class.getMethod("fillAnswer",
                com.yf.modules.exam.paper.dto.request.PaperQuFillReqDTO.class), "exam:client:enter");
    }

    private void assertPermission(Method method, String expected) {
        RequiresPermissions annotation = method.getAnnotation(RequiresPermissions.class);
        assertNotNull(annotation);
        assertArrayEquals(new String[]{expected}, annotation.value());
    }
}
