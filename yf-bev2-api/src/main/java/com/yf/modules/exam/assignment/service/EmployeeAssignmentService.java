package com.yf.modules.exam.assignment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yf.base.api.exception.ServiceException;
import com.yf.modules.exam.assignment.dto.request.*;
import com.yf.modules.exam.assignment.entity.ExamAssignment;
import com.yf.modules.exam.assignment.mapper.ExamAssignmentMapper;
import com.yf.modules.exam.exam.entity.Exam;
import com.yf.modules.exam.exam.service.ExamService;
import com.yf.modules.exam.exam.service.ExamRuleService;
import com.yf.modules.exam.paper.dto.response.PaperDetailRespDTO;
import com.yf.modules.exam.paper.entity.Paper;
import com.yf.modules.exam.paper.service.ObjectivePaperPolicy;
import com.yf.modules.exam.paper.service.PaperService;
import com.yf.modules.exam.position.service.PositionService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.util.*;

/** Employee issuance and explicitly projected lists; no credentials or scoring keys in list responses. */
@Service
@RequiredArgsConstructor
public class EmployeeAssignmentService {
    private final JdbcTemplate jdbc;
    private final ExamService exams;
    private final ExamRuleService rules;
    private final PositionService positions;
    private final ExamAssignmentMapper assignments;
    private final PaperService papers;

    private static final String ELIGIBLE = " u.state=0 AND EXISTS (SELECT 1 FROM el_sys_user_role ur WHERE ur.user_id=u.id AND ur.role_id='EMPLOYEE') AND NOT EXISTS (SELECT 1 FROM el_sys_user_role cr WHERE cr.user_id=u.id AND cr.role_id='CANDIDATE') ";
    private static final String STATUS = "CASE WHEN a.status IN ('COMPLETED','PENDING_REVIEW','DISABLED','EXPIRED') THEN a.status "
            + "WHEN p.id IS NOT NULL AND p.hand_state=0 AND (p.limit_time<=NOW() OR a.expire_at<=NOW()) THEN 'SETTLING' "
            + "WHEN a.expire_at<=NOW() THEN 'EXPIRED' WHEN a.valid_from>NOW() THEN 'UPCOMING' ELSE a.status END";
    private static final String JOINS = " FROM el_exam_assignment a LEFT JOIN el_exam e ON e.id=a.exam_id "
            + "LEFT JOIN el_sys_user u ON u.id=a.user_id LEFT JOIN el_sys_depart d ON d.id=a.depart_id "
            + "LEFT JOIN el_position pos ON pos.id=a.position_id LEFT JOIN el_paper p ON p.id=a.paper_id AND p.assignment_id=a.id AND p.user_id=a.user_id ";
    private static final String SAFE_FIELDS = "a.id, COALESCE(p.title,e.title) AS examTitle, a.batch_no AS batchNo, "
            + "d.dept_name AS departName,pos.name AS positionName,e.scene_type AS sceneType, "
            + "COALESCE(p.total_time,e.total_time) AS totalTime,a.valid_from AS validFrom,a.expire_at AS expireAt, "
            + "p.limit_time AS paperDeadline,a.paper_id AS paperId,a.disabled_reason AS disabledReason, "
            + STATUS + " AS status, CASE WHEN a.status='COMPLETED' AND p.hand_state=1 AND p.grading_state<>'PENDING' THEN p.passed ELSE NULL END AS passed";

    public Map<String,Object> employees(EmployeeAssignmentQueryDTO q) {
        if (StringUtils.isBlank(q.getDepartId())) return page(List.of(),0,q);
        List<Object> args = new ArrayList<>(List.of(q.getDepartId()));
        String where = " FROM el_sys_user u JOIN el_sys_depart d ON d.dept_code=u.dept_code WHERE d.id=? AND d.status=1 AND " + ELIGIBLE;
        if (StringUtils.isNotBlank(q.getKeyword())) { where += " AND (u.real_name LIKE ? OR u.employee_no LIKE ?)"; args.add(like(q.getKeyword())); args.add(like(q.getKeyword())); }
        long count = jdbc.queryForObject("SELECT COUNT(*)"+where,Long.class,args.toArray());
        args.add(q.getSize()); args.add((q.getCurrent()-1)*q.getSize());
        return page(jdbc.queryForList("SELECT u.id,u.real_name AS name,u.employee_no AS employeeNo,d.dept_name AS departName"+where+" ORDER BY u.employee_no,u.id LIMIT ? OFFSET ?",args.toArray()),count,q);
    }

    public List<Map<String,Object>> templates(EmployeeAssignmentQueryDTO q) {
        if (StringUtils.isBlank(q.getDepartId())) return List.of();
        List<Object> args = new ArrayList<>(List.of(q.getDepartId()));
        String where = " WHERE e.depart_id=? AND e.template_status=1 AND d.status=1 AND d.dept_type=2 AND d.parent_id<>'0' AND p.status=1 "
                + "AND EXISTS (SELECT 1 FROM el_depart_position dp WHERE dp.depart_id=e.depart_id AND dp.position_id=e.position_id) "
                + "AND EXISTS (SELECT 1 FROM el_exam_rule r WHERE r.exam_id=e.id AND r.qu_count>0) "
                + "AND NOT EXISTS (SELECT 1 FROM el_exam_rule r WHERE r.exam_id=e.id AND r.qu_count>0 AND (r.qu_type IS NULL OR r.qu_type NOT IN ('radio','multi','judge','short')))";
        if (StringUtils.isNotBlank(q.getPositionId())) { where += " AND e.position_id=?"; args.add(q.getPositionId()); }
        if (StringUtils.isNotBlank(q.getSceneType())) { where += " AND e.scene_type=?"; args.add(q.getSceneType()); }
        return jdbc.queryForList("SELECT e.id,e.title,e.position_id AS positionId,p.name AS positionName,e.scene_type AS sceneType,e.total_time AS totalTime,"
                + "(SELECT SUM(r.qu_count) FROM el_exam_rule r WHERE r.exam_id=e.id) AS questionCount FROM el_exam e JOIN el_sys_depart d ON d.id=e.depart_id JOIN el_position p ON p.id=e.position_id"+where+" ORDER BY p.sort,e.title,e.id",args.toArray());
    }

    @Transactional(rollbackFor=Exception.class, isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> create(EmployeeAssignmentCreateDTO req) {
        // Serialize natural-key retries before reading existing rows, including concurrent first issuance.
        if (jdbc.queryForList("SELECT id FROM el_exam WHERE id=? FOR UPDATE",req.getExamId()).isEmpty()) throw error("考核模板不存在");
        Exam exam = exams.getById(req.getExamId());
        if (!Integer.valueOf(1).equals(exam.getTemplateStatus())) throw error("考核模板未启用");
        positions.requireDepartmentPosition(exam.getDepartId(),exam.getPositionId());
        positions.requireGradeForPosition(exam.getTargetGradeId(),exam.getPositionId());
        var ruleList = rules.listByExam(exam.getId());
        ObjectivePaperPolicy.validateRules(ruleList);
        if (exam.getTotalTime()==null || exam.getTotalTime()<=0) throw error("考核时长必须大于零");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM el_repo WHERE id=? AND status=1 AND depart_id=? AND position_id=? AND scene_type=?",Integer.class,
                exam.getRepoId(),exam.getDepartId(),exam.getPositionId(),exam.getSceneType())==0) throw error("模板题库已停用或与部门、岗位、场景不匹配");
        java.math.BigDecimal total=java.math.BigDecimal.ZERO;
        for (var rule:ruleList) {
            if (rule.getQuCount()==0) continue;
            if (!Objects.equals(exam.getRepoId(),rule.getRepoId())) throw error("组卷规则题库与模板不匹配");
            int available=jdbc.queryForObject("SELECT COUNT(*) FROM el_repo_qu WHERE repo_id=? AND qu_type=? AND status=1",Integer.class,rule.getRepoId(),rule.getQuType());
            if (available<rule.getQuCount()) throw error("模板题库的有效题目不足，请联系管理员完善模板");
            total=total.add(rule.getQuScore().multiply(java.math.BigDecimal.valueOf(rule.getQuCount())));
        }
        if (exam.getQualifyScore()==null || exam.getQualifyScore().signum()<0 || exam.getQualifyScore().compareTo(total)>0) throw error("模板及格分配置无效");
        Date now = new Date();
        // Match DATETIME precision without rounding an immediate task into the future.
        Date from = req.getValidFrom()==null ? new Date(now.getTime() / 1000 * 1000) : req.getValidFrom();
        Date until = req.getExpireAt()==null ? new Date(from.getTime()+Duration.ofDays(14).toMillis()) : req.getExpireAt();
        if (!until.after(from) || !until.after(now)) throw error("截止时间必须晚于开始时间和当前时间");
        String batch = req.getBatchNo().trim();
        if (batch.isEmpty()) throw error("批次不能为空");
        List<String> ids = req.getUserIds().stream().distinct().sorted().toList();
        List<Map<String,Object>> users = new ArrayList<>();
        for (String id:ids) {
            var rows=jdbc.queryForList("SELECT u.id,u.real_name AS name FROM el_sys_user u JOIN el_sys_depart d ON d.dept_code=u.dept_code WHERE u.id=? AND d.id=? AND "+ELIGIBLE+" FOR UPDATE",id,exam.getDepartId());
            if (rows.isEmpty()) throw error("所选员工已停用、未审核或不属于模板部门，请刷新名单");
            users.add(rows.get(0));
        }
        int created=0; List<String> result=new ArrayList<>();
        for (var user:users) {
            String userId=(String)user.get("id");
            ExamAssignment existing=assignments.selectOne(new LambdaQueryWrapper<ExamAssignment>().eq(ExamAssignment::getUserId,userId).eq(ExamAssignment::getExamId,exam.getId()).eq(ExamAssignment::getBatchNo,batch));
            if (existing!=null) { result.add(existing.getId()); continue; }
            ExamAssignment a=new ExamAssignment(); a.setExamId(exam.getId()); a.setUserId(userId); a.setSubjectType("EMPLOYEE");
            a.setSubjectName((String)user.get("name")); a.setDepartId(exam.getDepartId()); a.setPositionId(exam.getPositionId()); a.setTargetGradeId(exam.getTargetGradeId());
            a.setBatchNo(batch); a.setValidFrom(from); a.setExpireAt(until); a.setStatus("ASSIGNED");
            assignments.insert(a); result.add(a.getId()); created++;
        }
        return Map.of("created",created,"existing",result.size()-created,"assignmentIds",result);
    }

    public Map<String,Object> paging(EmployeeAssignmentQueryDTO q,String owner) {
        List<Object> args=new ArrayList<>(); String where=" WHERE a.subject_type='EMPLOYEE'";
        if (owner!=null) { where+=" AND a.user_id=?"; args.add(owner); }
        if (StringUtils.isNotBlank(q.getKeyword())) {
            where+=owner==null ? " AND (a.subject_name LIKE ? OR u.employee_no LIKE ?)" : " AND COALESCE(p.title,e.title) LIKE ?";
            args.add(like(q.getKeyword())); if (owner==null) args.add(like(q.getKeyword()));
        }
        if (StringUtils.isNotBlank(q.getDepartId())) { where+=" AND a.depart_id=?"; args.add(q.getDepartId()); }
        if (StringUtils.isNotBlank(q.getSceneType())) { where+=" AND e.scene_type=?"; args.add(q.getSceneType()); }
        if (StringUtils.isNotBlank(q.getBatchNo())) { where+=" AND a.batch_no LIKE ?"; args.add(like(q.getBatchNo())); }
        var counts=jdbc.queryForList("SELECT "+STATUS+" AS status,COUNT(*) AS total"+JOINS+where+" GROUP BY "+STATUS,args.toArray());
        if (StringUtils.isNotBlank(q.getStatus())) {
            if ("TODO".equals(q.getStatus())) where+=" AND ("+STATUS+") IN ('UPCOMING','ASSIGNED')";
            else if ("CLOSED".equals(q.getStatus())) where+=" AND ("+STATUS+") IN ('EXPIRED','DISABLED')";
            else { where+=" AND ("+STATUS+")=?"; args.add(q.getStatus()); }
        }
        long count=jdbc.queryForObject("SELECT COUNT(*)"+JOINS+where,Long.class,args.toArray());
        args.add(q.getSize()); args.add((q.getCurrent()-1)*q.getSize());
        String fields=SAFE_FIELDS+(owner==null ? ",a.subject_name AS subjectName,u.employee_no AS employeeNo" : "");
        var result=page(jdbc.queryForList("SELECT "+fields+JOINS+where+" ORDER BY a.create_time DESC,a.id DESC LIMIT ? OFFSET ?",args.toArray()),count,q);
        result.put("counts",counts); return result;
    }

    @Transactional(rollbackFor=Exception.class)
    public void changeStatus(AssignmentStatusReqDTO req) {
        ExamAssignment a=assignments.selectByIdForUpdate(req.getId()); requireEmployee(a);
        Paper p=StringUtils.isBlank(a.getPaperId()) ? null : papers.getById(a.getPaperId());
        if (!a.getExpireAt().after(new Date()) || (p!=null && (!Integer.valueOf(0).equals(p.getHandState()) || p.getLimitTime()==null || !p.getLimitTime().after(new Date())))) throw error("考核已结束或正在结算，不能变更状态");
        if ("DISABLE".equals(req.getAction()) && Set.of("ASSIGNED","STARTED").contains(a.getStatus())) {
            if (StringUtils.isBlank(req.getReason())) throw error("请填写停用原因");
            a.setStatus("DISABLED"); a.setDisabledReason(req.getReason().trim());
        } else if ("ENABLE".equals(req.getAction()) && "DISABLED".equals(a.getStatus())) {
            a.setStatus(p==null ? "ASSIGNED" : "STARTED");
            // MyBatis ignores null fields on update; clear the reason explicitly.
            jdbc.update("UPDATE el_exam_assignment SET disabled_reason=NULL WHERE id=?",a.getId()); a.setDisabledReason(null);
        } else throw error("当前状态不允许此操作");
        assignments.updateById(a);
    }

    public PaperDetailRespDTO result(String id) {
        ExamAssignment a=assignments.selectById(id); requireEmployee(a);
        Paper p=StringUtils.isBlank(a.getPaperId()) ? null : papers.getById(a.getPaperId());
        if (!"COMPLETED".equals(a.getStatus()) || p==null || !a.getId().equals(p.getAssignmentId()) || !a.getUserId().equals(p.getUserId()) || !Integer.valueOf(1).equals(p.getHandState()) || "PENDING".equals(p.getGradingState())) throw error("考核结果尚未形成");
        return papers.fullDetail(p.getId());
    }
    private void requireEmployee(ExamAssignment a) { if (a==null || !"EMPLOYEE".equals(a.getSubjectType())) throw error("员工考核不存在"); }
    private ServiceException error(String message) { return new ServiceException(message+"！"); }
    private String like(String text) { return "%"+text.trim()+"%"; }
    private Map<String,Object> page(List<Map<String,Object>> rows,long total,EmployeeAssignmentQueryDTO q) {
        Map<String,Object> result=new LinkedHashMap<>(); result.put("records",rows); result.put("total",total); result.put("current",q.getCurrent()); result.put("size",q.getSize()); return result;
    }
}
