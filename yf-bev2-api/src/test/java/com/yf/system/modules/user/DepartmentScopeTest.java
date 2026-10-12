package com.yf.system.modules.user;

import com.yf.ability.shiro.dto.SysUserLoginDTO;
import com.yf.base.api.exception.ServiceException;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.*;

import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Timeout(15)
class DepartmentScopeTest {
    EmbeddedDatabase database;
    JdbcTemplate db;
    DepartmentScope scope;
    MockedStatic<UserUtils> users;
    SysUserLoginDTO principal;

    @BeforeEach void setup() {
        database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        db = new JdbcTemplate(database);
        scope = new DepartmentScope(db);
        db.execute("CREATE TABLE el_sys_user(id varchar(64),dept_code varchar(64),state int,create_by varchar(64))");
        db.execute("CREATE TABLE el_sys_user_role(user_id varchar(64),role_id varchar(64))");
        db.execute("CREATE TABLE el_sys_role(id varchar(64),data_scope int)");
        db.execute("CREATE TABLE el_sys_depart(id varchar(64),dept_code varchar(64),status int,dept_type int,parent_id varchar(64))");
        db.execute("CREATE TABLE el_exam_assignment(id varchar(64),depart_id varchar(64),create_by varchar(64),paper_id varchar(64),user_id varchar(64),exam_id varchar(64))");
        db.execute("CREATE TABLE el_paper(id varchar(64),assignment_id varchar(64),user_id varchar(64),exam_id varchar(64))");
        db.update("INSERT INTO el_sys_user VALUES('m','001001',0,''),('u','001001',2,'hr'),('foreign','002',2,'m')");
        db.update("INSERT INTO el_sys_role VALUES('DEPT_MANAGER',2),('HR',4)");
        db.update("INSERT INTO el_sys_user_role VALUES('m','DEPT_MANAGER')");
        db.update("INSERT INTO el_sys_depart VALUES('root','001',1,1,'0'),('d1','001001',1,2,'root'),('child','001001001',1,2,'d1'),('d2','002',1,2,'root'),('off','003',0,2,'root')");
        db.update("INSERT INTO el_exam_assignment VALUES('a1','d1','hr','p1','u','e'),('a2','d2','m','p2','foreign','e'),('a3','child','m','p3','childUser','e')");
        db.update("INSERT INTO el_paper VALUES('p1','a1','u','e'),('p2','a2','foreign','e'),('p3','a3','childUser','e'),('legacy',NULL,'u','e')");
        principal = new SysUserLoginDTO(); principal.setId("m"); principal.setDeptCode("002"); principal.setDataScope(4);
        users = mockStatic(UserUtils.class); users.when(() -> UserUtils.getUser(false)).thenReturn(principal);
    }
    @AfterEach void cleanup() { users.close(); database.shutdown(); }

    @Test void managerScopeComesFromLiveDepartmentAndCannotBeExpandedByRoleScopeOrExtraRole() {
        assertEquals("001001", scope.current().getDepartCode());
        db.update("UPDATE el_sys_role SET data_scope=4 WHERE id='DEPT_MANAGER'");
        db.update("INSERT INTO el_sys_user_role VALUES('m','HR')");
        assertEquals(2, scope.current().getLevel());
        scope.requireAssignment("a1");
        assertThrows(ServiceException.class, () -> scope.requireAssignment("a2"));
        assertThrows(ServiceException.class, () -> scope.requireAssignment("a3"));
    }
    @Test void allListPredicatesUseExactDepartmentAndIgnoreForeignCreatorOwnership() {
        var args = new ArrayList<Object>();
        String restriction = scope.current().sql("d.dept_code", "a.create_by", args);
        assertEquals(List.of("a1"), db.queryForList("SELECT a.id FROM el_exam_assignment a JOIN el_sys_depart d ON d.id=a.depart_id WHERE 1=1"+restriction, String.class, args.toArray()));
        scope.requireDepartment("d1");
        for (String id : List.of("d2", "child", "root", "missing"))
            assertThrows(ServiceException.class, () -> scope.requireDepartment(id));
    }
    @Test void directPaperAndEmployeeIdsCannotCrossDepartment() {
        scope.requirePaper("p1"); scope.requireEmployee("u");
        for (String id : List.of("p2", "p3", "legacy", "missing"))
            assertThrows(ServiceException.class, () -> scope.requirePaper(id));
        assertThrows(ServiceException.class, () -> scope.requireEmployee("foreign"));
        db.update("UPDATE el_exam_assignment SET exam_id='other' WHERE id='a1'");
        assertThrows(ServiceException.class, () -> scope.requirePaper("p1"));
    }
    @Test void departmentChangeTakesEffectWithoutUsingCachedSession() {
        db.update("UPDATE el_sys_user SET dept_code='002' WHERE id='m'");
        scope.requireAssignment("a2");
        assertThrows(ServiceException.class, () -> scope.requireAssignment("a1"));
    }
    @Test void missingDisabledRootOrUnassignedDepartmentFailsClosed() {
        for (String code : List.of("", "unknown", "001", "003")) {
            db.update("UPDATE el_sys_user SET dept_code=? WHERE id='m'", code);
            assertThrows(ServiceException.class, () -> scope.current());
        }
        db.update("UPDATE el_sys_user SET dept_code='001001' WHERE id='m'");
        db.update("UPDATE el_sys_depart SET status=0 WHERE id='d1'");
        assertThrows(ServiceException.class, () -> scope.current());
    }
    @Test void unauthenticatedDisabledAndRolelessAccountsFailClosed() {
        users.when(() -> UserUtils.getUser(false)).thenReturn(null);
        assertThrows(ServiceException.class, () -> scope.current());
        users.when(() -> UserUtils.getUser(false)).thenReturn(principal);
        db.update("UPDATE el_sys_user SET state=1 WHERE id='m'");
        assertThrows(ServiceException.class, () -> scope.current());
        db.update("UPDATE el_sys_user SET state=0 WHERE id='m'");
        db.update("DELETE FROM el_sys_user_role");
        assertThrows(ServiceException.class, () -> scope.current());
    }
    @Test void managerProvisioningRequiresAnIndependentRoleAndActiveBusinessDepartment() {
        scope.validateManagerAccount(List.of("DEPT_MANAGER"), "001001");
        for (String role : List.of("HR", "admin", "EMPLOYEE", "CANDIDATE"))
            assertThrows(ServiceException.class, () -> scope.validateManagerAccount(List.of("DEPT_MANAGER", role), "001001"));
        for (String code : List.of("", "001", "003", "unknown"))
            assertThrows(ServiceException.class, () -> scope.validateManagerAccount(List.of("DEPT_MANAGER"), code));
    }
    @Test void existingSelfSubtreeAndAllScopesArePreserved() {
        db.update("UPDATE el_sys_user_role SET role_id='HR'");
        scope.requireAssignment("a1"); scope.requireAssignment("a2"); scope.requirePaper("legacy");
        db.update("UPDATE el_sys_role SET data_scope=1 WHERE id='HR'");
        scope.requireAssignment("a2"); assertThrows(ServiceException.class, () -> scope.requireAssignment("a1"));
        db.update("UPDATE el_sys_role SET data_scope=3 WHERE id='HR'");
        scope.requireAssignment("a1"); scope.requireAssignment("a3");
        assertThrows(ServiceException.class, () -> scope.requireAssignment("a2"));
    }
}
