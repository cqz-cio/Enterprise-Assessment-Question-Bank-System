package com.yf.modules.exam.grading;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.yf.base.api.exception.ServiceException;
import com.yf.modules.exam.grading.dto.*;
import com.yf.modules.exam.paper.entity.Paper;
import com.yf.modules.exam.paper.service.PaperAccessService;
import com.yf.modules.exam.exam.service.ExamRecordService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.util.*;

/** Permission-gated management projection. Every write locks assignment then paper. */
@Service @RequiredArgsConstructor
public class GradingService {
    private final JdbcTemplate db;
    private final PaperAccessService access;
    private final ExamRecordService records;
    private static final String JOINS=" FROM el_paper p LEFT JOIN el_exam_assignment a ON a.id=p.assignment_id AND a.paper_id=p.id AND a.user_id=p.user_id "
            +"LEFT JOIN el_exam e ON e.id=p.exam_id LEFT JOIN el_sys_user u ON u.id=p.user_id LEFT JOIN el_position pos ON pos.id=a.position_id "
            +"LEFT JOIN el_sys_depart d ON d.id=a.depart_id LEFT JOIN el_sys_user g ON g.id=p.graded_by ";
    private static final String OBJECTIVE="(SELECT COALESCE(SUM(q.actual_score),0) FROM el_paper_qu q WHERE q.paper_id=p.id AND q.qu_type IN ('radio','multi','judge'))";
    private static final String SUBJECTIVE="(SELECT COALESCE(SUM(q.actual_score),0) FROM el_paper_qu q WHERE q.paper_id=p.id AND q.qu_type='short' AND q.grading_state='GRADED')";
    private static final String FIELDS="p.id,p.title,p.hand_time AS handTime,p.grading_state AS gradingState,p.grading_version AS version,"
            +"p.total_score AS totalScore,p.qualify_score AS qualifyScore,p.user_score AS userScore,p.passed,p.snapshot_source AS snapshotSource,"
            +"COALESCE(a.subject_name,u.real_name) AS subjectName,a.subject_type AS subjectType,a.batch_no AS batchNo,"
            +"a.status AS assignmentStatus,pos.name AS positionName,d.dept_name AS departName,e.scene_type AS sceneType,g.real_name AS graderName,p.graded_at AS gradedAt,"
            +OBJECTIVE+" AS objectiveScore,"+SUBJECTIVE+" AS subjectiveScore,"
            +"(SELECT COUNT(*) FROM el_paper_qu q WHERE q.paper_id=p.id AND q.qu_type='short') AS shortCount,"
            +"(SELECT COUNT(*) FROM el_paper_qu q WHERE q.paper_id=p.id AND q.qu_type='short' AND q.grading_state='GRADED') AS gradedCount";

    public Map<String,Object> paging(GradingQueryDTO q) {
        String where=" WHERE p.hand_state=1 AND p.grading_state=?";
        List<Object> args=new ArrayList<>(List.of(q.getState()==null?"PENDING":q.getState()));
        if (StringUtils.isNotBlank(q.getKeyword())) { where+=" AND (COALESCE(a.subject_name,u.real_name) LIKE ? OR p.title LIKE ?)"; args.add(like(q.getKeyword())); args.add(like(q.getKeyword())); }
        String[] columns={"a.position_id","a.depart_id","e.scene_type","p.exam_id"};
        String[] values={q.getPositionId(),q.getDepartId(),q.getSceneType(),q.getExamId()};
        for (int i=0;i<columns.length;i++) if (StringUtils.isNotBlank(values[i])) { where+=" AND "+columns[i]+"=?";args.add(values[i]); }
        if (StringUtils.isNotBlank(q.getBatchNo())) { where+=" AND a.batch_no LIKE ?";args.add(like(q.getBatchNo())); }
        if (StringUtils.isNotBlank(q.getGraderId())) { where+=" AND EXISTS (SELECT 1 FROM el_paper_grading_log l WHERE l.paper_id=p.id AND l.grader_id=?)";args.add(q.getGraderId()); }
        if (q.getSubmittedFrom()!=null) { where+=" AND p.hand_time>=?";args.add(q.getSubmittedFrom()); }
        if (q.getSubmittedTo()!=null) { where+=" AND p.hand_time<=?";args.add(q.getSubmittedTo()); }
        long count=db.queryForObject("SELECT COUNT(*)"+JOINS+where,Long.class,args.toArray());
        args.add(q.getSize()); args.add((q.getCurrent()-1)*q.getSize());
        var rows=db.queryForList("SELECT "+FIELDS+JOINS+where+" ORDER BY p.hand_time,p.id LIMIT ? OFFSET ?",args.toArray());
        return Map.of("records",rows,"total",count,"current",q.getCurrent(),"size",q.getSize());
    }

    public List<Map<String,Object>> positions() {
        return db.queryForList("SELECT DISTINCT pos.id,pos.name"+JOINS+" WHERE p.hand_state=1 AND p.grading_state IN ('PENDING','GRADED') AND pos.id IS NOT NULL ORDER BY pos.name,pos.id");
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=20)
    public Map<String,Object> detail(String paperId) { return readDetail(paperId); }

    private Map<String,Object> readDetail(String paperId) {
        var rows=db.queryForList("SELECT "+FIELDS+JOINS+" WHERE p.id=? AND p.hand_state=1 AND p.grading_state IN ('PENDING','GRADED')",paperId);
        if (rows.isEmpty()) throw error("试卷尚未交卷或无需人工阅卷");
        var result=rows.get(0);
        result.put("questions",db.queryForList("SELECT q.id,q.sort,q.content_snapshot AS content,q.reference_answer_snapshot AS referenceAnswer,"
                +"q.grading_criteria_snapshot AS gradingCriteria,q.text_answer AS textAnswer,q.score,q.actual_score AS actualScore,"
                +"q.grading_state AS gradingState,q.grader_comment AS comment,q.graded_at AS gradedAt,g.real_name AS graderName "
                +"FROM el_paper_qu q LEFT JOIN el_sys_user g ON g.id=q.grader_id WHERE q.paper_id=? AND q.qu_type='short' ORDER BY q.sort,q.id",paperId));
        result.put("logs",db.queryForList("SELECT l.id,l.paper_qu_id AS paperQuId,q.sort,l.action,l.score_before AS scoreBefore,l.score_after AS scoreAfter,"
                +"l.comment_before AS commentBefore,l.comment_after AS commentAfter,l.create_time AS createTime,g.real_name AS graderName "
                +"FROM el_paper_grading_log l LEFT JOIN el_sys_user g ON g.id=l.grader_id LEFT JOIN el_paper_qu q ON q.id=l.paper_qu_id "
                +"WHERE l.paper_id=? ORDER BY l.create_time DESC,l.id DESC",paperId));
        return result;
    }

    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED,timeout=20)
    public Map<String,Object> save(GradeSaveDTO request,String grader) {
        requireGrader(grader);
        var found=db.queryForList("SELECT paper_id FROM el_paper_qu WHERE id=?",request.getPaperQuId());
        if (found.isEmpty()) throw error("试题不存在");
        String paperId=(String)found.get(0).get("paper_id");
        Paper paper=access.lockForUpdate(paperId); requirePending(paper);
        var rows=db.queryForList("SELECT * FROM el_paper_qu WHERE id=? AND paper_id=? AND qu_type='short'",request.getPaperQuId(),paperId);
        if (rows.isEmpty()) throw error("只允许评阅简答题");
        var question=rows.get(0); BigDecimal score=request.getScore(), maximum=(BigDecimal)question.get("score");
        if (score==null||score.signum()<0||score.scale()>2||maximum==null||score.compareTo(maximum)>0) throw error("评分必须介于零与本题满分之间，最多两位小数");
        String comment=StringUtils.defaultString(request.getComment());
        if (comment.length()>2000) throw error("评语不能超过 2000 字");
        boolean graded="GRADED".equals(question.get("grading_state"));
        BigDecimal before=graded?(BigDecimal)question.get("actual_score"):null;
        // An exact retry is harmless even if its version is old; a differing stale edit is rejected.
        if (graded&&score.compareTo(before)==0&&comment.equals(StringUtils.defaultString((String)question.get("grader_comment")))) return readDetail(paperId);
        requireVersion(paperId,request.getExpectedVersion());
        db.update("UPDATE el_paper_qu SET actual_score=?,grading_state='GRADED',grader_id=?,grader_comment=?,graded_at=CURRENT_TIMESTAMP WHERE id=?",score,grader,comment,request.getPaperQuId());
        log(paperId,request.getPaperQuId(),grader,before,score,(String)question.get("grader_comment"),comment,graded?"REGRADE":"GRADE");
        db.update("UPDATE el_paper SET grading_version=grading_version+1 WHERE id=?",paperId);
        return readDetail(paperId);
    }

    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED,timeout=20)
    public Map<String,Object> finalizePaper(GradeFinalizeDTO request,String grader) {
        requireGrader(grader);
        Paper paper=access.lockForUpdate(request.getPaperId());
        if (Integer.valueOf(1).equals(paper.getHandState())&&"GRADED".equals(paper.getGradingState())) return readDetail(paper.getId());
        requirePending(paper); requireVersion(paper.getId(),request.getExpectedVersion());
        int incomplete=db.queryForObject("SELECT COUNT(*) FROM el_paper_qu WHERE paper_id=? AND (qu_type IS NULL OR qu_type NOT IN ('radio','multi','judge','short') OR (qu_type='short' AND (grading_state<>'GRADED' OR actual_score IS NULL OR actual_score<0 OR actual_score>score)))",Integer.class,paper.getId());
        int shorts=db.queryForObject("SELECT COUNT(*) FROM el_paper_qu WHERE paper_id=? AND qu_type='short'",Integer.class,paper.getId());
        if (incomplete>0||shorts==0) throw error("尚有简答题未评分或存在不支持的题型");
        BigDecimal total=db.queryForObject("SELECT COALESCE(SUM(actual_score),0) FROM el_paper_qu WHERE paper_id=?",BigDecimal.class,paper.getId());
        if (paper.getQualifyScore()==null||paper.getTotalScore()==null||total.compareTo(paper.getTotalScore())>0) throw error("试卷总分配置异常");
        boolean passed=total.compareTo(paper.getQualifyScore())>=0;
        db.update("UPDATE el_paper SET user_score=?,passed=?,grading_state='GRADED',grading_version=grading_version+1,graded_by=?,graded_at=CURRENT_TIMESTAMP WHERE id=?",total,passed,grader,paper.getId());
        if (StringUtils.isNotBlank(paper.getAssignmentId())) db.update("UPDATE el_exam_assignment SET status='COMPLETED',completed_at=CURRENT_TIMESTAMP,update_time=CURRENT_TIMESTAMP WHERE id=?",paper.getAssignmentId());
        records.joinRecord(paper.getExamId(),paper.getUserId(),paper.getId(),total,passed);
        log(paper.getId(),null,grader,null,total,null,null,"FINALIZE");
        return readDetail(paper.getId());
    }

    private void requirePending(Paper paper) {
        if (!Integer.valueOf(1).equals(paper.getHandState())||!"PENDING".equals(paper.getGradingState())) throw error("试卷不在待阅卷状态");
        if ("LEGACY_INCOMPLETE".equals(paper.getSnapshotSource())) throw error("历史试卷内容缺失，需先核实原始材料");
        if (StringUtils.isNotBlank(paper.getAssignmentId())&&!"PENDING_REVIEW".equals(db.queryForObject("SELECT status FROM el_exam_assignment WHERE id=?",String.class,paper.getAssignmentId()))) throw error("考核已停用或状态异常，不能阅卷");
    }
    private void requireVersion(String id,Long expected) {
        Long version=db.queryForObject("SELECT grading_version FROM el_paper WHERE id=?",Long.class,id);
        if (expected==null||!expected.equals(version)) throw error("评分已被更新，请刷新试卷后重新核对");
    }
    private void log(String paper,String question,String grader,BigDecimal before,BigDecimal after,String oldComment,String comment,String action) {
        db.update("INSERT INTO el_paper_grading_log(id,paper_id,paper_qu_id,grader_id,score_before,score_after,comment_before,comment_after,action,create_time) VALUES(?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",IdWorker.getIdStr(),paper,question,grader,before,after,oldComment,comment,action);
    }
    private void requireGrader(String grader) { if (StringUtils.isBlank(grader)) throw error("请先登录"); }
    private String like(String value) { return "%"+value.trim()+"%"; }
    private ServiceException error(String message) { return new ServiceException(message+"！"); }
}
