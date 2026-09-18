package com.yf.modules.exam.paper.service;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.yf.ability.task.service.JobService;
import com.yf.base.api.exception.ServiceException;
import com.yf.modules.exam.assignment.entity.ExamAssignment;
import com.yf.modules.exam.assignment.mapper.ExamAssignmentMapper;
import com.yf.modules.exam.assignment.service.AccessCodeManager;
import com.yf.modules.exam.assignment.service.ExamAssignmentService;
import com.yf.modules.exam.assignment.service.impl.ExamAssignmentServiceImpl;
import com.yf.modules.exam.exam.dto.ExamRuleDTO;
import com.yf.modules.exam.exam.entity.Exam;
import com.yf.modules.exam.exam.service.*;
import com.yf.modules.exam.paper.dto.request.PaperQuFillReqDTO;
import com.yf.modules.exam.paper.entity.Paper;
import com.yf.modules.exam.paper.mapper.*;
import com.yf.modules.exam.paper.service.impl.*;
import com.yf.modules.exam.position.service.PositionService;
import com.yf.modules.exam.repo.dto.RepoQuAnswerDTO;
import com.yf.modules.exam.repo.dto.request.RepoQuDetailDTO;
import com.yf.modules.exam.repo.service.RepoQuService;
import com.yf.system.modules.user.service.*;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Timeout(15)
class PaperSafetyIntegrationTest {
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate db;
    private PaperService papers;
    private PaperQuService questions;
    private ExamAssignmentService assignments;
    private ExamRecordService records;
    private ExecutorService workers;

    @BeforeEach void setUp() {
        context = new AnnotationConfigApplicationContext(Config.class);
        db = new JdbcTemplate(context.getBean(DataSource.class));
        papers = context.getBean(PaperService.class);
        questions = context.getBean(PaperQuService.class);
        assignments = context.getBean(ExamAssignmentService.class);
        records = context.getBean(ExamRecordService.class);
        workers = Executors.newFixedThreadPool(2);
        db.update("INSERT INTO el_exam_assignment(id,exam_id,user_id,subject_type,status,paper_id,valid_from,expire_at) VALUES('a','e','u','CANDIDATE','STARTED','p',DATEADD('DAY',-1,CURRENT_TIMESTAMP),DATEADD('DAY',1,CURRENT_TIMESTAMP))");
        db.update("INSERT INTO el_paper(id,user_id,exam_id,assignment_id,title,qualify_score,total_score,user_score,limit_time,create_time) VALUES('p','u','e','a','Exam',1,1,0,DATEADD('HOUR',1,CURRENT_TIMESTAMP),DATEADD('MINUTE',-5,CURRENT_TIMESTAMP))");
        db.update("INSERT INTO el_paper_qu(id,paper_id,qu_id,qu_type,answered,score,actual_score,sort,content_snapshot,analysis_snapshot) VALUES('pq','p','q','radio',false,1,0,1,'Original question','Private analysis')");
        db.update("INSERT INTO el_paper_qu_answer(id,paper_id,qu_id,answer_id,is_right,checked,sort,abc,content_snapshot) VALUES('pa','p','q','answer',true,false,0,'A','Original answer')");
    }

    @AfterEach void tearDown() throws Exception {
        workers.shutdownNow();
        assertTrue(workers.awaitTermination(3, TimeUnit.SECONDS));
        context.close();
    }

    @Test void snapshotReadsWorkWithoutAnyQuestionBankTablesAndDoNotExposeKeys() {
        var detail = questions.detailForAnswer("p", "q", "u");
        assertEquals("Original question", detail.getContent());
        assertEquals("Original answer", detail.getAnswerList().get(0).getContent());
        assertNull(detail.getAnalysis());
        assertNull(detail.getIsRight());
        assertNull(detail.getAnswerList().get(0).getIsRight());
        assertEquals("Private analysis", papers.fullDetail("p").getQuList().get(0).getAnalysis());
        assertTrue(papers.fullDetail("p").getQuList().get(0).getAnswerList().get(0).getIsRight());
    }

    @Test void shortAnswerAndForeignOptionsCannotReceiveObjectiveMarks() {
        db.update("UPDATE el_paper_qu SET qu_type='short'");
        assertThrows(ServiceException.class, () -> questions.fillAnswer(answer(List.of()), "u"));
        db.update("UPDATE el_paper_qu SET qu_type='radio'");
        assertThrows(ServiceException.class, () -> questions.fillAnswer(answer(List.of("foreign")), "u"));
        assertThrows(ServiceException.class, () -> questions.fillAnswer(answer(List.of("answer","answer")), "u"));
        assertThrows(ServiceException.class, () -> questions.fillAnswer(answer(null), "u"));
        assertEquals(0, db.queryForObject("SELECT actual_score FROM el_paper_qu", BigDecimal.class).signum());
    }

    @Test void ownerDeadlineAndDisabledAssignmentAreEnforced() {
        assertThrows(ServiceException.class, () -> questions.fillAnswer(answer(List.of("answer")), "other"));
        assertThrows(ServiceException.class, () -> papers.handPaper("p", "other"));
        assertThrows(ServiceException.class, () -> papers.handPaper("p", null));
        db.update("UPDATE el_exam_assignment SET status='DISABLED'");
        assertThrows(ServiceException.class, () -> questions.fillAnswer(answer(List.of("answer")), "u"));
        assertThrows(ServiceException.class, () -> papers.handPaper("p", "u"));
        db.update("UPDATE el_exam_assignment SET status='STARTED'");
        db.update("UPDATE el_paper SET limit_time=DATEADD('SECOND',-1,CURRENT_TIMESTAMP)");
        assertThrows(ServiceException.class, () -> questions.fillAnswer(answer(List.of("answer")), "u"));
    }

    @Test void answerWritesRollbackTogetherIfScoringUpdateFails() {
        db.execute("ALTER TABLE el_paper_qu ADD CONSTRAINT no_positive_score CHECK(actual_score <= 0)");
        assertThrows(RuntimeException.class, () -> questions.fillAnswer(answer(List.of("answer")), "u"));
        assertFalse(db.queryForObject("SELECT checked FROM el_paper_qu_answer", Boolean.class));
        assertEquals(0, db.queryForObject("SELECT actual_score FROM el_paper_qu", BigDecimal.class).signum());
    }

    @Test void duplicateConcurrentSubmissionsProduceOneSettlement() throws Exception {
        questions.fillAnswer(answer(List.of("answer")), "u");
        CountDownLatch go = new CountDownLatch(1);
        Future<?> first = workers.submit(() -> { await(go); papers.handPaper("p", "u"); });
        Future<?> second = workers.submit(() -> { await(go); papers.handPaper("p", "u"); });
        go.countDown(); first.get(5, TimeUnit.SECONDS); second.get(5, TimeUnit.SECONDS);
        verify(records, times(1)).joinRecord(eq("e"),eq("u"),eq("p"),any(),eq(true));
        assertEquals("COMPLETED", db.queryForObject("SELECT status FROM el_exam_assignment", String.class));
        assertThrows(ServiceException.class, () -> questions.fillAnswer(answer(List.of()), "u"));
    }

    @Test void submitWaitsForInFlightSaveAndReadsItsCommittedScore() throws Exception {
        CountDownLatch saved = new CountDownLatch(1), release = new CountDownLatch(1);
        TransactionTemplate tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        Future<?> saving = workers.submit(() -> tx.executeWithoutResult(status -> {
            questions.fillAnswer(answer(List.of("answer")), "u");
            saved.countDown(); await(release);
        }));
        assertTrue(saved.await(3, TimeUnit.SECONDS));
        Future<?> submit = workers.submit(() -> papers.handPaper("p", "u"));
        try { assertThrows(TimeoutException.class, () -> submit.get(150, TimeUnit.MILLISECONDS)); }
        finally { release.countDown(); }
        saving.get(5, TimeUnit.SECONDS); submit.get(5, TimeUnit.SECONDS);
        assertEquals(0, BigDecimal.ONE.compareTo(db.queryForObject("SELECT user_score FROM el_paper", BigDecimal.class)));
    }

    @Test void inFlightSubmitPreventsALaterAnswerWrite() throws Exception {
        CountDownLatch settling = new CountDownLatch(1), release = new CountDownLatch(1);
        doAnswer(inv -> { settling.countDown(); await(release); return null; })
                .when(records).joinRecord(any(),any(),any(),any(),any());
        Future<?> submit = workers.submit(() -> papers.handPaper("p", "u"));
        assertTrue(settling.await(3, TimeUnit.SECONDS));
        Future<?> saving = workers.submit(() -> questions.fillAnswer(answer(List.of("answer")), "u"));
        try { assertThrows(TimeoutException.class, () -> saving.get(150, TimeUnit.MILLISECONDS)); }
        finally { release.countDown(); }
        submit.get(5, TimeUnit.SECONDS);
        assertInstanceOf(ServiceException.class, assertThrows(ExecutionException.class,
                () -> saving.get(5, TimeUnit.SECONDS)).getCause());
        assertFalse(db.queryForObject("SELECT checked FROM el_paper_qu_answer", Boolean.class));
    }

    @Test void automaticAndManualDeadlineSubmissionIgnoreMinimumAndRemainIdempotent() throws Exception {
        db.update("UPDATE el_paper SET hand_min_snapshot=60,limit_time=DATEADD('SECOND',-1,CURRENT_TIMESTAMP)");
        Future<?> automatic = workers.submit(() -> papers.handPaper("p"));
        Future<?> manual = workers.submit(() -> papers.handPaper("p", "u"));
        automatic.get(5, TimeUnit.SECONDS); manual.get(5, TimeUnit.SECONDS);
        assertEquals(1, db.queryForObject("SELECT hand_state FROM el_paper", Integer.class));
        verify(records,times(1)).joinRecord(any(),any(),any(),any(),any());
    }

    @Test void earlyTimerCannotSubmitAndManualMinimumStillApplies() {
        db.update("UPDATE el_paper SET hand_min_snapshot=60");
        papers.handPaper("p");
        assertThrows(ServiceException.class, () -> papers.handPaper("p", "u"));
        assertEquals(0, db.queryForObject("SELECT hand_state FROM el_paper", Integer.class));
        verifyNoInteractions(records);
    }

    @Test void timerDoesNotReactivateDisabledAssignment() {
        db.update("UPDATE el_exam_assignment SET status='DISABLED'");
        db.update("UPDATE el_paper SET limit_time=DATEADD('SECOND',-1,CURRENT_TIMESTAMP)");
        papers.handPaper("p");
        assertEquals("DISABLED", db.queryForObject("SELECT status FROM el_exam_assignment", String.class));
        assertEquals(1, db.queryForObject("SELECT hand_state FROM el_paper", Integer.class));
    }

    @Test void legacySubjectivePaperClosesWithoutPublishingFinalResult() {
        db.update("UPDATE el_paper_qu SET qu_type='short', actual_score=1");
        papers.handPaper("p", "u");
        assertEquals("PENDING", db.queryForObject("SELECT grading_state FROM el_paper", String.class));
        assertNull(db.queryForObject("SELECT passed FROM el_paper", Boolean.class));
        assertEquals("PENDING_REVIEW", db.queryForObject("SELECT status FROM el_exam_assignment", String.class));
        assertFalse(assignments.myResult("a", "u").isResultAvailable());
        verifyNoInteractions(records);
    }

    @Test void legacyTemplateEntryIsClosed() {
        assertThrows(ServiceException.class, () -> papers.createPaper("e", "u"));
        assertThrows(ServiceException.class, () -> papers.preCheck("e", "u"));
    }

    @Test void newPaperFreezesContentAndConcurrentStartReturnsSamePaper() throws Exception {
        db.update("UPDATE el_exam_assignment SET paper_id=NULL,status='ASSIGNED'");
        db.update("DELETE FROM el_paper_qu_answer"); db.update("DELETE FROM el_paper_qu"); db.update("DELETE FROM el_paper");
        Exam exam = new Exam(); exam.setId("e"); exam.setTitle("Exam"); exam.setTemplateStatus(1);
        exam.setQualifyScore(BigDecimal.ONE); exam.setTotalScore(BigDecimal.ONE); exam.setTotalTime(30); exam.setOptionShuffle(1);
        when(context.getBean(ExamService.class).getById("e")).thenReturn(exam);
        ExamRuleDTO rule = new ExamRuleDTO(); rule.setQuType("radio"); rule.setRepoId("repo"); rule.setQuCount(1); rule.setQuScore(BigDecimal.ONE);
        when(context.getBean(ExamRuleService.class).listByExam("e")).thenReturn(List.of(rule));
        RepoQuDetailDTO question = new RepoQuDetailDTO(); question.setId("q"); question.setQuType("radio"); question.setContent("Frozen question"); question.setAnalysis("Frozen analysis");
        RepoQuAnswerDTO option = new RepoQuAnswerDTO(); option.setId("answer"); option.setContent("Frozen answer"); option.setIsRight(true);
        question.setAnswerList(List.of(option));
        when(context.getBean(RepoQuService.class).listForPaper("repo","radio",1)).thenReturn(List.of(question));
        Future<String> a = workers.submit(() -> assignments.start("a", "u").getPaperId());
        Future<String> b = workers.submit(() -> assignments.start("a", "u").getPaperId());
        String paperId = a.get(5, TimeUnit.SECONDS); assertEquals(paperId,b.get(5, TimeUnit.SECONDS));
        option.setContent("Changed answer"); question.setContent("Changed question");
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM el_paper", Integer.class));
        assertEquals("Frozen question", questions.detailForAnswer(paperId,"q","u").getContent());
        assertEquals("Frozen answer", questions.detailForAnswer(paperId,"q","u").getAnswerList().get(0).getContent());
        assertThrows(ServiceException.class, () -> assignments.start("a", "other"));
        papers.handPaper(paperId,"u");
        assertThrows(ServiceException.class, () -> assignments.start("a", "u"));
    }

    @Test void rejectedUnknownTemplateRollsBackNewPaperAndLeavesAssignmentUnstarted() {
        db.update("UPDATE el_exam_assignment SET paper_id=NULL,status='ASSIGNED'");
        db.update("DELETE FROM el_paper_qu_answer"); db.update("DELETE FROM el_paper_qu"); db.update("DELETE FROM el_paper");
        Exam exam = new Exam(); exam.setId("e"); exam.setTitle("Exam"); exam.setTemplateStatus(1);
        exam.setTotalScore(BigDecimal.ONE); exam.setQualifyScore(BigDecimal.ONE);
        when(context.getBean(ExamService.class).getById("e")).thenReturn(exam);
        ExamRuleDTO rule = new ExamRuleDTO(); rule.setQuType("unknown"); rule.setQuCount(1); rule.setQuScore(BigDecimal.ONE);
        when(context.getBean(ExamRuleService.class).listByExam("e")).thenReturn(List.of(rule));
        assertThrows(ServiceException.class, () -> assignments.start("a", "u"));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM el_paper", Integer.class));
        assertEquals("ASSIGNED", db.queryForObject("SELECT status FROM el_exam_assignment", String.class));
        assertNull(db.queryForObject("SELECT paper_id FROM el_exam_assignment", String.class));
        verifyNoInteractions(context.getBean(JobService.class));
    }

    @Test void employeeAssignmentResumesOnlyItsExistingUnexpiredPaper() {
        db.update("UPDATE el_exam_assignment SET subject_type='EMPLOYEE'");
        assertEquals("p", assignments.start("a", "u").getPaperId());
        assertThrows(ServiceException.class, () -> assignments.start("a", "other"));
        db.update("UPDATE el_paper SET limit_time=DATEADD('SECOND',-1,CURRENT_TIMESTAMP)");
        assertThrows(ServiceException.class, () -> assignments.start("a", "u"));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM el_paper", Integer.class));
    }

    private PaperQuFillReqDTO answer(List<String> options) {
        PaperQuFillReqDTO dto = new PaperQuFillReqDTO(); dto.setPaperId("p"); dto.setQuId("q"); dto.setCheckedItems(options); return dto;
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(5,TimeUnit.SECONDS)) throw new AssertionError("Latch timed out"); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new RuntimeException(ex); }
    }

    @Configuration @EnableTransactionManagement
    @MapperScan(basePackageClasses = {PaperMapper.class, ExamAssignmentMapper.class})
    static class Config {
        @Bean DataSource dataSource() {
            JdbcDataSource ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
            new ResourceDatabasePopulator(new ClassPathResource("paper-safety-schema.sql")).execute(ds);
            return ds;
        }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds);
            var resolver = new PathMatchingResourcePatternResolver();
            List<Resource> resources = new ArrayList<>();
            resources.addAll(Arrays.asList(resolver.getResources("classpath:mapper/modules/exam/paper/*.xml")));
            resources.addAll(Arrays.asList(resolver.getResources("classpath:mapper/modules/exam/assignment/*.xml")));
            factory.setMapperLocations(resources.toArray(Resource[]::new));
            return factory.getObject();
        }
        @Bean ExamService examService() { return mock(ExamService.class); }
        @Bean ExamRuleService ruleService() { return mock(ExamRuleService.class); }
        @Bean RepoQuService repoService() { return mock(RepoQuService.class); }
        @Bean ExamRecordService records() { return mock(ExamRecordService.class); }
        @Bean JobService jobService() { return mock(JobService.class); }
        @Bean PaperAccessService access(PaperMapper p, ExamAssignmentMapper a) { return new PaperAccessService(p,a); }
        @Bean PaperQuAnswerService answers() { return new PaperQuAnswerServiceImpl(); }
        @Bean PaperQuService questions(PaperQuAnswerService a, PaperAccessService p) { return new PaperQuServiceImpl(a,p); }
        @Bean PaperService papers(ExamService e, ExamRuleService r, RepoQuService q, PaperQuService pq,
                ExamRecordService records, JobService j, PaperAccessService access, ExamAssignmentMapper a) {
            return new PaperServiceImpl(e,r,q,pq,records,j,access,a);
        }
        @Bean ExamAssignmentService assignments(ExamService e, PaperService p) {
            return new ExamAssignmentServiceImpl(mock(PositionService.class),e,mock(SysUserService.class),mock(SysUserRoleService.class),p,mock(AccessCodeManager.class));
        }
    }
}
