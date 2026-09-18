<template>
  <ContentWrap>
    <div class="page-heading"
      ><div><h2>我的考核</h2><p>查看已分配的考核，在有效期内完成作答</p></div
      ><el-button icon="Refresh" :loading="loading" @click="load">刷新</el-button></div
    >
    <el-alert
      v-if="count('STARTED')"
      :title="`你有 ${count('STARTED')} 项考核正在进行，请在试卷截止时间前完成。`"
      type="info"
      show-icon
      :closable="false"
      class="notice"
    />
    <el-tabs v-model="query.status" @tab-change="search"
      ><el-tab-pane
        v-for="tab in tabs"
        :key="tab.value"
        :name="tab.value"
        :label="`${tab.label} ${tab.count}`"
    /></el-tabs>
    <div class="filters"
      ><el-input
        v-model="query.keyword"
        placeholder="搜索考核名称"
        clearable
        @keyup.enter="search"
      /><el-select v-model="query.sceneType" placeholder="全部场景" clearable @change="search"
        ><el-option
          v-for="(label, value) in scenes"
          :key="value"
          :label="label"
          :value="value" /></el-select
      ><el-button type="primary" @click="search">搜索</el-button
      ><el-button @click="reset">重置</el-button></div
    >
    <el-table v-loading="loading" :data="rows" stripe empty-text="暂无符合条件的考核">
      <el-table-column label="考核" min-width="240"
        ><template #default="{ row }"
          ><strong>{{ row.examTitle }}</strong
          ><div class="secondary">{{ row.departName }} · {{ row.positionName }}</div
          ><div class="secondary">批次：{{ row.batchNo }}</div></template
        ></el-table-column
      >
      <el-table-column label="场景" width="85"
        ><template #default="{ row }">{{ scenes[row.sceneType] }}</template></el-table-column
      >
      <el-table-column label="时长" width="90"
        ><template #default="{ row }">{{ row.totalTime }} 分钟</template></el-table-column
      >
      <el-table-column label="有效期" min-width="230"
        ><template #default="{ row }"
          ><div class="date-line">起 {{ date(row.validFrom) }}</div
          ><div class="date-line">止 {{ date(row.expireAt) }}</div
          ><div v-if="row.status === 'STARTED'" class="deadline"
            >试卷截止 {{ date(row.paperDeadline) }}</div
          ></template
        ></el-table-column
      >
      <el-table-column label="状态 / 结果" width="125"
        ><template #default="{ row }"
          ><el-tooltip :disabled="!row.disabledReason" :content="row.disabledReason"
            ><el-tag :type="statusType(row.status)">{{ statuses[row.status] }}</el-tag></el-tooltip
          ><div
            v-if="row.status === 'COMPLETED' && row.passed != null"
            :class="['pass-result', row.passed ? 'passed' : 'failed']"
            >{{ row.passed ? '通过' : '未通过' }}</div
          ></template
        ></el-table-column
      >
      <el-table-column label="操作" width="120" fixed="right"
        ><template #default="{ row }">
          <el-button
            v-if="['ASSIGNED', 'STARTED'].includes(row.status)"
            :type="row.status === 'STARTED' ? 'primary' : 'default'"
            :loading="starting === row.id"
            :disabled="!!starting && starting !== row.id"
            @click="enter(row)"
            >{{ row.status === 'STARTED' ? '继续考核' : '开始考核' }}</el-button
          >
          <el-button
            v-else-if="row.status === 'COMPLETED'"
            link
            type="primary"
            @click="showResult(row)"
            >查看结果</el-button
          >
          <el-button v-else disabled link>{{
            row.status === 'UPCOMING'
              ? '尚未开始'
              : row.status === 'PENDING_REVIEW'
                ? '等待阅卷'
                : row.status === 'SETTLING'
                  ? '等待结算'
                  : '不可进入'
          }}</el-button>
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
    <el-dialog v-model="resultVisible" title="考核结果" width="460px" align-center
      ><el-result
        :icon="!result.resultAvailable ? 'info' : result.passed ? 'success' : 'warning'"
        :title="!result.resultAvailable ? '结果处理中' : result.passed ? '考核通过' : '考核未通过'"
        :sub-title="resultTitle"
      /><template #footer
        ><el-button type="primary" @click="resultVisible = false">知道了</el-button></template
      ></el-dialog
    >
  </ContentWrap>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { ElMessageBox } from 'element-plus'
import { ContentWrap } from '@/components/ContentWrap'
import { createPaperByAssignmentApi, assignmentResultApi } from '@/api/modules/exam/assignment'
import {
  myAssignmentsApi,
  scenes,
  statuses,
  statusType,
  type AssignmentRow
} from '@/api/modules/exam/assignment/employee'
const router = useRouter()
const fresh = () => ({ current: 1, size: 10, keyword: '', sceneType: '', status: '' })
const query = ref(fresh())
const rows = ref<AssignmentRow[]>([])
const total = ref(0)
const loading = ref(false)
const counts = ref<{ status: string; total: number }[]>([])
const count = (...states: string[]) =>
  counts.value
    .filter((c) => !states.length || states.includes(c.status))
    .reduce((sum, c) => sum + Number(c.total), 0)
const tabs = computed(() => [
  { value: '', label: '全部', count: count() },
  { value: 'TODO', label: '待考核', count: count('ASSIGNED', 'UPCOMING') },
  { value: 'STARTED', label: '进行中', count: count('STARTED') },
  { value: 'PENDING_REVIEW', label: '待阅卷', count: count('PENDING_REVIEW') },
  { value: 'COMPLETED', label: '已完成', count: count('COMPLETED') },
  { value: 'CLOSED', label: '已过期 / 停用', count: count('EXPIRED', 'DISABLED') }
])
const starting = ref('')
const resultVisible = ref(false)
const result = ref<{ resultAvailable: boolean; passed: boolean | null }>({
  resultAvailable: false,
  passed: null
})
const resultTitle = ref('')
const date = (value?: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—')
let requestId = 0
async function load() {
  const id = ++requestId
  loading.value = true
  try {
    const { data } = await myAssignmentsApi({ ...query.value })
    if (id === requestId) {
      rows.value = data.records
      total.value = data.total
      counts.value = data.counts
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
async function enter(row: AssignmentRow) {
  if (starting.value) return
  starting.value = row.id
  try {
    if (row.status === 'ASSIGNED')
      await ElMessageBox.confirm(
        `本考核限时 ${row.totalTime} 分钟，每项任务只能作答一次。开始后计时不会暂停，最晚须在 ${date(row.expireAt)} 前交卷。确认开始？`,
        '考核须知',
        { confirmButtonText: '开始考核', cancelButtonText: '暂不开始', type: 'info' }
      )
    const { data } = await createPaperByAssignmentApi({ id: row.id })
    await router.push({
      name: 'ExamClientEnter',
      query: { id: data.paperId, assignmentId: row.id }
    })
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') await load()
  } finally {
    starting.value = ''
  }
}
async function showResult(row: AssignmentRow) {
  const { data } = await assignmentResultApi({ id: row.id })
  result.value = data
  resultTitle.value = row.examTitle
  resultVisible.value = true
}
let refreshTimer: ReturnType<typeof setInterval> | undefined
onMounted(() => {
  load()
  refreshTimer = setInterval(() => {
    if (!document.hidden && !starting.value && !resultVisible.value) load()
  }, 30000)
})
onUnmounted(() => {
  clearInterval(refreshTimer)
  ++requestId
})
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
  margin: 6px 0 0;
}
.notice {
  margin-bottom: 18px;
}
.filters {
  display: flex;
  gap: 12px;
  margin: 12px 0 22px;
  flex-wrap: wrap;
}
.filters .el-input {
  width: 260px;
}
.filters .el-select {
  width: 160px;
}
.date-line {
  font-size: 12px;
  color: #606266;
  line-height: 24px;
}
.deadline {
  color: #e6a23c;
  font-size: 12px;
  margin-top: 4px;
}
.pass-result {
  font-size: 12px;
  margin-top: 7px;
}
.passed {
  color: #67c23a;
}
.failed {
  color: #f56c6c;
}
.pagination {
  justify-content: flex-end;
  margin-top: 24px;
}
</style>
