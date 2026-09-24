"""Import/export, timing, access-scope and 100-user workload cases."""
import base64
import concurrent.futures
import datetime as dt
import io
import json
import statistics
import sys
import time
import uuid
import zipfile
import xml.etree.ElementTree as ET
import acceptance as a
import environment as e

NS='http://schemas.openxmlformats.org/spreadsheetml/2006/main'


def workbook(template,rows,sheet='xl/worksheets/sheet1.xml'):
    out=io.BytesIO()
    with zipfile.ZipFile(io.BytesIO(template)) as src,zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as dst:
        for info in src.infolist():
            raw=src.read(info.filename)
            if info.filename==sheet:
                tree=ET.fromstring(raw);data=tree.find('{'+NS+'}sheetData')
                for row in list(data)[1:]:data.remove(row)
                for i,values in enumerate(rows,2):
                    row=ET.SubElement(data,'{'+NS+'}row',{'r':str(i)})
                    for j,value in enumerate(values):
                        cell=ET.SubElement(row,'{'+NS+'}c',{'r':chr(65+j)+str(i),'t':'inlineStr'})
                        ET.SubElement(ET.SubElement(cell,'{'+NS+'}is'),'{'+NS+'}t').text=str(value)
                raw=ET.tostring(tree,encoding='utf-8',xml_declaration=True)
            dst.writestr(info,raw)
    return out.getvalue()


def cells(raw,sheet='xl/worksheets/sheet1.xml'):
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        ns={'s':NS};shared=[]
        if 'xl/sharedStrings.xml' in z.namelist():
            shared=[''.join(x.itertext()) for x in ET.fromstring(z.read('xl/sharedStrings.xml')).findall('s:si',ns)]
        root=ET.fromstring(z.read(sheet))
        a.require(not root.findall('.//s:f',ns),'Export must not contain formulas')
        rows=[]
        for row in root.findall('.//s:sheetData/s:row',ns):
            values=[]
            for cell in row:
                val=cell.find('s:v',ns)
                values.append(shared[int(val.text)] if cell.get('t')=='s' else ''.join(cell.itertext()))
            rows.append(values)
        return rows


def imports():
    f=a.fixture();hr=f['hr']['token'];admin=f['admin']['token'];emp=f['employee']['token'];rid=f['repos']['REGULARIZATION']
    template=a.call(a.QU+'import-template',token=admin,method='GET')
    rows=[
      ['QA-X1','单选题','QA Excel 单选题','正确','错误','','','','','A','简单','解析','',''],
      ['QA-X2','多选题','QA Excel 多选题','正确1','正确2','错误','','','','A,B','简单','解析','',''],
      ['QA-X3','判断题','QA Excel 判断题','','','','','','','正确','简单','解析','',''],
      ['QA-X4','简答题','QA Excel 简答题','','','','','','','','简单','参考答案','评分标准',''],
      ['QA-X5','单选题','QA Excel 单选题','正确','错误','','','','','A','简单','解析','',''],
      ['QA-X6','单选题','QA Excel 错误题','正确','错误','','','','','Z','简单','','','']]
    file=workbook(template,rows,'xl/worksheets/sheet2.xml')
    kwargs={'file':file,'fields':{'repoId':rid}}
    preview=a.call(a.QU+'import/validate',token=admin,**kwargs)
    a.check('IMPORT-Q-01','Excel 题目预校验不落库',lambda:a.require(e.sql("SELECT COUNT(*) FROM el_repo_qu WHERE external_code LIKE 'QA-X%'")=='0'))
    result=a.call(a.QU+'import',token=admin,**kwargs)
    a.check('IMPORT-Q-02','四题型 Excel 部分成功，重复与错误隔离',lambda:a.require(result.get('successCount')==4 and result.get('failureCount')==1 and result.get('duplicateCount')==1,str({k:v for k,v in result.items() if k!='errorReportBase64'})))
    a.check('IMPORT-Q-03','Excel 导入错误报告包含原始行号',lambda:a.require({row[-1] for row in cells(base64.b64decode(result['errorReportBase64']))[1:]}=={'6','7'}))
    retry=a.call(a.QU+'import',token=admin,**kwargs)
    a.check('IMPORT-Q-04','相同题目重传不重复创建',lambda:a.require(retry.get('successCount')==0))
    a.check('IMPORT-Q-05','员工无题目导入权限',lambda:a.call(a.QU+'import',token=emp,ok=False,**kwargs))
    word=a.call(a.QU+'import-word-template',token=admin,method='GET')
    wp=a.call(a.QU+'import-word/validate',token=admin,file=word,filename='QA.docx',fields={'repoId':rid})
    a.check('IMPORT-W-01','Word 模板四题型可预览',lambda:a.require(len(wp.get('questions',[]))==4,str({k:v for k,v in wp.items() if k!='questions'})))
    wr=a.call(a.QU+'import-word',token=admin,file=word,filename='QA.docx',fields={'repoId':rid})
    a.check('IMPORT-W-02','Word 四题型正确入库',lambda:a.require(wr.get('successCount')==4))
    a.check('IMPORT-W-03','重复 Word 导入不新增',lambda:a.require(a.call(a.QU+'import-word',token=admin,file=word,filename='QA.docx',fields={'repoId':rid}).get('successCount')==0))
    ct=a.call(a.A+'candidate/import-template',{},hr)
    now=dt.datetime.now();start=(now-dt.timedelta(minutes=1)).strftime('%Y-%m-%d %H:%M:%S');end=(now+dt.timedelta(days=14)).strftime('%Y-%m-%d %H:%M:%S')
    position=e.sql("SELECT code FROM el_position WHERE id='"+f['positionId']+"'")
    rows=[['QA导入'+str(i),f['prefix']+'_import'+str(1 if i==4 else i),'13800000000','invalid' if i==3 else 'qa@example.invalid',f['deptCode'],position,f['prefix']+'_import',start,end] for i in range(1,5)]
    raw=workbook(ct,rows)
    preview=a.call(a.A+'candidate/import-validate',token=hr,file=raw)
    task=preview['taskId']
    a.check('IMPORT-C-01','候选人预校验 2 有效/1 错误/1 重复',lambda:a.require((preview['validCount'],preview['failureCount'],preview['duplicateCount'])==(2,1,1)))
    a.check('IMPORT-C-02','其他 HR 不能提交别人的导入任务',lambda:a.call(a.A+'candidate/import',{'taskId':task},f['otherhr']['token'],ok=False))
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        replies=list(pool.map(lambda _:a.call(a.A+'candidate/import',{'taskId':task},hr),range(4)))
    a.check('IMPORT-C-03','并发确认导入只发放一次',lambda:a.require(all(x==replies[0] for x in replies) and replies[0]['successCount']==2))
    codes=a.call(a.A+'candidate/import-codes',{'taskId':task},hr);errors=a.call(a.A+'candidate/import-error',{'taskId':task},hr)
    a.check('IMPORT-C-04','考核码清单与错误行报告内容正确',lambda:a.require(len(cells(codes))==3 and len(cells(errors))==3))
    c=next(x for x in replies[0]['rows'] if x['status']=='SUCCESS')
    a.check('IMPORT-C-05','导入发放的考核码可真实认证',lambda:a.verify(c))
    a.call(a.A+'candidate/import-close',{'taskId':task},hr)
    a.check('IMPORT-C-06','关闭已发放任务后仍能恢复并下载原考核码',lambda:a.require(a.call(a.A+'candidate/import-restore',{'taskId':task},hr)==replies[0] and len(cells(a.call(a.A+'candidate/import-codes',{'taskId':task},hr)))==3))
    a.check('IMPORT-C-07','重复上传同一文件恢复原任务和原考核码',lambda:a.require(a.call(a.A+'candidate/import-validate',token=hr,file=raw)==replies[0]))
    a.check('IMPORT-C-08','未登录不能恢复清单',lambda:a.call(a.A+'candidate/import-restore',{'taskId':task},ok=False))
    a.check('IMPORT-C-09','员工不能恢复清单',lambda:a.call(a.A+'candidate/import-restore',{'taskId':task},emp,ok=False))
    a.check('IMPORT-C-10','其他 HR 不能恢复或下载他人清单',lambda:(a.call(a.A+'candidate/import-restore',{'taskId':task},f['otherhr']['token'],ok=False),a.call(a.A+'candidate/import-codes',{'taskId':task},f['otherhr']['token'],ok=False)))
    status,headers,body=a.request(a.A+'candidate/import-restore',{'taskId':task},hr)
    a.check('IMPORT-C-11','恢复清单响应禁止浏览器缓存',lambda:a.require(status==200 and headers.get('Cache-Control')=='no-store'))
    f['importArchive']={'view':replies[0],'candidate':c};a.save(f)
    # Fictional upload fixture for browser validation; never stores access codes.
    uirows=[['QA浏览器导入'+str(i),f['prefix']+'_uiimport'+str(i),'13800000000','bad-email' if i==3 else 'qa@example.invalid',f['deptCode'],position,f['prefix']+'_uiimport',start,end] for i in range(1,4)]
    (e.ROOT/'work/qa-candidates.xlsx').write_bytes(workbook(ct,uirows))


def archive_recovery():
    f=a.fixture();hr=f['hr']['token'];old=f['importArchive']['view'];task=old['taskId']
    restored=a.call(a.A+'candidate/import-restore',{},hr)
    a.check('IMPORT-C-12','真实进程重启后原 JWT 可恢复相同任务与全部原码',lambda:a.require(restored==old))
    a.check('IMPORT-C-13','真实进程重启后原考核码仍可认证',lambda:a.verify(f['importArchive']['candidate']))
    a.check('IMPORT-C-14','重启后重复提交不新增分配',lambda:a.require(a.call(a.A+'candidate/import',{'taskId':task},hr)==old and e.sql("SELECT COUNT(*) FROM el_exam_assignment WHERE batch_no='"+f['prefix']+"_import'")=='2'))
    payload=e.sql("SELECT payload FROM el_candidate_import_task WHERE id='"+task+"'")
    a.check('IMPORT-C-15','归档表只保存密文，不包含姓名、联系方式及原码',lambda:a.require(payload.startswith('v1:') and all(str(value) not in payload for row in old['rows'] for value in [row['candidateName'],*row['values'][2:4],row.get('accessCode','')] if value)))
    rows=[r for r in old['rows'] if r['status']=='SUCCESS']
    a.call(a.A+'candidate/reset-code',{'id':rows[0]['assignmentId']},hr)
    latest=a.call(a.A+'candidate/import-restore',{'taskId':task},hr)
    a.check('IMPORT-C-16','重置后恢复清单不会重新暴露旧码',lambda:a.require(not next(r for r in latest['rows'] if r.get('assignmentId')==rows[0]['assignmentId']).get('accessCode') and rows[0]['accessCode'] not in str(cells(a.call(a.A+'candidate/import-codes',{'taskId':task},hr)))))
    e.sql("UPDATE el_exam_assignment SET expire_at=DATE_SUB(NOW(),INTERVAL 1 MINUTE) WHERE id='"+rows[1]['assignmentId']+"'")
    latest=a.call(a.A+'candidate/import-restore',{'taskId':task},hr)
    a.check('IMPORT-C-17','已过期码不再通过恢复接口提供',lambda:a.require(all(not r.get('accessCode') for r in latest['rows'])))
    e.sql("UPDATE el_candidate_import_task SET expires_at=0 WHERE id='"+task+"'")
    a.check('IMPORT-C-18','到期清单拒绝恢复和下载',lambda:(a.call(a.A+'candidate/import-restore',{'taskId':task},hr,ok=False),a.call(a.A+'candidate/import-codes',{'taskId':task},hr,ok=False)))


def reports():
    f=a.fixture();hr=f['hr']['token'];admin=f['admin']['token']
    query={'batchNo':f['prefix'],'current':1,'size':1}
    page=a.call(a.RESULT+'paging',query,hr)
    raw=a.call(a.RESULT+'export',query,hr);rows=cells(raw)
    a.check('REPORT-03','导出跨越全部页且具有 18 列',lambda:a.require(any(len(r)==18 for r in rows) and len(rows)>=page['total']+1))
    zero=a.call(a.RESULT+'paging',{'scoreMin':0,'scoreMax':0},hr)
    a.check('REPORT-04','零分筛选不遗漏零分记录',lambda:a.require(zero['total']>0 and bool(zero['records']) and all(x['userScore']==0 for x in zero['records'])))
    a.check('REPORT-05','倒置分数范围拒绝',lambda:a.call(a.RESULT+'paging',{'scoreMin':5,'scoreMax':0},hr,ok=False))
    a.check('REPORT-06','不把空导出错误保存为 Excel',lambda:a.call(a.RESULT+'export',{'batchNo':'NO_MATCH_'+uuid.uuid4().hex},hr,ok=False))
    # Change only the disposable other-HR role to department scope.
    role=f['prefix']+'_scoped'
    e.sql(f"INSERT INTO el_sys_role(id,role_name,role_level,data_scope) SELECT '{role}','QA部门HR',role_level,2 FROM el_sys_role WHERE id='HR'; INSERT INTO el_sys_role_menu(id,role_id,menu_id) SELECT REPLACE(UUID(),'-',''),'{role}',menu_id FROM el_sys_role_menu WHERE role_id='HR'; UPDATE el_sys_user_role SET role_id='{role}' WHERE user_id='{f['otherhr']['id']}'; UPDATE el_sys_user SET dept_code='{f['otherDeptCode']}' WHERE id='{f['otherhr']['id']}'")
    token=a.login(f['otherhr'])['token'];f['otherhr']['token']=token;a.save(f)
    a.check('SCOPE-01','部门范围 HR 无法查询另一部门成绩',lambda:a.require(a.call(a.RESULT+'paging',{},token)['total']==0))
    a.check('SCOPE-02','部门范围 HR 不能直接读取他部门试卷成绩',lambda:a.call(a.RESULT+'detail',{'id':f['candidatePaper']},token,ok=False))
    a.check('SCOPE-03','部门范围 HR 导出不能绕过数据范围',lambda:a.call(a.RESULT+'export',{},token,ok=False))


def immediate():
    f=a.fixture();bad=[]
    for i in range(20):
        c=a.candidate(f,'regression'+str(i)+uuid.uuid4().hex[:4])
        try:a.verify(c)
        except AssertionError as error:bad.append(str(error))
    a.check('TIME-DEFAULT','20 次默认立即生效发放后马上认证均成功',lambda:a.require(not bad,str(bad)))


def load():
    f=a.fixture();hr=f['hr']['token'];prefix=f['prefix']+'_load'
    # 100 independent identities; no JWT mocking, each uses the real login API.
    users=[a.seed('u'+str(i),'EMPLOYEE',f['deptCode'],prefix) for i in range(100)]
    print('100 test identities prepared; logging in...',flush=True)
    tokens=[]
    for index,u in enumerate(users):
        tokens.append(a.login(u)['token'])
        if index%20==19:print(f'Authenticated {index+1}/100 test users',flush=True)
    issued=a.call(a.A+'employee/create',{'examId':f['exams']['REGULARIZATION'],'userIds':[u['id'] for u in users],'batchNo':prefix},hr)
    ids=dict(line.split('\t') for line in e.sql("SELECT user_id,id FROM el_exam_assignment WHERE batch_no='"+prefix+"'").splitlines())
    barrier=__import__('threading').Barrier(100)
    def worker(pair):
        u,t=pair;barrier.wait(timeout=20);times=[];started=time.perf_counter()
        pid=a.call(a.P+'create-by-assignment',{'id':ids[u['id']]},t)['paperId'];times.append(('start',time.perf_counter()-started))
        cards=a.call(a.Q+'list-card',{'id':pid},t)
        # 3 rounds, spaced at 5 seconds, model ordinary ongoing answer saves.
        for n in range(3):
            for item in [x for group in cards for x in group['itemList']]:
                qid=item['quId'];d=a.call(a.Q+'detail-for-answer',{'paperId':pid,'quId':qid},t)
                if d['quType']=='short':continue
                started=time.perf_counter();a.call(a.Q+'fill-answer',{'paperId':pid,'quId':qid,'checkedItems':[d['answerList'][0]['answerId']]},t)
                times.append(('save',time.perf_counter()-started))
            if n<2:time.sleep(5)
        started=time.perf_counter();a.call(a.P+'hand',{'id':pid},t);times.append(('hand',time.perf_counter()-started))
        a.require(a.own_result(ids[u['id']],t)['resultAvailable'])
        return times
    print('Starting 100-user concurrent workload...',flush=True)
    begun=time.perf_counter();alltimes=[];errors=[]
    with concurrent.futures.ThreadPoolExecutor(max_workers=100) as pool:
        futures=[pool.submit(worker,pair) for pair in zip(users,tokens)]
        for future in concurrent.futures.as_completed(futures):
            try:alltimes.extend(future.result())
            except Exception as error:errors.append(str(error))
    stats={'users':100,'durationSeconds':round(time.perf_counter()-begun,2),'errors':errors}
    for kind in ['start','save','hand']:
        values=sorted(v*1000 for k,v in alltimes if k==kind)
        if values:stats[kind]={'count':len(values),'p50Ms':round(statistics.median(values),1),'p95Ms':round(values[int((len(values)-1)*.95)],1),'maxMs':round(max(values),1)}
    (e.LOGS/'qa-load-results.json').write_text(json.dumps(stats,ensure_ascii=False,indent=2),encoding='utf-8')
    a.check('LOAD-01','100 独立用户并发开考/持续保存/交卷无请求错误',lambda:a.require(not errors,str(errors)[:1000]))
    a.check('LOAD-02','100 人任务均唯一试卷且全部交卷',lambda:a.require(e.sql("SELECT CONCAT(COUNT(*),':',COUNT(DISTINCT p.assignment_id),':',SUM(p.hand_state)) FROM el_paper p JOIN el_exam_assignment a ON a.id=p.assignment_id WHERE a.batch_no='"+prefix+"'")=='100:100:100'))
    print(json.dumps(stats,ensure_ascii=False),flush=True)


def ui_prepare():
    f=a.fixture()
    # Reuse known system APIs to create fresh UI identities and assignments.
    c=a.candidate(f,'UI'+uuid.uuid4().hex[:4],validFrom=(dt.datetime.now()-dt.timedelta(minutes=1)).strftime('%Y-%m-%d %H:%M:%S'))
    u=a.seed('browser','EMPLOYEE',f['deptCode'],f['prefix'])
    issued=a.call(a.A+'employee/create',{'examId':f['exams']['PROMOTION'],'userIds':[u['id']],'batchNo':f['prefix']+'_ui'},f['hr']['token'])
    f['ui']={'candidate':c,'employee':u,'assignment':issued['assignmentIds'][0]}
    a.save(f);print('Fictional browser fixture ready; credentials remain in ignored .local only.',flush=True)


if __name__=='__main__':
    sys.stdout.reconfigure(encoding='utf-8',errors='replace')
    if a.REPORT.exists():a.CASES.extend(json.loads(a.REPORT.read_text(encoding='utf-8')))
    globals()[sys.argv[1]]()
    a.REPORT.write_text(json.dumps(a.CASES,ensure_ascii=False,indent=2),encoding='utf-8')
    print('CASES',len(a.CASES),'FAILED',sum(x['status']=='FAIL' for x in a.CASES),flush=True)
    if any(x['status']=='FAIL' for x in a.CASES):raise SystemExit(1)
