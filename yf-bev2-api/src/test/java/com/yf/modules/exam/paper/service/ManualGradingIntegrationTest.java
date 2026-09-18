package com.yf.modules.exam.paper.service;

import com.yf.base.api.exception.ServiceException;
import com.yf.modules.exam.grading.*;
import com.yf.modules.exam.grading.dto.*;
import com.yf.modules.exam.exam.service.ExamRecordService;
import com.yf.modules.exam.paper.dto.request.PaperTextAnswerDTO;
import com.yf.modules.exam.repo.dto.request.RepoQuDetailDTO;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Timeout(15)
class ManualGradingIntegrationTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate db;
    GradingService grading;
    PaperQuService questions;
    PaperService papers;
    ExamRecordService records;

    @Configuration static class Extra {
        @Bean JdbcTemplate jdbc(DataSource source) { return new JdbcTemplate(source); }
        @Bean GradingService grading(JdbcTemplate db,PaperAccessService access,ExamRecordService records) { return new GradingService(db,access,records); }
    }
    @BeforeEach void setup() {
        context=new AnnotationConfigApplicationContext(PaperSafetyIntegrationTest.Config.class,Extra.class);
        db=context.getBean(JdbcTemplate.class);grading=context.getBean(GradingService.class);
        questions=context.getBean(PaperQuService.class);papers=context.getBean(PaperService.class);records=context.getBean(ExamRecordService.class);
        db.execute("CREATE TABLE el_sys_user(id varchar(64) PRIMARY KEY,real_name varchar(128))");
        db.execute("CREATE TABLE el_exam(id varchar(64),scene_type varchar(32))");
        db.execute("CREATE TABLE el_position(id varchar(64),name varchar(128))");
        db.execute("CREATE TABLE el_sys_depart(id varchar(64),dept_name varchar(128))");
        db.execute("CREATE TABLE el_paper_grading_log(id varchar(64) PRIMARY KEY,paper_id varchar(64),paper_qu_id varchar(64),grader_id varchar(64),score_before decimal(10,2),score_after decimal(10,2),comment_before varchar(2000),comment_after varchar(2000),action varchar(32),create_time timestamp)");
        db.update("INSERT INTO el_sys_user VALUES('u','Examinee'),('g','Grader')");
        db.update("INSERT INTO el_exam VALUES('e','REGULARIZATION')");
        db.update("INSERT INTO el_exam_assignment(id,user_id,exam_id,status,paper_id,expire_at) VALUES('a','u','e','STARTED','p',DATEADD('DAY',1,CURRENT_TIMESTAMP))");
        db.update("INSERT INTO el_paper(id,user_id,exam_id,assignment_id,title,total_score,qualify_score,create_time,limit_time) VALUES('p','u','e','a','Manual review',20,12,DATEADD('MINUTE',-5,CURRENT_TIMESTAMP),DATEADD('HOUR',1,CURRENT_TIMESTAMP))");
        db.update("INSERT INTO el_paper_qu(id,paper_id,qu_id,qu_type,score,actual_score,grading_state,content_snapshot,reference_answer_snapshot,grading_criteria_snapshot,sort) VALUES('short','p','q','short',10,0,'PENDING','Frozen prompt','Reference','Rubric',2),('obj','p','o','radio',10,10,'NOT_REQUIRED','Objective',NULL,NULL,1)");
    }
    @AfterEach void cleanup() { context.close(); }
    PaperTextAnswerDTO text(String answer) { var r=new PaperTextAnswerDTO();r.setPaperId("p");r.setQuId("q");r.setAnswerText(answer);return r; }
    GradeSaveDTO grade(String score,long version) { var r=new GradeSaveDTO();r.setPaperQuId("short");r.setScore(new BigDecimal(score));r.setExpectedVersion(version);r.setComment("Reason");return r; }
    GradeFinalizeDTO finish(long version) { var r=new GradeFinalizeDTO();r.setPaperId("p");r.setExpectedVersion(version);return r; }
    void submit() { papers.handPaper("p","u"); }
    long logs() { return db.queryForObject("SELECT COUNT(*) FROM el_paper_grading_log",Long.class); }

    @Test void textAnswerRestoresExactlyAndNeverExposesReferenceOrGrades() {
        String answer="A <script>literal text</script>\n第二行";
        assertTrue(questions.fillTextAnswer(text(answer),"u").getFilled());
        var result=questions.detailForAnswer("p","q","u");
        assertEquals(answer,result.getTextAnswer());assertNull(result.getReferenceAnswer());assertNull(result.getGraderComment());assertNull(result.getActualScore());assertNull(result.getAnalysis());
        assertFalse(questions.fillTextAnswer(text("   "),"u").getFilled());
        assertThrows(ServiceException.class,()->questions.fillTextAnswer(text(null),"u"));
        assertThrows(ServiceException.class,()->questions.fillTextAnswer(text("x".repeat(5001)),"u"));
    }
    @Test void textAnswerRejectsCrossUserWrongTypeExpiredDisabledAndSubmittedStates() {
        assertThrows(ServiceException.class,()->questions.fillTextAnswer(text("answer"),"v"));
        var wrong=text("answer");wrong.setQuId("o");assertThrows(ServiceException.class,()->questions.fillTextAnswer(wrong,"u"));
        db.update("UPDATE el_exam_assignment SET status='DISABLED'");assertThrows(ServiceException.class,()->questions.fillTextAnswer(text("answer"),"u"));
        db.update("UPDATE el_exam_assignment SET status='STARTED'");
        db.update("UPDATE el_paper SET limit_time=DATEADD('SECOND',-1,CURRENT_TIMESTAMP)");assertThrows(ServiceException.class,()->questions.fillTextAnswer(text("answer"),"u"));
        submit();assertThrows(ServiceException.class,()->questions.fillTextAnswer(text("answer"),"u"));
    }
    @Test void shortSnapshotNeedsNoOptionsAndPersistsReferenceWithoutLiveBank() {
        var q=new RepoQuDetailDTO();q.setId("q2");q.setQuType("short");q.setContent("Snapshot");q.setReferenceAnswer("Answer snapshot");q.setGradingCriteria("Criteria snapshot");
        questions.saveToPaper("p",BigDecimal.TEN,List.of(q),3,true);
        assertEquals("Answer snapshot",db.queryForObject("SELECT reference_answer_snapshot FROM el_paper_qu WHERE qu_id='q2'",String.class));
        assertEquals("PENDING",db.queryForObject("SELECT grading_state FROM el_paper_qu WHERE qu_id='q2'",String.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM el_paper_qu_answer",Integer.class));
    }
    @Test void gradingRequiresSubmissionAndEveryQuestionIncludingZeroMustBeSaved() {
        assertThrows(ServiceException.class,()->grading.save(grade("5",0),"g"));submit();
        assertThrows(ServiceException.class,()->grading.finalizePaper(finish(0),"g"));
        assertThrows(ServiceException.class,()->grading.save(grade("-1",0),"g"));
        assertThrows(ServiceException.class,()->grading.save(grade("11",0),"g"));
        assertThrows(ServiceException.class,()->grading.save(grade("1.234",0),"g"));
        var wrong=grade("1",0);wrong.setPaperQuId("obj");assertThrows(ServiceException.class,()->grading.save(wrong,"g"));
        grading.save(grade("0",0),"g");grading.finalizePaper(finish(1),"g");
        assertFalse(db.queryForObject("SELECT passed FROM el_paper",Boolean.class));
        assertEquals(0,new BigDecimal("10").compareTo(db.queryForObject("SELECT user_score FROM el_paper",BigDecimal.class)));
    }
    @Test void scoringIsAuditedRetriesAreIdempotentAndStaleEditsAreRejected() {
        submit();grading.save(grade("2.5",0),"g");grading.save(grade("2.5",0),"g");assertEquals(1,logs());
        assertThrows(ServiceException.class,()->grading.save(grade("4",0),"g"));
        grading.save(grade("4",1),"g");assertEquals(2,logs());
        assertEquals("REGRADE",db.queryForObject("SELECT action FROM el_paper_grading_log WHERE score_before IS NOT NULL",String.class));
        assertFalse(context.getBean(com.yf.modules.exam.assignment.service.ExamAssignmentService.class).myResult("a","u").isResultAvailable());
        assertThrows(ServiceException.class,()->grading.finalizePaper(finish(1),"g"));
        grading.finalizePaper(finish(2),"g");
        assertTrue(context.getBean(com.yf.modules.exam.assignment.service.ExamAssignmentService.class).myResult("a","u").getPassed());
        assertEquals("COMPLETED",db.queryForObject("SELECT status FROM el_exam_assignment",String.class));
        assertThrows(ServiceException.class,()->grading.save(grade("5",3),"g"));
    }
    @Test void failedAuditInsertRollsBackScoreAndVersion() {
        submit();db.execute("ALTER TABLE el_paper_grading_log ADD CONSTRAINT no_log CHECK(action<>'GRADE')");
        assertThrows(RuntimeException.class,()->grading.save(grade("4",0),"g"));
        assertEquals("PENDING",db.queryForObject("SELECT grading_state FROM el_paper_qu WHERE id='short'",String.class));
        assertEquals(0,db.queryForObject("SELECT grading_version FROM el_paper",Long.class));
    }
    @Test void failedFinalRecordRollsBackPublicationAndAudit() {
        submit();grading.save(grade("4",0),"g");doThrow(new RuntimeException("record failure")).when(records).joinRecord(any(),any(),any(),any(),any());
        assertThrows(RuntimeException.class,()->grading.finalizePaper(finish(1),"g"));
        assertEquals("PENDING",db.queryForObject("SELECT grading_state FROM el_paper",String.class));
        assertEquals("PENDING_REVIEW",db.queryForObject("SELECT status FROM el_exam_assignment",String.class));assertEquals(1,logs());
    }
    @Test void concurrentFinalizeWritesOneRecordAndOneAudit() throws Exception {
        submit();grading.save(grade("4",0),"g");ExecutorService workers=Executors.newFixedThreadPool(2);
        try { Future<?> a=workers.submit(()->grading.finalizePaper(finish(1),"g"));Future<?> b=workers.submit(()->grading.finalizePaper(finish(1),"g"));a.get(5,TimeUnit.SECONDS);b.get(5,TimeUnit.SECONDS); }
        finally { workers.shutdownNow();assertTrue(workers.awaitTermination(3,TimeUnit.SECONDS)); }
        verify(records,times(1)).joinRecord(eq("e"),eq("u"),eq("p"),eq(new BigDecimal("14.00")),eq(true));assertEquals(2,logs());
    }
    @Test void disabledAndIncompleteLegacyPapersCannotBeFinalized() {
        submit();db.update("UPDATE el_exam_assignment SET status='DISABLED'");assertThrows(ServiceException.class,()->grading.save(grade("3",0),"g"));
        db.update("UPDATE el_exam_assignment SET status='PENDING_REVIEW'");db.update("UPDATE el_paper SET snapshot_source='LEGACY_INCOMPLETE'");assertThrows(ServiceException.class,()->grading.save(grade("3",0),"g"));
    }
    @Test void pagingAndDetailAreManagementProjectionsAndPermissionsRemainIndependent() throws Exception {
        submit();assertEquals(1L,grading.paging(new GradingQueryDTO()).get("total"));assertNotNull(grading.detail("p").get("questions"));
        var method=GradingController.class.getMethod("save",GradeSaveDTO.class);
        assertArrayEquals(new String[]{"exam:grading:view","exam:grading:score"},method.getAnnotation(org.apache.shiro.authz.annotation.RequiresPermissions.class).value());
    }
}
