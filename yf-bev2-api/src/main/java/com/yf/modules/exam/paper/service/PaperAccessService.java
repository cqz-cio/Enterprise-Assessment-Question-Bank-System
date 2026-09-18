package com.yf.modules.exam.paper.service;

import com.yf.base.api.exception.ServiceException;
import com.yf.modules.exam.paper.entity.Paper;
import com.yf.modules.exam.paper.mapper.PaperMapper;
import com.yf.modules.exam.assignment.entity.ExamAssignment;
import com.yf.modules.exam.assignment.enums.AssignmentStatus;
import com.yf.modules.exam.assignment.mapper.ExamAssignmentMapper;
import org.apache.commons.lang3.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * 试卷对象级访问控制。所有考生侧接口统一从这里校验当前身份，
 * 避免只依赖前端隐藏或客户端传入的 userId。
 */
@Service
@RequiredArgsConstructor
public class PaperAccessService {

    private final PaperMapper paperMapper;
    private final ExamAssignmentMapper assignmentMapper;

    public Paper requireOwner(String paperId, String userId) {
        Paper paper = paperMapper.selectById(paperId);
        if (paper == null || userId == null || !userId.equals(paper.getUserId())) {
            throw new ServiceException("无权访问该试卷！");
        }
        return paper;
    }

    /** Call only inside the caller's transaction. Always lock assignment before paper. */
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Paper lockForUpdate(String paperId) {
        Paper observed = paperMapper.selectById(paperId);
        if (observed == null) throw new ServiceException("试卷不存在或无权访问！");
        if (StringUtils.isNotBlank(observed.getAssignmentId())) {
            ExamAssignment assignment = assignmentMapper.selectByIdForUpdate(observed.getAssignmentId());
            if (assignment == null || !observed.getUserId().equals(assignment.getUserId())
                    || !paperId.equals(assignment.getPaperId())) {
                throw new ServiceException("考核试卷关联异常！");
            }
        }
        Paper locked = paperMapper.selectByIdForUpdate(paperId);
        if (locked == null || !java.util.Objects.equals(observed.getAssignmentId(), locked.getAssignmentId())) {
            throw new ServiceException("考核试卷关联异常！");
        }
        return locked;
    }

    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Paper requireLockedWritableOwner(String paperId, String userId) {
        Paper paper = lockForUpdate(paperId);
        if (userId == null || !userId.equals(paper.getUserId())) throw new ServiceException("无权访问该试卷！");
        return validateWritable(paper);
    }

    public Paper requireWritableOwner(String paperId, String userId) {
        return validateWritable(requireOwner(paperId, userId));
    }

    private Paper validateWritable(Paper paper) {
        if (paper.getHandState() != null && paper.getHandState() != 0) {
            throw new ServiceException("试卷已交卷，不能继续修改！");
        }
        if (paper.getLimitTime() != null && !paper.getLimitTime().after(new Date())) {
            throw new ServiceException("考试已结束，不能继续修改！");
        }
        if (StringUtils.isNotBlank(paper.getAssignmentId())) {
            ExamAssignment assignment = assignmentMapper.selectById(paper.getAssignmentId());
            if (assignment == null || !paper.getUserId().equals(assignment.getUserId())
                    || !paper.getId().equals(assignment.getPaperId())
                    || (assignment.getValidFrom() != null && assignment.getValidFrom().after(new Date()))
                    || !AssignmentStatus.STARTED.equals(assignment.getStatus())
                    || assignment.getExpireAt() == null || !assignment.getExpireAt().after(new Date())) {
                throw new ServiceException("考核当前不可作答！");
            }
        }
        return paper;
    }
}
