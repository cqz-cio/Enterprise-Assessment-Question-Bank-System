package com.yf.modules.exam.assignment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yf.ability.shiro.dto.SysUserLoginDTO;
import com.yf.base.api.api.dto.PagingReqDTO;
import com.yf.base.api.exception.ServiceException;
import com.yf.base.utils.passwd.PassHandler;
import com.yf.base.utils.passwd.PassInfo;
import com.yf.modules.exam.assignment.dto.request.AssignmentQueryReqDTO;
import com.yf.modules.exam.assignment.dto.request.AssignmentStatusReqDTO;
import com.yf.modules.exam.assignment.dto.request.CandidateCreateReqDTO;
import com.yf.modules.exam.assignment.dto.request.CandidateVerifyReqDTO;
import com.yf.modules.exam.assignment.dto.response.AssignmentCurrentRespDTO;
import com.yf.modules.exam.assignment.dto.response.AssignmentListRespDTO;
import com.yf.modules.exam.assignment.dto.response.AssignmentResultRespDTO;
import com.yf.modules.exam.assignment.dto.response.AssignmentStartRespDTO;
import com.yf.modules.exam.assignment.dto.response.CandidateCreateRespDTO;
import com.yf.modules.exam.assignment.dto.response.CandidateVerifyRespDTO;
import com.yf.modules.exam.assignment.entity.ExamAssignment;
import com.yf.modules.exam.assignment.enums.AssignmentStatus;
import com.yf.modules.exam.assignment.mapper.ExamAssignmentMapper;
import com.yf.modules.exam.assignment.service.AccessCodeManager;
import com.yf.modules.exam.assignment.service.ExamAssignmentService;
import com.yf.modules.exam.common.AssessmentScene;
import com.yf.modules.exam.exam.entity.Exam;
import com.yf.modules.exam.exam.service.ExamService;
import com.yf.modules.exam.paper.entity.Paper;
import com.yf.modules.exam.paper.dto.response.PaperDetailRespDTO;
import com.yf.modules.exam.paper.service.PaperService;
import com.yf.modules.exam.position.entity.Position;
import com.yf.modules.exam.position.service.PositionService;
import com.yf.system.modules.user.entity.SysUser;
import com.yf.system.modules.user.enums.SysRoleId;
import com.yf.system.modules.user.enums.UserState;
import com.yf.system.modules.user.service.SysUserRoleService;
import com.yf.system.modules.user.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExamAssignmentServiceImpl extends ServiceImpl<ExamAssignmentMapper, ExamAssignment>
        implements ExamAssignmentService {

    private static final Duration DEFAULT_VALIDITY = Duration.ofDays(14);

    private final PositionService positionService;
    private final ExamService examService;
    private final SysUserService sysUserService;
    private final SysUserRoleService sysUserRoleService;
    private final PaperService paperService;
    private final AccessCodeManager accessCodeManager;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CandidateCreateRespDTO createCandidate(CandidateCreateReqDTO reqDTO) {
        Position position = positionService.requireEnabled(reqDTO.getPositionId());
        positionService.requireDepartmentPosition(reqDTO.getDepartId(), position.getId());
        Exam exam = examService.getOne(new LambdaQueryWrapper<Exam>()
                .eq(Exam::getDepartId, reqDTO.getDepartId())
                .eq(Exam::getPositionId, position.getId())
                .eq(Exam::getSceneType, AssessmentScene.INTERVIEW)
                .eq(Exam::getTemplateStatus, 1), false);
        if (exam == null) {
            throw new ServiceException("该岗位未配置启用的入职/面试考核模板！");
        }

        Date validFrom = reqDTO.getValidFrom() == null ? new Date() : reqDTO.getValidFrom();
        Date expireAt = reqDTO.getExpireAt() == null
                ? new Date(validFrom.getTime() + DEFAULT_VALIDITY.toMillis()) : reqDTO.getExpireAt();
        if (!expireAt.after(validFrom)) {
            throw new ServiceException("截止时间必须晚于可进入时间！");
        }

        String assignmentId = IdWorker.getIdStr();
        SysUser candidate = buildCandidateUser(assignmentId, reqDTO);
        sysUserService.save(candidate);
        sysUserRoleService.saveRoles(candidate.getId(), List.of(SysRoleId.CANDIDATE), false);

        String code = generateUniqueCode();
        ExamAssignment assignment = new ExamAssignment();
        assignment.setId(assignmentId);
        assignment.setExamId(exam.getId());
        assignment.setUserId(candidate.getId());
        assignment.setSubjectType(SysRoleId.CANDIDATE);
        assignment.setSubjectName(StringUtils.trim(reqDTO.getCandidateName()));
        assignment.setCandidateNo(StringUtils.trim(reqDTO.getCandidateNo()));
        assignment.setMobile(StringUtils.trim(reqDTO.getMobile()));
        assignment.setEmail(StringUtils.trim(reqDTO.getEmail()));
        assignment.setDepartId(reqDTO.getDepartId());
        assignment.setPositionId(position.getId());
        assignment.setBatchNo(StringUtils.trim(reqDTO.getBatchNo()));
        assignment.setAccessCodeLookup(accessCodeManager.lookup(code));
        assignment.setAccessCodeHash(accessCodeManager.hash(code));
        assignment.setValidFrom(validFrom);
        assignment.setExpireAt(expireAt);
        assignment.setStatus(AssignmentStatus.ASSIGNED);
        save(assignment);

        return createResponse(assignment, position, exam, code);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CandidateVerifyRespDTO verifyCandidate(CandidateVerifyReqDTO reqDTO) {
        String code = accessCodeManager.normalize(reqDTO.getAccessCode());
        ExamAssignment assignment = getOne(new LambdaQueryWrapper<ExamAssignment>()
                .eq(ExamAssignment::getAccessCodeLookup, accessCodeManager.lookup(code))
                .eq(ExamAssignment::getSubjectType, SysRoleId.CANDIDATE), false);
        if (assignment == null
                || !StringUtils.trim(reqDTO.getCandidateName()).equals(assignment.getSubjectName())
                || !accessCodeManager.matches(code, assignment.getAccessCodeHash())) {
            throw credentialError();
        }

        assignment = lockCandidate(assignment.getId());
        if (!StringUtils.trim(reqDTO.getCandidateName()).equals(assignment.getSubjectName())
                || !accessCodeManager.matches(code, assignment.getAccessCodeHash())) throw credentialError();
        Date now = new Date();
        boolean resultOnly = AssignmentStatus.COMPLETED.equals(assignment.getStatus())
                || AssignmentStatus.PENDING_REVIEW.equals(assignment.getStatus());
        if (resultOnly) {
            Paper paper = StringUtils.isBlank(assignment.getPaperId()) ? null : paperService.getById(assignment.getPaperId());
            if (assignment.getExpireAt() == null || !assignment.getExpireAt().after(now)
                    || paper == null || !Integer.valueOf(1).equals(paper.getHandState())
                    || !assignment.getId().equals(paper.getAssignmentId()) || !assignment.getUserId().equals(paper.getUserId())) {
                throw credentialError();
            }
        } else ensureEnterable(assignment, now);
        if (AssignmentStatus.ASSIGNED.equals(assignment.getStatus())) {
            assignment.setStatus(AssignmentStatus.STARTED);
            assignment.setActivatedAt(now);
            updateById(assignment);
        }

        SysUserLoginDTO session = sysUserService.loginCandidate(assignment.getUserId(), assignment.getExpireAt());
        Position position = positionService.getById(assignment.getPositionId());
        Exam exam = examService.getById(assignment.getExamId());
        return CandidateVerifyRespDTO.builder()
                .token(session.getToken())
                .assignmentId(assignment.getId())
                .examId(assignment.getExamId())
                .paperId(assignment.getPaperId())
                .candidateName(assignment.getSubjectName())
                .positionName(position == null ? null : position.getName())
                .examTitle(exam == null ? null : exam.getTitle())
                .validFrom(assignment.getValidFrom())
                .expireAt(assignment.getExpireAt())
                .assignmentStatus(assignment.getStatus())
                .build();
    }

    @Override
    public IPage<AssignmentListRespDTO> candidatePaging(PagingReqDTO<AssignmentQueryReqDTO> reqDTO) {
        return baseMapper.paging(reqDTO.toPage(), reqDTO.getParams());
    }

    @Override
    public PaperDetailRespDTO candidateResultDetail(String id) {
        ExamAssignment assignment = getById(id);
        if (assignment == null || !SysRoleId.CANDIDATE.equals(assignment.getSubjectType())) {
            throw new ServiceException("候选人考核不存在或无权访问！");
        }
        if (!AssignmentStatus.COMPLETED.equals(assignment.getStatus())
                || StringUtils.isBlank(assignment.getPaperId())) {
            throw new ServiceException("考核结果尚未形成！");
        }

        Paper paper = paperService.getById(assignment.getPaperId());
        if (paper == null
                || !assignment.getId().equals(paper.getAssignmentId())
                || !assignment.getUserId().equals(paper.getUserId())
                || !Integer.valueOf(1).equals(paper.getHandState())
                || "PENDING".equals(paper.getGradingState())) {
            throw new ServiceException("候选人考核试卷状态异常！");
        }

        PaperDetailRespDTO detail = paperService.fullDetail(paper.getId());
        if (detail == null) {
            throw new ServiceException("候选人考核试卷不存在！");
        }
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CandidateCreateRespDTO resetCode(String id) {
        ExamAssignment assignment = lockCandidate(id);
        ensureEnterable(assignment, new Date());
        String code = generateUniqueCode();
        assignment.setAccessCodeLookup(accessCodeManager.lookup(code));
        assignment.setAccessCodeHash(accessCodeManager.hash(code));
        updateById(assignment);
        sysUserService.invalidateSessions(List.of(assignment.getUserId()));
        return createResponse(assignment, positionService.getById(assignment.getPositionId()),
                examService.getById(assignment.getExamId()), code);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(AssignmentStatusReqDTO reqDTO) {
        ExamAssignment assignment = lockCandidate(reqDTO.getId());
        String action = reqDTO.getAction().trim().toUpperCase(Locale.ROOT);
        if ("DISABLE".equals(action)) {
            if (AssignmentStatus.COMPLETED.equals(assignment.getStatus())
                    || AssignmentStatus.EXPIRED.equals(assignment.getStatus())) {
                throw new ServiceException("已完成或已过期的考核不能停用！");
            }
            assignment.setStatus(AssignmentStatus.DISABLED);
            assignment.setDisabledReason(StringUtils.trimToNull(reqDTO.getReason()));
        } else if ("ENABLE".equals(action)) {
            if (!AssignmentStatus.DISABLED.equals(assignment.getStatus())) {
                throw new ServiceException("只有已停用的考核可以恢复！");
            }
            if (!assignment.getExpireAt().after(new Date())) {
                assignment.setStatus(AssignmentStatus.EXPIRED);
                updateById(assignment);
                throw new ServiceException("考核已过期，不能恢复！");
            }
            Paper paper = StringUtils.isBlank(assignment.getPaperId())
                    ? null : paperService.getById(assignment.getPaperId());
            if (paper != null && Integer.valueOf(1).equals(paper.getHandState())) {
                throw new ServiceException("考核已完成，不能恢复！");
            }
            assignment.setStatus(paper == null ? AssignmentStatus.ASSIGNED : AssignmentStatus.STARTED);
            assignment.setDisabledReason(null);
        } else {
            throw new ServiceException("操作只允许 DISABLE 或 ENABLE！");
        }
        updateById(assignment);
        sysUserService.invalidateSessions(List.of(assignment.getUserId()));
    }

    @Override
    public AssignmentCurrentRespDTO current(String id, String userId) {
        ExamAssignment assignment = requireOwner(id, userId);
        Position position = positionService.getById(assignment.getPositionId());
        Exam exam = examService.getById(assignment.getExamId());
        return AssignmentCurrentRespDTO.builder()
                .assignmentId(assignment.getId())
                .examId(assignment.getExamId())
                .paperId(assignment.getPaperId())
                .subjectName(assignment.getSubjectName())
                .positionName(position == null ? null : position.getName())
                .examTitle(exam == null ? null : exam.getTitle())
                .validFrom(assignment.getValidFrom())
                .expireAt(assignment.getExpireAt())
                .status(effectiveStatus(assignment))
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssignmentStartRespDTO start(String id, String userId) {
        ExamAssignment assignment = lock(id);
        if (!userId.equals(assignment.getUserId())) {
            throw new ServiceException("考核信息不存在或无权访问！");
        }
        ensureEnterable(assignment, new Date());
        if (StringUtils.isNotBlank(assignment.getPaperId())) {
            Paper existing = paperService.getById(assignment.getPaperId());
            if (existing == null || !userId.equals(existing.getUserId())) {
                throw new ServiceException("考核试卷状态异常，请联系管理员！");
            }
            if (!Integer.valueOf(0).equals(existing.getHandState())) {
                throw new ServiceException("考核已完成，不能重复进入！");
            }
            if (existing.getLimitTime() == null || !existing.getLimitTime().after(new Date())) {
                throw new ServiceException("试卷已到期，不能继续作答，结果正在结算！");
            }
            return new AssignmentStartRespDTO(existing.getId(), true);
        }

        String paperId = paperService.createPaperForAssignment(assignment);
        assignment.setPaperId(paperId);
        assignment.setStatus(AssignmentStatus.STARTED);
        if (assignment.getActivatedAt() == null) {
            assignment.setActivatedAt(new Date());
        }
        updateById(assignment);
        return new AssignmentStartRespDTO(paperId, false);
    }

    @Override
    public AssignmentResultRespDTO myResult(String id, String userId) {
        ExamAssignment assignment = requireOwner(id, userId);
        boolean available = AssignmentStatus.COMPLETED.equals(effectiveStatus(assignment))
                && StringUtils.isNotBlank(assignment.getPaperId());
        Boolean passed = null;
        if (available) {
            Paper paper = paperService.getById(assignment.getPaperId());
            available = paper != null && Integer.valueOf(1).equals(paper.getHandState())
                    && assignment.getId().equals(paper.getAssignmentId())
                    && userId.equals(paper.getUserId()) && !"PENDING".equals(paper.getGradingState());
            passed = available ? paper.getPassed() : null;
        }
        return new AssignmentResultRespDTO(assignment.getId(), available, passed);
    }

    private SysUser buildCandidateUser(String assignmentId, CandidateCreateReqDTO reqDTO) {
        SysUser user = new SysUser();
        user.setId(IdWorker.getIdStr());
        user.setUserName("candidate_" + assignmentId);
        user.setRealName(StringUtils.trim(reqDTO.getCandidateName()));
        user.setState(UserState.NORMAL);
        user.setMobile(StringUtils.trim(reqDTO.getMobile()));
        user.setEmail(StringUtils.trim(reqDTO.getEmail()));
        user.setAvatar(SysUser.DEFAULT_AVATAR);
        PassInfo pass = PassHandler.buildPassword(UUID.randomUUID().toString());
        user.setPassword(pass.getPassword());
        user.setSalt(pass.getSalt());
        return user;
    }

    private String generateUniqueCode() {
        for (int i = 0; i < 20; i++) {
            String code = accessCodeManager.generate();
            if (count(new LambdaQueryWrapper<ExamAssignment>()
                    .eq(ExamAssignment::getAccessCodeLookup, accessCodeManager.lookup(code))) == 0) {
                return code;
            }
        }
        throw new ServiceException("考核码生成失败，请稍后重试！");
    }

    private ExamAssignment lockCandidate(String id) {
        ExamAssignment assignment = lock(id);
        if (!SysRoleId.CANDIDATE.equals(assignment.getSubjectType())) throw new ServiceException("候选人考核不存在！");
        return assignment;
    }

    private ExamAssignment lock(String id) {
        ExamAssignment assignment = baseMapper.selectByIdForUpdate(id);
        if (assignment == null) {
            throw new ServiceException("考核信息不存在！");
        }
        return assignment;
    }

    private ExamAssignment requireOwner(String id, String userId) {
        ExamAssignment assignment = getOne(new LambdaQueryWrapper<ExamAssignment>()
                .eq(ExamAssignment::getId, id)
                .eq(ExamAssignment::getUserId, userId), false);
        if (assignment == null) {
            throw new ServiceException("考核信息不存在或无权访问！");
        }
        return assignment;
    }

    private void ensureEnterable(ExamAssignment assignment, Date now) {
        if (AssignmentStatus.DISABLED.equals(assignment.getStatus())) {
            throw new ServiceException("考核已停用！");
        }
        if (AssignmentStatus.COMPLETED.equals(assignment.getStatus())
                || AssignmentStatus.PENDING_REVIEW.equals(assignment.getStatus())) {
            throw new ServiceException("考核已完成，不能重复进入！");
        }
        if (!assignment.getExpireAt().after(now) || AssignmentStatus.EXPIRED.equals(assignment.getStatus())) {
            assignment.setStatus(AssignmentStatus.EXPIRED);
            updateById(assignment);
            if (SysRoleId.CANDIDATE.equals(assignment.getSubjectType())) {
                sysUserService.invalidateSessions(List.of(assignment.getUserId()));
            }
            throw new ServiceException("考核已过期！");
        }
        if (assignment.getValidFrom() != null && assignment.getValidFrom().after(now)) {
            throw new ServiceException("考核尚未开始！");
        }
        if (!AssignmentStatus.isEnterable(assignment.getStatus())) {
            throw new ServiceException("考核当前不可进入！");
        }
    }

    private String effectiveStatus(ExamAssignment assignment) {
        if (AssignmentStatus.isEnterable(assignment.getStatus())
                && !assignment.getExpireAt().after(new Date())) {
            return AssignmentStatus.EXPIRED;
        }
        return assignment.getStatus();
    }

    private CandidateCreateRespDTO createResponse(ExamAssignment assignment, Position position,
                                                   Exam exam, String code) {
        return CandidateCreateRespDTO.builder()
                .assignmentId(assignment.getId())
                .candidateName(assignment.getSubjectName())
                .positionName(position == null ? null : position.getName())
                .examTitle(exam == null ? null : exam.getTitle())
                .accessCode(code)
                .entryUrl("/#/exam-entry")
                .build();
    }

    private ServiceException credentialError() {
        return new ServiceException("考核信息不存在或凭证错误！");
    }
}
