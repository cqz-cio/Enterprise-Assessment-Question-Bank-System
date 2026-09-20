package com.yf.modules.exam.report;

import com.yf.base.api.exception.ServiceException;
import com.yf.system.modules.user.UserUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

/** One submitted assignment per row. All report operations share the same server-side scope. */
@Service @RequiredArgsConstructor
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=30)
public class ResultReportService {
    public static final int EXPORT_LIMIT=10000;
    private final JdbcTemplate db;
    private static final String FINAL="(p.grading_state IN ('NOT_REQUIRED','GRADED') AND p.passed IS NOT NULL)";
    private static final String JOINS=" FROM el_paper p INNER JOIN el_exam_assignment a ON a.id=p.assignment_id AND a.paper_id=p.id AND a.user_id=p.user_id AND a.exam_id=p.exam_id "
            +"LEFT JOIN el_sys_user u ON u.id=p.user_id LEFT JOIN el_exam e ON e.id=p.exam_id LEFT JOIN el_position pos ON pos.id=a.position_id "
            +"LEFT JOIN el_sys_depart d ON d.id=a.depart_id LEFT JOIN el_sys_user g ON g.id=p.graded_by ";
    private static final String NUMBER="CASE WHEN a.subject_type='CANDIDATE' THEN a.candidate_no ELSE u.employee_no END";
    private static final String FIELDS="p.id,a.id AS assignmentId,a.subject_name AS subjectName,a.subject_type AS subjectType,"+NUMBER+" AS subjectNo,"
            +"d.dept_name AS departName,pos.name AS positionName,e.scene_type AS sceneType,a.batch_no AS batchNo,p.title,p.hand_time AS handTime,"
            +"p.total_score AS totalScore,p.qualify_score AS qualifyScore,p.grading_state AS gradingState,g.real_name AS graderName,p.graded_at AS gradedAt,"
            +"CASE WHEN "+FINAL+" THEN p.user_score END AS userScore,CASE WHEN "+FINAL+" THEN p.passed END AS passed,"
            +"(SELECT COALESCE(SUM(q.actual_score),0) FROM el_paper_qu q WHERE q.paper_id=p.id AND q.qu_type IN ('radio','multi','judge')) AS objectiveScore,"
            +"CASE WHEN "+FINAL+" THEN (SELECT COALESCE(SUM(q.actual_score),0) FROM el_paper_qu q WHERE q.paper_id=p.id AND q.qu_type='short' AND q.grading_state='GRADED') END AS subjectiveScore,"
            +"(SELECT COUNT(*) FROM el_paper_qu q WHERE q.paper_id=p.id AND q.qu_type='short' AND q.grading_state='GRADED') AS gradedCount";

    private static class Filter {
        String sql=" WHERE p.hand_state=1 AND ("+FINAL+" OR p.grading_state='PENDING')";
        final List<Object> args=new ArrayList<>();
        void add(String sql,Object... values) { this.sql+=" AND "+sql; args.addAll(Arrays.asList(values)); }
    }
    private Filter filter(ResultQuery q,boolean state) {
        if (q.getScoreMin()!=null&&q.getScoreMax()!=null&&q.getScoreMin().compareTo(q.getScoreMax())>0) throw error("最低分不能大于最高分");
        if (q.getSubmittedFrom()!=null&&q.getSubmittedTo()!=null&&q.getSubmittedFrom().after(q.getSubmittedTo())) throw error("交卷开始时间不能晚于结束时间");
        Filter f=new Filter();
        var user=UserUtils.getUser(false);
        if (user==null||!has(user.getId())) throw error("无法确定当前用户的数据权限");
        // A login session can outlive a department or role change. Resolve current scope in this transaction.
        var scopes=db.queryForList("SELECT u.dept_code AS deptCode,MAX(r.data_scope) AS dataScope FROM el_sys_user u "
                +"INNER JOIN el_sys_user_role ur ON ur.user_id=u.id INNER JOIN el_sys_role r ON r.id=ur.role_id "
                +"WHERE u.id=? AND u.state=0 GROUP BY u.dept_code",user.getId());
        if(scopes.isEmpty()||!(scopes.get(0).get("dataScope") instanceof Number scope)) throw error("无法确定当前用户的数据权限");
        String deptCode=(String)scopes.get(0).get("deptCode");
        switch(scope.intValue()) {
            case 1 -> f.add("a.create_by=?",user.getId());
            case 2 -> { if (!has(deptCode)) throw error("用户未配置部门"); f.add("d.dept_code=?",deptCode); }
            case 3 -> { if (!has(deptCode)) throw error("用户未配置部门"); f.add("LEFT(d.dept_code,?)=?",deptCode.length(),deptCode); }
            case 4 -> { }
            default -> throw error("不支持的数据权限");
        }
        if (has(q.getKeyword())) { String v=like(q.getKeyword()); f.add("(a.subject_name LIKE ? ESCAPE '!' OR "+NUMBER+" LIKE ? ESCAPE '!')",v,v); }
        if (has(q.getTitle())) f.add("p.title LIKE ? ESCAPE '!'",like(q.getTitle()));
        if (has(q.getBatchNo())) f.add("a.batch_no LIKE ? ESCAPE '!'",like(q.getBatchNo()));
        String[] columns={"a.depart_id","a.position_id","a.subject_type","e.scene_type"};
        String[] values={q.getDepartId(),q.getPositionId(),q.getSubjectType(),q.getSceneType()};
        for(int i=0;i<columns.length;i++) if(has(values[i])) f.add(columns[i]+"=?",values[i]);
        if(q.getSubmittedFrom()!=null) f.add("p.hand_time>=?",q.getSubmittedFrom());
        if(q.getSubmittedTo()!=null) f.add("p.hand_time<=?",q.getSubmittedTo());
        if(q.getPassed()!=null) f.add(FINAL+" AND p.passed=?",q.getPassed());
        if(q.getScoreMin()!=null) f.add(FINAL+" AND p.user_score>=?",q.getScoreMin());
        if(q.getScoreMax()!=null) f.add(FINAL+" AND p.user_score<=?",q.getScoreMax());
        if(state&&"COMPLETED".equals(q.getState())) f.add(FINAL);
        if(state&&"PENDING".equals(q.getState())) f.add("p.grading_state='PENDING'");
        return f;
    }
    private long count(Filter f,String extra) { return db.queryForObject("SELECT COUNT(*)"+JOINS+f.sql+extra,Long.class,f.args.toArray()); }
    private List<Map<String,Object>> rows(Filter f,int limit,int offset) {
        var args=new ArrayList<>(f.args); args.add(limit); args.add(offset);
        return db.queryForList("SELECT "+FIELDS+JOINS+f.sql+" ORDER BY p.hand_time DESC,p.id DESC LIMIT ? OFFSET ?",args.toArray());
    }
    public Map<String,Object> paging(ResultQuery q) {
        Filter f=filter(q,true), all=filter(q,false);
        return Map.of("records",rows(f,q.getSize(),(q.getCurrent()-1)*q.getSize()),"total",count(f,""),
                "all",count(all,""),"completed",count(all," AND "+FINAL),"pending",count(all," AND p.grading_state='PENDING'"));
    }
    public Map<String,Object> detail(String id) {
        Filter f=filter(new ResultQuery(),true); f.add("p.id=?",id);
        var rows=rows(f,1,0); if(rows.isEmpty()) throw error("成绩不存在或无权查看"); return rows.get(0);
    }
    public Map<String,Object> options() {
        Filter f=filter(new ResultQuery(),true);
        return Map.of("departments",db.queryForList("SELECT DISTINCT d.id,d.dept_name AS name"+JOINS+f.sql+" AND d.id IS NOT NULL ORDER BY name",f.args.toArray()),
                "positions",db.queryForList("SELECT DISTINCT pos.id,pos.name"+JOINS+f.sql+" AND pos.id IS NOT NULL ORDER BY name",f.args.toArray()));
    }
    public Map<String,Object> preview(ResultQuery q) {
        Filter f=filter(q,true); return Map.of("total",count(f,""),"pending",count(f," AND p.grading_state='PENDING'"),"limit",EXPORT_LIMIT);
    }
    public byte[] export(ResultQuery q) {
        Filter f=filter(q,true); long total=count(f,"");
        if(total==0) throw error("没有可导出的成绩");
        if(total>EXPORT_LIMIT) throw error("单次最多导出 10000 条，请缩小筛选范围");
        return ResultWorkbook.write(rows(f,EXPORT_LIMIT,0));
    }
    private static boolean has(String s) { return s!=null&&!s.isBlank(); }
    private static String like(String s) { return "%"+s.trim().replace("!","!!").replace("%","!%").replace("_","!_")+"%"; }
    private static ServiceException error(String s) { return new ServiceException(s); }
}
