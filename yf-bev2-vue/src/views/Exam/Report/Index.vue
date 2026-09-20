<template>
  <ContentWrap>
    <div class="title-row">
      <div><h2>成绩查询</h2><p>查看已交卷考核，按筛选条件导出成绩列表</p></div>
      <el-button
        v-hasPermi="['exam:results:export']"
        type="primary"
        :disabled="loading || failed || !page.total"
        :loading="previewing"
        @click="openExport"
        >导出 Excel</el-button
      >
    </div>
    <el-form class="filters" label-position="top" @submit.prevent="search">
      <el-form-item label="考生"
        ><el-input
          v-model="draft.keyword"
          placeholder="姓名 / 编号 / 工号"
          clearable
          @keyup.enter="search"
      /></el-form-item>
      <el-form-item label="人员类型"
        ><el-select v-model="draft.subjectType" clearable placeholder="全部人员"
          ><el-option label="候选人" value="CANDIDATE" /><el-option
            label="员工"
            value="EMPLOYEE" /></el-select
      ></el-form-item>
      <el-form-item label="部门"
        ><el-select v-model="draft.departId" clearable filterable placeholder="全部部门"
          ><el-option
            v-for="v in options.departments"
            :key="v.id"
            :value="v.id"
            :label="v.name" /></el-select
      ></el-form-item>
      <el-form-item label="岗位"
        ><el-select v-model="draft.positionId" clearable filterable placeholder="全部岗位"
          ><el-option
            v-for="v in options.positions"
            :key="v.id"
            :value="v.id"
            :label="v.name" /></el-select
      ></el-form-item>
      <el-form-item label="场景"
        ><el-select v-model="draft.sceneType" clearable placeholder="全部场景"
          ><el-option
            v-for="(label, value) in scenes"
            :key="value"
            :value="value"
            :label="label" /></el-select
      ></el-form-item>
      <el-form-item label="批次"
        ><el-input
          v-model="draft.batchNo"
          clearable
          placeholder="输入考核批次"
          @keyup.enter="search"
      /></el-form-item>
      <el-form-item label="考核名称"
        ><el-input v-model="draft.title" clearable placeholder="输入考核名称" @keyup.enter="search"
      /></el-form-item>
      <el-form-item label="最终结果"
        ><el-select
          v-model="draft.passed"
          clearable
          placeholder="全部结果"
          @clear="draft.passed = null"
          ><el-option label="通过" :value="true" /><el-option
            label="未通过"
            :value="false" /></el-select
      ></el-form-item>
      <el-form-item label="交卷时间" class="span-two"
        ><el-date-picker
          v-model="dates"
          type="datetimerange"
          value-format="YYYY-MM-DD HH:mm:ss"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
      /></el-form-item>
      <el-form-item label="最终总分" class="span-two"
        ><div class="score-range"
          ><el-input-number
            v-model="draft.scoreMin"
            :min="0"
            :max="99999999"
            :precision="2"
            :controls="false"
            placeholder="最低分" /><span>至</span
          ><el-input-number
            v-model="draft.scoreMax"
            :min="0"
            :max="99999999"
            :precision="2"
            :controls="false"
            placeholder="最高分" /></div
      ></el-form-item>
      <div class="filter-actions"
        ><el-button type="primary" native-type="submit">查询</el-button
        ><el-button @click="reset">重置</el-button></div
      >
    </el-form>
    <el-tabs v-model="state" class="status-tabs" @tab-change="changeState">
      <el-tab-pane :label="`全部已交卷 (${page.all})`" name="ALL" />
      <el-tab-pane :label="`已完成 (${page.completed})`" name="COMPLETED" />
      <el-tab-pane :label="`待阅卷 (${page.pending})`" name="PENDING" />
    </el-tabs>
    <el-alert v-if="failed" title="成绩加载失败，请重新查询" type="error" :closable="false" />
    <el-table
      v-loading="loading"
      :data="page.records"
      stripe
      empty-text="暂无符合条件的成绩"
      class="results-table"
    >
      <el-table-column label="考生 / 编号" min-width="130"
        ><template #default="{ row }"
          ><strong>{{ row.subjectName }}</strong
          ><div class="secondary">{{ person(row) }} · {{ row.subjectNo || '—' }}</div></template
        ></el-table-column
      >
      <el-table-column label="部门 / 岗位" min-width="125"
        ><template #default="{ row }"
          >{{ row.departName || '—'
          }}<div class="secondary">{{ row.positionName || '—' }}</div></template
        ></el-table-column
      >
      <el-table-column label="考核 / 批次" min-width="175"
        ><template #default="{ row }"
          >{{ row.title }}<div class="secondary">{{ row.batchNo || '—' }}</div></template
        ></el-table-column
      >
      <el-table-column label="场景" width="65"
        ><template #default="{ row }">{{ scenes[row.sceneType] || '—' }}</template></el-table-column
      >
      <el-table-column label="最终总分" min-width="130"
        ><template #default="{ row }"
          ><strong>{{ row.userScore ?? '—' }}</strong
          ><div class="secondary"
            >满分 {{ row.totalScore }} / 及格 {{ row.qualifyScore }}</div
          ></template
        ></el-table-column
      >
      <el-table-column label="最终结果" width="95"
        ><template #default="{ row }"
          ><el-tag :type="row.passed == null ? 'info' : row.passed ? 'success' : 'danger'">{{
            result(row)
          }}</el-tag></template
        ></el-table-column
      >
      <el-table-column label="阅卷状态" width="110"
        ><template #default="{ row }"
          ><el-tag :type="row.gradingState === 'PENDING' ? 'warning' : 'success'">{{
            grading(row)
          }}</el-tag></template
        ></el-table-column
      >
      <el-table-column label="交卷时间" min-width="150"
        ><template #default="{ row }">{{ date(row.handTime) }}</template></el-table-column
      >
      <el-table-column label="操作" width="65" fixed="right"
        ><template #default="{ row }"
          ><el-button type="primary" link @click="openDetail(row.id)">查看</el-button></template
        ></el-table-column
      >
    </el-table>
    <el-pagination
      v-model:current-page="applied.current"
      v-model:page-size="applied.size"
      class="pagination"
      :total="page.total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      @current-change="load"
      @size-change="resize"
    />
    <el-alert
      title="导出包含当前筛选条件下的全部页。待阅卷记录的主观分、最终总分和是否通过留空；考生端仍只显示是否通过。"
      type="info"
      :closable="false"
      class="result-note"
    />
    <el-dialog v-model="detailVisible" title="成绩详情" width="680px" :close-on-click-modal="false">
      <div v-loading="detailLoading" class="detail-grid"
        ><template v-if="detail"
          ><div v-for="[label, value] in detailFields" :key="label"
            ><label>{{ label }}</label
            ><span>{{ value }}</span></div
          ></template
        ></div
      >
      <template #footer><el-button @click="detailVisible = false">关闭</el-button></template>
    </el-dialog>
    <el-dialog
      v-model="exportVisible"
      title="导出成绩"
      width="700px"
      :close-on-click-modal="false"
      :close-on-press-escape="!exporting"
      :show-close="!exporting"
    >
      <div class="export-summary"
        >将导出 <strong>{{ exportInfo.total }}</strong> 条考核记录
        <span>· 当前筛选条件下的全部页</span></div
      >
      <h4>筛选范围</h4
      ><div class="filter-tags"
        ><el-tag v-for="tag in exportTags" :key="tag" type="info">{{ tag }}</el-tag></div
      >
      <h4>导出字段 <span class="secondary">18 列 · Excel (.xlsx)</span></h4
      ><div class="column-grid"
        ><span v-for="column in columns" :key="column">{{ column }}</span></div
      >
      <el-alert
        :title="`其中 ${exportInfo.pending} 条待阅卷，主观分、最终总分和是否通过将留空。`"
        type="warning"
        :closable="false"
        class="result-note"
      />
      <p class="secondary"
        >不包含手机号、邮箱、考核码、题目或答案。导出时以最新成绩及当前权限为准。</p
      >
      <el-alert
        v-if="exportInfo.total > exportInfo.limit"
        :title="`单次最多 ${exportInfo.limit} 条，请缩小筛选范围。`"
        type="error"
        :closable="false"
      />
      <template #footer
        ><el-button :disabled="exporting" @click="exportVisible = false">取消</el-button
        ><el-button
          type="primary"
          :loading="exporting"
          :disabled="!exportInfo.total || exportInfo.total > exportInfo.limit"
          @click="exportFile"
          >确认导出</el-button
        ></template
      >
    </el-dialog>
  </ContentWrap>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import { ContentWrap } from '@/components/ContentWrap'
import { scenes } from '@/api/modules/exam/assignment/employee'
import {
  resultPaging,
  resultOptions,
  resultDetail,
  resultPreview,
  downloadResults,
  type ResultQuery,
  type ResultRow,
  type ResultPage,
  type ResultOptions,
  type ResultCounts
} from '@/api/modules/exam/results'
const fresh = (): ResultQuery => ({ current: 1, size: 10, state: 'ALL', passed: null })
const emptyPage = (): ResultPage => ({ records: [], total: 0, all: 0, completed: 0, pending: 0 })
const draft = ref(fresh()),
  applied = ref(fresh()),
  state = ref('ALL'),
  dates = ref<string[]>([])
const page = ref(emptyPage()),
  options = ref<ResultOptions>({ departments: [], positions: [] })
const loading = ref(false),
  failed = ref(false),
  previewing = ref(false),
  exporting = ref(false),
  exportVisible = ref(false)
const detailVisible = ref(false),
  detailLoading = ref(false),
  detail = ref<ResultRow>()
const exportQuery = ref(fresh()),
  exportInfo = ref<ResultCounts>({ total: 0, pending: 0, limit: 10000 })
let loadId = 0,
  detailId = 0
const columns = [
  '姓名',
  '人员类型',
  '编号/工号',
  '部门',
  '岗位',
  '场景',
  '批次',
  '考核名称',
  '客观分',
  '主观分',
  '最终总分',
  '满分',
  '及格分',
  '是否通过',
  '交卷时间',
  '阅卷状态',
  '终审人',
  '完成阅卷时间'
]
const person = (r: ResultRow) => (r.subjectType === 'CANDIDATE' ? '候选人' : '员工')
const result = (r: ResultRow) => (r.passed == null ? '待阅卷' : r.passed ? '通过' : '未通过')
const grading = (r: ResultRow) =>
  r.gradingState === 'NOT_REQUIRED'
    ? '自动评分'
    : r.gradingState === 'GRADED'
      ? '已完成阅卷'
      : r.gradedCount > 0
        ? '阅卷中'
        : '待阅卷'
const date = (v: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '—')
const detailFields = computed(() => {
  const r = detail.value
  return r
    ? [
        ['考生', `${r.subjectName} · ${person(r)}`],
        ['编号 / 工号', r.subjectNo || '—'],
        ['考核', r.title],
        ['批次', r.batchNo || '—'],
        ['客观分', r.objectiveScore],
        ['主观分', r.subjectiveScore ?? '待阅卷'],
        ['最终总分', r.userScore == null ? '待阅卷' : `${r.userScore} / ${r.totalScore}`],
        ['最终结果', result(r)],
        ['阅卷状态', grading(r)],
        ['交卷时间', date(r.handTime)],
        ['终审人', r.graderName || '—'],
        ['完成阅卷时间', date(r.gradedAt)]
      ]
    : []
})
const exportTags = computed(() => {
  const q = exportQuery.value
  const tags = [
    q.state === 'PENDING' ? '待阅卷' : q.state === 'COMPLETED' ? '已完成' : '全部已交卷'
  ]
  if (q.keyword) tags.push(`考生：${q.keyword}`)
  if (q.subjectType) tags.push(q.subjectType === 'CANDIDATE' ? '候选人' : '员工')
  if (q.departId)
    tags.push(
      `部门：${options.value.departments.find((v) => v.id === q.departId)?.name || q.departId}`
    )
  if (q.positionId)
    tags.push(
      `岗位：${options.value.positions.find((v) => v.id === q.positionId)?.name || q.positionId}`
    )
  if (q.sceneType) tags.push(scenes[q.sceneType])
  if (q.batchNo) tags.push(`批次：${q.batchNo}`)
  if (q.title) tags.push(`考核：${q.title}`)
  if (q.passed != null) tags.push(q.passed ? '通过' : '未通过')
  if (q.scoreMin != null || q.scoreMax != null)
    tags.push(`总分：${q.scoreMin ?? '不限'} 至 ${q.scoreMax ?? '不限'}`)
  if (q.submittedFrom) tags.push(`交卷：${q.submittedFrom} 至 ${q.submittedTo}`)
  return tags
})
async function load() {
  const id = ++loadId
  loading.value = true
  failed.value = false
  try {
    const res = await resultPaging({ ...applied.value })
    if (id === loadId) page.value = res.data
  } catch {
    if (id === loadId) {
      page.value = emptyPage()
      failed.value = true
    }
  } finally {
    if (id === loadId) loading.value = false
  }
}
function search() {
  if (
    draft.value.scoreMin != null &&
    draft.value.scoreMax != null &&
    draft.value.scoreMin > draft.value.scoreMax
  ) {
    ElMessage.warning('最低分不能大于最高分')
    return
  }
  applied.value = {
    ...draft.value,
    current: 1,
    size: applied.value.size,
    state: state.value,
    submittedFrom: dates.value?.[0] || null,
    submittedTo: dates.value?.[1] || null
  }
  void load()
}
function reset() {
  draft.value = fresh()
  dates.value = []
  state.value = 'ALL'
  search()
}
function changeState() {
  applied.value = { ...applied.value, state: state.value, current: 1 }
  void load()
}
function resize() {
  applied.value.current = 1
  void load()
}
async function openDetail(id: string) {
  const requestId = ++detailId
  detail.value = undefined
  detailVisible.value = true
  detailLoading.value = true
  try {
    const response = await resultDetail(id)
    if (requestId === detailId) detail.value = response.data
  } catch {
    if (requestId === detailId) detailVisible.value = false
  } finally {
    if (requestId === detailId) detailLoading.value = false
  }
}
async function openExport() {
  previewing.value = true
  const query = { ...applied.value }
  try {
    const response = await resultPreview(query)
    exportQuery.value = query
    exportInfo.value = response.data
    exportVisible.value = true
  } catch {
    /* request interceptor presents the error */
  } finally {
    previewing.value = false
  }
}
async function exportFile() {
  exporting.value = true
  try {
    await downloadResults(exportQuery.value)
    exportVisible.value = false
    ElMessage.success('成绩文件已生成')
  } catch {
    /* API presents the error, keep dialog available for retry */
  } finally {
    exporting.value = false
  }
}
onMounted(() => {
  void load()
  resultOptions()
    .then((res) => {
      options.value = res.data
    })
    .catch(() => {})
})
</script>
<style scoped>
.title-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 22px;
  gap: 16px;
}
h2 {
  font-size: 20px;
  margin: 0 0 6px;
  font-weight: 600;
}
.title-row p {
  color: #909399;
  font-size: 13px;
  margin: 0;
}
.filters {
  background: #f7f8fa;
  padding: 16px;
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px 14px;
}
.filters :deep(.el-form-item) {
  margin: 0;
  min-width: 0;
}
.filters :deep(.el-select),
.filters :deep(.el-date-editor) {
  width: 100%;
  min-width: 0;
}
.span-two {
  grid-column: span 2;
}
.score-range {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}
.score-range :deep(.el-input-number) {
  width: 100%;
  min-width: 0;
}
.filter-actions {
  display: flex;
  align-items: flex-end;
  padding-bottom: 1px;
}
.status-tabs {
  margin-top: 20px;
}
.results-table {
  font-size: 13px;
}
.results-table :deep(.el-table__cell) {
  padding: 14px 0;
}
.results-table strong {
  font-weight: 500;
  color: #303133;
}
.secondary {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
}
.pagination {
  justify-content: flex-end;
  margin-top: 20px;
}
.result-note {
  margin-top: 20px;
}
.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 22px;
  min-height: 120px;
}
.detail-grid label {
  display: block;
  color: #909399;
  margin-bottom: 6px;
  font-size: 13px;
}
.detail-grid span {
  overflow-wrap: anywhere;
}
.export-summary {
  padding: 16px;
  background: #ecf5ff;
  border-radius: 4px;
  color: #606266;
}
.export-summary strong {
  color: #409eff;
  font-size: 24px;
  margin: 0 6px;
}
.export-summary span {
  color: #909399;
  font-size: 13px;
}
h4 {
  margin: 24px 0 12px;
  font-weight: 500;
}
.filter-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.column-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  background: #f7f8fa;
  padding: 16px;
  font-size: 13px;
}
@media (max-width: 1100px) {
  .filters {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (max-width: 650px) {
  .filters {
    grid-template-columns: 1fr;
  }
  .span-two {
    grid-column: auto;
  }
  .title-row {
    flex-wrap: wrap;
  }
  .pagination {
    overflow: auto;
    justify-content: flex-start;
  }
  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
