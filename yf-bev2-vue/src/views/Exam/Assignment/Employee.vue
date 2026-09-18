<template>
  <ContentWrap>
    <div class="page-heading">
      <div><h2>员工考核</h2><p>发放已有考核，跟进员工完成情况</p></div>
      <el-button
        v-hasPermi="['exam:assignment:employee:add']"
        type="primary"
        icon="Plus"
        @click="issueVisible = true"
        >发放考核</el-button
      >
    </div>
    <el-form inline class="filters" @submit.prevent="search">
      <el-form-item
        ><el-input
          v-model="query.keyword"
          placeholder="姓名 / 工号"
          clearable
          @keyup.enter="search"
      /></el-form-item>
      <el-form-item><DepartmentSelect v-model="query.departId" /></el-form-item>
      <el-form-item
        ><el-select v-model="query.sceneType" placeholder="考核场景" clearable
          ><el-option
            v-for="(label, value) in scenes"
            :key="value"
            :label="label"
            :value="value" /></el-select
      ></el-form-item>
      <el-form-item
        ><el-input v-model="query.batchNo" placeholder="考核批次" clearable @keyup.enter="search"
      /></el-form-item>
      <el-form-item
        ><el-select v-model="query.status" placeholder="全部状态" clearable
          ><el-option
            v-for="(label, value) in statuses"
            :key="value"
            :label="label"
            :value="value" /></el-select
      ></el-form-item>
      <el-form-item
        ><el-button type="primary" @click="search">搜索</el-button
        ><el-button @click="reset">重置</el-button></el-form-item
      >
    </el-form>
    <div class="list-tip">同一员工、模板和批次不重复创建任务；员工使用个人账号参加考核。</div>
    <el-table
      v-loading="loading"
      :data="rows"
      stripe
      empty-text="暂无员工考核，可点击右上角发放考核"
    >
      <el-table-column label="员工" min-width="125"
        ><template #default="{ row }"
          ><strong>{{ row.subjectName }}</strong
          ><div class="secondary">{{ row.employeeNo || '—' }}</div></template
        ></el-table-column
      >
      <el-table-column label="部门 / 岗位" min-width="150"
        ><template #default="{ row }"
          >{{ row.departName }}<div class="secondary">{{ row.positionName }}</div></template
        ></el-table-column
      >
      <el-table-column label="考核 / 批次" min-width="220"
        ><template #default="{ row }"
          >{{ row.examTitle }}<div class="secondary">{{ row.batchNo }}</div></template
        ></el-table-column
      >
      <el-table-column label="场景" width="80"
        ><template #default="{ row }">{{ scenes[row.sceneType] }}</template></el-table-column
      >
      <el-table-column label="状态" width="100"
        ><template #default="{ row }"
          ><el-tooltip :disabled="!row.disabledReason" :content="row.disabledReason"
            ><el-tag :type="statusType(row.status)">{{ statuses[row.status] }}</el-tag></el-tooltip
          ></template
        ></el-table-column
      >
      <el-table-column label="有效期" min-width="190"
        ><template #default="{ row }"
          ><div class="date-line">起 {{ date(row.validFrom) }}</div
          ><div class="date-line">止 {{ date(row.expireAt) }}</div></template
        ></el-table-column
      >
      <el-table-column label="操作" width="100" fixed="right"
        ><template #default="{ row }">
          <el-button
            v-if="row.status === 'COMPLETED'"
            v-hasPermi="['exam:assignment:employee:result']"
            link
            type="primary"
            @click="showResult(row)"
            >查看结果</el-button
          >
          <el-button
            v-else-if="['ASSIGNED', 'UPCOMING', 'STARTED', 'DISABLED'].includes(row.status)"
            v-hasPermi="['exam:assignment:employee:edit']"
            link
            :type="row.status === 'DISABLED' ? 'primary' : 'danger'"
            @click="changeStatus(row)"
            >{{ row.status === 'DISABLED' ? '恢复' : '停用' }}</el-button
          >
          <span v-else class="secondary">—</span>
        </template></el-table-column
      >
    </el-table>
    <el-pagination
      v-model:current-page="query.current"
      v-model:page-size="query.size"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      class="pagination"
      @current-change="load"
      @size-change="search"
    />
    <EmployeeIssueDialog v-model:visible="issueVisible" @issued="search" />
    <CandidateResultDialog
      v-model:visible="resultVisible"
      :assignment-id="resultRow.id"
      :candidate="resultRow"
      employee
    />
  </ContentWrap>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import dayjs from 'dayjs'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ContentWrap } from '@/components/ContentWrap'
import DepartmentSelect from '@/views/Exam/components/DepartmentSelect.vue'
import EmployeeIssueDialog from './components/EmployeeIssueDialog.vue'
import CandidateResultDialog from './components/CandidateResultDialog.vue'
import {
  employeePagingApi,
  employeeStatusApi,
  scenes,
  statuses,
  statusType,
  type AssignmentRow
} from '@/api/modules/exam/assignment/employee'

const fresh = () => ({
  current: 1,
  size: 10,
  keyword: '',
  departId: '',
  sceneType: '',
  batchNo: '',
  status: ''
})
const query = ref(fresh())
const rows = ref<AssignmentRow[]>([])
const total = ref(0)
const loading = ref(false)
const issueVisible = ref(false)
const resultVisible = ref(false)
const resultRow = ref<Record<string, any>>({})
const date = (value: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—')
let requestId = 0
async function load() {
  const id = ++requestId
  loading.value = true
  try {
    const { data } = await employeePagingApi({ ...query.value })
    if (id === requestId) {
      rows.value = data.records
      total.value = data.total
    }
  } finally {
    if (id === requestId) loading.value = false
  }
}
function search() {
  query.value.current = 1
  load()
}
function reset() {
  query.value = fresh()
  load()
}
function showResult(row: AssignmentRow) {
  resultRow.value = row
  resultVisible.value = true
}
async function changeStatus(row: AssignmentRow) {
  const action = row.status === 'DISABLED' ? 'ENABLE' : 'DISABLE'
  try {
    let reason = ''
    if (action === 'DISABLE') {
      const result = await ElMessageBox.prompt(
        `停用后，${row.subjectName} 将无法继续此项考核。`,
        '停用考核',
        {
          inputPlaceholder: '请填写停用原因',
          inputValidator: (value) =>
            (!!value?.trim() && value.length <= 500) || '请输入 1–500 字的原因',
          confirmButtonText: '确认停用',
          cancelButtonText: '取消'
        }
      )
      reason = result.value
    } else
      await ElMessageBox.confirm('恢复原任务和原试卷，作答倒计时不会重置。', '恢复考核', {
        confirmButtonText: '确认恢复',
        cancelButtonText: '取消'
      })
    await employeeStatusApi({ id: row.id, action, reason })
    ElMessage.success('状态已更新')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') await load()
  }
}
onMounted(load)
</script>

<style scoped>
.page-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}
h2 {
  margin: 0 0 8px;
  font-size: 20px;
  font-weight: 600;
}
p,
.secondary {
  color: #909399;
  font-size: 13px;
  margin: 5px 0 0;
}
.filters :deep(.el-form-item) {
  width: 150px;
  margin-right: 12px;
  margin-bottom: 16px;
}
.filters :deep(.el-select) {
  width: 100%;
}
.list-tip {
  background: #f5f7fa;
  color: #909399;
  padding: 11px 14px;
  font-size: 13px;
  margin-bottom: 16px;
}
.date-line {
  font-size: 12px;
  color: #606266;
  line-height: 24px;
}
.pagination {
  justify-content: flex-end;
  margin-top: 24px;
}
</style>
