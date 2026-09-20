package com.yf.modules.exam.report;

import com.yf.base.api.exception.ServiceException;
import com.yf.system.modules.user.UserUtils;
import com.yf.ability.shiro.dto.SysUserLoginDTO;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import jakarta.validation.Validation;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Timeout(15)
class ResultReportTest {
    EmbeddedDatabase database;
    JdbcTemplate db;
    ResultReportService service;
    MockedStatic<UserUtils> users;
    SysUserLoginDTO user;
    @BeforeEach void setup() {
        database=new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).addScript("paper-safety-schema.sql").build();
        db=new JdbcTemplate(database); service=new ResultReportService(db);
        db.execute("CREATE TABLE el_sys_user(id varchar(64),real_name varchar(128),employee_no varchar(64),dept_code varchar(64),state int DEFAULT 0)");
        db.execute("CREATE TABLE el_sys_user_role(user_id varchar(64),role_id varchar(64))");
        db.execute("CREATE TABLE el_sys_role(id varchar(64),data_scope int)");
        db.execute("CREATE TABLE el_exam(id varchar(64),scene_type varchar(32))");
        db.execute("CREATE TABLE el_position(id varchar(64),name varchar(128))");
        db.execute("CREATE TABLE el_sys_depart(id varchar(64),dept_name varchar(128),dept_code varchar(64))");
        db.update("INSERT INTO el_sys_user(id,real_name,employee_no) VALUES('u','员工','0001'),('g','终审人',NULL)");
        db.update("INSERT INTO el_sys_user(id,dept_code) VALUES('manager','001')");
        db.update("INSERT INTO el_sys_user_role VALUES('manager','report')");
        db.update("INSERT INTO el_sys_role VALUES('report',4)");
        db.update("INSERT INTO el_exam VALUES('e','INTERVIEW')");
        db.update("INSERT INTO el_position VALUES('pos','岗位')");
        db.update("INSERT INTO el_sys_depart VALUES('d1','部门1','001'),('d2','子部门','001001'),('d3','其他部门','002')");
        user=new SysUserLoginDTO(); user.setId("manager"); user.setDataScope(4); user.setDeptCode("001");
        users=mockStatic(UserUtils.class); users.when(()->UserUtils.getUser(false)).thenReturn(user);
        add("1","NOT_REQUIRED",0,"d1","manager"); add("2","GRADED",80,"d2","other"); add("3","PENDING",99,"d3","other");
    }
    void add(String id,String state,int score,String dept,String owner) {
        db.update("INSERT INTO el_exam_assignment(id,user_id,exam_id,paper_id,subject_type,subject_name,candidate_no,batch_no,depart_id,position_id,create_by) VALUES(?, 'u','e',?,'CANDIDATE',?,'00001',?,?,'pos',?)","a"+id,"p"+id,"=1+1", "batch"+id,dept,owner);
        db.update("INSERT INTO el_paper(id,assignment_id,user_id,exam_id,title,hand_state,hand_time,grading_state,user_score,total_score,qualify_score,passed,graded_by) VALUES(?,?,'u','e','测试考核',1,CURRENT_TIMESTAMP,?,?,100,60,?,'g')","p"+id,"a"+id,state,score,score>=60);
        db.update("INSERT INTO el_paper_qu(id,paper_id,qu_type,actual_score,grading_state) VALUES(?,?,'radio',0,'NOT_REQUIRED'),(?,?,'short',80,'GRADED')","q"+id,"p"+id,"s"+id,"p"+id);
    }
    @AfterEach void cleanup() { users.close(); database.shutdown(); }
    void scope(Integer value) { db.update("UPDATE el_sys_role SET data_scope=?",value); }
    @SuppressWarnings("unchecked") List<Map<String,Object>> rows(ResultQuery q) { return (List<Map<String,Object>>)service.paging(q).get("records"); }
    @Test void separatesAssignmentsAndIncludesFinalManualResultsButBlanksPending() {
        assertEquals(3L,service.paging(new ResultQuery()).get("total"));
        var pending=service.detail("p3"); assertNull(pending.get("userScore"));assertNull(pending.get("passed"));assertNull(pending.get("subjectiveScore"));
        assertEquals(new BigDecimal("80.00"),service.detail("p2").get("userScore"));
        var q=new ResultQuery();q.setState("COMPLETED"); assertEquals(2,rows(q).size()); q.setState("PENDING");assertEquals(1,rows(q).size());
    }
    @Test void finalFiltersIncludeZeroAndNeverMatchPendingOrUnsubmitted() {
        var q=new ResultQuery(); q.setScoreMax(BigDecimal.ZERO); assertEquals("p1",rows(q).get(0).get("id"));
        q.setPassed(true);assertTrue(rows(q).isEmpty());q.setScoreMax(null);assertEquals("p2",rows(q).get(0).get("id"));
        db.update("UPDATE el_paper SET hand_state=0 WHERE id='p2'");assertTrue(rows(q).isEmpty());
    }
    @Test void appliesEveryFilterAndUsesLiteralWildcards() {
        var q=new ResultQuery();q.setSubjectType("CANDIDATE");q.setKeyword("00001");q.setBatchNo("batch2");q.setTitle("考核");q.setDepartId("d2");q.setPositionId("pos");q.setSceneType("INTERVIEW");assertEquals(1,rows(q).size());
        q.setKeyword("%");assertTrue(rows(q).isEmpty());q.setKeyword(null);q.setSubjectType("EMPLOYEE");assertTrue(rows(q).isEmpty());
        q=new ResultQuery();q.setSubmittedTo(new Date(0));assertTrue(rows(q).isEmpty());q.setSubmittedTo(null);q.setSubmittedFrom(new Date(System.currentTimeMillis()+60000));assertTrue(rows(q).isEmpty());
    }
    @Test void allOperationsEnforceSelfDepartmentAndDescendantScope() {
        scope(1);assertEquals(1,rows(new ResultQuery()).size());assertThrows(ServiceException.class,()->service.detail("p2"));
        scope(2); assertEquals(1L,service.preview(new ResultQuery()).get("total"));
        scope(3);assertEquals(2,rows(new ResultQuery()).size());
        assertEquals(2,((List<?>)service.options().get("departments")).size());
        db.update("UPDATE el_sys_user SET dept_code='00%' WHERE id='manager'");assertEquals(0,rows(new ResultQuery()).size());
        scope(9);assertThrows(ServiceException.class,()->service.export(new ResultQuery()));
        scope(null);assertThrows(ServiceException.class,()->service.options());
        users.when(()->UserUtils.getUser(false)).thenReturn(null);assertThrows(ServiceException.class,()->service.paging(new ResultQuery()));
    }
    @Test void rejectsMismatchedOwnerExamAndAssignmentLinks() {
        db.update("UPDATE el_exam_assignment SET user_id='other' WHERE id='a1'");
        db.update("UPDATE el_exam_assignment SET exam_id='other' WHERE id='a2'");
        db.update("UPDATE el_exam_assignment SET paper_id='other' WHERE id='a3'");
        assertEquals(0,rows(new ResultQuery()).size());assertThrows(ServiceException.class,()->service.detail("p1"));
    }
    @Test void workbookExportsAllMatchingPagesAsSafeTextAndBlankPendingCells() throws Exception {
        var q=new ResultQuery();q.setSize(1);assertEquals(1,rows(q).size());
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(service.export(q)))) {
            var sheet=book.getSheetAt(0);assertEquals(3,sheet.getLastRowNum());assertEquals(18,sheet.getRow(0).getLastCellNum());
            for(int i=1;i<=3;i++) { var row=sheet.getRow(i);assertEquals(CellType.STRING,row.getCell(0).getCellType());assertEquals("=1+1",row.getCell(0).getStringCellValue());assertEquals("00001",row.getCell(2).getStringCellValue());
                if(row.getCell(6).getStringCellValue().equals("batch3")) { assertEquals(CellType.BLANK,row.getCell(9).getCellType());assertEquals(CellType.BLANK,row.getCell(10).getCellType());assertEquals(CellType.BLANK,row.getCell(13).getCellType()); }
            }
        }
        scope(1);
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(service.export(q)))) { assertEquals(1,book.getSheetAt(0).getLastRowNum()); }
    }
    @Test void rejectsInvalidRangesEmptyExportsAndExcessiveExports() {
        var q=new ResultQuery();q.setScoreMin(BigDecimal.TEN);q.setScoreMax(BigDecimal.ZERO);assertThrows(ServiceException.class,()->service.paging(q));
        q.setScoreMin(null);q.setScoreMax(null);q.setSubmittedFrom(new Date(100));q.setSubmittedTo(new Date(0));assertThrows(ServiceException.class,()->service.export(q));
        q.setSubmittedFrom(null);assertThrows(ServiceException.class,()->service.export(q));
        var mockDb=mock(JdbcTemplate.class);when(mockDb.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("dataScope",4,"deptCode","001")));
        when(mockDb.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenReturn(10001L);
        assertThrows(ServiceException.class,()->new ResultReportService(mockDb).export(new ResultQuery()));
    }
    @Test void requestValidationRejectsInvalidEnumsBoundsAndPagination() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var q=new ResultQuery();q.setCurrent(0);q.setSize(101);q.setState("anything");q.setSceneType("invalid");q.setScoreMin(new BigDecimal("-1"));
            assertTrue(factory.getValidator().validate(q).size()>=5);
        }
    }
    @Test void everyEndpointHasExplicitPermissionsAndExportNeedsBoth() throws Exception {
        for(var method:ResultReportController.class.getDeclaredMethods()) {
            var permission=method.getAnnotation(RequiresPermissions.class);assertNotNull(permission);
            assertEquals("exam:results:view",permission.value()[0]);
            if(method.getName().equals("export")||method.getName().equals("preview")) assertArrayEquals(new String[]{"exam:results:view","exam:results:export"},permission.value());
        }
    }
    @Test void legacyRecordMapperIncludesGradedPapersAndHonorsZeroAndNameFilters() throws Exception {
        db.execute("ALTER TABLE el_sys_user ADD user_name varchar(128)");
        db.execute("ALTER TABLE el_exam ADD title varchar(255)");
        db.execute("CREATE TABLE el_exam_record(id varchar(64),user_id varchar(64),exam_id varchar(64),paper_id varchar(64),try_count int,max_score decimal(10,2),last_score decimal(10,2),passed boolean)");
        db.update("INSERT INTO el_exam_record VALUES('rc','u','e','p2',1,0,0,false)");
        var factory=new com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean();
        factory.setDataSource(database);
        factory.setMapperLocations(new org.springframework.core.io.ClassPathResource("mapper/modules/exam/exam/ExamRecordMapper.xml"));
        try(var session=factory.getObject().openSession()) {
            var mapper=session.getMapper(com.yf.modules.exam.exam.mapper.ExamRecordMapper.class);
            var query=new com.yf.modules.exam.exam.dto.request.ExamRecordListReqDTO();query.setUserId("u");query.setUserName("员工");query.setScoreMax(BigDecimal.ZERO);
            assertEquals(1,mapper.paging(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1,10),query).getRecords().size());
            assertEquals(1,mapper.clientPaging(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1,10),query).getRecords().size());
            session.clearCache();db.update("UPDATE el_exam_record SET max_score=1");
            assertTrue(mapper.paging(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1,10),query).getRecords().isEmpty());
            session.clearCache();db.update("UPDATE el_paper SET grading_state='PENDING' WHERE id='p2'");
            assertTrue(mapper.clientPaging(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1,10),query).getRecords().isEmpty());
        }
    }
}
