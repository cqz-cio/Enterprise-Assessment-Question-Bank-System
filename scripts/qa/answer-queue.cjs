const fs=require('fs'),vm=require('vm'),assert=require('assert/strict');
const req=require('module').createRequire(process.cwd()+'/yf-bev2-vue/package.json');
const compiler=req('vue/compiler-sfc'),ts=req('typescript');
const file='yf-bev2-vue/src/views/Exam/Exam/Client/Enter.vue';
const descriptor=compiler.parse(fs.readFileSync(file,'utf8'),{filename:file}).descriptor;
const tree=ts.createSourceFile('Enter.ts',descriptor.scriptSetup.content,ts.ScriptTarget.Latest,true,ts.ScriptKind.TS);
const raw=tree.statements.filter(s=>!ts.isImportDeclaration(s)).map(s=>s.getFullText(tree)).join('\n');
const js=ts.transpileModule(raw,{compilerOptions:{target:ts.ScriptTarget.ES2020,module:ts.ModuleKind.None}}).outputText;
const events=[],pending=[],messages=[],submissions=[];
const ctx={ref:v=>({value:v}),onMounted:()=>{},onBeforeUnmount:()=>{},onBeforeRouteLeave:()=>{},setTimeout,clearTimeout,
 useRoute:()=>({name:'CandidateExamEnter',query:{id:'p',assignmentId:'a'}}),useRouter:()=>({push:async()=>events.push('navigation')}),
 ElMessage:{error:x=>messages.push(x)},ElMessageBox:{confirm:async()=>{}},
 fillAnswerApi:x=>new Promise((resolve,reject)=>{events.push(['objective',x]);pending.push({resolve,reject})}),
 fillTextAnswerApi:x=>new Promise((resolve,reject)=>{events.push(['text',x]);pending.push({resolve,reject})}),
 handApi:async x=>submissions.push(x),quCardApi:async()=>({data:[]}),quDetailApi:async x=>({data:{quId:x.quId,quType:'short',textAnswer:'restored'}})
};
vm.createContext(ctx);vm.runInContext(js+';globalThis.sut={detail,cardList,itemClick,handPaper,quDetail,scheduleTextSave,saveTextAnswer,saveStates,getQueue:()=>saveQueue};',ctx,{timeout:2000});
const tick=()=>new Promise(resolve=>setImmediate(resolve));
(async()=>{
 const s=ctx.sut;s.cardList.value=[{itemList:[{quId:'q1',answered:false},{quId:'q2',answered:false}]}];
 s.detail.value={quId:'q1',quType:'radio',answerList:[{answerId:'a',checked:false},{answerId:'b',checked:false}]};
 s.itemClick(s.detail.value.answerList[0]);s.itemClick(s.detail.value.answerList[1]);await tick();assert.equal(pending.length,1);
 const submitting=s.handPaper();await tick();assert.equal(submissions.length,0);
 pending[0].resolve({data:{filled:true}});await tick();assert.equal(pending.length,2);pending[1].resolve({data:{filled:true}});await submitting;
 assert.equal(submissions.length,1);assert.equal(events.filter(x=>x[0]==='objective')[1][1].checkedItems[0],'b');
 s.detail.value={quId:'q2',quType:'short',textAnswer:'最新文字\n第二行'};s.scheduleTextSave();
 const switching=s.quDetail('q1');await tick();assert.equal(events.filter(x=>x[0]==='text')[0][1].answerText,'最新文字\n第二行');assert.equal(s.detail.value.quId,'q2');
 pending[2].resolve({data:{filled:true}});await switching;assert.equal(s.detail.value.quId,'q1');assert.equal(s.cardList.value[0].itemList[1].answered,true);
 s.detail.value={quId:'q2',quType:'short',textAnswer:'失败保留'};s.saveTextAnswer();await tick();pending[3].reject(new Error('offline'));await s.getQueue();
 await s.handPaper();assert.equal(submissions.length,1);assert.equal(s.detail.value.textAnswer,'失败保留');assert.equal(s.saveStates.value.q2,'failed');
 await s.quDetail('q1');assert.equal(s.detail.value.quId,'q2');
 s.saveTextAnswer();await tick();pending[4].resolve({data:{filled:true}});await s.getQueue();assert.equal(s.saveStates.value.q2,'saved');
 s.detail.value.textAnswer='';s.scheduleTextSave();const submit=s.handPaper();await tick();assert.equal(submissions.length,1);assert.equal(events.filter(x=>x[0]==='text').at(-1)[1].answerText,'');
 pending[5].resolve({data:{filled:false}});await submit;assert.equal(submissions.length,2);assert.equal(s.cardList.value[0].itemList[1].answered,false);
 console.log('PASS: ordered objective saves; text debounce flushed before navigation/submit; failed text retained and blocks switching/submission; retry and empty answer supported.');
})().catch(e=>{console.error(e);process.exitCode=1;});
