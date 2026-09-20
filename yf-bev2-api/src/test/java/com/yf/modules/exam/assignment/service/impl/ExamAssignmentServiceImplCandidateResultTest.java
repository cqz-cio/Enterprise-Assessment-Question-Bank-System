package com.yf.modules.exam.assignment.service.impl;

import com.yf.base.api.exception.ServiceException;
import com.yf.modules.exam.assignment.entity.ExamAssignment;
import com.yf.modules.exam.assignment.enums.AssignmentStatus;
import com.yf.modules.exam.assignment.mapper.ExamAssignmentMapper;
import com.yf.modules.exam.assignment.service.AccessCodeManager;
import com.yf.modules.exam.exam.service.ExamService;
import com.yf.modules.exam.paper.dto.response.PaperDetailRespDTO;
import com.yf.modules.exam.paper.entity.Paper;
import com.yf.modules.exam.paper.service.PaperService;
import com.yf.modules.exam.position.service.PositionService;
import com.yf.system.modules.user.enums.SysRoleId;
import com.yf.system.modules.user.service.SysUserRoleService;
import com.yf.system.modules.user.service.SysUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExamAssignmentServiceImplCandidateResultTest {

    private ExamAssignmentMapper assignmentMapper;
    private PaperService paperService;
    private ExamAssignmentServiceImpl service;

    @BeforeEach
    void setUp() {
        assignmentMapper = mock(ExamAssignmentMapper.class);
        paperService = mock(PaperService.class);
        service = new ExamAssignmentServiceImpl(
                mock(PositionService.class), mock(ExamService.class), mock(SysUserService.class),
                mock(SysUserRoleService.class), paperService, mock(AccessCodeManager.class),mock(com.yf.modules.exam.assignment.importing.CandidateIdentityGuard.class));
        ReflectionTestUtils.setField(service, "baseMapper", assignmentMapper);
    }

    @Test
    void completedCandidateResultReturnsItsBoundPaperDetail() {
        ExamAssignment assignment = completedAssignment();
        Paper paper = handedPaper("assignment-1", "candidate-1");
        PaperDetailRespDTO detail = new PaperDetailRespDTO();
        detail.setId("paper-1");
        when(assignmentMapper.selectById("assignment-1")).thenReturn(assignment);
        when(paperService.getById("paper-1")).thenReturn(paper);
        when(paperService.fullDetail("paper-1")).thenReturn(detail);

        assertSame(detail, service.candidateResultDetail("assignment-1"));
    }

    @Test
    void unfinishedCandidateResultIsRejected() {
        ExamAssignment assignment = completedAssignment();
        assignment.setStatus(AssignmentStatus.STARTED);
        when(assignmentMapper.selectById("assignment-1")).thenReturn(assignment);

        assertThrows(ServiceException.class,
                () -> service.candidateResultDetail("assignment-1"));
        verify(paperService, never()).fullDetail(any());
    }

    @Test
    void paperFromAnotherAssignmentIsRejected() {
        when(assignmentMapper.selectById("assignment-1")).thenReturn(completedAssignment());
        when(paperService.getById("paper-1"))
                .thenReturn(handedPaper("assignment-other", "candidate-1"));

        assertThrows(ServiceException.class,
                () -> service.candidateResultDetail("assignment-1"));
        verify(paperService, never()).fullDetail(any());
    }

    private ExamAssignment completedAssignment() {
        ExamAssignment assignment = new ExamAssignment();
        assignment.setId("assignment-1");
        assignment.setUserId("candidate-1");
        assignment.setSubjectType(SysRoleId.CANDIDATE);
        assignment.setStatus(AssignmentStatus.COMPLETED);
        assignment.setPaperId("paper-1");
        return assignment;
    }

    private Paper handedPaper(String assignmentId, String userId) {
        Paper paper = new Paper();
        paper.setId("paper-1");
        paper.setAssignmentId(assignmentId);
        paper.setUserId(userId);
        paper.setHandState(1);
        return paper;
    }
}
