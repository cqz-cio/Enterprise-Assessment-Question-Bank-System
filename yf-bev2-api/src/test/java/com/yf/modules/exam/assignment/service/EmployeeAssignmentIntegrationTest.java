package com.yf.modules.exam.assignment.service;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.yf.base.api.exception.ServiceException;
import com.yf.modules.exam.assignment.dto.request.*;
import com.yf.modules.exam.assignment.mapper.ExamAssignmentMapper;
import com.yf.modules.exam.exam.dto.ExamRuleDTO;
import com.yf.modules.exam.exam.entity.Exam;
import com.yf.modules.exam.exam.service.*;
import com.yf.modules.exam.paper.entity.Paper;
import com.yf.modules.exam.paper.service.PaperService;
import com.yf.modules.exam.position.service.PositionService;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Timeout(15)
class EmployeeAssignmentIntegrationTest {
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate db;
    private EmployeeAssignmentService service;
    @BeforeEach void setup() {
        context=new AnnotationConfigApplicationContext(Config.class); db=context.getBean(JdbcTemplate.class); service=context.getBean(EmployeeAssignmentService.class);
        db.execute("CREATE TABLE el_exam(id varchar(64) PRIMARY KEY,title varchar(128),depart_id varchar(64),position_id varchar(64),scene_type varchar(32),total_time int,template_status int)");
        db.execute("CREATE TABLE el_sys_user(id varchar(64) PRIMARY KEY,real_name varchar(128),employee_no varchar(64),dept_code varchar(64),state int)");
        db.execute("CREATE TABLE el_sys_user_role(id varchar(64),user_id varchar(64),role_id varchar(64))");
        db.execute("CREATE TABLE el_sys_depart(id varchar(64),dept_name varchar(64),dept_code varchar(64),status int,dept_type int,parent_id varchar(64))");
        db.execute("CREATE TABLE el_position(id varchar(64),name varchar(64),status int,sort int)");
        db.execute("CREATE TABLE el_depart_position(depart_id varchar(64),position_id varchar(64))");
        db.execute("CREATE TABLE el_exam_rule(exam_id varchar(64),qu_type varchar(32),qu_count int)");
        db.execute("CREATE TABLE el_repo(id varchar(64),status int,depart_id varchar(64),position_id varchar(64),scene_type varchar(32))");
        db.execute("CREATE TABLE el_repo_qu(repo_id varchar(64),qu_type varchar(32),status int)");
        db.update("INSERT INTO el_repo VALUES('repo',1,'d','pos','REGULARIZATION')");
        db.update("INSERT INTO el_repo_qu VALUES('repo','radio',1)");
        db.execute("ALTER TABLE el_exam_assignment ADD CONSTRAINT employee_batch UNIQUE(user_id,exam_id,batch_no)");
        db.update("INSERT INTO el_sys_depart VALUES('d','Department','D',1,2,'root')");
        db.update("INSERT INTO el_position VALUES('pos','Position',1,0)");
        db.update("INSERT INTO el_depart_position VALUES('d','pos')");
        db.update("INSERT INTO el_exam VALUES('e','Template','d','pos','REGULARIZATION',30,1)");
        db.update("INSERT INTO el_exam_rule VALUES('e','radio',1)");
        db.update("INSERT INTO el_sys_user VALUES('u','Employee','E001','D',0),('v','Other','E002','D',0),('pending','Pending','E003','D',2),('foreign','Foreign','E004','OTHER',0),('candidate','Candidate',NULL,'D',0)");
        for (String id:List.of("u","v","pending","foreign")) db.update("INSERT INTO el_sys_user_role VALUES(?,?, 'EMPLOYEE')",id,id);
        db.update("INSERT INTO el_sys_user_role VALUES('c','candidate','CANDIDATE')");
        Exam exam=new Exam(); exam.setId("e"); exam.setTitle("Template"); exam.setDepartId("d"); exam.setPositionId("pos"); exam.setTemplateStatus(1);
        exam.setRepoId("repo"); exam.setSceneType("REGULARIZATION"); exam.setTotalTime(30); exam.setQualifyScore(BigDecimal.ONE);
        when(context.getBean(ExamService.class).getById("e")).thenReturn(exam);
        ExamRuleDTO rule=new ExamRuleDTO(); rule.setQuType("radio"); rule.setQuCount(1); rule.setQuScore(BigDecimal.TEN);
        rule.setRepoId("repo");
        when(context.getBean(ExamRuleService.class).listByExam("e")).thenReturn(List.of(rule));
    }
    @AfterEach void cleanup() { context.close(); }
    private EmployeeAssignmentCreateDTO request(String... ids) { var r=new EmployeeAssignmentCreateDTO(); r.setExamId("e"); r.setUserIds(List.of(ids)); r.setBatchNo("Batch"); return r; }
    private String create(String user) { return ((List<String>)service.create(request(user)).get("assignmentIds")).get(0); }
    private EmployeeAssignmentQueryDTO query() { var q=new EmployeeAssignmentQueryDTO(); q.setDepartId("d"); return q; }
    @SuppressWarnings("unchecked") private List<Map<String,Object>> rows(Map<String,Object> response) { return (List<Map<String,Object>>)response.get("records"); }

    @Test void issuanceDeduplicatesAndPreservesOriginalDatesAndNoCodes() {
        var first=service.create(request("u","u","v")); assertEquals(2,first.get("created"));
        var retry=request("u","v"); retry.setExpireAt(new Date(System.currentTimeMillis()+3600000));
        assertEquals(2,service.create(retry).get("existing"));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM el_exam_assignment WHERE access_code_hash IS NOT NULL OR access_code_lookup IS NOT NULL",Integer.class));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM el_exam_assignment WHERE expire_at>DATEADD('DAY',13,NOW())",Integer.class));
    }
    @Test void simultaneousFirstIssuanceCreatesOneNaturalKey() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(3); CountDownLatch go=new CountDownLatch(1);
        try {
            List<Future<Map<String,Object>>> futures=new ArrayList<>();
            for(int i=0;i<3;i++) futures.add(pool.submit(()->{ go.await(3,TimeUnit.SECONDS); return service.create(request("u")); }));
            go.countDown(); int created=0; for(var f:futures) created+=(Integer)f.get(8,TimeUnit.SECONDS).get("created");
            assertEquals(1,created); assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM el_exam_assignment",Integer.class));
        } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(2,TimeUnit.SECONDS)); }
    }
    @Test void invalidEmployeeRejectsEntireBatchAndRollbackProtectsPartialInsert() {
        for(String id:List.of("pending","foreign","candidate","missing")) assertThrows(ServiceException.class,()->service.create(request("u",id)));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM el_exam_assignment",Integer.class));
        db.execute("ALTER TABLE el_exam_assignment ADD CONSTRAINT fail_second CHECK(user_id<>'v')");
        assertThrows(RuntimeException.class,()->service.create(request("u","v")));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM el_exam_assignment",Integer.class));
    }
    @Test void disabledTemplateShortRulesAndExpiredWindowAreRejected() {
        Exam exam=context.getBean(ExamService.class).getById("e"); exam.setTemplateStatus(0);
        assertThrows(ServiceException.class,()->service.create(request("u"))); exam.setTemplateStatus(1);
        var rule=context.getBean(ExamRuleService.class).listByExam("e").get(0); rule.setQuType("short");
        assertThrows(ServiceException.class,()->service.create(request("u"))); rule.setQuType("radio");
        var req=request("u"); req.setExpireAt(new Date(0)); assertThrows(ServiceException.class,()->service.create(req));
    }
    @Test void optionsOnlyContainActiveEmployeesAndTemplatesAllowShortButRejectUnknownRules() {
        assertEquals(2L,service.employees(query()).get("total")); assertEquals(1,service.templates(query()).size());
        db.update("UPDATE el_exam_rule SET qu_type='short'"); assertEquals(1,service.templates(query()).size());
        db.update("UPDATE el_exam_rule SET qu_type='unknown'"); assertTrue(service.templates(query()).isEmpty());
    }
    @Test void insufficientQuestionBankFailsBeforeIssuance() {
        db.update("UPDATE el_repo_qu SET status=0");
        assertThrows(ServiceException.class,()->service.create(request("u")));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM el_exam_assignment",Integer.class));
    }
    @Test void ownerProjectionCountsAndPassOnlyResultsAreIsolated() {
        String id=create("u"); create("v");
        db.update("INSERT INTO el_paper(id,assignment_id,user_id,title,hand_state,grading_state,passed,total_time,user_score) VALUES('p',?,'u','Snapshot title',1,'NOT_REQUIRED',true,30,100)",id);
        db.update("UPDATE el_exam_assignment SET status='COMPLETED',paper_id='p' WHERE id=?",id);
        var response=service.paging(new EmployeeAssignmentQueryDTO(),"u"); assertEquals(1L,response.get("total")); var row=rows(response).get(0);
        assertEquals("Snapshot title",row.get("examTitle")); assertEquals(true,row.get("passed"));
        for(String key:List.of("userScore","totalScore","accessCodeHash","userId","subjectName","employeeNo","analysis")) assertFalse(row.containsKey(key));
        db.update("UPDATE el_paper SET grading_state='PENDING'"); assertNull(rows(service.paging(new EmployeeAssignmentQueryDTO(),"u")).get(0).get("passed"));
        assertEquals(0L,service.paging(new EmployeeAssignmentQueryDTO(),"stranger").get("total"));
    }
    @Test void effectiveStatesAndFiltersDistinguishFutureExpiredAndSettling() {
        String id=create("u"); db.update("UPDATE el_exam_assignment SET valid_from=DATEADD('DAY',1,NOW()) WHERE id=?",id);
        assertEquals("UPCOMING",rows(service.paging(query(),"u")).get(0).get("status"));
        db.update("UPDATE el_exam_assignment SET valid_from=DATEADD('DAY',-2,NOW()),expire_at=DATEADD('DAY',-1,NOW())");
        assertEquals("EXPIRED",rows(service.paging(query(),"u")).get(0).get("status"));
        db.update("INSERT INTO el_paper(id,assignment_id,user_id,hand_state,limit_time) VALUES('p',?,'u',0,DATEADD('SECOND',-1,NOW()))",id);
        db.update("UPDATE el_exam_assignment SET status='STARTED',paper_id='p'");
        assertEquals("SETTLING",rows(service.paging(query(),"u")).get(0).get("status"));
        var filter=query(); filter.setStatus("TODO"); assertEquals(0L,service.paging(filter,"u").get("total"));
    }
    @Test void disableRestoreRequireValidStateAndNeverResetPaper() {
        String id=create("u"); var req=new AssignmentStatusReqDTO(); req.setId(id); req.setAction("DISABLE");
        assertThrows(ServiceException.class,()->service.changeStatus(req)); req.setReason("Schedule changed"); service.changeStatus(req);
        req.setAction("ENABLE"); service.changeStatus(req); assertNull(db.queryForObject("SELECT disabled_reason FROM el_exam_assignment",String.class));
        assertEquals("ASSIGNED",db.queryForObject("SELECT status FROM el_exam_assignment",String.class));
        db.update("UPDATE el_exam_assignment SET paper_id='p',status='DISABLED'");
        Paper p=new Paper(); p.setHandState(0); p.setLimitTime(new Date(0)); when(context.getBean(PaperService.class).getById("p")).thenReturn(p);
        assertThrows(ServiceException.class,()->service.changeStatus(req));
        assertEquals("p",db.queryForObject("SELECT paper_id FROM el_exam_assignment",String.class));
    }
    @Configuration @EnableTransactionManagement @MapperScan(basePackageClasses=ExamAssignmentMapper.class)
    static class Config {
        @Bean DataSource dataSource() { var ds=new JdbcDataSource(); ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000;DATABASE_TO_LOWER=TRUE"); new ResourceDatabasePopulator(new ClassPathResource("paper-safety-schema.sql")).execute(ds); return ds; }
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception { var factory=new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds); factory.setMapperLocations(new ClassPathResource("mapper/modules/exam/assignment/ExamAssignmentMapper.xml")); return factory.getObject(); }
        @Bean ExamService exams() { return mock(ExamService.class); }
        @Bean ExamRuleService rules() { return mock(ExamRuleService.class); }
        @Bean PositionService positions() { return mock(PositionService.class); }
        @Bean PaperService papers() { return mock(PaperService.class); }
        @Bean EmployeeAssignmentService employeeService(JdbcTemplate j,ExamService e,ExamRuleService r,PositionService p,ExamAssignmentMapper a,PaperService paper) { return new EmployeeAssignmentService(j,e,r,p,a,paper); }
    }
}
