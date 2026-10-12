"""Answer payloads are readable only while the owner's assessment is active."""
import json
import acceptance as a
import environment as e


def main():
    f = a.fixture()
    token = f['employee']['token']
    if 'answerRead' not in f:
        aid = a.call(a.A+'employee/create', {'examId':f['exams']['PROMOTION'],
            'userIds':[f['employee']['id']], 'batchNo':f['prefix']+'_read_guard'}, f['hr']['token'])['assignmentIds'][0]
        pid = a.call(a.P+'create-by-assignment', {'id':aid}, token)['paperId']
        qid = e.sql(f"SELECT qu_id FROM el_paper_qu WHERE paper_id='{pid}' LIMIT 1")
        f['answerRead'] = {'assignmentId':aid, 'paperId':pid, 'quId':qid}
        a.save(f)
    sample = f['answerRead']
    aid, pid, qid = (sample[k] for k in ['assignmentId','paperId','quId'])

    def read(paper=pid, question=qid, ok=True, actor=token):
        a.call(a.Q+'list-card', {'id':paper}, actor, ok=ok)
        a.call(a.Q+'detail-for-answer', {'paperId':paper,'quId':question}, actor, ok=ok)

    a.check('READ-ACTIVE', '有效试卷的本人仍可读取答题卡及题目', read)
    a.check('READ-OTHER', '其他员工和未登录者不能读取答题内容', lambda: [read(ok=False,actor=t) for t in [None,f['other']['token']]])
    for label, paper, owner in [
        ('OBJECTIVE',f['employeePaper'],token),
        ('GRADED',f['gradedPaper'],token),
        ('PENDING',f['scopeFlow']['foreignPaper'],f['foreign']['token'])
    ]:
        question = e.sql(f"SELECT qu_id FROM el_paper_qu WHERE paper_id='{paper}' LIMIT 1")
        a.check('READ-SUBMITTED-'+label, '已交卷/终审的本人也不能回读答题内容',
                lambda paper=paper,question=question,owner=owner:read(paper,question,False,owner))

    def disabled():
        a.call(a.A+'employee/change-status', {'id':aid,'action':'DISABLE','reason':'虚构验收'}, f['hr']['token'])
        try:
            read(ok=False)
        finally:
            a.call(a.A+'employee/change-status', {'id':aid,'action':'ENABLE'}, f['hr']['token'])
    a.check('READ-DISABLED', '员工任务停用后不能回读题目和已保存答案', disabled)
    a.check('READ-RESTORED', '恢复任务后仍读取同一份未交卷试卷', lambda: (
        a.require(a.call(a.P+'create-by-assignment', {'id':aid}, token)['paperId']==pid), read()))

    def expired():
        original = e.sql(f"SELECT DATE_FORMAT(expire_at,'%Y-%m-%d %H:%i:%s') FROM el_exam_assignment WHERE id='{aid}'")
        try:
            e.sql(f"UPDATE el_exam_assignment SET expire_at=DATE_SUB(NOW(), INTERVAL 1 SECOND) WHERE id='{aid}'")
            read(ok=False)
        finally:
            e.sql(f"UPDATE el_exam_assignment SET expire_at='{original}' WHERE id='{aid}'")
    a.check('READ-EXPIRED', '分配已过期但试卷计时尚未结束时仍拒绝答题读取', expired)
    a.check('READ-RESULT', '关闭答题读取后本人仍能查询最终是否通过', lambda: (
        a.require(a.own_result(f['employeeAssignment'],token)['passed'] is True),
        a.require(a.call(a.RESULT+'detail', {'id':f['gradedPaper']}, f['hr']['token'])['id']==f['gradedPaper'])))


if __name__ == '__main__':
    if a.REPORT.exists(): a.CASES.extend(json.loads(a.REPORT.read_text(encoding='utf-8')))
    main()
    if any(case['status']=='FAIL' for case in a.CASES): raise SystemExit(1)
