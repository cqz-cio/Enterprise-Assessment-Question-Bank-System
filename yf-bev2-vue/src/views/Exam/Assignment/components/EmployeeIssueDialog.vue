<template>
  <el-dialog
    v-model="open"
    title="发放员工考核"
    width="1020px"
    top="8vh"
    destroy-on-close
    :close-on-click-modal="false"
    :close-on-press-escape="!saving"
    :show-close="!saving"
    class="employee-issue-dialog"
    @open="initialize"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="formRules"
      label-position="top"
      :disabled="saving"
      class="issue-body"
    >
      <div class="issue-column">
        <h3><span>1</span>选择考核模板</h3>
        <div class="field-grid">
          <el-form-item label="部门" prop="departId"
            ><DepartmentSelect v-model="form.departId" @update:model-value="departmentChanged"
          /></el-form-item>
          <el-form-item label="岗位" prop="positionId"
            ><el-select
              v-model="form.positionId"
              placeholder="请选择岗位"
              clearable
              @change="form.examId = ''"
              ><el-option
                v-for="p in positions"
                :key="p.id"
                :value="p.id"
                :label="p.name" /></el-select
          ></el-form-item>
          <el-form-item label="场景" prop="sceneType"
            ><el-select v-model="form.sceneType" placeholder="请选择场景" @change="form.examId = ''"
              ><el-option
                v-for="(label, value) in scenes"
                :key="value"
                :value="value"
                :label="label" /></el-select
          ></el-form-item>
          <el-form-item label="考核模板" prop="examId"
            ><el-select
              v-model="form.examId"
              placeholder="请选择考核模板"
              :loading="templatesLoading"
              no-data-text="暂无可发放的客观题模板"
              ><el-option
                v-for="t in filteredTemplates"
                :key="t.id"
                :value="t.id"
                :label="t.title" /></el-select
          ></el-form-item>
        </div>
        <div class="template-info">{{
          selectedTemplate
            ? `时长：${selectedTemplate.totalTime} 分钟　 题量：${selectedTemplate.questionCount} 题　 状态：已启用`
            : '请选择部门、岗位和场景，再选择已启用的考核模板'
        }}</div>
        <h3 class="second-section"><span>3</span>设置批次与有效期</h3>
        <el-form-item label="考核批次" prop="batchNo"
          ><el-input v-model="form.batchNo" maxlength="64" placeholder="例如：2026-09-转正"
        /></el-form-item>
        <div class="field-grid">
          <el-form-item label="可开始时间" prop="validFrom"
            ><el-date-picker
              v-model="form.validFrom"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              format="YYYY-MM-DD HH:mm"
              :clearable="false"
          /></el-form-item>
          <el-form-item label="截止时间" prop="expireAt"
            ><el-date-picker
              v-model="form.expireAt"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              format="YYYY-MM-DD HH:mm"
              :clearable="false"
          /></el-form-item>
        </div>
        <p class="hint">默认有效期为 14 天，可根据考核安排调整</p>
      </div>
      <div class="issue-column employees">
        <h3><span>2</span>选择员工</h3>
        <el-radio-group v-model="mode" @change="modeChanged"
          ><el-radio label="manual">按员工选择</el-radio
          ><el-radio label="department">选择本部门全部员工</el-radio></el-radio-group
        >
        <el-input
          v-model="keyword"
          placeholder="搜索姓名 / 工号"
          clearable
          class="employee-search"
          :disabled="!form.departId || mode === 'department'"
          @keyup.enter="searchEmployees"
          ><template #append
            ><el-button :disabled="mode === 'department'" @click="searchEmployees"
              >搜索</el-button
            ></template
          ></el-input
        >
        <p class="hint">仅显示审核通过且启用的员工</p>
        <el-table
          ref="employeeTable"
          v-loading="employeesLoading"
          :data="employees"
          row-key="id"
          height="210"
          border
          @selection-change="selectionChanged"
        >
          <el-table-column
            v-if="mode === 'manual'"
            type="selection"
            width="40"
            :reserve-selection="true"
          />
          <el-table-column v-else width="40"
            ><template #default
              ><el-checkbox :model-value="true" disabled aria-label="本次已选择" /></template
          ></el-table-column>
          <el-table-column prop="name" label="姓名" min-width="95" show-overflow-tooltip />
          <el-table-column prop="employeeNo" label="工号" min-width="100" show-overflow-tooltip />
          <el-table-column prop="departName" label="部门" min-width="80" />
          <el-table-column label="账号状态" width="90"
            ><template #default
              ><el-tag type="success" size="small">正常</el-tag></template
            ></el-table-column
          >
          <template #empty>{{ form.departId ? '暂无符合条件的员工' : '请先选择部门' }}</template>
        </el-table>
        <el-pagination
          v-if="mode === 'manual' && employeeTotal > 10"
          v-model:current-page="employeePage"
          small
          :page-size="10"
          :total="employeeTotal"
          layout="prev, pager, next"
          @current-change="loadEmployees"
        />
        <p class="selected-count"
          >已选择 {{ selected.length }} 位员工
          <el-button
            v-if="selected.length && mode === 'manual'"
            link
            size="small"
            @click="clearSelection"
            >清空</el-button
          ></p
        >
        <p class="hint">员工使用个人账号进入，无需考核码</p>
        <p v-if="mode === 'department'" class="hint"
          >按当前名单发放，后续新增员工需另行发放。单次最多 500 人。</p
        >
      </div>
    </el-form>
    <template #footer
      ><div class="dialog-footer"
        ><span>同一员工、模板和批次不重复创建任务</span
        ><div
          ><el-button :disabled="saving" @click="open = false">取消</el-button
          ><el-button
            type="primary"
            :loading="saving"
            :disabled="!selected.length || employeesLoading || templatesLoading"
            @click="submit"
            >确认发放给 {{ selected.length }} 人</el-button
          ></div
        ></div
      ></template
    >
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import dayjs from 'dayjs'
import { ElMessage, type FormInstance, type FormRules, type TableInstance } from 'element-plus'
import DepartmentSelect from '@/views/Exam/components/DepartmentSelect.vue'
import {
  employeeCreateApi,
  employeeOptionsApi,
  employeeTemplatesApi,
  scenes
} from '@/api/modules/exam/assignment/employee'

interface Employee {
  id: string
  name: string
  employeeNo: string
  departName: string
}
interface Template {
  id: string
  title: string
  positionId: string
  positionName: string
  sceneType: string
  totalTime: number
  questionCount: number
}
const props = defineProps<{ visible: boolean }>()
const emit = defineEmits(['update:visible', 'issued'])
const open = computed({ get: () => props.visible, set: (value) => emit('update:visible', value) })
const fresh = () => ({
  departId: '',
  positionId: '',
  sceneType: 'REGULARIZATION',
  examId: '',
  batchNo: '',
  validFrom: dayjs().format('YYYY-MM-DD HH:mm:ss'),
  expireAt: dayjs().add(14, 'day').format('YYYY-MM-DD HH:mm:ss')
})
const form = ref(fresh())
const formRef = ref<FormInstance>()
const employeeTable = ref<TableInstance>()
const formRules: FormRules = Object.fromEntries(
  ['departId', 'positionId', 'sceneType', 'examId', 'batchNo', 'validFrom', 'expireAt'].map(
    (key) => [key, [{ required: true, message: '请填写或选择此项', trigger: 'change' }]]
  )
)
const templates = ref<Template[]>([])
const positions = computed(() =>
  Array.from(
    new Map(
      templates.value.map((t) => [t.positionId, { id: t.positionId, name: t.positionName }])
    ).values()
  )
)
const filteredTemplates = computed(() =>
  templates.value.filter(
    (t) => t.positionId === form.value.positionId && t.sceneType === form.value.sceneType
  )
)
const selectedTemplate = computed(() => templates.value.find((t) => t.id === form.value.examId))
const employees = ref<Employee[]>([])
const selected = ref<Employee[]>([])
const employeeTotal = ref(0)
const employeePage = ref(1)
const keyword = ref('')
const mode = ref('manual')
const saving = ref(false)
const templatesLoading = ref(false)
const employeesLoading = ref(false)
let employeeRequest = 0
let templateRequest = 0
function clearSelection() {
  employeeTable.value?.clearSelection()
  selected.value = []
}
function initialize() {
  ++employeeRequest
  ++templateRequest
  form.value = fresh()
  templates.value = []
  employees.value = []
  employeeTotal.value = 0
  mode.value = 'manual'
  keyword.value = ''
  employeePage.value = 1
  clearSelection()
  employeesLoading.value = false
  templatesLoading.value = false
  nextTick(() => formRef.value?.clearValidate())
}
async function departmentChanged() {
  form.value.positionId = ''
  form.value.examId = ''
  templates.value = []
  mode.value = 'manual'
  keyword.value = ''
  employeePage.value = 1
  clearSelection()
  const id = ++templateRequest
  templatesLoading.value = true
  const load = loadEmployees()
  try {
    const { data } = await employeeTemplatesApi({ departId: form.value.departId })
    if (id === templateRequest) templates.value = data
  } finally {
    if (id === templateRequest) templatesLoading.value = false
    await load
  }
}
async function loadEmployees() {
  const id = ++employeeRequest
  employeesLoading.value = true
  try {
    const { data } = await employeeOptionsApi({
      departId: form.value.departId,
      keyword: mode.value === 'department' ? '' : keyword.value,
      current: mode.value === 'department' ? 1 : employeePage.value,
      size: mode.value === 'department' ? 500 : 10
    })
    if (id !== employeeRequest) return
    employees.value = data.records
    employeeTotal.value = data.total
    if (mode.value === 'department') {
      if (data.total > 500) {
        ElMessage.warning('本部门超过 500 人，请按员工分批选择发放')
        mode.value = 'manual'
        clearSelection()
        return loadEmployees()
      }
      selected.value = data.records
    }
  } finally {
    if (id === employeeRequest) employeesLoading.value = false
  }
}
function selectionChanged(rows: Employee[]) {
  if (mode.value === 'manual') selected.value = rows
}
function searchEmployees() {
  employeePage.value = 1
  loadEmployees()
}
function modeChanged() {
  clearSelection()
  keyword.value = ''
  employeePage.value = 1
  loadEmployees()
}
async function submit() {
  if (saving.value || !(await formRef.value?.validate().catch(() => false))) return
  if (!form.value.batchNo.trim()) {
    ElMessage.warning('请输入考核批次')
    return
  }
  if (
    !dayjs(form.value.expireAt).isAfter(form.value.validFrom) ||
    !dayjs(form.value.expireAt).isAfter(dayjs())
  ) {
    ElMessage.warning('截止时间必须晚于开始时间和当前时间')
    return
  }
  if (!selected.value.length || selected.value.length > 500) {
    ElMessage.warning('请选择 1–500 位员工')
    return
  }
  saving.value = true
  try {
    const { data } = await employeeCreateApi({
      examId: form.value.examId,
      batchNo: form.value.batchNo.trim(),
      validFrom: form.value.validFrom,
      expireAt: form.value.expireAt,
      userIds: selected.value.map((u) => u.id)
    })
    ElMessage.success(
      `已发放 ${data.created} 人${data.existing ? `，${data.existing} 人已有该批次任务，保留原任务及有效期` : ''}`
    )
    open.value = false
    emit('issued')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.issue-body {
  display: grid;
  grid-template-columns: 1.05fr 1fr;
  gap: 28px;
  max-height: 64vh;
  overflow: auto;
  padding: 0 4px;
}
.issue-column {
  min-width: 0;
}
.employees {
  border-left: 1px solid #ebeef5;
  padding-left: 24px;
}
h3 {
  margin: 0 0 16px;
  font-size: 14px;
  color: #303133;
  font-weight: 600;
}
h3 span {
  display: inline-block;
  color: #409eff;
  background: #ecf5ff;
  border-radius: 50%;
  width: 22px;
  line-height: 22px;
  text-align: center;
  margin-right: 8px;
}
.second-section {
  margin-top: 22px;
}
.field-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 14px;
}
.field-grid :deep(.el-select),
.field-grid :deep(.el-date-editor) {
  width: 100%;
}
.issue-body :deep(.el-form-item) {
  margin-bottom: 16px;
}
.issue-body :deep(.el-form-item__label) {
  line-height: 20px;
  margin-bottom: 4px;
}
.template-info {
  background: #f5f7fa;
  padding: 10px;
  color: #606266;
  font-size: 12px;
}
.hint {
  color: #909399;
  font-size: 12px;
  margin: 8px 0;
}
.employee-search {
  margin: 10px 0 2px;
}
.selected-count {
  color: #409eff;
  font-size: 13px;
  margin: 12px 0;
}
.dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px solid #ebeef5;
  padding-top: 16px;
}
.dialog-footer > span {
  font-size: 12px;
  color: #909399;
}
@media (max-width: 760px) {
  .issue-body {
    grid-template-columns: 1fr;
  }
  .employees {
    border-left: 0;
    padding-left: 0;
  }
  .dialog-footer {
    gap: 10px;
    flex-wrap: wrap;
  }
}
</style>
<style>
.employee-issue-dialog {
  max-width: calc(100vw - 32px);
}
.employee-issue-dialog .el-dialog__body {
  padding: 22px 24px 16px;
}
.employee-issue-dialog .el-dialog__header {
  padding-bottom: 18px;
  border-bottom: 1px solid #ebeef5;
}
</style>
