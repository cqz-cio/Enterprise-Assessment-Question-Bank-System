<template>
  <el-dialog v-model="dialogVisible" title="候选人测评结果" width="980px" destroy-on-close>
    <div v-loading="loading" class="result-dialog-body">
      <template v-if="paperData.id">
        <div class="result-heading">
          <div>
            <h2>{{ candidate.subjectName }}</h2>
            <p>{{ paperData.title }}</p>
          </div>
          <el-tag :type="paperData.passed ? 'success' : 'danger'" effect="dark" size="large">
            {{ paperData.passed ? '测评通过' : '测评未通过' }}
          </el-tag>
        </div>

        <el-descriptions :column="4" border class="result-summary">
          <el-descriptions-item label="候选人编号">{{
            candidate.candidateNo || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="部门">{{
            candidate.departName || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="岗位">{{
            candidate.positionName || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="批次">{{ candidate.batchNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="得分"
            >{{ paperData.userScore }} / {{ paperData.totalScore }}</el-descriptions-item
          >
          <el-descriptions-item label="及格分">{{ paperData.qualifyScore }}</el-descriptions-item>
          <el-descriptions-item label="考试用时"
            >{{ paperData.userTime ?? 0 }} 分钟</el-descriptions-item
          >
          <el-descriptions-item label="交卷时间">{{
            paperData.handTime || '-'
          }}</el-descriptions-item>
        </el-descriptions>

        <div class="question-list">
          <el-card
            v-for="(question, index) in paperData.quList || []"
            :key="question.id"
            shadow="never"
            class="question-card"
          >
            <template #header>
              <div class="question-header">
                <div>
                  <strong>第 {{ index + 1 }} 题</strong>
                  <el-tag size="small" class="question-type">{{
                    questionTypeText(question.quType)
                  }}</el-tag>
                </div>
                <span :class="question.isRight ? 'score-right' : 'score-wrong'">
                  得分 {{ question.actualScore ?? 0 }} / {{ question.score }}
                </span>
              </div>
            </template>

            <div class="question-content" v-html="question.content"></div>
            <div v-if="question.answerList?.length" class="answer-list">
              <div
                v-for="answer in question.answerList"
                :key="answer.id"
                :class="['answer-row', { selected: answer.checked, correct: answer.isRight }]"
              >
                <span>{{ answer.abc }}. {{ answer.content }}</span>
                <span class="answer-marks">
                  <el-tag v-if="answer.checked" size="small" type="primary">候选人选择</el-tag>
                  <el-tag v-if="answer.isRight" size="small" type="success">正确答案</el-tag>
                </span>
              </div>
            </div>
            <div class="answer-summary">
              <span>候选人答案：{{ selectedAnswers(question) }}</span>
              <span>正确答案：{{ correctAnswers(question) }}</span>
            </div>
          </el-card>
        </div>
      </template>
    </div>

    <template #footer>
      <el-button type="primary" @click="dialogVisible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import { computed, ref, watch } from 'vue'
import { candidateResultDetailApi } from '@/api/modules/exam/assignment'

const props = withDefaults(
  defineProps<{
    visible: boolean
    assignmentId?: string
    candidate?: Record<string, any>
  }>(),
  {
    assignmentId: '',
    candidate: () => ({})
  }
)

const emit = defineEmits(['update:visible'])
const loading = ref(false)
const paperData = ref<any>({ quList: [] })

const dialogVisible = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value)
})

const questionTypeText = (type: string) =>
  ({
    radio: '单选题',
    multi: '多选题',
    judge: '判断题',
    short: '简答题'
  })[type] || type

const answerLabels = (question: any, field: 'checked' | 'isRight') => {
  const labels = (question.answerList || [])
    .filter((answer: any) => answer[field])
    .map((answer: any) => answer.abc)
  return labels.length ? labels.join('、') : '未答'
}

const selectedAnswers = (question: any) => answerLabels(question, 'checked')
const correctAnswers = (question: any) => answerLabels(question, 'isRight')

watch([() => props.visible, () => props.assignmentId], async ([visible, assignmentId]) => {
  if (!visible || !assignmentId) return
  loading.value = true
  paperData.value = { quList: [] }
  try {
    const response = await candidateResultDetailApi({ id: assignmentId })
    paperData.value = response.data
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.result-dialog-body {
  min-height: 220px;
}
.result-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}
.result-heading h2 {
  margin: 0 0 6px;
  font-size: 22px;
}
.result-heading p {
  margin: 0;
  color: #909399;
}
.result-summary {
  margin-bottom: 22px;
}
.question-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-height: 52vh;
  overflow: auto;
  padding-right: 4px;
}
.question-card {
  border-color: #e5e7eb;
}
.question-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.question-type {
  margin-left: 10px;
}
.score-right {
  color: #67c23a;
  font-weight: 600;
}
.score-wrong {
  color: #f56c6c;
  font-weight: 600;
}
.question-content {
  margin-bottom: 14px;
  font-size: 15px;
}
.question-content :deep(p) {
  margin: 0;
}
.answer-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.answer-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 9px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
}
.answer-row.selected {
  border-color: #409eff;
  background: #ecf5ff;
}
.answer-row.correct {
  border-color: #67c23a;
}
.answer-marks {
  display: flex;
  flex-shrink: 0;
  gap: 6px;
}
.answer-summary {
  display: flex;
  justify-content: space-between;
  margin-top: 14px;
  color: #606266;
  font-size: 14px;
}
</style>
