<template>
  <ContentWrap>
    <DataTable ref="table" :options="options" :query="query" @on-add="openCreate">
      <template #actions>
        <el-button v-hasPermi="['exam:assignment:candidate:import']" :loading="templateLoading" @click="downloadTemplate">下载模板</el-button>
        <el-button v-hasPermi="['exam:assignment:candidate:import']" type="primary" @click="importVisible = true">Excel 批量导入</el-button>
      </template>
      <template #search>
        <el-input v-model="query.params.subjectName" class="filter-item" placeholder="候选人姓名" />
        <DepartmentSelect v-model="query.params.departId" class="filter-item" @update:model-value="query.params.positionId = ''" />
        <el-select v-model="query.params.positionId" class="filter-item" clearable placeholder="岗位"><el-option v-for="p in queryPositions" :key="p.id" :label="p.name" :value="p.id" /></el-select>
        <el-input v-model="query.params.batchNo" class="filter-item" placeholder="批次" />
        <el-select v-model="query.params.status" class="filter-item" clearable placeholder="状态"><el-option v-for="(label,value) in statusText" :key="value" :label="label" :value="value" /></el-select>
      </template>
      <template #columns>
        <el-table-column label="候选人编号" prop="candidateNo" width="140" /><el-table-column label="姓名" prop="subjectName" width="100" />
        <el-table-column label="部门" prop="departName" min-width="120" />
        <el-table-column label="岗位" prop="positionName" min-width="130" /><el-table-column label="批次" prop="batchNo" width="120" />
        <el-table-column label="测评" prop="examTitle" min-width="160" show-overflow-tooltip />
        <el-table-column label="状态" width="100"><template #default="{row}"><el-tag :type="row.status==='COMPLETED'?'success':row.status==='DISABLED'||row.status==='EXPIRED'?'info':'primary'">{{statusText[row.status]||row.status}}</el-tag></template></el-table-column>
        <el-table-column label="生效时间" prop="validFrom" width="170" /><el-table-column label="截止时间" prop="expireAt" width="170" />
        <el-table-column label="操作" fixed="right" width="240"><template #default="{row}"><el-button v-if="row.status==='COMPLETED'" v-hasPermi="['exam:assignment:candidate:result']" link type="primary" @click="openResult(row)">查看结果</el-button><el-button v-if="row.status==='ASSIGNED'||row.status==='STARTED'" v-hasPermi="['exam:assignment:candidate:code']" link type="primary" @click="resetCode(row)">重置口令</el-button><el-button v-if="row.status!=='DISABLED'&&row.status!=='COMPLETED'&&row.status!=='EXPIRED'" v-hasPermi="['exam:assignment:edit']" link type="danger" @click="changeStatus(row,'DISABLED')">停用</el-button><el-button v-if="row.status==='DISABLED'" v-hasPermi="['exam:assignment:edit']" link type="success" @click="changeStatus(row,'ACTIVE')">恢复</el-button></template></el-table-column>
      </template>
    </DataTable>
    <el-dialog v-model="createVisible" title="发放入职测评" width="640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px"><el-row :gutter="16">
        <el-col :span="12"><el-form-item label="姓名" prop="candidateName"><el-input v-model="form.candidateName" /></el-form-item></el-col><el-col :span="12"><el-form-item label="候选人编号" prop="candidateNo"><el-input v-model="form.candidateNo" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="手机号" prop="mobile"><el-input v-model="form.mobile" /></el-form-item></el-col><el-col :span="12"><el-form-item label="邮箱" prop="email"><el-input v-model="form.email" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="部门" prop="departId"><DepartmentSelect v-model="form.departId" @update:model-value="onFormDepartmentChange" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="岗位" prop="positionId"><el-select v-model="form.positionId" class="!w-full" placeholder="请先选择部门"><el-option v-for="p in formPositions" :key="p.id" :label="p.name" :value="p.id" /></el-select></el-form-item></el-col><el-col :span="12"><el-form-item label="批次" prop="batchNo"><el-input v-model="form.batchNo" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="生效时间" prop="validFrom"><el-date-picker v-model="form.validFrom" class="!w-full" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" /></el-form-item></el-col><el-col :span="12"><el-form-item label="失效时间" prop="expireAt"><el-date-picker v-model="form.expireAt" class="!w-full" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" /></el-form-item></el-col>
      </el-row></el-form>
      <template #footer><el-button @click="createVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="createCandidate">确认发放</el-button></template>
    </el-dialog>
    <el-dialog v-model="codeVisible" title="测评口令" width="430px" :close-on-click-modal="false"><el-alert title="口令只展示本次，请立即安全发送给候选人" type="warning" :closable="false" /><div class="access-code">{{issuedCode}}</div><template #footer><el-button @click="copyCode">复制口令</el-button><el-button type="primary" @click="codeVisible=false">我已保存</el-button></template></el-dialog>
    <CandidateResultDialog v-model:visible="resultVisible" :assignment-id="resultAssignmentId" :candidate="resultCandidate" />
    <CandidateImportDialog v-model:visible="importVisible" @imported="table?.reload()" />
  </ContentWrap>
</template>
<script lang="ts" setup>
import { reactive, ref, watch } from 'vue'; import dayjs from 'dayjs'; import type { FormInstance,FormRules } from 'element-plus'; import { ElMessage,ElMessageBox } from 'element-plus'
import { ContentWrap } from '@/components/ContentWrap'; import { DataTable } from '@/components/DataTable'; import type { OptionsType,TableQueryType } from '@/components/DataTable/src/types'
import { listByDepartmentApi } from '@/api/modules/exam/position'; import { createCandidateApi,resetCandidateCodeApi,changeAssignmentStatusApi } from '@/api/modules/exam/assignment'
import DepartmentSelect from '@/views/Exam/components/DepartmentSelect.vue'
import CandidateResultDialog from '@/views/Exam/Assignment/components/CandidateResultDialog.vue'
import CandidateImportDialog from '@/views/Exam/Assignment/components/CandidateImportDialog.vue'
import { downloadCandidateImport } from '@/api/modules/exam/assignment/candidateImport'
const importVisible = ref(false)
const templateLoading = ref(false)
async function downloadTemplate() {
  templateLoading.value = true
  try { await downloadCandidateImport('template') }
  catch (e: any) { ElMessage.error(e.message || '下载失败') }
  finally { templateLoading.value = false }
}
const table=ref();const formRef=ref<FormInstance>();const queryPositions=ref<any[]>([]);const formPositions=ref<any[]>([]);const createVisible=ref(false);const codeVisible=ref(false);const resultVisible=ref(false);const resultAssignmentId=ref('');const resultCandidate=ref<Record<string,any>>({});const saving=ref(false);const issuedCode=ref('')
const statusText:Record<string,string>={ASSIGNED:'待测评',STARTED:'进行中',PENDING_REVIEW:'待阅卷',COMPLETED:'已完成',DISABLED:'已停用',EXPIRED:'已过期'}
const query=ref<TableQueryType>({current:1,size:10,params:{subjectName:'',departId:'',positionId:'',batchNo:'',status:''}});const options=ref<OptionsType>({listUrl:'/api/exam/assignment/candidate/paging',add:{enable:true,permission:['exam:assignment:candidate:add']}})
const fresh=()=>({candidateName:'',candidateNo:'',mobile:'',email:'',departId:'',positionId:'',batchNo:'',validFrom:dayjs().format('YYYY-MM-DD HH:mm:ss'),expireAt:dayjs().add(14,'day').format('YYYY-MM-DD HH:mm:ss')});const form=ref(fresh())
const rules=reactive<FormRules>({candidateName:[{required:true,message:'请输入姓名',trigger:'blur'}],candidateNo:[{required:true,message:'请输入候选人编号',trigger:'blur'}],mobile:[{required:true,message:'请输入手机号',trigger:'blur'}],email:[{required:true,message:'请输入邮箱',trigger:'blur'}],departId:[{required:true,message:'请选择部门',trigger:'change'}],positionId:[{required:true,message:'请选择岗位',trigger:'change'}],batchNo:[{required:true,message:'请输入批次',trigger:'blur'}],validFrom:[{required:true,message:'请选择生效时间',trigger:'change'}],expireAt:[{required:true,message:'请选择失效时间',trigger:'change'}]})
function openCreate(){form.value=fresh();formPositions.value=[];createVisible.value=true}
async function onFormDepartmentChange(value:string|string[]){form.value.positionId='';formPositions.value=typeof value==='string'&&value?(await listByDepartmentApi(value)).data||[]:[]}
watch(()=>query.value.params.departId,async value=>{queryPositions.value=value?(await listByDepartmentApi(value)).data||[]:[]})
async function createCandidate(){if(!await formRef.value?.validate())return;saving.value=true;try{const res=await createCandidateApi(form.value);issuedCode.value=res.data.accessCode;createVisible.value=false;codeVisible.value=true;table.value?.reload();ElMessage.success('发放成功')}finally{saving.value=false}}
async function resetCode(row:any){await ElMessageBox.confirm(`确认重置 ${row.subjectName} 的测评口令？旧口令将立即失效。`,'重置口令',{type:'warning'});const res=await resetCandidateCodeApi({id:row.id});issuedCode.value=res.data.accessCode;codeVisible.value=true}
function openResult(row:any){resultAssignmentId.value=row.id;resultCandidate.value=row;resultVisible.value=true}
async function changeStatus(row:any,status:string){const action=status==='DISABLED'?'DISABLE':'ENABLE';await ElMessageBox.confirm(`确认${action==='DISABLE'?'停用':'恢复'}该测评？`,'状态变更',{type:'warning'});await changeAssignmentStatusApi({id:row.id,action});table.value?.reload();ElMessage.success('状态已更新')}
async function copyCode(){await navigator.clipboard.writeText(issuedCode.value);ElMessage.success('已复制')}
</script>
<style scoped>.access-code{font-size:38px;font-weight:700;letter-spacing:10px;text-align:center;margin:28px 0 16px;color:var(--el-color-primary)}</style>
