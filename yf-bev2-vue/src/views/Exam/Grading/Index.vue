<template>
  <GradingDetail v-if="paperId" :key="paperId" :paper-id="paperId" @back="back" />
  <ContentWrap v-else>
    <div class="heading"><h2>人工阅卷</h2><p>逐题评阅简答题，完成后自动生成考核结果。</p></div>
    <el-tabs v-model="query.state" @tab-change="search">
      <el-tab-pane label="待阅卷" name="PENDING" />
      <el-tab-pane label="已完成" name="GRADED" />
    </el-tabs>
    <el-form inline class="filters" @submit.prevent="search">
      <el-form-item
        ><el-input
          v-model="query.keyword"
          placeholder="姓名 / 考核名称"
          clearable
          @keyup.enter="search"
      /></el-form-item>
      <el-form-item
        ><el-select v-model="query.positionId" placeholder="全部岗位" clearable
          ><el-option
            v-for="position in positions"
            :key="position.id"
            :value="position.id"
            :label="position.name" /></el-select
      ></el-form-item>
      <el-form-item
        ><el-select v-model="query.sceneType" placeholder="全部场景" clearable
          ><el-option
            v-for="(label, value) in scenes"
            :key="value"
            :value="value"
            :label="label" /></el-select
      ></el-form-item>
      <el-form-item
        ><el-input v-model="query.batchNo" placeholder="考核批次" clearable @keyup.enter="search"
      /></el-form-item>
      <el-form-item
        ><el-date-picker
          v-model="dates"
          type="datetimerange"
          start-placeholder="提交开始时间"
          end-placeholder="提交结束时间"
          value-format="YYYY-MM-DD HH:mm:ss"
      /></el-form-item>
      <el-form-item
        ><el-button type="primary" @click="search">查询</el-button
        ><el-button @click="reset">重置</el-button></el-form-item
      >
    </el-form>
    <div class="list-tip">按交卷时间排序，优先处理较早提交的试卷</div>
    <el-table v-loading="loading" :data="rows" stripe empty-text="暂无符合条件的试卷">
      <el-table-column label="考生" min-width="130"
        ><template #default="{ row }"
          ><strong>{{ row.subjectName || '历史考生' }}</strong
          ><div class="secondary">{{
            row.subjectType === 'CANDIDATE' ? '候选人' : '员工'
          }}</div></template
        ></el-table-column
      >
      <el-table-column label="考核 / 批次" min-width="190"
        ><template #default="{ row }"
          >{{ row.title }}<div class="secondary">{{ row.batchNo || '—' }}</div></template
        ></el-table-column
      >
      <el-table-column label="岗位 / 场景" min-width="130"
        ><template #default="{ row }"
          >{{ row.positionName || '—'
          }}<div class="secondary">{{ scenes[row.sceneType] || '历史考核' }}</div></template
        ></el-table-column
      >
      <el-table-column label="交卷时间" min-width="160"
        ><template #default="{ row }">{{ date(row.handTime) }}</template></el-table-column
      >
      <el-table-column prop="objectiveScore" label="客观题得分" width="110" />
      <el-table-column label="简答题进度" width="120"
        ><template #default="{ row }"
          >{{ row.gradedCount }} / {{ row.shortCount }} 已评分</template
        ></el-table-column
      >
      <el-table-column label="状态" width="100"
        ><template #default="{ row }"
          ><el-tag :type="row.gradingState === 'GRADED' ? 'success' : 'warning'">{{
            row.gradingState === 'GRADED' ? '已完成' : row.gradedCount > 0 ? '阅卷中' : '待阅卷'
          }}</el-tag></template
        ></el-table-column
      >
      <el-table-column label="操作" width="105" fixed="right"
        ><template #default="{ row }"
          ><el-button link type="primary" @click="open(row.id)">{{
            row.gradingState === 'GRADED' ? '查看记录' : row.gradedCount > 0 ? '继续阅卷' : '去阅卷'
          }}</el-button></template
        ></el-table-column
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
    <el-alert
      title="管理端可查看分数、参考答案和阅卷记录；考生端只展示是否通过。"
      type="info"
      :closable="false"
      class="result-note"
    />
  </ContentWrap>
</template>
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { ContentWrap } from '@/components/ContentWrap'
import { scenes } from '@/api/modules/exam/assignment/employee'
import { gradingPagingApi, gradingPositionsApi, type GradingRow } from '@/api/modules/exam/grading'
import GradingDetail from './components/GradingDetail.vue'
const route = useRoute(),
  router = useRouter()
const paperId = computed(() => String(route.query.paperId || ''))
const fresh = () => ({
  current: 1,
  size: 10,
  state: 'PENDING',
  keyword: '',
  positionId: '',
  sceneType: '',
  batchNo: ''
})
const query = ref(fresh()),
  positions = ref<{ id: string; name: string }[]>([]),
  dates = ref<string[]>([]),
  rows = ref<GradingRow[]>([]),
  total = ref(0),
  loading = ref(false)
let loadId = 0
const date = (value: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—')
async function load() {
  const id = ++loadId
  loading.value = true
  try {
    const response = await gradingPagingApi({
      ...query.value,
      submittedFrom: dates.value?.[0] || null,
      submittedTo: dates.value?.[1] || null
    })
    if (id !== loadId) return
    rows.value = response.data.records
    total.value = response.data.total
  } finally {
    if (id === loadId) loading.value = false
  }
}
function search() {
  query.value.current = 1
  load()
}
function reset() {
  query.value = fresh()
  dates.value = []
  load()
}
function open(id: string) {
  router.push({ query: { ...route.query, paperId: id } })
}
function back() {
  const { paperId: _paper, ...query } = route.query
  router.push({ query })
}
watch(paperId, (value) => {
  if (!value) load()
})
onMounted(() => {
  gradingPositionsApi().then((response) => {
    positions.value = response.data
  })
  if (!paperId.value) load()
})
</script>
<style scoped>
.heading h2 {
  font-size: 20px;
  margin: 0 0 6px;
}
.heading p,
.secondary,
.list-tip {
  color: #909399;
  font-size: 13px;
}
.heading p {
  margin: 0 0 22px;
}
.filters {
  background: #f7f8fa;
  padding: 14px 14px 0;
}
.filters :deep(.el-form-item) {
  margin-right: 10px;
  margin-bottom: 14px;
}
.filters :deep(.el-input) {
  width: 175px;
}
.filters :deep(.el-select) {
  width: 120px;
}
.filters :deep(.el-date-editor) {
  width: 330px;
}
.list-tip {
  margin: 18px 0 12px;
}
.secondary {
  margin-top: 5px;
  font-size: 12px;
}
.pagination {
  justify-content: flex-end;
  margin-top: 20px;
}
.result-note {
  margin-top: 24px;
}
</style>
