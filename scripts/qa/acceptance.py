"""Real HTTP acceptance against the disposable environment, with machine-readable cases."""
import concurrent.futures
import datetime as dt
import hashlib
import http.client
import io
import json
from pathlib import Path
import secrets
import sys
import time
import uuid
import zipfile
import xml.etree.ElementTree as ET
import environment as env

FIXTURE = env.ROOT / '.local/qa-fixtures.json'
REPORT = env.LOGS / 'qa-api-results.json'
A = '/api/exam/assignment/'
P = '/api/exam/paper/paper/'
Q = '/api/exam/paper/qu/'
G = '/api/exam/grading/'
E = '/api/exam/exam/exam/'
R = '/api/exam/repo/repo/'
QU = '/api/exam/repo/qu/'
RESULT = '/api/exam/results/'
CASES = []
LATENCY = []


def request(path, body=None, token=None, method='POST', file=None, filename='test.xlsx', fields=None):
    c = http.client.HTTPConnection('127.0.0.1', env.load()['port'], timeout=25)
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['token'] = token
    if file is not None:
        boundary = 'qa' + uuid.uuid4().hex
        parts = []
        for key, value in (fields or {}).items():
            parts.append(('--'+boundary+'\r\nContent-Disposition: form-data; name="'+key+'"\r\n\r\n'+str(value)+'\r\n').encode())
        parts.append(('--'+boundary+'\r\nContent-Disposition: form-data; name="file"; filename="'+filename+'"\r\nContent-Type: application/octet-stream\r\n\r\n').encode()+file+b'\r\n')
        body_bytes = b''.join(parts)+('--'+boundary+'--\r\n').encode()
        headers['Content-Type'] = 'multipart/form-data; boundary='+boundary
    else:
        body_bytes = json.dumps(body or {}, ensure_ascii=False).encode() if method == 'POST' else None
    started = time.perf_counter()
    try:
        c.request(method, path, body_bytes, headers)
        r = c.getresponse()
        data, status, response_headers = r.read(), r.status, dict(r.getheaders())
    finally:
        c.close()
    LATENCY.append((path, (time.perf_counter()-started)*1000))
    payload = json.loads(data) if 'json' in response_headers.get('Content-Type', '') else data
    return status, response_headers, payload


def call(path, body=None, token=None, ok=True, **kwargs):
    status, headers, payload = request(path, body, token, **kwargs)
    if status >= 500:
        raise AssertionError(f'{path}: unexpected server failure HTTP {status}')
    if not ok and not (status in (401, 403) or (status == 200 and isinstance(payload, dict) and isinstance(payload.get('code'), int) and payload['code'] != 0)):
        raise AssertionError(f'{path}: expected a handled rejection, got HTTP {status}')
    success = status == 200 and (not isinstance(payload, dict) or payload.get('code') == 0)
    if success != ok:
        message = payload.get('msg', payload.get('message', '')) if isinstance(payload, dict) else 'non-JSON response'
        raise AssertionError(f'{path}: HTTP {status}, code={payload.get("code") if isinstance(payload,dict) else "-"}, {message}')
    return payload.get('data') if isinstance(payload, dict) else payload


def check(case_id, title, fn):
    started = time.perf_counter()
    try:
        fn()
        result = {'id': case_id, 'name': title, 'status': 'PASS'}
    except Exception as error:
        result = {'id': case_id, 'name': title, 'status': 'FAIL', 'error': str(error)[:1000]}
    result['seconds'] = round(time.perf_counter()-started, 3)
    CASES[:] = [old for old in CASES if old['id'] != case_id]
    CASES.append(result)
    print(result['status'], case_id, title, result.get('error', ''), flush=True)
    REPORT.write_text(json.dumps(CASES, ensure_ascii=False, indent=2), encoding='utf-8')


def require(value, reason='Assertion failed'):
    if not value:
        raise AssertionError(reason)


def captcha():
    key = str(uuid.uuid4())
    image = call('/api/common/captcha/gen?key='+key, method='GET')
    require(image.startswith(b'\x89PNG'), 'Captcha must be PNG')
    # Only the dedicated test Redis: normal login still validates and consumes this code.
    return {'captchaKey': key, 'captchaValue': env.redis('GET', 'sys:captcha:'+key)}


def login(account, ok=True, **changes):
    return call('/api/sys/user/login',
                {'userName': account['userName'], 'password': account['password'], **captcha(), **changes}, ok=ok)


def seed(name, role, dept, prefix):
    uid = prefix+'_'+name
    password, salt = secrets.token_urlsafe(20), secrets.token_hex(4)
    md5 = lambda text: hashlib.md5(text.encode()).hexdigest()
    hashed = md5(md5(password)+salt)
    env.sql(f"INSERT INTO el_sys_user(id,user_name,real_name,password,salt,state,dept_code,employee_no) VALUES('{uid}','{uid}','QA虚构{name}','{hashed}','{salt}',0,'{dept}','{uid}'); INSERT INTO el_sys_user_role(id,user_id,role_id) VALUES('{uid}_r','{uid}','{role}')")
    return {'id': uid, 'userName': uid, 'password': password}


def save(f):
    FIXTURE.write_text(json.dumps(f, ensure_ascii=False, indent=2), encoding='utf-8')


def fixture():
    return json.loads(FIXTURE.read_text(encoding='utf-8'))


def candidate(f, suffix, **changes):
    return call(A+'candidate/create', {'candidateName':'QA虚构候选人'+suffix,
        'candidateNo':f['prefix']+suffix, 'mobile':'13800000000', 'email':'qa@example.invalid',
        'departId':f['departId'], 'positionId':f['positionId'], 'batchNo':f['prefix'], **changes}, f['hr']['token'])


def verify(candidate):
    return call(A+'candidate/verify', {'candidateName':candidate['candidateName'], 'accessCode':candidate['accessCode']})


def own_result(aid, token):
    result = call(A+'my-result', {'id':aid}, token)
    require(set(result).issubset({'assignmentId','resultAvailable','passed'}), 'Result exposes extra fields')
    return result


def no_answers(payload):
    if isinstance(payload, list):
        for item in payload:
            no_answers(item)
    elif isinstance(payload, dict):
        for key, value in payload.items():
            require(not (key in {'analysis','isRight','actualScore','referenceAnswer','gradingCriteria','userScore','objectiveScore','subjectiveScore','graderComment'} and value is not None),
                    'Candidate payload leaks '+key)
            no_answers(value)


def prepare():
    require(not FIXTURE.exists(), 'Existing fixture; use a fresh environment')
    prefix = 'qa'+uuid.uuid4().hex[:10]
    departs = [row.split('\t') for row in env.sql("SELECT id,dept_code FROM el_sys_depart WHERE status=1 AND parent_id<>'0' ORDER BY id").splitlines()]
    f = {'prefix':prefix, 'departId':departs[0][0], 'deptCode':departs[0][1], 'otherDepartId':departs[1][0], 'otherDeptCode':departs[1][1]}
    for name, role in [('admin','admin'),('hr','HR'),('otherhr','HR')]:
        f[name] = seed(name,role,f['deptCode'],prefix)
        f[name]['token'] = login(f[name])['token']
    save(f)
    admin = f['admin']['token']
    pos = {'code':prefix.upper(), 'name':'QA全链路岗位', 'status':1,'sort':1, 'departmentIds':[f['departId']], 'grades':[]}
    call('/api/exam/position/save', pos, admin)
    f['positionId'] = env.sql("SELECT id FROM el_position WHERE code='"+prefix.upper()+"'")
    f['repos'], f['exams'], f['questions'] = {}, {}, {}
    for scene in ['INTERVIEW','REGULARIZATION','PROMOTION']:
        repo = {'title':'QA'+scene,'catId':'RECRUITMENT' if scene=='INTERVIEW' else 'EMPLOYEE_PROMOTION',
                'departId':f['departId'],'positionId':f['positionId'],'sceneType':scene,'status':1}
        call(R+'save',repo,admin)
        rid = env.sql(f"SELECT id FROM el_repo WHERE title='QA{scene}'")
        f['repos'][scene] = rid
        types = ['radio','multi','judge'] + (['short'] if scene=='PROMOTION' else [])
        for kind in types:
            answers = [] if kind=='short' else [{'content':'正确选项一','isRight':True},{'content':'正确选项二' if kind=='multi' else '错误选项','isRight':kind=='multi'}]+([{'content':'错误选项','isRight':False}] if kind=='multi' else [])
            body = {'repoId':rid,'quType':kind,'difficultyLevel':'1','content':f'QA {scene} {kind} 企业规范题',
                    'analysis':'保密解析','referenceAnswer':'参考答案','gradingCriteria':'评分要点','status':1,'answerList':answers}
            call(QU+'save',body,admin)
        f['questions'][scene] = env.sql(f"SELECT id,qu_type FROM el_repo_qu WHERE repo_id='{rid}'")
        exam = {'title':'QA'+scene+'考核','content':'虚构验收考核','departId':f['departId'],'positionId':f['positionId'],
                'sceneType':scene,'repoId':rid,'totalTime':30,'handMin':0,'qualifyScore':20,'templateStatus':1,
                'optionShuffle':1,'openType':1,'state':0,'chance':1,'lateMax':0,
                'ruleList':[{'quType':kind,'quCount':1,'quScore':10} for kind in types]}
        call(E+'save',exam,admin)
        f['exams'][scene] = env.sql(f"SELECT id FROM el_exam WHERE repo_id='{rid}'")
    f['employee'] = {'userName':prefix+'_employee','password':secrets.token_urlsafe(20)}
    register = {**f['employee'], 'realName':'QA虚构员工','employeeNo':prefix+'_E01','deptCode':f['deptCode'],'mobile':'','email':'',**captcha()}
    call('/api/sys/user/reg',register)
    f['employee']['id'] = env.sql("SELECT id FROM el_sys_user WHERE user_name='"+f['employee']['userName']+"'")
    save(f)
    check('SETUP-01','管理员通过真实 API 创建岗位、三场景题库/题目/模板',lambda: require(all(f['exams'].values())))
    check('AUTH-01','员工手机邮箱留空注册后待审核',lambda: require(env.sql("SELECT state FROM el_sys_user WHERE id='"+f['employee']['id']+"'")=='2'))
    check('AUTH-02','未审核员工不可登录',lambda: login(f['employee'],ok=False))
    call('/api/sys/user/registration/audit',{'userId':f['employee']['id'],'action':'APPROVE','remark':'QA审核'},f['hr']['token'])
    f['employee']['token'] = login(f['employee'])['token']
    f['other'] = seed('other','EMPLOYEE',f['deptCode'],prefix)
    f['other']['token'] = login(f['other'])['token']
    f['foreign'] = seed('foreign','EMPLOYEE',f['otherDeptCode'],prefix)
    f['foreign']['token'] = login(f['foreign'])['token']
    save(f)
    check('AUTH-03','HR 审核后员工可正常登录',lambda: require(f['employee']['token'] is not None))


def api():
    f=fixture();admin=f['admin']['token'];hr=f['hr']['token'];emp=f['employee']['token'];other=f['other']['token']
    check('AUTH-04','缺少验证码拒绝登录',lambda: call('/api/sys/user/login',f['employee'],ok=False))
    challenge=captcha()
    login(f['other'],**challenge)
    check('AUTH-05','验证码一次性消费不可重放',lambda: call('/api/sys/user/login',{**f['other'],**challenge},ok=False))
    # The successful login above rotates that identity token.
    f['other']['token']=login(f['other'])['token'];other=f['other']['token'];save(f)
    check('AUTH-06','篡改 token 拒绝认证',lambda: call('/api/sys/user/info',{},emp[:-6]+'AAAAAA',ok=False))
    for name,token in [('匿名',None),('员工',emp)]:
        for endpoint in ['employee/create','candidate/paging']:
            check('PERM-'+name+'-'+endpoint,name+'不能访问管理分配接口',lambda endpoint=endpoint,token=token:call(A+endpoint,{},token,ok=False))
        check('PERM-'+name+'-report',name+'不能查询完整成绩',lambda token=token:call(RESULT+'paging',{},token,ok=False))
    check('PERM-HR-bank','HR 不能创建题库',lambda:call(R+'save',{},hr,ok=False))
    check('PERM-HR-grading','HR 默认不能阅卷',lambda:call(G+'paging',{},hr,ok=False))
    check('EMP-01','跨部门员工发放失败且整批回滚',lambda:call(A+'employee/create',{'examId':f['exams']['REGULARIZATION'],'userIds':[f['employee']['id'],f['foreign']['id']],'batchNo':f['prefix']+'_invalid'},hr,ok=False))
    check('EMP-02','无效批次没有残留任务',lambda:require(env.sql("SELECT COUNT(*) FROM el_exam_assignment WHERE batch_no='"+f['prefix']+"_invalid'")=='0'))
    body={'examId':f['exams']['REGULARIZATION'],'userIds':[f['employee']['id'],f['other']['id']],'batchNo':f['prefix']}
    issued=call(A+'employee/create',body,hr);aid=issued['assignmentIds'][0]
    aid=env.sql(f"SELECT id FROM el_exam_assignment WHERE user_id='{f['employee']['id']}'")
    f['employeeAssignment']=aid;save(f)
    check('EMP-03','默认发放有效期为 14 天',lambda:require(env.sql("SELECT TIMESTAMPDIFF(DAY,valid_from,expire_at) FROM el_exam_assignment WHERE id='"+aid+"'")=='14'))
    check('EMP-04','重复发放不创建新任务',lambda:require(call(A+'employee/create',body,hr)['created']==0))
    mine=call(A+'my-paging',{'userId':f['other']['id']},emp)
    check('EMP-05','个人列表忽略伪造 userId',lambda:require(len(mine['records'])==1 and mine['records'][0]['id']==aid))
    c=f.get('candidate') or candidate(f,'A');f['candidate']=c;save(f);ct=verify(c)['token']
    check('CAND-01','发放考核码为 6 位大写字母数字',lambda:require(len(c['accessCode'])==6 and c['accessCode'].isalnum() and c['accessCode']==c['accessCode'].upper()))
    check('CAND-02','重复候选人编号与批次拒绝新增',lambda:candidate_duplicate(f))
    check('CAND-03','候选人不能查管理成绩',lambda:call(RESULT+'paging',{},ct,ok=False))
    check('CAND-04','错误姓名拒绝认证',lambda:call(A+'candidate/verify',{'candidateName':'错误姓名','accessCode':c['accessCode']},ok=False))
    def concurrent_start():
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
            values=list(pool.map(lambda _:call(P+'create-by-assignment',{'id':c['assignmentId']},ct),range(4)))
        require(len({x['paperId'] for x in values})==1)
        f['candidatePaper']=values[0]['paperId'];save(f)
    check('PAPER-01','4 个并发开考请求只生成一卷',concurrent_start)
    pid=f['candidatePaper'];cards=[item for group in call(Q+'list-card',{'id':pid},ct) for item in group['itemList']]
    check('PAPER-02','试卷生成三类客观题',lambda:require(len(cards)==3))
    for endpoint, payload in [(P+'detail',{'id':pid}),(Q+'list-card',{'id':pid}),(P+'hand',{'id':pid}),(A+'current',{'id':c['assignmentId']}),(A+'my-result',{'id':c['assignmentId']}),(P+'create-by-assignment',{'id':c['assignmentId']})]:
        check('CROSS-'+endpoint,'员工不可读取或操作他人考核',lambda endpoint=endpoint,payload=payload:call(endpoint,payload,emp,ok=False))
    for card in cards:
        qid=card['quId']
        detail=call(Q+'detail-for-answer',{'paperId':pid,'quId':qid},ct)
        check('LEAK-'+detail['quType'],'作答详情不泄漏评分键、解析及分数',lambda detail=detail:no_answers(detail))
        answer_ids=env.sql(f"SELECT answer_id FROM el_paper_qu_answer WHERE paper_id='{pid}' AND qu_id='{qid}' AND is_right=1").splitlines()
        check('ANSWER-'+detail['quType'],'合法答案保存及刷新恢复',lambda qid=qid,answer_ids=answer_ids:answer_and_restore(pid,qid,answer_ids,ct))
        check('CROSS-answer-'+detail['quType'],'跨用户保存答案被拒绝',lambda qid=qid,answer_ids=answer_ids:call(Q+'fill-answer',{'paperId':pid,'quId':qid,'checkedItems':answer_ids},emp,ok=False))
        if detail['quType']=='multi':
            check('ANSWER-invalid','伪造选项及重复选项拒绝',lambda qid=qid,answer_ids=answer_ids:invalid_answers(pid,qid,answer_ids,ct))
    first=cards[0]['quId'];before=call(Q+'detail-for-answer',{'paperId':pid,'quId':first},ct)
    env.sql(f"UPDATE el_repo_qu SET content='修改后的源题',analysis='修改后的解析' WHERE id='{first}'; UPDATE el_repo_qu_answer SET content='修改后的源选项',is_right=NOT is_right WHERE qu_id='{first}'")
    check('SNAPSHOT-01','修改源题和评分键不改变已生成快照',lambda:require(call(Q+'detail-for-answer',{'paperId':pid,'quId':first},ct)==before))
    def hand():
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
            responses=list(pool.map(lambda _:call(P+'hand',{'id':pid},ct),range(4)))
        for response in responses:no_answers(response)
        require(env.sql(f"SELECT try_count FROM el_exam_record WHERE user_id=(SELECT user_id FROM el_paper WHERE id='{pid}')")=='1')
        require(env.sql(f"SELECT user_score FROM el_paper WHERE id='{pid}'")=='30.00')
    check('HAND-01','4 并发交卷一次结算且快照自动评分 30 分',hand)
    check('RESULT-01','候选人立即可查通过结果且无额外敏感字段',lambda:require(own_result(c['assignmentId'],ct)['passed'] is True))
    check('HAND-02','交卷后禁止改答',lambda:call(Q+'fill-answer',{'paperId':pid,'quId':first,'checkedItems':[]},ct,ok=False))
    check('HAND-03','交卷后禁止重考',lambda:call(P+'create-by-assignment',{'id':c['assignmentId']},ct,ok=False))
    ct=verify(c)['token']
    check('RESULT-02','原有效期内原考核码可再次查询通过结果',lambda:require(own_result(c['assignmentId'],ct)['passed'] is True))
    check('REPORT-01','HR 可查询完整成绩及作答',lambda:require(call(A+'candidate/result-detail',{'id':c['assignmentId']},hr)['userScore']==30))
    # Employee objective submission: multi deliberately incomplete -> zero, others correct.
    ep=call(P+'create-by-assignment',{'id':aid},emp)['paperId']
    for card in [item for group in call(Q+'list-card',{'id':ep},emp) for item in group['itemList']]:
        qid=card['quId'];correct=env.sql(f"SELECT answer_id FROM el_paper_qu_answer WHERE paper_id='{ep}' AND qu_id='{qid}' AND is_right=1").splitlines()
        call(Q+'fill-answer',{'paperId':ep,'quId':qid,'checkedItems':correct[:1]},emp)
    call(P+'hand',{'id':ep},emp)
    check('SCORE-01','多选少选得 0 分，单选和判断正常计分',lambda:require(env.sql(f"SELECT user_score FROM el_paper WHERE id='{ep}'")=='20.00'))
    check('RESULT-03','员工交卷后立即自动公布是否通过',lambda:require(own_result(aid,emp)['passed'] is True))
    f['employeePaper']=ep;save(f)


def candidate_duplicate(f):
    call(A+'candidate/create',{'candidateName':'QA重复','candidateNo':f['prefix']+'A','mobile':'13800000000','email':'qa@example.invalid',
         'departId':f['departId'],'positionId':f['positionId'],'batchNo':f['prefix']},f['hr']['token'],ok=False)


def answer_and_restore(pid,qid,answer_ids,token):
    no_answers(call(Q+'fill-answer',{'paperId':pid,'quId':qid,'checkedItems':answer_ids},token))
    detail=call(Q+'detail-for-answer',{'paperId':pid,'quId':qid},token)
    selected={a['answerId'] for a in detail['answerList'] if a.get('checked')}
    require(selected==set(answer_ids),'Saved choices differ from reloaded choices')


def invalid_answers(pid,qid,answer_ids,token):
    for value in [['fake-option'],answer_ids+answer_ids]:
        call(Q+'fill-answer',{'paperId':pid,'quId':qid,'checkedItems':value},token,ok=False)


def grading():
    f=fixture();emp=f['employee']['token'];admin=f['admin']['token'];hr=f['hr']['token']
    aid=call(A+'employee/create',{'examId':f['exams']['PROMOTION'],'userIds':[f['employee']['id']],'batchNo':f['prefix']+'_grading'},hr)['assignmentIds'][0]
    pid=call(P+'create-by-assignment',{'id':aid},emp)['paperId']
    qid=env.sql(f"SELECT qu_id FROM el_paper_qu WHERE paper_id='{pid}' AND qu_type='short'")
    text='QA文字作答\n保留换行、中文和特殊字符 < > & 😀'
    check('SHORT-01','简答题超过 5000 字符拒绝',lambda:call(Q+'fill-text-answer',{'paperId':pid,'quId':qid,'answerText':'x'*5001},emp,ok=False))
    call(Q+'fill-text-answer',{'paperId':pid,'quId':qid,'answerText':text},emp)
    check('SHORT-02','简答中文/换行/特殊字符保存和恢复',lambda:require(call(Q+'detail-for-answer',{'paperId':pid,'quId':qid},emp)['textAnswer']==text))
    call(P+'hand',{'id':pid},emp)
    check('GRADE-01','含主观题交卷后待阅卷不公布结果',lambda:require(own_result(aid,emp)['resultAvailable'] is False))
    d=call(G+'detail',{'id':pid},admin);pq=d['questions'][0]['id']
    check('GRADE-02','尚有未评分题不允许完成阅卷',lambda:call(G+'finalize',{'paperId':pid,'expectedVersion':d['version']},admin,ok=False))
    for score in [-1,11,1.234]:
        check('GRADE-range-'+str(score),'非法评分拒绝',lambda score=score:call(G+'question/save',{'paperQuId':pq,'score':score,'expectedVersion':d['version']},admin,ok=False))
    body={'paperQuId':pq,'score':0,'comment':'明确零分','expectedVersion':d['version']}
    d=call(G+'question/save',body,admin)
    check('GRADE-03','零分正确保存且重试不重复留痕',lambda:require(len(call(G+'question/save',body,admin)['logs'])==1))
    check('GRADE-04','旧版本评分覆盖被拒绝',lambda:call(G+'question/save',{**body,'score':8},admin,ok=False))
    d=call(G+'question/save',{**body,'score':8,'comment':'复核后8分','expectedVersion':d['version']},admin)
    check('GRADE-05','评分修改保留前后值审计',lambda:require(len(d['logs'])==2))
    def finalize():
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
            list(pool.map(lambda _:call(G+'finalize',{'paperId':pid,'expectedVersion':d['version']},admin),range(4)))
        require(env.sql(f"SELECT COUNT(*) FROM el_paper_grading_log WHERE paper_id='{pid}' AND action='FINALIZE'")=='1')
    check('GRADE-06','并发完成阅卷只产生一次终审',finalize)
    check('RESULT-04','终审后员工只看到未通过',lambda:require(own_result(aid,emp)['passed'] is False))
    check('GRADE-07','终审后禁止修改评分',lambda:call(G+'question/save',{**body,'score':9,'expectedVersion':d['version']},admin,ok=False))
    check('REPORT-02','HR 成绩包含终审后 8 分结果',lambda:require(call(A+'employee/result-detail',{'id':aid},hr)['userScore']==8))
    f['gradedPaper']=pid;save(f)


def expiry():
    f=fixture();hr=f['hr']['token'];emp=f['employee']['token']
    suffix=uuid.uuid4().hex[:6]
    now=dt.datetime.now()
    fmt=lambda x:x.strftime('%Y-%m-%d %H:%M:%S')
    future=candidate(f,'future'+suffix,validFrom=fmt(now+dt.timedelta(days=1)),expireAt=fmt(now+dt.timedelta(days=2)))
    check('TIME-01','未来生效的考核码拒绝提前进入',lambda:call(A+'candidate/verify',{'candidateName':future['candidateName'],'accessCode':future['accessCode']},ok=False))
    expired=candidate(f,'expire'+suffix);expired_token=verify(expired)['token']
    pid=call(P+'create-by-assignment',{'id':expired['assignmentId']},expired_token)['paperId']
    qid=env.sql(f"SELECT qu_id FROM el_paper_qu WHERE paper_id='{pid}' LIMIT 1")
    env.sql(f"UPDATE el_paper SET limit_time=DATE_SUB(NOW(),INTERVAL 1 SECOND),hand_min_snapshot=999 WHERE id='{pid}'")
    check('TIME-02','到期禁止保存答案',lambda:call(Q+'fill-answer',{'paperId':pid,'quId':qid,'checkedItems':[]},expired_token,ok=False))
    def auto_hand():
        until=time.monotonic()+45
        while time.monotonic()<until:
            if env.sql(f"SELECT hand_state FROM el_paper WHERE id='{pid}'")=='1':return
            time.sleep(1)
        raise AssertionError('Overdue recovery did not submit in 45 seconds')
    check('TIME-03','定时补偿到期自动交卷并绕过最短作答时间',auto_hand)
    check('TIME-04','到期任务不能重开试卷',lambda:call(P+'create-by-assignment',{'id':expired['assignmentId']},expired_token,ok=False))
    reset=candidate(f,'reset'+suffix);old=verify(reset)['token'];rp=call(P+'create-by-assignment',{'id':reset['assignmentId']},old)['paperId']
    new=call(A+'candidate/reset-code',{'id':reset['assignmentId']},hr)
    check('CODE-01','重置后旧码失效',lambda:call(A+'candidate/verify',{'candidateName':reset['candidateName'],'accessCode':reset['accessCode']},ok=False))
    reset['accessCode']=new['accessCode'];new_token=verify(reset)['token']
    check('CODE-02','新码恢复原试卷',lambda:require(call(P+'create-by-assignment',{'id':reset['assignmentId']},new_token)['paperId']==rp))
    call(A+'change-status',{'id':reset['assignmentId'],'action':'DISABLE','reason':'QA停用'},hr)
    check('CODE-03','停用后的候选人会话不可继续作答',lambda:call(Q+'list-card',{'id':rp},new_token,ok=False))
    check('CODE-04','停用考核码不能认证',lambda:call(A+'candidate/verify',{'candidateName':reset['candidateName'],'accessCode':reset['accessCode']},ok=False))
    aid=call(A+'employee/create',{'examId':f['exams']['REGULARIZATION'],'userIds':[f['employee']['id']],'batchNo':f['prefix']+'_disable'},hr)['assignmentIds'][0]
    ep=call(P+'create-by-assignment',{'id':aid},emp)['paperId']
    call(A+'employee/change-status',{'id':aid,'action':'DISABLE','reason':'QA暂停'},hr)
    check('EMP-06','员工任务停用不撤销员工账号',lambda:call('/api/sys/user/info',{},emp))
    check('EMP-07','停用员工任务不可继续作答',lambda:call(P+'create-by-assignment',{'id':aid},emp,ok=False))
    call(A+'employee/change-status',{'id':aid,'action':'ENABLE'},hr)
    check('EMP-08','员工任务恢复沿用同一试卷',lambda:require(call(P+'create-by-assignment',{'id':aid},emp)['paperId']==ep))


def main():
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    action=sys.argv[1] if len(sys.argv)>1 else 'all'
    if REPORT.exists():
        CASES.extend(json.loads(REPORT.read_text(encoding='utf-8')))
    for name in (['prepare','api','grading','expiry'] if action=='all' else [action]):
        print('Running '+name,flush=True)
        try:
            globals()[name]()
            CASES[:] = [old for old in CASES if old['id'] != 'BLOCK-'+name]
            REPORT.write_text(json.dumps(CASES, ensure_ascii=False, indent=2), encoding='utf-8')
        except Exception as error:
            check('BLOCK-'+name,name+' 前置/基础流程',lambda error=error:(_ for _ in ()).throw(error))
            raise
    print('CASES',len(CASES),'FAILED',sum(x['status']=='FAIL' for x in CASES),flush=True)
    if any(x['status']=='FAIL' for x in CASES):
        raise SystemExit(1)

if __name__=='__main__':main()
