package com.yf.system.modules.user;

import com.yf.base.api.exception.ServiceException;
import com.yf.system.modules.user.enums.SysRoleId;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Resolves management data access from the database, never from request or cached role values. */
@Service
@RequiredArgsConstructor
public class DepartmentScope {
    private final JdbcTemplate db;

    @Getter
    @RequiredArgsConstructor
    public static class Scope {
        private final String userId;
        private final String departCode;
        private final int level;
        private final boolean departmentManager;
        private final boolean employeeOnly;

        /** Column arguments are internal SQL identifiers, never client input. */
        public String sql(String departmentColumn, String creatorColumn, List<Object> args) {
            return switch (level) {
                case 1 -> { args.add(userId); yield " AND " + creatorColumn + "=?"; }
                case 2 -> { args.add(departCode); yield " AND " + departmentColumn + "=?"; }
                case 3 -> {
                    args.add(departCode.length()); args.add(departCode);
                    yield " AND LEFT(" + departmentColumn + ",?)=?";
                }
                case 4 -> "";
                default -> throw denied();
            };
        }

        public boolean allowsDepartment(String code) {
            return level == 1 || level == 4 || (code != null &&
                    (level == 2 ? code.equals(departCode) : code.startsWith(departCode)));
        }
    }

    public Scope current() {
        var user = UserUtils.getUser(false);
        if (user == null || StringUtils.isBlank(user.getId())) throw denied();
        var rows = db.queryForList("SELECT u.dept_code AS deptCode,MAX(r.data_scope) AS dataScope,"
                + "MAX(CASE WHEN r.id='DEPT_MANAGER' THEN 1 ELSE 0 END) AS manager,"
                + "MAX(CASE WHEN r.id='HR' THEN 1 ELSE 0 END) AS hr,"
                + "MAX(CASE WHEN r.id='admin' THEN 1 ELSE 0 END) AS administrator "
                + "FROM el_sys_user u JOIN el_sys_user_role ur ON ur.user_id=u.id "
                + "JOIN el_sys_role r ON r.id=ur.role_id WHERE u.id=? AND u.state=0 GROUP BY u.dept_code", user.getId());
        if (rows.isEmpty() || !(rows.get(0).get("dataScope") instanceof Number value)) throw denied();
        var row = rows.get(0);
        boolean manager = one(row.get("manager"));
        int level = manager ? 2 : value.intValue();
        String code = (String) row.get("deptCode");
        if (level < 1 || level > 4 || ((level == 2 || level == 3) && StringUtils.isBlank(code))) throw denied();
        if (manager) requireBusinessDepartment(code);
        return new Scope(user.getId(), code, level, manager,
                manager || (one(row.get("hr")) && !one(row.get("administrator"))));
    }

    public void requireDepartment(String id) { requireDepartment(current(), id); }

    public void requireDepartment(Scope scope, String id) {
        var codes = db.queryForList("SELECT dept_code FROM el_sys_depart WHERE id=?", String.class, id);
        if (codes.size() != 1 || !scope.allowsDepartment(codes.get(0))) throw denied();
    }

    public void requireAssignment(String id) {
        Scope scope = current();
        List<Object> args = new ArrayList<>(List.of(id));
        String restriction = scope.sql("d.dept_code", "a.create_by", args);
        if (db.queryForObject("SELECT COUNT(*) FROM el_exam_assignment a LEFT JOIN el_sys_depart d ON d.id=a.depart_id "
                + "WHERE a.id=?" + restriction, Integer.class, args.toArray()) != 1) throw denied();
    }

    public void requirePaper(String id) {
        Scope scope = current();
        List<Object> args = new ArrayList<>(List.of(id));
        String restriction = scope.sql("d.dept_code", "a.create_by", args);
        if (db.queryForObject("SELECT COUNT(*) FROM el_paper p LEFT JOIN el_exam_assignment a "
                + "ON a.id=p.assignment_id AND a.paper_id=p.id AND a.user_id=p.user_id AND a.exam_id=p.exam_id "
                + "LEFT JOIN el_sys_depart d ON d.id=a.depart_id WHERE p.id=?" + restriction,
                Integer.class, args.toArray()) != 1) throw denied();
    }

    public void requireEmployee(String id) {
        Scope scope = current();
        List<Object> args = new ArrayList<>(List.of(id));
        String restriction = scope.sql("u.dept_code", "u.create_by", args);
        if (db.queryForObject("SELECT COUNT(*) FROM el_sys_user u WHERE u.id=?" + restriction,
                Integer.class, args.toArray()) != 1) throw denied();
    }

    public void validateManagerAccount(List<String> roles, String deptCode) {
        if (roles == null || !roles.contains(SysRoleId.DEPT_MANAGER)) return;
        if (roles.size() != 1) throw new ServiceException("部门主管须使用独立角色，不能混用管理员、HR 或考生身份！");
        requireBusinessDepartment(deptCode);
    }

    private void requireBusinessDepartment(String code) {
        if (StringUtils.isBlank(code) || db.queryForObject("SELECT COUNT(*) FROM el_sys_depart "
                + "WHERE dept_code=? AND status=1 AND dept_type=2 AND parent_id<>'0'", Integer.class, code) != 1)
            throw new ServiceException("部门主管必须绑定一个启用的业务部门，请联系管理员！");
    }

    private static boolean one(Object value) { return value instanceof Number n && n.intValue() == 1; }
    private static ServiceException denied() { return new ServiceException("数据不存在或超出当前账号的部门权限！"); }
}
