"""Department manager regression in the existing disposable QA environment only."""
import json
import secrets
import sys
import acceptance as a
import environment as e
from extended import cells


def main():
    f = a.fixture()
    admin, hr = f['admin']['token'], f['hr']['token']
    prefix = f['prefix'] + '_dm'
    managers = []
    for index, code in enumerate([f['deptCode'], f['otherDeptCode']]):
        account = {'userName': prefix+str(index), 'password': secrets.token_urlsafe(20)}
        body = {**account, 'realName': 'QA虚构部门主管'+str(index), 'roles': ['DEPT_MANAGER'], 'deptCode': code, 'state': 0}
        for bad in [{'deptCode': ''}, {'roles': ['DEPT_MANAGER', 'HR']}]:
            a.check('DM-CONFIG-'+str(index)+str(len(bad.get('roles', []))), '主管账号拒绝缺失部门或混合管理身份',
                    lambda bad=bad: a.call('/api/sys/user/save', {**body, **bad}, admin, ok=False))
        a.call('/api/sys/user/save', body, admin)
        account['id'] = e.sql("SELECT id FROM el_sys_user WHERE user_name='"+account['userName']+"'")
        account['token'] = a.login(account)['token']
        managers.append(account)
    first, second = [m['token'] for m in managers]
    f['managers'] = managers
    a.save(f)
    a.check('DM-ROLE', '主管登录返回独立角色与本部门范围', lambda: a.require(
        a.call('/api/sys/user/info', {}, first)['roles'] == ['DEPT_MANAGER'] and
        a.call('/api/sys/user/info', {}, first)['dataScope'] == 2))
    def check_permissions():
        permissions = set(a.call('/api/sys/user/info', {}, first)['permissions'])
        a.require({'sys:user:registration:audit', 'exam:assignment:employee:add',
                   'exam:results:view', 'exam:results:export', 'exam:grading:finalize'}.issubset(permissions))
        a.require(not permissions.intersection({'sys:user:add', 'sys:user:edit',
                    'exam:assignment:candidate:import', 'repo:repo:add', 'exam:exam:add'}))
    a.check('DM-PERMISSIONS', '主管实际授权覆盖四项职责且不含账号或题库维护', check_permissions)

    def flatten(nodes):
        return [node['id'] for node in nodes] + [x for node in nodes for x in flatten(node.get('children') or [])]
    a.check('DM-TREE', '主管部门选择仅包含所属部门及组织路径', lambda: a.require(
        f['departId'] in flatten(a.call('/api/sys/depart/tree', {}, first)) and
        f['otherDepartId'] not in flatten(a.call('/api/sys/depart/tree', {}, first))))
    for token, label in [(first, '主管'), (f['employee']['token'], '员工'), (None, '匿名')]:
        forbidden = ['/api/sys/user/save', '/api/sys/user/batch-role', a.R+'save', a.E+'save'] if token == first else [a.G+'paging', '/api/sys/user/registration/audit-batch']
        for index, path in enumerate(forbidden):
            a.check('DM-PERM-'+label+str(index), label+'不能操作未授权管理接口',
                    lambda token=token, path=path: a.call(path, {}, token, ok=False))
    a.check('DM-NO-IMPORT', '主管默认不获得候选人批量导入权限', lambda: a.call(a.A+'candidate/import-template', {}, first, ok=False))

    pending = []
    for index, code in enumerate([f['deptCode'], f['otherDeptCode'], f['deptCode']]):
        user = a.seed('pending'+str(index), 'EMPLOYEE', code, prefix)
        e.sql("UPDATE el_sys_user SET state=2 WHERE id='"+user['id']+"'")
        pending.append(user)
    a.check('DM-USERS', '篡改部门、角色和范围参数不能列出其他部门员工', lambda: a.require(
        a.call('/api/sys/user/paging', {'current':1, 'size':100, 'params':{'deptCode':f['otherDeptCode'], 'roleIds':['admin'], 'scope':{'level':4}}}, first)['total'] == 0))
    a.check('DM-AUDIT-CROSS', '直接审核其他部门员工被拒绝', lambda: a.call('/api/sys/user/registration/audit', {'userId':pending[1]['id'], 'action':'APPROVE'}, first, ok=False))
    a.check('DM-AUDIT-BATCH', '混合部门审核失败且整批回滚', lambda: (
        a.call('/api/sys/user/registration/audit-batch', {'ids':[p['id'] for p in pending[:2]], 'action':'APPROVE'}, first, ok=False),
        a.require(e.sql("SELECT SUM(state) FROM el_sys_user WHERE id IN ('"+pending[0]['id']+"','"+pending[1]['id']+"')") == '4')))
    a.call('/api/sys/user/registration/audit-batch', {'ids':[pending[0]['id']], 'action':'APPROVE'}, first)
    a.call('/api/sys/user/registration/audit-batch', {'ids':[pending[1]['id']], 'action':'APPROVE'}, second)
    a.call('/api/sys/user/registration/audit-batch', {'ids':[pending[2]['id']], 'action':'REJECT'}, first)
    a.check('DM-AUDIT-OK', '本部门主管可通过、驳回且记录审核人', lambda: a.require(
        e.sql("SELECT CONCAT(state,':',audit_by) FROM el_sys_user WHERE id='"+pending[0]['id']+"'") == '0:'+managers[0]['id'] and
        e.sql("SELECT state FROM el_sys_user WHERE id='"+pending[2]['id']+"'") == '1'))
    a.check('DM-AUDIT-RETRY', '重复审核不覆盖原审核记录', lambda: a.call('/api/sys/user/registration/audit', {'userId':pending[0]['id'], 'action':'REJECT'}, first, ok=False))

    for path in ['employee/options', 'employee/templates']:
        a.check('DM-'+path, '员工名单与模板不能跨部门读取', lambda path=path: a.call(a.A+path, {'departId':f['otherDepartId']}, first, ok=False))
    a.check('DM-ISSUE-CROSS', '其他部门主管即使知道模板 ID 也不能发放', lambda: a.call(a.A+'employee/create', {'examId':f['exams']['PROMOTION'], 'userIds':[pending[0]['id']], 'batchNo':prefix}, second, ok=False))
    issue = {'examId':f['exams']['PROMOTION'], 'userIds':[pending[0]['id']], 'batchNo':prefix}
    aid = a.call(a.A+'employee/create', issue, first)['assignmentIds'][0]
    a.check('DM-ISSUE-RETRY', '主管重复发放复用原任务', lambda: a.require(a.call(a.A+'employee/create', issue, first)['created'] == 0))
    a.check('DM-LIST', '管理列表和统计按部门隔离', lambda: a.require(a.call(a.A+'employee/paging', {'batchNo':prefix}, second)['total'] == 0))
    a.check('DM-STATUS-CROSS', '跨部门任务停用被拒绝', lambda: a.call(a.A+'employee/change-status', {'id':aid, 'action':'DISABLE', 'reason':'QA'}, second, ok=False))
    a.call(a.A+'employee/change-status', {'id':aid, 'action':'DISABLE', 'reason':'QA'}, first)
    a.call(a.A+'employee/change-status', {'id':aid, 'action':'ENABLE'}, first)
    employee = a.login(pending[0])['token']
    paper = a.call(a.P+'create-by-assignment', {'id':aid}, employee)['paperId']
    a.call(a.P+'hand', {'id':paper}, employee)
    detail = a.call(a.G+'detail', {'id':paper}, first)
    question = detail['questions'][0]
    score = {'paperQuId':question['id'], 'score':5, 'comment':'QA主管阅卷', 'expectedVersion':detail['version']}
    for path, data in [('detail', {'id':paper}), ('question/save', score), ('finalize', {'paperId':paper, 'expectedVersion':detail['version']})]:
        a.check('DM-GRADE-'+path, '跨部门阅卷详情、评分与终审被拒绝', lambda path=path, data=data: a.call(a.G+path, data, second, ok=False))
    a.check('DM-GRADE-LIST', '待阅列表及岗位选项无其他部门数据', lambda: a.require(
        a.call(a.G+'paging', {'batchNo':prefix}, second)['total'] == 0 and not a.call(a.G+'positions', {}, second)))
    saved = a.call(a.G+'question/save', score, first)
    a.call(a.G+'question/save', score, first)
    finish = {'paperId':paper, 'expectedVersion':saved['version']}
    a.call(a.G+'finalize', finish, first)
    a.call(a.G+'finalize', finish, first)
    a.check('DM-FINAL', '主管阅卷幂等并自动公布考生是否通过', lambda: a.require(a.own_result(aid, employee)['resultAvailable']))
    a.check('DM-RETAKE', '主管发放的任务完成后仍不能重考', lambda: a.call(a.P+'create-by-assignment', {'id':aid}, employee, ok=False))
    a.call(a.A+'employee/result-detail', {'id':aid}, first)
    a.check('DM-RESULT-CROSS', '跨部门管理结果详情被拒绝', lambda: a.call(a.A+'employee/result-detail', {'id':aid}, second, ok=False))
    a.check('DM-REPORT-CROSS', '结果详情、导出及统计均拒绝越权', lambda: (
        a.call(a.RESULT+'detail', {'id':paper}, second, ok=False),
        a.call(a.RESULT+'export', {'batchNo':prefix}, second, ok=False),
        a.require(a.call(a.RESULT+'export-preview', {'batchNo':prefix}, second)['total'] == 0)))
    a.check('DM-EXPORT', '主管能导出本部门结果', lambda: a.require(len(cells(a.call(a.RESULT+'export', {'batchNo':prefix}, first))) == 2))

    candidate_body = {'candidateName':'QA主管候选人', 'candidateNo':prefix+'_candidate', 'mobile':'13800000000', 'email':'qa@example.invalid', 'departId':f['departId'], 'positionId':f['positionId'], 'batchNo':prefix}
    a.check('DM-CAND-CROSS-CREATE', '候选人发放部门不可伪造', lambda: a.call(a.A+'candidate/create', candidate_body, second, ok=False))
    candidate = a.call(a.A+'candidate/create', candidate_body, first)
    for path, data in [('candidate/reset-code', {'id':candidate['assignmentId']}), ('change-status', {'id':candidate['assignmentId'], 'action':'DISABLE', 'reason':'QA'}), ('candidate/result-detail', {'id':f['candidate']['assignmentId']})]:
        a.check('DM-CAND-'+path, '其他部门候选人的凭证和结果不能操作', lambda path=path, data=data: a.call(a.A+path, data, second, ok=False))
    a.check('DM-CAND-LIST', '候选人列表按分配部门隔离', lambda: a.require(a.call(a.A+'candidate/paging', {'current':1,'size':100,'params':{'batchNo':prefix}}, second)['total'] == 0))
    a.check('DM-HR-RECORDS', '主管可以查看 HR 发放给本部门的历史成绩', lambda: a.call(a.RESULT+'detail', {'id':f['candidatePaper']}, first))
    a.check('DM-POSITION', '按部门读取岗位不能绕过边界', lambda: a.call('/api/exam/position/list-by-department', {'id':f['departId']}, second, ok=False))

    # Live identity checks use only fictional QA accounts. Restore the fixture afterwards.
    e.sql("UPDATE el_sys_user SET dept_code='"+f['otherDeptCode']+"' WHERE id='"+managers[0]['id']+"'")
    a.check('DM-LIVE-DEPT', '旧会话中的部门变更立即影响数据访问', lambda: a.call(a.G+'detail', {'id':paper}, first, ok=False))
    e.sql("UPDATE el_sys_user SET dept_code='"+f['deptCode']+"' WHERE id='"+managers[0]['id']+"'")
    a.call(a.G+'detail', {'id':paper}, first)
    e.sql("UPDATE el_sys_user SET dept_code=NULL WHERE id='"+managers[0]['id']+"'")
    a.check('DM-NO-DEPT', '主管部门失效时拒绝继续使用旧会话', lambda: a.call(a.G+'paging', {}, first, ok=False))
    e.sql("UPDATE el_sys_user SET dept_code='"+f['deptCode']+"' WHERE id='"+managers[0]['id']+"'")
    # A pending employee is retained only for the actual browser audit step.
    ui_user = a.seed('ui_pending', 'EMPLOYEE', f['deptCode'], prefix)
    e.sql("UPDATE el_sys_user SET state=2 WHERE id='"+ui_user['id']+"'")
    f['managerUi'] = {'employee':ui_user, 'paper':paper, 'batch':prefix}
    a.save(f)


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    if a.REPORT.exists(): a.CASES.extend(json.loads(a.REPORT.read_text(encoding='utf-8')))
    main()
    print('Department manager regression complete.', flush=True)
    if any(row['status'] == 'FAIL' for row in a.CASES): raise SystemExit(1)
