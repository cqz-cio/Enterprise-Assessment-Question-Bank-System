package com.yf.modules.exam.paper.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.yf.ability.task.enums.JobGroup;
import com.yf.ability.task.service.JobService;
import com.yf.base.api.api.dto.PagingReqDTO;
import com.yf.base.api.exception.ServiceException;
import com.yf.base.utils.BeanMapper;
import com.yf.base.utils.CronUtils;
import com.yf.base.utils.DecimalUtils;
import com.yf.base.utils.jackson.JsonHelper;
import com.yf.modules.exam.exam.dto.ExamRuleDTO;
import com.yf.modules.exam.exam.entity.Exam;
import com.yf.modules.exam.exam.service.ExamRecordService;
import com.yf.modules.exam.exam.service.ExamRuleService;
import com.yf.modules.exam.exam.service.ExamService;
import com.yf.modules.exam.assignment.entity.ExamAssignment;
import com.yf.modules.exam.assignment.enums.AssignmentStatus;
import com.yf.modules.exam.assignment.mapper.ExamAssignmentMapper;
import com.yf.modules.exam.jobs.HandPaperJob;
import com.yf.modules.exam.paper.dto.PaperDTO;
import com.yf.modules.exam.paper.dto.response.PaperCheckRespDTO;
import com.yf.modules.exam.paper.dto.response.PaperDetailRespDTO;
import com.yf.modules.exam.paper.dto.response.PaperRealTimeRespDTO;
import com.yf.modules.exam.paper.entity.Paper;
import com.yf.modules.exam.paper.mapper.PaperMapper;
import com.yf.modules.exam.paper.service.PaperQuService;
import com.yf.modules.exam.paper.service.PaperAccessService;
import com.yf.modules.exam.paper.service.PaperService;
import com.yf.modules.exam.repo.dto.request.RepoQuDetailDTO;
import com.yf.modules.exam.repo.service.RepoQuService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * <p>
 * 试卷业务实现类
 * </p>
 *
 * @author 聪明笨狗
 * @since 2025-04-14 17:40
 */
@RequiredArgsConstructor
@Service
public class PaperServiceImpl extends ServiceImpl<PaperMapper, Paper> implements PaperService {

    private final ExamService examService;
    private final ExamRuleService examRuleService;
    private final RepoQuService repoQuService;
    private final PaperQuService paperQuService;
    private final ExamRecordService examRecordService;
    private final JobService jobService;
    private final PaperAccessService paperAccessService;
    private final ExamAssignmentMapper examAssignmentMapper;

    @Override
    public IPage<PaperDTO> paging(PagingReqDTO<PaperDTO> reqDTO) {

        //查询条件
        QueryWrapper<Paper> wrapper = new QueryWrapper<>();

        // 请求参数
        PaperDTO params = reqDTO.getParams();

        if (params!=null) {
            if (StringUtils.isNotBlank(params.getExamId())) {
                wrapper.lambda().eq(Paper::getExamId, params.getExamId());
            }
            if (StringUtils.isNotBlank(params.getUserId())) {
                wrapper.lambda().eq(Paper::getUserId, params.getUserId());
            }
        }

        // 考试时间倒序
        wrapper.lambda().orderByDesc(Paper::getCreateTime);

        //获得数据
        IPage<Paper> page = this.page(reqDTO.toPage(), wrapper);
        //转换结果
        return JsonHelper.parseObject(page, new TypeReference<Page<PaperDTO>>() {});
    }


    @Override
    public PaperDTO detail(String id, String userId) {
        Paper entity = paperAccessService.requireOwner(id, userId);
        PaperDTO dto = new PaperDTO();
        BeanMapper.copy(entity, dto);
        return dto;
    }


    @Override
    public PaperCheckRespDTO preCheck(String examId, String userId) {
        throw new ServiceException("直接按考试模板进入的入口已停用，请从本人考核分配进入！");
    }

    @Override
    public String createPaper(String examId, String userId) {
        throw new ServiceException("直接按考试模板开考的入口已停用，请从本人考核分配进入！");
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public String createPaperForAssignment(ExamAssignment assignment) {
        // Re-lock and reload rather than trusting the object supplied by the caller.
        assignment = examAssignmentMapper.selectByIdForUpdate(assignment.getId());
        if (assignment == null || !AssignmentStatus.isEnterable(assignment.getStatus())
                || assignment.getExpireAt() == null || !assignment.getExpireAt().after(new Date())
                || (assignment.getValidFrom() != null && assignment.getValidFrom().after(new Date()))) {
            throw new ServiceException("考核当前不可进入！");
        }
        Paper existing = this.getOne(new QueryWrapper<Paper>().lambda()
                .eq(Paper::getAssignmentId, assignment.getId()), false);
        if (existing != null) {
            if (!existing.getUserId().equals(assignment.getUserId())) {
                throw new ServiceException("考核试卷状态异常，请联系管理员！");
            }
            if (!Integer.valueOf(0).equals(existing.getHandState())
                    || existing.getLimitTime() == null || !existing.getLimitTime().after(new Date())) {
                throw new ServiceException("考核已交卷或已到期，不能重新进入！");
            }
            return existing.getId();
        }

        Exam exam = examService.getById(assignment.getExamId());
        if (exam == null || !Integer.valueOf(1).equals(exam.getTemplateStatus())) {
            throw new ServiceException("考核模板不存在或已停用！");
        }

        Paper paper = new Paper();
        paper.setTitle(exam.getTitle());
        paper.setExamId(exam.getId());
        paper.setAssignmentId(assignment.getId());
        paper.setUserId(assignment.getUserId());
        paper.setTotalScore(exam.getTotalScore());
        paper.setQualifyScore(exam.getQualifyScore());
        paper.setUserScore(DecimalUtils.zero());
        paper.setUserTime(0);
        paper.setHandState(0);
        paper.setHandMinSnapshot(exam.getHandMin() == null ? 0 : exam.getHandMin());
        paper.setGradingState("NOT_REQUIRED");
        paper.setSnapshotSource("CREATED");

        Integer totalTime = exam.getTotalTime();
        paper.setTotalTime(totalTime);
        Date deadline = assignment.getExpireAt();
        if (totalTime != null && totalTime > 0) {
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.MINUTE, totalTime);
            if (calendar.getTime().before(deadline)) {
                deadline = calendar.getTime();
            }
        }
        paper.setLimitTime(deadline);
        this.save(paper);

        List<ExamRuleDTO> ruleList = examRuleService.listByExam(exam.getId());
        com.yf.modules.exam.paper.service.ObjectivePaperPolicy.validateRules(ruleList);
        BigDecimal calculatedTotal = ruleList.stream()
                .filter(r -> r.getQuCount() > 0)
                .map(r -> r.getQuScore().multiply(BigDecimal.valueOf(r.getQuCount())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (exam.getQualifyScore() == null || exam.getQualifyScore().signum() < 0
                || exam.getQualifyScore().compareTo(calculatedTotal) > 0) {
            throw new ServiceException("及格分必须介于零与试卷总分之间！");
        }
        paper.setTotalScore(calculatedTotal);
        this.updateById(paper);
        int sort = 1;
        for (ExamRuleDTO rule : ruleList) {
            if (rule.getQuCount() == null || rule.getQuCount() == 0) {
                continue;
            }
            List<RepoQuDetailDTO> quList = repoQuService.listForPaper(
                    rule.getRepoId(), rule.getQuType(), rule.getQuCount());
            paperQuService.saveToPaper(paper.getId(), rule.getQuScore(), quList, sort, Integer.valueOf(1).equals(exam.getOptionShuffle()));
            sort += quList.size();
        }

        String jobName = "force:hand:paper:" + paper.getId();
        jobService.addCronJob(HandPaperJob.class, jobName, JobGroup.SYSTEM,
                CronUtils.dateToCron(paper.getLimitTime()), paper.getId());
        return paper.getId();
    }

    /** Quartz and the recovery scanner use this deadline-only path. */
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 20)
    @Override
    public void handPaper(String paperId) {
        Paper paper = paperAccessService.lockForUpdate(paperId);
        if (!Integer.valueOf(0).equals(paper.getHandState())) return;
        if (paper.getLimitTime() == null || paper.getLimitTime().after(new Date())) return;
        settle(paper, true);
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 20)
    @Override
    public void handPaper(String paperId, String userId) {
        Paper paper = paperAccessService.lockForUpdate(paperId);
        if (userId == null || !userId.equals(paper.getUserId())) throw new ServiceException("无权访问该试卷！");
        if (!Integer.valueOf(0).equals(paper.getHandState())) return;
        if (StringUtils.isNotBlank(paper.getAssignmentId())) {
            ExamAssignment assignment = examAssignmentMapper.selectByIdForUpdate(paper.getAssignmentId());
            if (AssignmentStatus.DISABLED.equals(assignment.getStatus())) {
                throw new ServiceException("考核已停用！");
            }
        }
        boolean expired = paper.getLimitTime() != null && !paper.getLimitTime().after(new Date());
        settle(paper, expired);
    }

    private void settle(Paper paper, boolean forced) {
        Date now = new Date();
        long elapsed = Math.max(0, (now.getTime() - paper.getCreateTime().getTime()) / 60_000);
        int minimum = paper.getHandMinSnapshot() == null ? 0 : paper.getHandMinSnapshot();
        if (!forced && elapsed < minimum) {
            throw new ServiceException(String.format("请至少作答%s分钟后再交卷！", minimum));
        }
        boolean pending = paperQuService.count(new QueryWrapper<com.yf.modules.exam.paper.entity.PaperQu>()
                .eq("paper_id", paper.getId()).and(w -> w.isNull("qu_type").or().notIn("qu_type", "radio", "multi", "judge"))) > 0;
        BigDecimal score = paperQuService.sumTotalScore(paper.getId());
        paper.setUserScore(score);
        paper.setHandState(1);
        paper.setHandTime(now);
        long usedUntil = forced && paper.getLimitTime() != null
                ? Math.min(now.getTime(), paper.getLimitTime().getTime()) : now.getTime();
        paper.setUserTime((int) Math.max(0, (usedUntil - paper.getCreateTime().getTime()) / 60_000));
        paper.setGradingState(pending ? "PENDING" : "NOT_REQUIRED");
        paper.setPassed(pending ? null : DecimalUtils.ge(score, paper.getQualifyScore()));
        this.updateById(paper);
        if (!pending) {
            examRecordService.joinRecord(paper.getExamId(), paper.getUserId(), paper.getId(), score, paper.getPassed());
        }
        if (StringUtils.isNotBlank(paper.getAssignmentId())) {
            ExamAssignment assignment = examAssignmentMapper.selectByIdForUpdate(paper.getAssignmentId());
            // Disabling an assignment must not be undone by its old timer.
            if (!AssignmentStatus.DISABLED.equals(assignment.getStatus())) {
                assignment.setStatus(pending ? AssignmentStatus.PENDING_REVIEW : AssignmentStatus.COMPLETED);
                assignment.setSubmittedAt(now);
                if (!pending) assignment.setCompletedAt(now);
                examAssignmentMapper.updateById(assignment);
            }
        }
    }

    @Override
    public PaperRealTimeRespDTO realTimeState(String paperId, String userId) {

        Paper paper = paperAccessService.requireOwner(paperId, userId);

        PaperRealTimeRespDTO respDTO = new PaperRealTimeRespDTO();

        long limit = paper.getLimitTime().getTime();
        long leftSeconds = (limit - System.currentTimeMillis()) / 1000;

        respDTO.setLeftSeconds((int) leftSeconds);
        respDTO.setHanded(paper.getHandState() != null && paper.getHandState().equals(1));

        return respDTO;
    }

    @Override
    public PaperDetailRespDTO fullDetail(String id) {
        return baseMapper.selectPaperDetail(id);
    }

}
