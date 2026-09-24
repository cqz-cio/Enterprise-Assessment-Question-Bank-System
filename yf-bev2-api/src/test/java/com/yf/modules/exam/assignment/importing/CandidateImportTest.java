package com.yf.modules.exam.assignment.importing;

import com.yf.base.api.exception.ServiceException;
import com.yf.modules.exam.assignment.dto.request.CandidateCreateReqDTO;
import com.yf.modules.exam.assignment.dto.response.CandidateCreateRespDTO;
import com.yf.modules.exam.assignment.service.ExamAssignmentService;
import com.yf.system.modules.user.UserUtils;
import jakarta.validation.Validation;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yf.modules.exam.assignment.service.AccessCodeManager;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Timeout(20)
class CandidateImportTest {
    EmbeddedDatabase database; JdbcTemplate db; CandidateImportService service;
    static final String PEPPER="test-only-candidate-import-pepper-32-characters";
    CandidateImportArchive archive; AccessCodeManager codes; DataSourceTransactionManager manager;
    CandidateIdentityGuard guard; ExamAssignmentService assignments; MockedStatic<UserUtils> users;
    jakarta.validation.ValidatorFactory factory;
    @BeforeEach void setup() {
        database=new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build(); db=new JdbcTemplate(database);
        db.execute("CREATE TABLE el_candidate_issue_key(candidate_no varchar_ignorecase(64),batch_no varchar_ignorecase(64),PRIMARY KEY(candidate_no,batch_no))");
        db.execute("CREATE TABLE el_sys_user(id varchar(64),dept_code varchar(64),state int)");
        db.execute("CREATE TABLE el_sys_user_role(user_id varchar(64),role_id varchar(64))");
        db.execute("CREATE TABLE el_sys_role(id varchar(64),data_scope int)");
        db.execute("CREATE TABLE el_sys_role_menu(role_id varchar(64),menu_id varchar(64))");
        db.execute("CREATE TABLE el_sys_menu(id varchar(64),permission_tag varchar(128))");
        db.execute("CREATE TABLE el_sys_depart(id varchar(64),dept_code varchar(64),dept_name varchar(128),status int,dept_type int,parent_id varchar(64))");
        db.execute("CREATE TABLE el_position(id varchar(64),code varchar(64),name varchar(128),status int)");
        db.execute("CREATE TABLE el_depart_position(depart_id varchar(64),position_id varchar(64))");
        db.execute("CREATE TABLE el_exam(id varchar(64),title varchar(128),depart_id varchar(64),position_id varchar(64),scene_type varchar(32),template_status int)");
        db.execute("CREATE TABLE el_exam_assignment(id varchar(64),depart_id varchar(64),access_code_lookup varchar(64),status varchar(32),expire_at timestamp)");
        db.execute("CREATE TABLE el_candidate_import_task(id varchar(36) PRIMARY KEY,owner_id varchar(64),file_hash varchar(64),committed tinyint,expires_at bigint,created_at bigint,payload clob)");
        db.update("INSERT INTO el_sys_user VALUES('hr','001',0),('other','001',0)");
        db.update("INSERT INTO el_sys_role VALUES('HR',4)"); db.update("INSERT INTO el_sys_user_role VALUES('hr','HR'),('other','HR')");
        db.update("INSERT INTO el_sys_menu VALUES('import','exam:assignment:candidate:import')");db.update("INSERT INTO el_sys_role_menu VALUES('HR','import')");
        db.update("INSERT INTO el_sys_depart VALUES('d','001','部门',1,2,'root')"); db.update("INSERT INTO el_position VALUES('p','SALES','岗位',1)"); db.update("INSERT INTO el_depart_position VALUES('d','p')");
        db.update("INSERT INTO el_exam VALUES('e','面试','d','p','INTERVIEW',1)");
        guard=new CandidateIdentityGuard(db); assignments=mock(ExamAssignmentService.class); factory=Validation.buildDefaultValidatorFactory();
        manager=new DataSourceTransactionManager(database); codes=new AccessCodeManager(PEPPER);
        archive=spy(new CandidateImportArchive(db,new ObjectMapper(),manager,PEPPER));
        service=new CandidateImportService(db,assignments,guard,factory.getValidator(),archive,codes);
        users=mockStatic(UserUtils.class); users.when(UserUtils::getUserId).thenReturn("hr");
        var tx=new TransactionTemplate(new DataSourceTransactionManager(database));
        when(assignments.createCandidate(any())).thenAnswer(call->tx.execute(status->{
            CandidateCreateReqDTO req=call.getArgument(0); guard.reserve(req.getCandidateNo(),req.getBatchNo());
            if(req.getCandidateName().equals("失败")) throw new ServiceException("模拟本行失败");
            db.update("INSERT INTO el_exam_assignment VALUES(?,?,?,?,?)",req.getCandidateNo(),req.getDepartId(),codes.lookup("TEST23"),"ASSIGNED",new java.sql.Timestamp(req.getExpireAt().getTime()));
            return CandidateCreateRespDTO.builder().assignmentId(req.getCandidateNo()).accessCode("TEST23").examTitle("面试").build();
        }));
    }
    @AfterEach void cleanup() { users.close(); factory.close(); database.shutdown(); }
    List<String> row(String number) { return new ArrayList<>(List.of("测试人员",number,"13800000000","demo@example.invalid","001","SALES","TEST-BATCH","2099-01-01 09:00:00","2099-01-15 09:00:00")); }
    MockMultipartFile file(List<List<String>> rows) throws Exception {
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(CandidateImportWorkbook.template(List.of())))) {
            var sheet=book.getSheet("候选人数据"); int i=1;
            for(var values:rows) { var r=sheet.createRow(i++); for(int j=0;j<values.size();j++) r.createCell(j).setCellValue(values.get(j)); }
            var out=new ByteArrayOutputStream(); book.write(out);return new MockMultipartFile("file","test.xlsx","application/octet-stream",out.toByteArray());
        }
    }
    @Test void partialSuccessReportsExactRowsAndRepeatedCommitKeepsSameCodes() throws Exception {
        var bad=row("bad"); bad.set(3,"broken-email"); var duplicate=row("A");
        var preview=service.preview(file(List.of(row("A"),bad,duplicate,row("B"))));
        assertEquals(2,preview.getValidCount()); assertEquals(1,preview.getFailureCount()); assertEquals(1,preview.getDuplicateCount());
        assertEquals(3,preview.getRows().get(1).getRowNumber()); assertTrue(preview.getRows().get(1).getMessage().contains("邮箱"));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM el_exam_assignment",Integer.class));
        var result=service.commit(preview.getTaskId()); assertEquals(2,result.getSuccessCount());
        assertEquals(2,service.commit(preview.getTaskId()).getSuccessCount());verify(assignments,times(2)).createCandidate(any());
        try(var codes=new XSSFWorkbook(new ByteArrayInputStream(service.report(preview.getTaskId(),true)))) { assertEquals(2,codes.getSheetAt(0).getLastRowNum()); assertEquals("TEST23",codes.getSheetAt(0).getRow(1).getCell(9).getStringCellValue()); }
        try(var errors=new XSSFWorkbook(new ByteArrayInputStream(service.report(preview.getTaskId(),false)))) { assertEquals(2,errors.getSheetAt(0).getLastRowNum());assertEquals("3",errors.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()); }
        var retry=service.preview(file(List.of(row("a"))));assertEquals(1,retry.getDuplicateCount());
    }
    @Test void rowFailureRollsBackReservationWithoutRollingBackOtherRows() throws Exception {
        var failed=row("B");failed.set(0,"失败");var task=service.preview(file(List.of(row("A"),failed,row("C"))));
        var result=service.commit(task.getTaskId());assertEquals(2,result.getSuccessCount());assertEquals(1,result.getFailureCount());assertFalse(guard.exists("B","TEST-BATCH"));
        assertEquals(1,service.preview(file(List.of(row("B")))).getValidCount());
    }
    @Test void permissionsOwnerAndClosedTaskFailClosed() throws Exception {
        var task=service.preview(file(List.of(row("A"))));
        users.when(UserUtils::getUserId).thenReturn("other");
        assertThrows(ServiceException.class,()->service.commit(task.getTaskId()));assertThrows(ServiceException.class,()->service.report(task.getTaskId(),false));assertThrows(ServiceException.class,()->service.close(task.getTaskId()));
        users.when(UserUtils::getUserId).thenReturn("hr");service.close(task.getTaskId());assertThrows(ServiceException.class,()->service.commit(task.getTaskId()));
        users.when(UserUtils::getUserId).thenReturn(null);assertThrows(ServiceException.class,()->service.template());
        users.when(UserUtils::getUserId).thenReturn("hr");db.update("DELETE FROM el_sys_role_menu");assertThrows(ServiceException.class,()->service.template());
        assertArrayEquals(new String[]{"exam:assignment:candidate:import"},CandidateImportController.class.getAnnotation(RequiresPermissions.class).value());
    }
    @Test void scopeAndStateAreRecheckedBeforeIssuanceAndDownload() throws Exception {
        var task=service.preview(file(List.of(row("A"))));db.update("UPDATE el_position SET status=0");
        assertEquals(1,service.commit(task.getTaskId()).getFailureCount());verify(assignments,never()).createCandidate(any());
        db.update("UPDATE el_position SET status=1");task=service.preview(file(List.of(row("B"))));service.commit(task.getTaskId());String id=task.getTaskId();
        db.update("UPDATE el_sys_role SET data_scope=2");db.update("UPDATE el_sys_user SET dept_code='999'");
        assertThrows(ServiceException.class,()->service.report(id,true));assertThrows(ServiceException.class,()->service.commit(id));assertEquals(1,service.preview(file(List.of(row("C")))).getFailureCount());
    }
    @Test void expiredWindowInvalidDateMissingTemplateAndAmbiguityAreRowErrors() throws Exception {
        var past=row("A");past.set(7,"2000-01-01 09:00:00");past.set(8,"2000-01-02 09:00:00");
        var invalid=row("B");invalid.set(7,"2099-02-30 09:00:00");assertEquals(2,service.preview(file(List.of(past,invalid))).getFailureCount());
        db.update("UPDATE el_exam SET template_status=0");assertEquals(1,service.preview(file(List.of(row("C")))).getFailureCount());
        db.update("UPDATE el_exam SET template_status=1");db.update("INSERT INTO el_exam SELECT 'second',title,depart_id,position_id,scene_type,template_status FROM el_exam");assertEquals(1,service.preview(file(List.of(row("D")))).getFailureCount());
    }
    @Test void changedDatabaseDuplicateIsSkippedAtConfirm() throws Exception {
        var task=service.preview(file(List.of(row("A"))));guard.reserve("a","test-batch");assertEquals(1,service.commit(task.getTaskId()).getDuplicateCount());verify(assignments,never()).createCandidate(any());
    }
    @Test void databaseGuardSerializesConcurrentReservationsAndRollsBack() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(4);var tx=new TransactionTemplate(new DataSourceTransactionManager(database));
        try {
            List<Callable<Boolean>> calls=new ArrayList<>(); for(int i=0;i<4;i++) calls.add(()->{try {tx.execute(s->{guard.reserve("A","B");return null;});return true;} catch(CandidateIdentityGuard.DuplicateCandidateException ex){return false;}});
            int successes=0;for(var f:pool.invokeAll(calls,10,TimeUnit.SECONDS)) if(f.get()) successes++;assertEquals(1,successes);
            assertThrows(ServiceException.class,()->tx.execute(s->{guard.reserve("C","D");throw new ServiceException("rollback");}));assertFalse(guard.exists("C","D"));
        } finally {pool.shutdownNow();assertTrue(pool.awaitTermination(2,TimeUnit.SECONDS));}
    }
    @Test void workbookRejectsFormulaOversizeAndKeepsLeadingZerosAndLiteralStrings() throws Exception {
        var values=row("00001");values.set(0,"=1+1");var file=file(List.of(values));
        assertEquals("00001",CandidateImportWorkbook.read(file).get(0).getValues().get(1));
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(file.getBytes()))) {
            book.getSheet("候选人数据").getRow(1).getCell(0).setCellFormula("1+1");var out=new ByteArrayOutputStream();book.write(out);
            assertEquals(1,service.preview(new MockMultipartFile("file","test.xlsx","",out.toByteArray())).getFailureCount());
        }
        var preview=service.preview(file);var report=preview.getRows().get(0);report.setStatus("ERROR");report.setMessage("测试");
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(CandidateImportWorkbook.report(List.of(report),false)))) {assertEquals(CellType.STRING,book.getSheetAt(0).getRow(1).getCell(1).getCellType());}
        assertThrows(ServiceException.class,()->CandidateImportWorkbook.read(new MockMultipartFile("file","x.xls","",new byte[1])));
        assertThrows(ServiceException.class,()->CandidateImportWorkbook.read(new MockMultipartFile("file","x.xlsx","",new byte[5*1024*1024+1])));
        assertThrows(ServiceException.class,()->service.preview(file(Collections.nCopies(501,row("A")))));
        var bomb=new ByteArrayOutputStream();
        try(var zip=new java.util.zip.ZipOutputStream(bomb)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("xl/sharedStrings.xml"));
            byte[] block=new byte[1024*1024];for(int i=0;i<33;i++) zip.write(block);zip.closeEntry();
        }
        assertTrue(assertThrows(ServiceException.class,()->CandidateImportWorkbook.read(new MockMultipartFile("file","x.xlsx","",bomb.toByteArray()))).getMessage().contains("展开后过大"));
    }
    @Test void expiredTaskIsNotAccessible() throws Exception {
        var task=service.preview(file(List.of(row("A"))));
        var internal=archive.get(task.getTaskId(),"hr");internal.expires=0;archive.save(internal);
        assertThrows(ServiceException.class,()->service.commit(task.getTaskId()));service.cleanup();
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM el_candidate_import_task",Integer.class));
    }

    CandidateImportService restarted(String pepper) {
        return new CandidateImportService(db,assignments,guard,factory.getValidator(),
                new CandidateImportArchive(db,new ObjectMapper(),manager,pepper),codes);
    }
    @Test void closeAndRestartRestoreSameCodesAndSameFileDoesNotReissue() throws Exception {
        var upload=file(List.of(row("A")));var preview=service.preview(upload);service.commit(preview.getTaskId());
        service.close(preview.getTaskId());var next=restarted(PEPPER);
        var restored=next.restore(null);assertEquals(preview.getTaskId(),restored.getTaskId());
        assertTrue(restored.isCommitted());assertEquals("TEST23",restored.getRows().get(0).getAccessCode());
        assertTrue(restored.getExpiresAt()>System.currentTimeMillis()+29L*24*60*60_000);
        assertEquals(preview.getTaskId(),next.preview(upload).getTaskId());
        next.commit(preview.getTaskId());verify(assignments,times(1)).createCandidate(any());
        String ciphertext=db.queryForObject("SELECT payload FROM el_candidate_import_task",String.class);
        assertTrue(ciphertext.startsWith("v1:"));assertFalse(ciphertext.contains("TEST23"));assertFalse(ciphertext.contains("demo@example.invalid"));
        users.when(UserUtils::getUserId).thenReturn("other");assertNull(next.restore(null));
        assertThrows(ServiceException.class,()->next.restore(preview.getTaskId()));
    }
    @Test void archiveFailureRollsBackIssuedAssignmentAndCanRetry() throws Exception {
        var task=service.preview(file(List.of(row("A"))));
        doThrow(new ServiceException("模拟归档写入失败")).when(archive).save(any());
        assertThrows(ServiceException.class,()->service.commit(task.getTaskId()));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM el_exam_assignment",Integer.class));assertFalse(guard.exists("A","TEST-BATCH"));
        doCallRealMethod().when(archive).save(any());assertEquals(1,service.commit(task.getTaskId()).getSuccessCount());
    }
    @Test void interruptedPartialImportResumesOnlyUnprocessedRowsAfterRestart() throws Exception {
        var task=service.preview(file(List.of(row("A"),row("B"))));var writes=new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(call->{if(writes.incrementAndGet()==2)throw new ServiceException("模拟中断");return call.callRealMethod();}).when(archive).save(any());
        assertThrows(ServiceException.class,()->service.commit(task.getTaskId()));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM el_exam_assignment",Integer.class));
        service.close(task.getTaskId());var restored=restarted(PEPPER);assertEquals(1,restored.restore(task.getTaskId()).getSuccessCount());
        assertEquals(2,restored.commit(task.getTaskId()).getSuccessCount());
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM el_exam_assignment",Integer.class));
    }
    @Test void wrongKeyCiphertextTamperingAndOwnerReplayFailClosed() throws Exception {
        var task=service.preview(file(List.of(row("A"))));service.commit(task.getTaskId());
        assertThrows(ServiceException.class,()->restarted(PEPPER+"different").restore(task.getTaskId()));
        String payload=db.queryForObject("SELECT payload FROM el_candidate_import_task WHERE id=?",String.class,task.getTaskId());
        db.update("UPDATE el_candidate_import_task SET payload=? WHERE id=?",payload.substring(0,20)+(payload.charAt(20)=='A'?'B':'A')+payload.substring(21),task.getTaskId());
        assertThrows(ServiceException.class,()->service.restore(task.getTaskId()));
        db.update("UPDATE el_candidate_import_task SET payload=?,owner_id='other' WHERE id=?",payload,task.getTaskId());
        users.when(UserUtils::getUserId).thenReturn("other");assertThrows(ServiceException.class,()->service.restore(task.getTaskId()));
    }
    @Test void resetDisabledOrExpiredCodesAreRedactedFromRestoredResults() throws Exception {
        var task=service.preview(file(List.of(row("A"),row("B"),row("C"))));service.commit(task.getTaskId());
        db.update("UPDATE el_exam_assignment SET access_code_lookup='changed' WHERE id='A'");
        db.update("UPDATE el_exam_assignment SET status='DISABLED' WHERE id='B'");
        db.update("UPDATE el_exam_assignment SET expire_at=TIMESTAMP '2000-01-01 00:00:00' WHERE id='C'");
        assertTrue(service.restore(task.getTaskId()).getRows().stream().allMatch(r->r.getAccessCode()==null));
        assertNotNull(service.report(task.getTaskId(),true));
    }
    @Test void separateServiceInstancesCommitSameTaskWithoutDuplicateIssuance() throws Exception {
        var task=service.preview(file(List.of(row("A"),row("B"))));
        ExecutorService pool=Executors.newFixedThreadPool(3);
        try {
            List<Callable<Integer>> calls=new ArrayList<>();
            for(int i=0;i<3;i++) calls.add(()->{try(var current=mockStatic(UserUtils.class)){
                current.when(UserUtils::getUserId).thenReturn("hr");return restarted(PEPPER).commit(task.getTaskId()).getSuccessCount();
            }});
            for(var f:pool.invokeAll(calls,10,TimeUnit.SECONDS)) assertEquals(2,f.get());
            assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM el_exam_assignment",Integer.class));verify(assignments,times(2)).createCandidate(any());
        } finally {pool.shutdownNow();assertTrue(pool.awaitTermination(2,TimeUnit.SECONDS));}
    }
}
