package com.yf.modules.exam.assignment.importing;

import com.yf.base.api.exception.ServiceException;
import com.yf.modules.exam.assignment.dto.request.CandidateCreateReqDTO;
import com.yf.modules.exam.assignment.importing.CandidateImportModels.*;
import com.yf.modules.exam.assignment.service.ExamAssignmentService;
import com.yf.system.modules.user.UserUtils;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.time.*;
import java.time.format.ResolverStyle;
import java.util.*;
import java.security.MessageDigest;
import com.yf.modules.exam.assignment.service.AccessCodeManager;
import com.yf.modules.exam.assignment.importing.CandidateImportArchive.Task;

@Service @RequiredArgsConstructor
public class CandidateImportService {
    private final JdbcTemplate db;
    private final ExamAssignmentService assignments;
    private final CandidateIdentityGuard guard;
    private final Validator validator;
    private final CandidateImportArchive archive;
    private final AccessCodeManager codes;
    record Scope(String userId, int level, String department) {
        boolean allows(String departmentCode) {
            return level==1 || level==4 || (department!=null && departmentCode!=null &&
                    (level==2 ? department.equals(departmentCode) : level==3 && departmentCode.startsWith(department)));
        }
    }
    Scope scope() {
        String id = UserUtils.getUserId();
        if(id==null || id.isBlank()) throw new ServiceException("请先登录");
        var found = db.queryForList("SELECT u.dept_code AS deptCode,MAX(r.data_scope) AS dataScope FROM el_sys_user u "
                +"JOIN el_sys_user_role ur ON ur.user_id=u.id JOIN el_sys_role r ON r.id=ur.role_id WHERE u.id=? AND u.state=0 "
                +"AND EXISTS (SELECT 1 FROM el_sys_user_role x JOIN el_sys_role_menu rm ON rm.role_id=x.role_id "
                +"JOIN el_sys_menu m ON m.id=rm.menu_id WHERE x.user_id=u.id AND m.permission_tag='exam:assignment:candidate:import') GROUP BY u.dept_code",id);
        if(found.isEmpty() || !(found.get(0).get("dataScope") instanceof Number n) || n.intValue()<1 || n.intValue()>4)
            throw new ServiceException("无候选人导入权限或账号已停用");
        return new Scope(id,n.intValue(),(String)found.get(0).get("deptCode"));
    }

    public byte[] template() {
        Scope scope=scope();
        var refs=db.queryForList("SELECT d.dept_code,d.dept_name,p.code,p.name FROM el_sys_depart d "
                +"JOIN el_depart_position dp ON dp.depart_id=d.id JOIN el_position p ON p.id=dp.position_id "
                +"WHERE d.status=1 AND d.dept_type=2 AND d.parent_id<>'0' AND p.status=1 ORDER BY d.dept_code,p.code");
        List<List<String>> rows=new ArrayList<>();
        for(var r:refs) if(scope.allows((String)r.get("dept_code"))) rows.add(List.of((String)r.get("dept_code"),(String)r.get("dept_name"),(String)r.get("code"),(String)r.get("name")));
        return CandidateImportWorkbook.template(rows);
    }

    public ImportView preview(MultipartFile file) {
        Scope scope=scope();
        List<ImportRow> rows=CandidateImportWorkbook.read(file);
        String fingerprint;
        try { fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file.getBytes())); }
        catch(Exception ex) { throw new ServiceException("文件读取失败，请重新上传"); }
        Task previous=archive.find(scope.userId(),fingerprint);
        if(previous!=null) { checkResultScope(previous,scope); return view(previous.id,previous); }
        Set<String> keys=new HashSet<>();
        for(var row:rows) validate(row,scope,keys);
        Task task=new Task(); task.id=UUID.randomUUID().toString(); task.owner=scope.userId(); task.filename=file.getOriginalFilename();
        task.fileHash=fingerprint; task.expires=System.currentTimeMillis()+CandidateImportArchive.PREVIEW_TTL; task.rows=rows;
        archive.create(task);
        return view(task.id,task);
    }

    private CandidateCreateReqDTO validate(ImportRow row,Scope scope,Set<String> keys) {
        var v=row.getValues();
        row.setCandidateName(v.get(0)); row.setCandidateNo(v.get(1)); row.setBatchNo(v.get(6)); row.setValidFrom(v.get(7)); row.setExpireAt(v.get(8));
        if(row.getMessage()!=null && "ERROR".equals(row.getStatus())) return null;
        row.setStatus("ERROR");
        try {
            if(v.stream().anyMatch(String::isBlank)) throw new ServiceException("9 个字段均必填，请补全空白项");
            var req=new CandidateCreateReqDTO(); req.setCandidateName(v.get(0)); req.setCandidateNo(v.get(1)); req.setMobile(v.get(2)); req.setEmail(v.get(3)); req.setBatchNo(v.get(6));
            if(!v.get(2).matches("\\+?[0-9][0-9 -]{5,30}")) throw new ServiceException("手机号格式不正确，请按文本填写有效号码");
            if(v.get(3).length()>128) throw new ServiceException("邮箱不能超过 128 字符");
            req.setValidFrom(date(v.get(7))); req.setExpireAt(date(v.get(8)));
            if(!req.getExpireAt().after(req.getValidFrom()) || !req.getExpireAt().after(new Date())) throw new ServiceException("截止时间必须晚于生效时间且未过期");
            var matches=db.queryForList("SELECT d.id AS departId,d.dept_code AS departCode,d.dept_name AS departName,p.id AS positionId,p.name AS positionName "
                    +"FROM el_sys_depart d JOIN el_depart_position dp ON dp.depart_id=d.id JOIN el_position p ON p.id=dp.position_id "
                    +"WHERE d.dept_code=? AND p.code=? AND d.status=1 AND d.dept_type=2 AND d.parent_id<>'0' AND p.status=1",v.get(4),v.get(5));
            if(matches.size()!=1 || !scope.allows((String)matches.get(0).get("departCode"))) throw new ServiceException("部门岗位组合不可用或超出数据范围");
            var match=matches.get(0); req.setDepartId((String)match.get("departId")); req.setPositionId((String)match.get("positionId"));
            var violations=validator.validate(req);
            if(!violations.isEmpty()) throw new ServiceException(violations.stream().map(x->x.getMessage()).sorted().findFirst().orElse("字段错误"));
            row.setDepartName((String)match.get("departName")); row.setPositionName((String)match.get("positionName"));
            var exams=db.queryForList("SELECT id,title FROM el_exam WHERE depart_id=? AND position_id=? AND scene_type='INTERVIEW' AND template_status=1",req.getDepartId(),req.getPositionId());
            if(exams.size()!=1) throw new ServiceException("该部门岗位必须配置且仅配置一个启用的面试测评");
            row.setExamTitle((String)exams.get(0).get("title"));
            String key=v.get(1).toUpperCase(Locale.ROOT)+"\u0000"+v.get(6).toUpperCase(Locale.ROOT);
            if(guard.exists(v.get(1),v.get(6)) || !keys.add(key)) {
                row.setStatus("DUPLICATE"); row.setMessage("该编号在本批次已有考核或在文件中重复，将跳过"); return null;
            }
            row.setStatus("VALID"); row.setMessage("必填项完整，模板匹配成功"); return req;
        } catch(ServiceException ex) { row.setMessage(ex.getMessage()); return null; }
    }
    private Date date(String value) {
        try { return Date.from(LocalDateTime.parse(value,CandidateImportWorkbook.DATE.withResolverStyle(ResolverStyle.STRICT)).atZone(ZoneId.of("Asia/Shanghai")).toInstant()); }
        catch(RuntimeException ex) { throw new ServiceException("时间格式应为 yyyy-MM-dd HH:mm:ss，且必须为有效日期"); }
    }

    private static class RowFailure extends RuntimeException {
        final int rowNumber; final String status, message;
        RowFailure(int rowNumber,String status,String message) { this.rowNumber=rowNumber;this.status=status;this.message=message; }
    }
    private void saveProgress(Task task) {
        if(task.rows.stream().noneMatch(r->"VALID".equals(r.getStatus()))) task.committed=true;
        // A process interruption after the first committed row must not lose its code.
        task.expires=System.currentTimeMillis()+CandidateImportArchive.RESULT_TTL;
        archive.save(task);
    }
    public ImportView commit(String id) {
        Scope initial=scope();
        while(true) {
            try {
                ImportView result=archive.locked(id,initial.userId(),task->{
                    Scope current=scope();
                    if(task.committed) { checkResultScope(task,current); return view(id,task); }
                    ImportRow row=task.rows.stream().filter(r->"VALID".equals(r.getStatus())).findFirst().orElse(null);
                    if(row==null) { saveProgress(task); checkResultScope(task,current); return view(id,task); }
                    row.setMessage(null);
                    var req=validate(row,current,new HashSet<>());
                    if(req!=null) {
                        try {
                            // createCandidate joins this transaction: assignment and encrypted result commit together.
                            var issued=assignments.createCandidate(req);
                            row.setAssignmentId(issued.getAssignmentId()); row.setAccessCode(issued.getAccessCode());
                            row.setExamTitle(issued.getExamTitle()); row.setStatus("SUCCESS"); row.setMessage("已发放");
                        } catch(CandidateIdentityGuard.DuplicateCandidateException ex) {
                            throw new RowFailure(row.getRowNumber(),"DUPLICATE","该编号在本批次已有考核，将跳过");
                        } catch(ServiceException ex) {
                            throw new RowFailure(row.getRowNumber(),"ERROR",ex.getMessage());
                        } catch(RuntimeException ex) {
                            throw new RowFailure(row.getRowNumber(),"ERROR","本行发放失败，请联系管理员后重试");
                        }
                    }
                    // Saving outside the catch ensures archive failures roll back issuance and remain retryable.
                    saveProgress(task);
                    if(task.committed) { checkResultScope(task,current); return view(id,task); }
                    return null;
                });
                if(result!=null) return result;
            } catch(RowFailure failure) {
                // The failed row's transaction rolled back, including the natural-key reservation.
                archive.locked(id,initial.userId(),task->{
                    scope();
                    for(var row:task.rows) if(row.getRowNumber()==failure.rowNumber && "VALID".equals(row.getStatus())) {
                        row.setStatus(failure.status); row.setMessage(failure.message);
                    }
                    saveProgress(task);return null;
                });
            }
        }
    }
    public ImportView restore(String id) {
        Scope scope=scope();
        Task task=id==null || id.isBlank()?archive.find(scope.userId(),null):archive.get(id,scope.userId());
        if(task==null) return null;
        checkResultScope(task,scope);
        return view(task.id,task);
    }
    public byte[] report(String id,boolean includeCodes) {
        Scope scope=scope(); Task task=archive.get(id,scope.userId());
        if(includeCodes && !task.committed) throw new ServiceException("请先确认导入");
        checkResultScope(task,scope);
        return CandidateImportWorkbook.report(task.rows,includeCodes);
    }
    private record Credential(String department,String lookup,String status,Date expires) { }
    private void checkResultScope(Task task,Scope scope) {
        for(var row:task.rows) if("SUCCESS".equals(row.getStatus())) {
            // Explicit getTimestamp: MySQL's untyped DATETIME getObject returns LocalDateTime.
            var found=db.query("SELECT d.dept_code,a.access_code_lookup,a.status,a.expire_at FROM el_exam_assignment a JOIN el_sys_depart d ON d.id=a.depart_id WHERE a.id=?",
                    (rs,index)->new Credential(rs.getString("dept_code"),rs.getString("access_code_lookup"),
                            rs.getString("status"),rs.getTimestamp("expire_at")),row.getAssignmentId());
            if(found.size()!=1 || !scope.allows(found.get(0).department()))
                throw new ServiceException("数据权限已变化，无法读取本次清单");
            var assignment=found.get(0);
            if(row.getAccessCode()!=null && (!codes.lookup(row.getAccessCode()).equals(assignment.lookup())
                    || "DISABLED".equals(assignment.status())
                    || assignment.expires()==null || !assignment.expires().after(new Date()))) {
                row.setAccessCode(null); row.setMessage("考核码已重置、停用或过期，请在候选人列表核对");
            }
        }
    }
    public void close(String id) { archive.close(id,scope().userId()); }
    @Scheduled(fixedDelay=60000)
    public void cleanup() { archive.cleanup(); }
    private ImportView view(String id,Task t) {
        ImportView result=new ImportView(); result.setTaskId(id); result.setFileName(t.filename); result.setExpiresAt(t.expires); result.setCommitted(t.committed); result.setRows(new ArrayList<>(t.rows)); result.setTotalCount(t.rows.size());
        for(var r:t.rows) switch(r.getStatus()) {
            case "VALID" -> result.setValidCount(result.getValidCount()+1);
            case "SUCCESS" -> result.setSuccessCount(result.getSuccessCount()+1);
            case "DUPLICATE" -> result.setDuplicateCount(result.getDuplicateCount()+1);
            default -> result.setFailureCount(result.getFailureCount()+1);
        }
        return result;
    }
}
