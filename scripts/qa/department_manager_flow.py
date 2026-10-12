"""Additional bidirectional department isolation and fixtures for the actual UI flow."""
import json
import secrets
import acceptance as a
import environment as e
from extended import cells


def flatten(nodes):
    return [n['id'] for n in nodes] + [v for n in nodes for v in flatten(n.get('children') or [])]


def main():
    f = a.fixture()
    admin, hr = f['admin']['token'], f['hr']['token']
    managers = f['managers']
    first, second = [m['token'] for m in managers]
    if 'scopeFlow' not in f:
        prefix = f['prefix']+'_flow'
        flow = {'batch': prefix, 'uiBatch': prefix+'_ui'}
        position_code = prefix.upper()
        a.call('/api/exam/position/save', {'code':position_code,'name':'家具部验收岗位',
            'status':1,'sort':1,'departmentIds':[f['otherDepartId']],'grades':[]}, admin)
        flow['positionId'] = e.sql("SELECT id FROM el_position WHERE code='"+position_code+"'")
        flow['exams'] = {}
        for scene in ['INTERVIEW', 'PROMOTION']:
            title = prefix+'_'+scene
            a.call(a.R+'save', {'title':title,'catId':'RECRUITMENT' if scene=='INTERVIEW' else 'EMPLOYEE_PROMOTION',
                'departId':f['otherDepartId'],'positionId':flow['positionId'],'sceneType':scene,'status':1}, admin)
            rid = e.sql("SELECT id FROM el_repo WHERE title='"+title+"'")
            kinds = ['radio'] + (['short'] if scene=='PROMOTION' else [])
            for kind in kinds:
                a.call(a.QU+'save', {'repoId':rid,'quType':kind,'difficultyLevel':'1',
                    'content':'家具部验收：工作交接应如何记录？','analysis':'记录进度与待办',
                    'referenceAnswer':'说明当前进度、风险及待办事项','gradingCriteria':'说明完整得满分',
                    'status':1,'answerList':[] if kind=='short' else [
                        {'content':'明确进度和待办','isRight':True},{'content':'无需记录','isRight':False}]}, admin)
            a.call(a.E+'save', {'title':title,'content':'独立验收虚构考核','departId':f['otherDepartId'],
                'positionId':flow['positionId'],'sceneType':scene,'repoId':rid,'totalTime':30,
                'handMin':0,'qualifyScore':10,'templateStatus':1,'optionShuffle':1,'openType':1,
                'state':0,'chance':1,'lateMax':0,
                'ruleList':[{'quType':kind,'quCount':1,'quScore':10} for kind in kinds]}, admin)
            flow['exams'][scene] = e.sql("SELECT id FROM el_exam WHERE repo_id='"+rid+"'")
        flow['foreignAssignment'] = a.call(a.A+'employee/create', {
            'examId':flow['exams']['PROMOTION'],'userIds':[f['foreign']['id']],'batchNo':prefix}, hr)['assignmentIds'][0]
        employee_token = a.login(f['foreign'])['token']
        f['foreign']['token'] = employee_token
        flow['foreignPaper'] = a.call(a.P+'create-by-assignment', {'id':flow['foreignAssignment']}, employee_token)['paperId']
        a.call(a.P+'hand', {'id':flow['foreignPaper']}, employee_token)
        flow['candidate'] = a.call(a.A+'candidate/create', {'candidateName':'家具部验收候选人',
            'candidateNo':prefix,'mobile':'13800000000','email':'qa@example.invalid',
            'departId':f['otherDepartId'],'positionId':flow['positionId'],'batchNo':prefix}, second)
        flow['pending'] = []
        for index, code in enumerate([f['deptCode'], f['otherDeptCode']]):
            account = a.seed('audit'+str(index), 'EMPLOYEE', code, prefix)
            e.sql("UPDATE el_sys_user SET state=2 WHERE id='"+account['id']+"'")
            flow['pending'].append(account)
        flow['uiManagers'] = []
        for index, code in enumerate([f['deptCode'], f['otherDeptCode']]):
            account = {'userName':prefix+'_ui_manager'+str(index),'password':secrets.token_urlsafe(16)}
            a.call('/api/sys/user/save', {**account,'realName':'验收'+('餐具' if index==0 else '家具')+'主管',
                'deptCode':code,'roles':['DEPT_MANAGER'],'state':0}, admin)
            account['id'] = e.sql("SELECT id FROM el_sys_user WHERE user_name='"+account['userName']+"'")
            flow['uiManagers'].append(account)
        flow['uiEmployee'] = {'userName':prefix+'_ui_employee','password':secrets.token_urlsafe(16)}
        a.call('/api/sys/user/reg', {**flow['uiEmployee'],'realName':'验收餐具员工',
            'employeeNo':prefix+'_UI','deptCode':f['deptCode'],'mobile':'','email':'',**a.captcha()})
        flow['uiEmployee']['id'] = e.sql("SELECT id FROM el_sys_user WHERE user_name='"+flow['uiEmployee']['userName']+"'")
        child = prefix+'_child'
        child_code = f['deptCode']+'999'
        e.sql(f"INSERT INTO el_sys_depart(id,parent_id,dept_type,dept_name,dept_code,dept_level,sort,status) SELECT '{child}',id,2,'验收下级部门','{child_code}',dept_level+1,99,1 FROM el_sys_depart WHERE id='{f['departId']}'")
        flow['childId'], flow['childCode'] = child, child_code
        f['scopeFlow'] = flow
        a.save(f)
    flow = f['scopeFlow']
    pid, aid = flow['foreignPaper'], flow['foreignAssignment']

    a.check('ISO-B-OWN', '家具主管可读取 HR 发放的本部门待阅试卷', lambda: a.require(a.call(a.G+'detail', {'id':pid}, second)['id'] == pid))
    for actor, paper, assignment, suffix in [(first,pid,aid,'A-B'),(second,f['gradedPaper'],None,'B-A')]:
        for path, data in [(a.G+'detail',{'id':paper}),(a.RESULT+'detail',{'id':paper})] + ([(a.A+'employee/result-detail',{'id':assignment})] if assignment else []):
            a.check('ISO-DETAIL-'+suffix+path, '两个部门互相不能读取答卷和成绩详情', lambda path=path,data=data,actor=actor:a.call(path,data,actor,ok=False))

    a.check('ISO-LIST', '另一部门有真实记录时，篡改筛选仍返回零数量和零明细', lambda: (
        a.require(a.call(a.A+'employee/paging', {'departId':f['otherDepartId'],'batchNo':flow['batch'],'scope':{'level':4}}, first)['total']==0),
        a.require(a.call(a.G+'paging', {'departId':f['otherDepartId'],'batchNo':flow['batch']}, first)['total']==0),
        a.require(all(a.call(a.RESULT+'paging', {'departId':f['otherDepartId']}, first)[k]==0 for k in ['total','all','completed','pending']))))
    a.check('ISO-OPTIONS', '成绩部门、岗位和阅卷岗位选项不包含另一部门', lambda: (
        a.require(f['otherDepartId'] not in [d['id'] for d in a.call(a.RESULT+'options', {}, first)['departments']]),
        a.require(flow['positionId'] not in [p['id'] for p in a.call(a.G+'positions', {}, first)])))
    a.check('ISO-EXPORT-B', '家具主管导出实际只包含本部门待阅记录', lambda: a.require(
        len(cells(a.call(a.RESULT+'export', {'batchNo':flow['batch']}, second)))==2))
    a.check('ISO-EXPORT-A', '餐具主管不能导出家具部真实成绩', lambda: a.call(a.RESULT+'export', {
        'batchNo':flow['batch'],'departId':f['otherDepartId']}, first, ok=False))
    def overlapping_export():
        rows = cells(a.call(a.RESULT+'export', {'batchNo':f['prefix']}, first))
        own_name = e.sql("SELECT dept_name FROM el_sys_depart WHERE id='"+f['departId']+"'")
        a.require(len(rows)>1 and all(row[3]==own_name for row in rows[1:]))
    a.check('ISO-EXPORT-SHARED-PREFIX', '批次关键字同时匹配两部门时仍仅导出本部门行', overlapping_export)
    a.check('ISO-CAND-B', '家具主管可发放并列出本部门候选人', lambda: a.require(a.call(a.A+'candidate/paging', {'current':1,'size':100,'params':{'batchNo':flow['batch']}}, second)['total']==1))
    a.check('ISO-CAND-A', '餐具主管不能查看或重置家具候选人', lambda: (
        a.require(a.call(a.A+'candidate/paging', {'current':1,'size':100,'params':{'batchNo':flow['batch']}}, first)['total']==0),
        a.call(a.A+'candidate/reset-code', {'id':flow['candidate']['assignmentId']}, first, ok=False)))

    def mixed_issue():
        batch = flow['batch']+'_mixed'
        a.call(a.A+'employee/create', {'examId':f['exams']['PROMOTION'],
            'userIds':[f['other']['id'],f['foreign']['id']],'batchNo':batch,'departId':f['departId']}, first, ok=False)
        a.require(e.sql("SELECT COUNT(*) FROM el_exam_assignment WHERE batch_no='"+batch+"'")=='0')
    a.check('ISO-MIXED-ISSUE', '发放混合部门名单整批拒绝并无任务残留', mixed_issue)
    def mixed_audit():
        pending = flow['pending']
        a.call('/api/sys/user/registration/audit-batch', {'ids':[p['id'] for p in pending],'action':'APPROVE'}, first, ok=False)
        a.require(e.sql("SELECT SUM(state) FROM el_sys_user WHERE id IN ('"+pending[0]['id']+"','"+pending[1]['id']+"')")=='4')
    a.check('ISO-MIXED-AUDIT', '混合部门审核整批回滚，两名员工保持待审核', mixed_audit)
    a.check('ISO-CHILD', '主管范围不扩展到下级部门', lambda: (
        a.require(flow['childId'] not in flatten(a.call('/api/sys/depart/tree', {}, first))),
        a.call(a.A+'employee/options', {'departId':flow['childId']}, first, ok=False)))

    legacy = [(a.P+'full-detail',{'id':pid}), (a.P+'paging',{'current':1,'size':10,'params':{'examId':flow['exams']['PROMOTION']}}),
              ('/api/exam/exam/record/paging',{'current':1,'size':10}), (a.E+'detail',{'id':flow['exams']['PROMOTION']}),
              (a.R+'detail',{'id':f['repos']['PROMOTION']}), ('/api/sys/user/detail',{'id':f['foreign']['id']}),
              ('/api/sys/user/state',{'ids':[f['foreign']['id']],'state':1}),
              ('/api/sys/user/batch-role',{'ids':[managers[0]['id']],'roleIds':['admin']})]
    for index, (path, body) in enumerate(legacy):
        a.check('ISO-LEGACY-'+str(index), '主管不能经旧接口读取或修改无授权数据 '+path,
                lambda path=path,body=body:a.call(path,body,first,ok=False))

    def profile_injection():
        nonlocal first
        current = a.call('/api/sys/user/info', {}, first)
        status, _, payload = a.request('/api/sys/user/update', {'realName':current['realName'],
            'id':f['foreign']['id'],'userName':f['foreign']['userName'],'deptCode':f['otherDeptCode'],
            'roles':['admin'],'state':0,'dataScope':4}, first)
        a.require(status==200 and payload.get('code')==0, 'Personal profile request failed unexpectedly')
        first = payload['data']['token']
        managers[0]['token'] = first
        a.save(f)
        info = a.call('/api/sys/user/info', {}, first)
        a.require(info['id']==managers[0]['id'] and info['deptCode']==f['deptCode'] and info['roles']==['DEPT_MANAGER'] and info['dataScope']==2)
        a.call(a.G+'detail', {'id':pid}, first, ok=False)
    a.check('ISO-PROFILE', '个人资料请求不能伪造用户、部门、角色或数据范围', profile_injection)

    def broaden_role():
        original = e.sql("SELECT data_scope FROM el_sys_role WHERE id='DEPT_MANAGER'")
        try:
            e.sql("UPDATE el_sys_role SET data_scope=4 WHERE id='DEPT_MANAGER'")
            a.require(a.call('/api/sys/user/info', {}, first)['dataScope']==2)
            a.call(a.G+'detail', {'id':pid}, first, ok=False)
        finally:
            e.sql("UPDATE el_sys_role SET data_scope="+original+" WHERE id='DEPT_MANAGER'")
    a.check('ISO-FORCED-SCOPE', '即使角色范围错误配置为全量，主管仍限精确本部门', broaden_role)
    def disabled_department():
        try:
            e.sql("UPDATE el_sys_depart SET status=0 WHERE id='"+f['departId']+"'")
            for path in [a.A+'employee/paging',a.G+'paging',a.RESULT+'paging','/api/sys/user/paging']:
                a.call(path, {}, first, ok=False)
        finally:
            e.sql("UPDATE el_sys_depart SET status=1 WHERE id='"+f['departId']+"'")
    a.check('ISO-DEPT-DISABLED', '停用部门后旧主管会话无法继续查询业务', disabled_department)

    a.check('ISO-ANON-EMPLOYEE', '匿名和员工均不能使用主管审核、发放、评分和导出接口', lambda: [
        a.call(path, body, token, ok=False)
        for token in [None,f['employee']['token']]
        for path,body in [('/api/sys/user/registration/audit-batch',{'ids':[flow['pending'][0]['id']],'action':'APPROVE'}),
            (a.A+'employee/create',{'examId':flow['exams']['PROMOTION'],'userIds':[f['foreign']['id']],'batchNo':'forbidden'}),
            (a.G+'detail',{'id':pid}),(a.RESULT+'export',{'batchNo':flow['batch']})]])
    a.save(f)
    print('Additional isolation checks complete; separate UI fixtures available.', flush=True)


if __name__ == '__main__':
    if a.REPORT.exists(): a.CASES.extend(json.loads(a.REPORT.read_text(encoding='utf-8')))
    main()
    if any(case['status']=='FAIL' for case in a.CASES): raise SystemExit(1)
