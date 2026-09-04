package com.yf.modules.exam.paper.service;

import com.yf.base.api.exception.ServiceException;
import com.yf.modules.exam.paper.entity.Paper;
import com.yf.modules.exam.paper.mapper.PaperMapper;
import com.yf.modules.exam.assignment.mapper.ExamAssignmentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaperAccessServiceTest {

    private PaperMapper paperMapper;
    private PaperAccessService accessService;

    @BeforeEach
    void setUp() {
        paperMapper = mock(PaperMapper.class);
        accessService = new PaperAccessService(paperMapper, mock(ExamAssignmentMapper.class));
    }

    @Test
    void ownerCanAccessOwnPaper() {
        Paper paper = paper("paper-1", "user-a", 0, new Date(System.currentTimeMillis() + 60_000));
        when(paperMapper.selectById("paper-1")).thenReturn(paper);

        assertSame(paper, accessService.requireOwner("paper-1", "user-a"));
    }

    @Test
    void crossUserAccessIsRejectedWithoutDisclosingPaper() {
        when(paperMapper.selectById("paper-1"))
                .thenReturn(paper("paper-1", "user-b", 0, new Date(System.currentTimeMillis() + 60_000)));

        assertThrows(ServiceException.class, () -> accessService.requireOwner("paper-1", "user-a"));
        assertThrows(ServiceException.class, () -> accessService.requireOwner("missing", "user-a"));
    }

    @Test
    void submittedOrExpiredPaperCannotBeChanged() {
        when(paperMapper.selectById("submitted"))
                .thenReturn(paper("submitted", "user-a", 1, new Date(System.currentTimeMillis() + 60_000)));
        when(paperMapper.selectById("expired"))
                .thenReturn(paper("expired", "user-a", 0, new Date(System.currentTimeMillis() - 1_000)));

        assertThrows(ServiceException.class,
                () -> accessService.requireWritableOwner("submitted", "user-a"));
        assertThrows(ServiceException.class,
                () -> accessService.requireWritableOwner("expired", "user-a"));
    }

    private Paper paper(String id, String userId, int handState, Date limitTime) {
        Paper paper = new Paper();
        paper.setId(id);
        paper.setUserId(userId);
        paper.setHandState(handState);
        paper.setLimitTime(limitTime);
        return paper;
    }
}
