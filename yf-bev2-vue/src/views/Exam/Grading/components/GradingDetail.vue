<template>
  <div v-loading="loading" class="grading-page">
    <div v-if="!detail && !loading" class="box"
      ><el-empty description="暂时无法加载试卷"
        ><el-button @click="load">重新加载</el-button></el-empty
      ></div
    >
    <template v-if="detail">
      <header class="box detail-head">
        <el-button link type="primary" @click="back">‹ 返回阅卷列表</el-button>
        <div class="title-row"
          ><h2
            >{{ detail.title }}
            <el-tag :type="completed ? 'success' : 'warning'">{{
              completed ? '已完成' : detail.gradedCount > 0 ? '阅卷中' : '待阅卷'
            }}</el-tag></h2
          ><span class="muted">交卷时间：{{ date(detail.handTime) }}</span></div
        >
        <div class="metadata"
          ><span>考生　{{ detail.subjectName || '历史考生' }}</span
          ><span
            >部门 / 岗位　{{ detail.departName || '—' }} / {{ detail.positionName || '—' }}</span
          ><span>场景　{{ scenes[detail.sceneType] || '历史考核' }}</span
          ><span>批次　{{ detail.batchNo || '—' }}</span></div
        >
      </header>
      <el-alert
        v-if="blocked"
        :title="
          detail.snapshotSource === 'LEGACY_INCOMPLETE'
            ? '历史试卷内容缺失，请核实原始材料，当前不能评分或完成阅卷。'
            : '考核已停用或状态异常，当前仅可查看。'
        "
        type="warning"
        :closable="false"
        class="warning"
      />
      <el-alert
        v-else-if="detail.snapshotSource !== 'CREATED'"
        title="历史试卷内容在迁移时回填，请核对原始材料；未保存的历史文字答案无法恢复。"
        type="warning"
        :closable="false"
        class="warning"
      />
      <div class="grading-grid">
        <aside class="box question-nav"
          ><h3>简答题</h3
          ><div class="muted">已评分 {{ detail.gradedCount }} / {{ detail.shortCount }} 题</div
          ><el-progress
            :percentage="detail.shortCount ? (detail.gradedCount / detail.shortCount) * 100 : 0"
            :show-text="false"
            class="progress"
          />
          <button
            v-for="(item, index) in detail.questions"
            :key="item.id"
            class="question-button"
            :class="{ active: item.id === question?.id }"
            :disabled="saving"
            @click="choose(index)"
            ><span>第 {{ index + 1 }} 题</span
            ><small :class="item.gradingState === 'GRADED' ? 'done' : 'pending'">{{
              item.gradingState === 'GRADED' ? `${item.actualScore} / ${item.score}` : '待评分'
            }}</small></button
          >
          <div class="history"
            ><strong>评分记录</strong
            ><div v-for="log in detail.logs.slice(0, 2)" :key="log.id" class="log-preview"
              >{{ date(log.createTime) }}<br />{{ log.graderName || '阅卷人' }} ·
              {{ log.action === 'FINALIZE' ? '完成阅卷' : `试卷第 ${log.sort} 题` }}<br />{{
                log.action === 'FINALIZE'
                  ? `最终 ${log.scoreAfter} 分`
                  : `${log.scoreBefore ?? '未评分'} → ${log.scoreAfter} 分`
              }}</div
            ><div v-if="!detail.logs.length" class="muted">暂无评分记录</div
            ><el-button v-if="detail.logs.length" link type="primary" @click="logsVisible = true"
              >查看全部记录</el-button
            ></div
          >
        </aside>
        <article v-if="question" class="box question-main"
          ><div class="title-row"
            ><h3>第 {{ selected + 1 }} 题 · 简答题</h3
            ><span class="muted">满分 {{ question.score }} 分</span></div
          ><div class="question-content" v-html="question.content || '历史题干缺失'"></div
          ><h4>考生作答</h4><div class="answer-box">{{ question.textAnswer || '未作答' }}</div
          ><div class="reference"
            ><h4>参考答案与评分标准</h4
            ><div class="reference-text">{{
              question.referenceAnswer || '未配置参考答案，请结合题干评阅。'
            }}</div
            ><div class="criteria">{{ question.gradingCriteria || '未配置评分标准。' }}</div></div
          ></article
        >
        <div v-if="question" class="box scoring"
          ><h3>本题评分</h3><label for="grading-score">得分 <span class="required">*</span></label
          ><div class="score-control"
            ><el-input-number
              id="grading-score"
              v-model="score"
              :min="0"
              :max="Number(question.score)"
              :precision="2"
              :step="0.5"
              :controls="false"
              :disabled="!canEdit || saving"
              placeholder="请输入得分"
            /><span class="muted">/ {{ question.score }} 分</span></div
          ><p class="muted score-note">0 分也需保存，不能留空。</p
          ><label for="grading-comment">阅卷评语 <span class="muted">（选填）</span></label
          ><el-input
            id="grading-comment"
            v-model="comment"
            type="textarea"
            :rows="4"
            maxlength="2000"
            show-word-limit
            :disabled="!canEdit || saving"
            placeholder="说明得分依据或需要改进的地方"
          />
          <p class="save-state" :class="{ pending: dirty }">{{
            dirty
              ? '有未保存的修改'
              : question.gradingState === 'GRADED'
                ? '✓ 评分已保存'
                : '尚未评分'
          }}</p
          ><el-button
            v-hasPermi="['exam:grading:score']"
            type="primary"
            class="save-button"
            :loading="saving"
            :disabled="!canEdit || score == null"
            @click="save"
            >保存本题评分</el-button
          >
          <div class="summary"
            ><div
              ><span>客观题得分</span><span>{{ detail.objectiveScore }}</span></div
            ><div
              ><span>已保存简答题得分</span><span>{{ detail.subjectiveScore }}</span></div
            ><div
              ><span>{{ completed ? '最终总分' : '当前累计分' }}</span
              ><strong>{{ totalScore }} / {{ detail.totalScore }}</strong></div
            ><div
              ><span>通过分数</span><span>{{ detail.qualifyScore }} 分</span></div
            ><el-alert
              title="全部简答题评分后，才能完成阅卷并生成最终结果。"
              type="info"
              :closable="false"
          /></div>
        </div>
        <el-empty v-if="!question" description="没有可评阅的简答题，请核实历史试卷题型。" />
      </div>
      <footer class="box grade-footer"
        ><span :class="completed ? 'done' : 'pending'">{{
          completed
            ? '阅卷已完成，考生可查看是否通过'
            : dirty
              ? '请先保存当前题目的评分'
              : detail.gradedCount < detail.shortCount
                ? `还有 ${detail.shortCount - detail.gradedCount} 道简答题未评分`
                : '全部题目已评分，请检查后完成阅卷'
        }}</span
        ><div
          ><el-button :disabled="saving" @click="reload">刷新试卷</el-button
          ><el-button :disabled="saving" @click="back">返回列表</el-button
          ><el-button
            v-hasPermi="['exam:grading:finalize']"
            type="primary"
            :disabled="!canFinalize"
            :loading="saving"
            @click="confirmVisible = true"
            >完成阅卷</el-button
          ></div
        ></footer
      >
    </template>
    <el-dialog
      v-model="confirmVisible"
      title="确认完成阅卷"
      width="520px"
      :close-on-click-modal="false"
      ><template v-if="detail"
        ><p>{{ detail.subjectName }} · {{ detail.title }}</p
        ><el-descriptions :column="1"
          ><el-descriptions-item label="客观题">{{ detail.objectiveScore }} 分</el-descriptions-item
          ><el-descriptions-item label="简答题"
            >{{ detail.subjectiveScore }} 分</el-descriptions-item
          ><el-descriptions-item label="最终总分"
            >{{ totalScore }} / {{ detail.totalScore }}</el-descriptions-item
          ><el-descriptions-item label="考核结果"
            ><el-tag :type="totalScore >= detail.qualifyScore ? 'success' : 'danger'">{{
              totalScore >= detail.qualifyScore ? '通过' : '未通过'
            }}</el-tag></el-descriptions-item
          ></el-descriptions
        ><el-alert
          title="确认后自动开放是否通过结果，考生不会看到分数、评语或参考答案。"
          type="info"
          :closable="false"
        /><p class="muted">完成后本页将只读，评分过程保留在阅卷记录中。</p></template
      ><template #footer
        ><el-button :disabled="saving" @click="confirmVisible = false">继续检查</el-button
        ><el-button type="primary" :loading="saving" @click="finalize"
          >确认完成</el-button
        ></template
      ></el-dialog
    >
    <el-drawer v-model="logsVisible" title="阅卷记录" size="500px"
      ><div v-for="log in detail?.logs || []" :key="log.id" class="audit-log"
        ><strong>{{
          log.action === 'FINALIZE'
            ? '完成阅卷'
            : `${log.action === 'REGRADE' ? '修改评分' : '首次评分'} · 试卷第 ${log.sort} 题`
        }}</strong
        ><p class="muted">{{ log.graderName || '阅卷人' }} · {{ date(log.createTime) }}</p
        ><p>得分：{{ log.scoreBefore ?? '未评分' }} → {{ log.scoreAfter }}</p
        ><template v-if="log.action !== 'FINALIZE'"
          ><p class="reference-text">原评语：{{ log.commentBefore || '无' }}</p
          ><p class="reference-text">新评语：{{ log.commentAfter || '无' }}</p></template
        ></div
      ></el-drawer
    >
  </div>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import {
  gradingDetailApi,
  gradingSaveApi,
  gradingFinalizeApi,
  type GradingDetail
} from '@/api/modules/exam/grading'
import { scenes } from '@/api/modules/exam/assignment/employee'
const props = defineProps<{ paperId: string }>(),
  emit = defineEmits(['back'])
const detail = ref<GradingDetail>(),
  loading = ref(false),
  saving = ref(false),
  selected = ref(0),
  score = ref<number>(),
  comment = ref(''),
  confirmVisible = ref(false),
  logsVisible = ref(false)
const question = computed(() => detail.value?.questions[selected.value])
const completed = computed(() => detail.value?.gradingState === 'GRADED')
const blocked = computed(
  () =>
    detail.value?.snapshotSource === 'LEGACY_INCOMPLETE' ||
    (!!detail.value?.assignmentStatus &&
      !['PENDING_REVIEW', 'COMPLETED'].includes(detail.value.assignmentStatus))
)
const canEdit = computed(() => !completed.value && !blocked.value)
const dirty = computed(
  () =>
    !!question.value &&
    (score.value !==
      (question.value.gradingState === 'GRADED' ? Number(question.value.actualScore) : undefined) ||
      comment.value !== (question.value.comment || ''))
)
const canFinalize = computed(
  () =>
    !!detail.value &&
    canEdit.value &&
    !dirty.value &&
    !saving.value &&
    detail.value.shortCount > 0 &&
    detail.value.gradedCount === detail.value.shortCount
)
const totalScore = computed(
  () =>
    Math.round(
      (Number(detail.value?.objectiveScore || 0) + Number(detail.value?.subjectiveScore || 0)) * 100
    ) / 100
)
const date = (value: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '—')
function syncForm() {
  score.value =
    question.value?.gradingState === 'GRADED' ? Number(question.value.actualScore) : undefined
  comment.value = question.value?.comment || ''
}
async function load() {
  loading.value = true
  try {
    detail.value = (await gradingDetailApi(props.paperId)).data
    selected.value = Math.min(
      selected.value,
      Math.max(0, (detail.value?.questions.length || 1) - 1)
    )
    syncForm()
  } finally {
    loading.value = false
  }
}
async function mayLeave() {
  if (saving.value) return false
  if (!dirty.value) return true
  try {
    await ElMessageBox.confirm('当前评分尚未保存，是否放弃修改？', '未保存的评分', {
      confirmButtonText: '放弃修改',
      cancelButtonText: '继续评分',
      type: 'warning'
    })
    syncForm()
    return true
  } catch {
    return false
  }
}
async function choose(index: number) {
  if (index === selected.value || !(await mayLeave())) return
  selected.value = index
  syncForm()
}
async function back() {
  if (await mayLeave()) emit('back')
}
async function reload() {
  if (await mayLeave()) await load()
}
async function save() {
  if (saving.value || !question.value || !detail.value || score.value == null || !canEdit.value)
    return
  saving.value = true
  try {
    detail.value = (
      await gradingSaveApi({
        paperQuId: question.value.id,
        score: score.value,
        comment: comment.value,
        expectedVersion: detail.value.version
      })
    ).data
    syncForm()
    ElMessage.success('评分已保存')
  } finally {
    saving.value = false
  }
}
async function finalize() {
  if (!detail.value || !canFinalize.value) return
  saving.value = true
  try {
    detail.value = (
      await gradingFinalizeApi({ paperId: props.paperId, expectedVersion: detail.value.version })
    ).data
    syncForm()
    confirmVisible.value = false
    ElMessage.success('阅卷已完成')
  } finally {
    saving.value = false
  }
}
function beforeUnload(event: BeforeUnloadEvent) {
  if (dirty.value || saving.value) {
    event.preventDefault()
    event.returnValue = ''
  }
}
onBeforeRouteLeave(mayLeave)
onBeforeRouteUpdate(mayLeave)
onMounted(() => {
  load()
  window.addEventListener('beforeunload', beforeUnload)
})
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
</script>
<style scoped>
.box {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 20px;
}
.detail-head {
  margin-bottom: 16px;
}
.title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.title-row h2 {
  font-size: 20px;
  margin: 12px 0;
}
.box h3 {
  font-size: 16px;
  margin: 0 0 16px;
}
.box h4 {
  font-size: 14px;
  margin: 0 0 12px;
}
.metadata {
  display: flex;
  flex-wrap: wrap;
  gap: 14px 26px;
  color: #606266;
  font-size: 13px;
  margin-top: 8px;
}
.muted {
  color: #909399;
  font-size: 13px;
}
.grading-grid {
  display: grid;
  grid-template-columns: 175px minmax(0, 1fr) 280px;
  gap: 16px;
}
.progress {
  margin: 12px 0 20px;
}
.question-button {
  width: 100%;
  padding: 12px 9px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: white;
  margin-bottom: 10px;
  cursor: pointer;
  color: #606266;
  font-size: 13px;
}
.question-button.active {
  background: #ecf5ff;
  border-color: #409eff;
  color: #409eff;
}
.question-button small {
  font-size: 11px;
}
.done {
  color: #67a941;
}
.pending {
  color: #b88230;
}
.history {
  border-top: 1px solid #ebeef5;
  margin-top: 24px;
  padding-top: 18px;
  font-size: 12px;
  color: #909399;
}
.log-preview {
  line-height: 1.8;
  margin: 10px 0;
}
.question-content {
  line-height: 1.85;
  font-size: 15px;
  margin-bottom: 22px;
  overflow-wrap: anywhere;
}
.question-content :deep(img) {
  max-width: 100%;
}
.answer-box {
  background: #f8f9fb;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 16px;
  color: #606266;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.9;
  font-size: 14px;
  max-height: 420px;
  overflow: auto;
}
.reference {
  border-top: 1px solid #ebeef5;
  margin-top: 22px;
  padding-top: 20px;
}
.reference-text,
.criteria {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.8;
  color: #606266;
  font-size: 13px;
}
.criteria {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid #ebeef5;
}
.scoring label {
  display: block;
  color: #606266;
  font-size: 13px;
  margin-bottom: 8px;
}
.required {
  color: #f56c6c;
}
.score-control {
  display: flex;
  align-items: center;
  gap: 12px;
}
.score-control :deep(.el-input-number) {
  width: 120px;
}
.score-note {
  font-size: 12px;
  margin: 8px 0 22px;
}
.save-state {
  font-size: 12px;
  color: #909399;
  margin: 10px 0;
}
.save-button {
  width: 100%;
  margin-top: 5px;
}
.summary {
  border-top: 1px solid #ebeef5;
  margin-top: 20px;
  padding-top: 10px;
}
.summary > div:not(.el-alert) {
  display: flex;
  justify-content: space-between;
  color: #606266;
  font-size: 13px;
  margin: 13px 0;
  gap: 8px;
}
.summary strong {
  font-size: 18px;
  font-weight: 500;
  color: #303133;
}
.summary :deep(.el-alert__title) {
  font-size: 12px;
  line-height: 1.7;
}
.grade-footer {
  margin-top: 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  font-size: 13px;
}
.warning {
  margin-bottom: 16px;
}
.audit-log {
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 20px;
  padding-bottom: 12px;
}
@media (max-width: 1150px) {
  .grading-grid {
    grid-template-columns: 145px minmax(0, 1fr) 250px;
  }
  .box {
    padding: 15px;
  }
}
@media (max-width: 900px) {
  .grading-grid {
    grid-template-columns: 1fr;
  }
  .grade-footer {
    flex-wrap: wrap;
  }
  .question-nav {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
  }
  .question-nav .question-button {
    width: 120px;
  }
  .question-nav .history {
    width: 100%;
  }
}
</style>
