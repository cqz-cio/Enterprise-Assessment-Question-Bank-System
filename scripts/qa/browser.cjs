/* Browser acceptance with fresh isolated Chromium contexts and actual UI controls. */
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const { chromium } = require('playwright');
const root = path.resolve(__dirname, '../..');
const base = 'http://127.0.0.1:18090';
const fixture = () => JSON.parse(fs.readFileSync(path.join(root, '.local/qa-fixtures.json'), 'utf8'));
const out = path.join(root, 'work/qa-browser');
fs.mkdirSync(out, { recursive: true });
const results = fs.existsSync(path.join(out,'results.json')) ? JSON.parse(fs.readFileSync(path.join(out,'results.json'),'utf8')).results : [];
const errors = [];
const assert = (ok, message) => { if (!ok) throw new Error(message); };
const py = (code, ...args) => execFileSync('python', ['-c', "import sys;sys.path.insert(0,'scripts/qa');"+code, ...args], { cwd: root, timeout: 20000, encoding:'utf8', windowsHide:true }).trim();
async function record(name, fn) {
  for(let i=results.length-1;i>=0;i--)if(results[i].name===name)results.splice(i,1);
  try { await fn(); results.push({name,status:'PASS'}); console.log('PASS',name); }
  catch (e) { results.push({name,status:'FAIL',error:String(e).slice(0,1200)}); console.log('FAIL',name,String(e).slice(0,500)); throw e; }
  finally { fs.writeFileSync(path.join(out,'results.json'),JSON.stringify({results,errors},null,2)); }
}
async function pageFor(browser, label) {
  const context = await browser.newContext({viewport:{width:1440,height:1000},acceptDownloads:true});
  const page = await context.newPage();
  page.setDefaultTimeout(12000);
  page.on('pageerror', e => errors.push({label,error:e.message}));
  return page;
}
async function login(page, account) {
  await page.goto(base+'/#/login', {waitUntil:'networkidle'});
  await page.getByPlaceholder('请输入用户名').fill(account.userName);
  await page.getByPlaceholder('请输入密码',{exact:true}).fill(account.password);
  const source = await page.locator('img[src*="/captcha/gen"]').getAttribute('src');
  const key = new URL(source, base).searchParams.get('key');
  const code = py("import environment as e;print(e.redis('GET','sys:captcha:'+sys.argv[1]))",key);
  await page.locator('input[placeholder="请输入验证码"]').fill(code);
  const response = page.waitForResponse(r => r.url().endsWith('/api/sys/user/login'));
  await page.getByRole('button',{name:/^登\s*录$/}).click();
  const payload = await (await response).json();
  assert(payload.code===0,'UI login failed: '+payload.msg);
  await page.waitForURL(url => !url.hash.startsWith('#/login'));
  await page.waitForLoadState('networkidle');
}
async function snapshot(page,name) {
  await page.screenshot({path:path.join(out,name+'.png'),fullPage:true,animations:'disabled'});
}
async function nextQuestion(page) {
  await Promise.all([page.waitForResponse(r=>r.url().endsWith('/detail-for-answer')),page.getByRole('button',{name:'下一题',exact:true}).click()]);
  await page.waitForLoadState('networkidle');
}
async function hand(page) {
  await page.getByRole('button',{name:'立即交卷',exact:true}).click();
  const confirm=page.getByRole('button',{name:'确认交卷',exact:true});
  if(await confirm.isVisible()) await confirm.click();
  await page.waitForURL(url => url.hash.includes('result'));
  await page.locator('.el-result__title').waitFor();
}
async function candidate(browser) {
  const page=await pageFor(browser,'candidate');const c=fixture().ui.candidate;
  await record('UI-CAND-01 姓名和考核码认证并开始测评',async()=>{
    await page.goto(base+'/#/exam-entry');
    await page.getByPlaceholder('姓名',{exact:true}).fill(c.candidateName);
    await page.getByPlaceholder('6 位测评口令').fill(c.accessCode);
    await page.getByRole('button',{name:'验证并进入'}).click();
    await page.getByRole('button',{name:'开始测评',exact:true}).click();
    await page.waitForURL(url=>url.hash.includes('/candidate/exam'));
    await page.locator('.answer-item').first().waitFor();
  });
  let current=page.url();
  await record('UI-CAND-02 客观题选择、刷新后原卷与答案恢复',async()=>{
    const saved=page.waitForResponse(r=>r.url().endsWith('/fill-answer'));
    await page.locator('.answer-item').first().click();
    assert((await (await saved).json()).code===0,'Answer did not save');
    await page.reload({waitUntil:'networkidle'});
    assert(page.url()===current,'Paper changed after refresh');
    await page.locator('.answer-item.checked').first().waitFor();
    await snapshot(page,'candidate-resume');
  });
  await record('UI-CAND-03 逐题作答交卷，结果只显示是否通过',async()=>{
    for(let i=1;i<3;i++){
      await nextQuestion(page);
      await page.locator('.answer-item').first().waitFor();
      const saved=page.waitForResponse(r=>r.url().endsWith('/fill-answer'));
      await page.locator('.answer-item').first().click();await saved;
    }
    await hand(page);
    const text=await page.locator('body').innerText();
    assert(/通过/.test(text),'Result not displayed');
    assert(!/最终总分|标准答案|参考答案|客观分|主观分/.test(text),'Private score leaked in UI');
    await snapshot(page,'candidate-result');
  });
  await page.context().close();
}
async function employee(browser) {
  const page=await pageFor(browser,'employee');const f=fixture();
  await record('UI-EMP-01 员工正式登录并进入我的考核',async()=>{
    await login(page,f.ui.employee);
    await page.goto(base+'/#/client/exam/list',{waitUntil:'networkidle'});
    const entry=page.getByRole('button',{name:/^(开始考核|继续考核)$/});
    const fresh=(await entry.innerText())==='开始考核';
    await entry.click();
    if(fresh)await page.locator('.el-message-box').getByRole('button',{name:'开始考核',exact:true}).click();
    await page.waitForURL(url=>url.hash.includes('/client/exam/enter'));
    await page.locator('.tag-item').first().waitFor();
    await page.waitForLoadState('networkidle');
  });
  let paper=new URLSearchParams(page.url().split('?')[1]).get('id');
  let longAnswer='浏览器验收：核对业务资料，按公司制度审批，记录处理结果。';
  await record('UI-EMP-02 客观题与简答题逐题保存并刷新恢复',async()=>{
    for(let i=0;i<4;i++){
      if(i>0){await nextQuestion(page);}
      const text=page.getByPlaceholder('请在此输入你的回答');
      if(await text.isVisible()){
        const saved=page.waitForResponse(r=>r.url().endsWith('/fill-text-answer'));
        await text.fill(longAnswer);await saved;
      }else{
        const saved=page.waitForResponse(r=>r.url().endsWith('/fill-answer'));
        await page.locator('.answer-item').first().click();await saved;
      }
    }
    await page.reload({waitUntil:'networkidle'});
    for(let i=0;i<3;i++)await nextQuestion(page);
    assert(await page.getByPlaceholder('请在此输入你的回答').inputValue()===longAnswer,'Short answer lost');
    await snapshot(page,'employee-short-answer');
  });
  await record('UI-EMP-03 主观题交卷后显示等待阅卷',async()=>{
    await hand(page);
    const text=await page.locator('body').innerText();
    assert(/阅卷|结果.*生成|处理中/.test(text),'Pending result not visible');
    assert(!/测评未通过|考核未通过/.test(text),'Pending shown as failed');
    await snapshot(page,'employee-pending');
  });
  const data=fixture();data.ui.paper=paper;fs.writeFileSync(path.join(root,'.local/qa-fixtures.json'),JSON.stringify(data,null,2));
  await page.context().close();
}
async function grading(browser) {
  const page=await pageFor(browser,'admin');const f=fixture();
  await record('UI-GRADE-01 管理员打开待阅卷试卷',async()=>{
    await login(page,f.admin);
    await page.goto(base+'/#/admin/exam/grading?paperId='+f.ui.paper,{waitUntil:'networkidle'});
    await page.getByText('考生作答',{exact:true}).waitFor();
    assert((await page.locator('.answer-box').innerText()).includes('浏览器验收'),'Saved answer unavailable to grader');
  });
  await record('UI-GRADE-02 保存评分、审计记录并完成阅卷',async()=>{
    await page.getByRole('spinbutton').fill('10');
    await page.getByPlaceholder('说明得分依据或需要改进的地方').fill('浏览器验收：覆盖主要要点。');
    await page.getByRole('button',{name:'保存本题评分'}).click();
    await page.getByText('✓ 评分已保存',{exact:true}).waitFor();
    await page.getByRole('button',{name:'完成阅卷',exact:true}).click();
    await page.locator('.el-dialog').getByRole('button',{name:'确认完成',exact:true}).click();
    await page.getByText('阅卷已完成，考生可查看是否通过',{exact:true}).waitFor();
    await page.locator('.el-dialog').waitFor({state:'hidden'});
    assert((await page.locator('body').innerText()).includes('未评分 → 10'),'Grading audit missing');
    await snapshot(page,'admin-grading-complete');
  });
  await page.context().close();
  const ep=await pageFor(browser,'employee-result');
  await record('UI-RESULT-01 员工重新登录后看到已终审结果',async()=>{
    await login(ep,f.ui.employee);
    await ep.goto(base+'/#/client/exam/list',{waitUntil:'networkidle'});
    await ep.getByRole('button',{name:'查看结果',exact:true}).click();
    const text=await ep.locator('.el-dialog').innerText();
    assert(/通过/.test(text)&&!/最终总分|标准答案|解析/.test(text),'Result is not pass-only');
    await snapshot(ep,'employee-final-result');
  });
  await ep.context().close();
}
async function hr(browser) {
  const page=await pageFor(browser,'hr');const f=fixture();
  await record('UI-HR-01 HR 登录并按批次筛选查看成绩',async()=>{
    await login(page,f.hr);
    await page.goto(base+'/#/admin/exam/results',{waitUntil:'networkidle'});
    await page.getByPlaceholder('输入考核批次').fill(f.prefix+'_ui');
    await page.getByRole('button',{name:'查询',exact:true}).click();
    await page.locator('.results-table .el-table__row').first().waitFor();
    await page.getByRole('button',{name:'查看',exact:true}).first().click();
    await page.locator('.el-dialog .detail-grid span').first().waitFor();
    assert((await page.locator('.el-dialog').innerText()).includes('QA虚构browser'),'Wrong result detail');
    await page.locator('.el-dialog .el-loading-mask').waitFor({state:'hidden'});
    await snapshot(page,'hr-result-detail');
    await page.locator('.el-dialog').getByRole('button',{name:'关闭',exact:true}).click();
  });
  await record('UI-HR-02 成绩 Excel 从页面真实下载',async()=>{
    await page.getByRole('button',{name:'导出 Excel',exact:true}).click();
    const downloading=page.waitForEvent('download');
    await page.getByRole('button',{name:'确认导出',exact:true}).click();
    const download=await downloading;await download.saveAs(path.join(out,'results.xlsx'));
    assert(fs.statSync(path.join(out,'results.xlsx')).size>1000,'Empty export');
    const counts=JSON.parse(py("import extended as x,json;from pathlib import Path;r=x.cells(Path('work/qa-browser/results.xlsx').read_bytes());print(json.dumps([len(r),len(r[0])]))"));
    assert(counts[0]===2&&counts[1]===18,'Unexpected exported rows/columns');
  });
  await record('UI-HR-03 候选人 Excel 上传、预校验、部分成功发放',async()=>{
    await page.goto(base+'/#/admin/exam/candidate',{waitUntil:'networkidle'});
    await Promise.all([page.waitForResponse(r=>r.url().endsWith('/import-restore')),page.getByRole('button',{name:'Excel 批量导入',exact:true}).click()]);
    await page.locator('.import-body .el-loading-mask').waitFor({state:'hidden'});
    if(await page.getByRole('button',{name:'继续导入',exact:true}).isVisible()) await page.getByRole('button',{name:'继续导入',exact:true}).click();
    await page.locator('input[type=file]').setInputFiles(path.join(root,'work/qa-candidates.xlsx'));
    await page.getByRole('button',{name:'开始校验',exact:true}).click();
    await page.getByRole('button',{name:'确认导入 2 条并发放',exact:true}).click();
    await page.getByRole('button',{name:'下载发放清单（含考核码）',exact:true}).waitFor();
  });
  const originalCodes=await page.locator('.candidate-import-dialog .code').allTextContents();
  assert(originalCodes.length===2 && originalCodes.every(code=>/^[A-Z0-9]{6}$/.test(code)),'Expected two valid codes');
  await record('UI-HR-04 关闭、刷新、重新打开恢复原清单并下载',async()=>{
    // Access codes stay in this process only, with no screenshot or report containing them.
    await page.getByRole('button',{name:'完成',exact:true}).click();
    await page.reload({waitUntil:'networkidle'});
    await page.getByRole('button',{name:'Excel 批量导入',exact:true}).click();
    await page.getByRole('button',{name:'下载发放清单（含考核码）',exact:true}).waitFor();
    assert(JSON.stringify(await page.locator('.candidate-import-dialog .code').allTextContents())===JSON.stringify(originalCodes),'Restored codes differ');
    const downloading=page.waitForEvent('download');
    await page.getByRole('button',{name:'下载发放清单（含考核码）',exact:true}).click();
    const download=await downloading;
    const location=await download.path();assert(fs.statSync(location).size>1000,'Empty code list');
    await download.delete();
    await page.getByRole('button',{name:'完成',exact:true}).click();
    await snapshot(page,'hr-candidate-list');
  });
  await page.context().close();
}
async function password(browser) {
  const page=await pageFor(browser,'password');const f=fixture();
  await record('UI-PASS-01 修改密码成功提示、退出并以新密码重新登录',async()=>{
    await login(page,f.ui.employee);
    await page.goto(base+'/#/client/user/passwd',{waitUntil:'networkidle'});
    await page.locator('.el-form-item').filter({hasText:'旧的密码'}).locator('input').fill(f.ui.employee.password);
    const newPassword=require('node:crypto').randomBytes(18).toString('base64url');
    await page.locator('.el-form-item').filter({hasText:'新的密码'}).locator('input').fill(newPassword);
    await page.locator('.el-form-item').filter({hasText:'确认密码'}).locator('input').fill(newPassword);
    await page.getByRole('button',{name:'保存',exact:true}).click();
    await page.getByText('修改成功，即将重新登录！',{exact:true}).waitFor();
    await page.waitForURL(url=>url.hash.startsWith('#/login'));
    f.ui.employee.password=newPassword;fs.writeFileSync(path.join(root,'.local/qa-fixtures.json'),JSON.stringify(f,null,2));
    await login(page,f.ui.employee);
  });
  await page.context().close();
}
(async()=>{
  let browser;
  try {
    browser=await chromium.launch({headless:true,channel:process.env.QA_BROWSER_CHANNEL || 'chrome'});
    const action=process.argv[2]||'all';
    for(const [name,fn] of Object.entries({candidate,employee,grading,hr,password}))if(action==='all'||action.split(',').includes(name))await fn(browser);
    assert(errors.length===0,'Unhandled browser errors: '+JSON.stringify(errors));
    console.log('Browser checks complete:',results.length);
  }catch(error){console.error(String(error));process.exitCode=1;}
  finally{fs.writeFileSync(path.join(out,'results.json'),JSON.stringify({results,errors},null,2));if(browser)await browser.close();}
})();
