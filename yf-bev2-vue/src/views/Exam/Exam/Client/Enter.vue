<template>
  <el-row :gutter="20">
    <el-col :span="24">
      <el-card>
        <div class="top-opt-box">
          <ExamTimer
            :paperId="paperId"
            :assignment-id="assignmentId"
            @overdue="timeExpired = true"
            @handed="allowLeave = true"
          />

          <el-button
            icon="Select"
            size="large"
            type="primary"
            :loading="submitting"
            :disabled="timeExpired"
            @click="handPaper"
            >立即交卷
          </el-button>
        </div>
      </el-card>
    </el-col>
    <el-col :span="24" v-if="timeExpired"
      ><el-alert
        title="作答时间已结束，系统正在交卷，请等待结果。"
        type="warning"
        :closable="false"
    /></el-col>
    <el-col :span="4">
      <el-card class="answer-card">
        <div v-for="card in cardList" :key="card?.quType">
          <h3>{{ card?.quType_dictText }}</h3>
          <el-tag
            v-for="(item, i) in card?.itemList"
            :key="i"
            :class="{ 'ml-2': i > 0 }"
            :type="tagType(item)"
            class="tag-item"
            size="large"
            @click="item.quId && quDetail(item.quId)"
            >{{ i + 1 }}
          </el-tag>
        </div>
      </el-card>
    </el-col>
    <el-col :span="20">
      <el-card class="answer-card">
        <h3> {{ detail.quType_dictText }}</h3>
        <div v-html="detail.content"></div>
        <div v-if="detail.quType === 'short'" class="text-answer">
          <label for="short-answer">你的回答</label>
          <el-input
            id="short-answer"
            v-model="detail.textAnswer"
            type="textarea"
            :rows="9"
            maxlength="5000"
            show-word-limit
            :disabled="submitting || timeExpired || questionLoading"
            placeholder="请在此输入你的回答"
            @input="scheduleTextSave"
          />
          <div class="save-status" :class="{ failed: saveStates[detail.quId] === 'failed' }">
            <span>{{
              saveStates[detail.quId] === 'failed'
                ? '答案保存失败，请重试后再切题或交卷'
                : ['pending', 'saving'].includes(saveStates[detail.quId])
                  ? '正在保存…'
                  : '✓ 答案已保存'
            }}</span>
            <el-button
              v-if="saveStates[detail.quId] === 'failed'"
              link
              type="danger"
              :disabled="timeExpired"
              @click="saveTextAnswer"
              >重新保存</el-button
            >
          </div>
          <p class="answer-note">简答题由阅卷人员评阅，完成后可查看是否通过。</p>
        </div>
        <div v-else>
          <div
            v-for="item in detail.answerList"
            :key="item.answerId"
            :class="{ checked: item.checked }"
            class="answer-item"
            @click="itemClick(item)"
          >
            <div v-if="detail.quType === 'multi'">
              <input :checked="item.checked" type="checkbox" />
            </div>
            <div v-if="detail.quType === 'radio' || detail.quType === 'judge'">
              <input :checked="item.checked" type="radio" />
            </div>
            <div>
              {{ item.content }}
            </div>
          </div>
        </div>

        <div class="!text-center !pt-20px">
          <el-button
            :disabled="!hasPrev || submitting || questionLoading"
            icon="Back"
            size="large"
            type="primary"
            @click="prevQu"
            >上一题
          </el-button>
          <el-button
            :disabled="!hasNext || submitting || questionLoading"
            icon="Right"
            size="large"
            type="primary"
            @click="nextQu"
            >下一题
          </el-button>
        </div>
      </el-card>
    </el-col>
  </el-row>
</template>

<script lang="ts" setup>
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import {
  fillAnswerApi,
  fillTextAnswerApi,
  handApi,
  quCardApi,
  quDetailApi
} from '@/api/modules/exam/paper'
import { QuCardType } from '@/views/Exam/Exam/types'
import ExamTimer from '@/views/Exam/Exam/Client/components/ExamTimer.vue'

const { push } = useRouter()

const route = useRoute()
const paperId = route.query.id as string
const assignmentId = route.query.assignmentId as string
const detail = ref<any>({})
const cardList = ref<QuCardType[]>([])
const allQuIds = ref<string[]>([])

const hasNext = ref(false)
let saveQueue: Promise<void> = Promise.resolve()
const failedQuestions = new Set<string>()
const submitting = ref(false)
const questionLoading = ref(false)
const timeExpired = ref(false)
const allowLeave = ref(false)
const saveStates = ref<Record<string, string>>({})
let textTimer: ReturnType<typeof setTimeout> | undefined
let detailRequest = 0

const hasPrev = ref(false)

// 查找答题卡
const listCard = () => {
  quCardApi({ id: paperId }).then((res) => {
    cardList.value = res.data
    // 加载第一个题目
    buildAllQuIds()
  })
}

// 下一题
const nextQu = () => {
  const index = allQuIds.value.indexOf(detail.value.quId)
  quDetail(allQuIds.value[index + 1])
}

// 上一题
const prevQu = () => {
  const index = allQuIds.value.indexOf(detail.value.quId)
  quDetail(allQuIds.value[index - 1])
}

// 查找详情
const quDetail = async (quId: string) => {
  if (submitting.value || questionLoading.value) return
  const requestId = ++detailRequest
  questionLoading.value = true
  try {
    flushTextSave()
    await saveQueue
    if (failedQuestions.has(detail.value.quId)) {
      ElMessage.error('请先重新保存当前题目的答案。')
      return
    }
    const res = await quDetailApi({ paperId, quId })
    if (requestId !== detailRequest) return
    const index = allQuIds.value.indexOf(quId)
    hasPrev.value = index > 0
    hasNext.value = index < allQuIds.value.length - 1
    detail.value = res.data
  } finally {
    questionLoading.value = false
  }
}

const buildAllQuIds = () => {
  const ids: string[] = []
  if (cardList.value.length === 0) {
    return
  }
  for (let i = 0; i < cardList.value.length; i++) {
    const itemList = cardList.value[i].itemList || []
    for (let j = 0; j < itemList.length; j++) {
      if (itemList[j].quId) ids.push(itemList[j].quId as string)
    }
  }
  allQuIds.value = ids

  // 查找第一题
  if (ids[0]) quDetail(ids[0])
}

// 显示首个题目
const handPaper = async () => {
  if (submitting.value || questionLoading.value || timeExpired.value) return
  submitting.value = true
  try {
    flushTextSave()
    await saveQueue
    if (failedQuestions.size > 0) {
      ElMessage.error('仍有答案保存失败，请返回对应题目重新选择后再交卷。')
      return
    }
    const unanswered = cardList.value
      .flatMap((card) => card.itemList || [])
      .filter((item) => !item.answered).length
    try {
      await ElMessageBox.confirm(
        unanswered
          ? `还有 ${unanswered} 道题未作答，交卷后无法修改答案。`
          : '交卷后无法修改答案，确认提交吗？',
        '确认交卷',
        { confirmButtonText: '确认交卷', cancelButtonText: '继续作答', type: 'warning' }
      )
    } catch {
      return
    }
    await handApi({ id: paperId })
    allowLeave.value = true
    if (route.name === 'CandidateExamEnter' && assignmentId) {
      await push({ name: 'CandidateExamResult', query: { assignmentId } })
    } else {
      await push({ name: 'ExamClientResult', query: { id: paperId } })
    }
  } finally {
    submitting.value = false
  }
}

const tagType = (item: any) => {
  if (saveStates.value[item.quId] === 'failed') return 'danger'
  if (item.quId === detail.value.quId) {
    return 'danger'
  }
  if (item.answered) {
    return 'primary'
  }
  return 'info'
}

const itemClick = (e: any) => {
  if (submitting.value || questionLoading.value || timeExpired.value) return
  e.checked = !e.checked
  // 单选排他
  if (detail.value.quType === 'radio' || detail.value.quType === 'judge') {
    for (const a of detail.value.answerList) {
      if (a.answerId !== e.answerId) {
        a.checked = false
      }
    }
  }

  // 直接触发保存
  saveAnswer()
}

const markAnswered = (id: string, answered: boolean) => {
  for (const card of cardList.value) {
    const itemList = card.itemList || []
    for (const item of itemList) {
      if (item.quId === id) {
        item.answered = answered
      }
    }
  }
}

const saveAnswer = () => {
  const checkedItems: string[] = []
  for (const item of detail.value.answerList) {
    if (item.checked) {
      checkedItems.push(item.answerId)
    }
  }

  const params = { paperId: paperId, quId: detail.value.quId, checkedItems: checkedItems }

  // Serialize saves so a slow older request cannot overwrite the latest selection.
  saveQueue = saveQueue.then(async () => {
    try {
      const res = await fillAnswerApi(params)
      failedQuestions.delete(params.quId)
      markAnswered(params.quId, res.data.filled)
    } catch {
      failedQuestions.add(params.quId)
      markAnswered(params.quId, false)
      ElMessage.error('答案保存失败，请重新选择该题答案后再交卷。')
    }
  })
}

function scheduleTextSave() {
  if (textTimer) clearTimeout(textTimer)
  saveStates.value[detail.value.quId] = 'pending'
  textTimer = setTimeout(saveTextAnswer, 500)
}
function flushTextSave() {
  if (textTimer) saveTextAnswer()
}
function saveTextAnswer() {
  if (textTimer) clearTimeout(textTimer)
  textTimer = undefined
  const params = { paperId, quId: detail.value.quId, answerText: detail.value.textAnswer || '' }
  if (detail.value.quType !== 'short') return
  saveStates.value[params.quId] = 'saving'
  saveQueue = saveQueue.then(async () => {
    try {
      const response = await fillTextAnswerApi(params)
      failedQuestions.delete(params.quId)
      markAnswered(params.quId, response.data.filled)
      if (detail.value.quId !== params.quId || detail.value.textAnswer === params.answerText)
        saveStates.value[params.quId] = 'saved'
    } catch {
      failedQuestions.add(params.quId)
      saveStates.value[params.quId] = 'failed'
      ElMessage.error('文字答案保存失败，请重新保存后再交卷。')
    }
  })
}
onBeforeRouteLeave(async () => {
  if (allowLeave.value || timeExpired.value) return true
  flushTextSave()
  await saveQueue
  if (failedQuestions.size) {
    ElMessage.error('还有答案保存失败，请重新保存后再离开。')
    return false
  }
  return true
})
function beforeUnload(event: BeforeUnloadEvent) {
  if (Object.values(saveStates.value).some((state) => state !== 'saved')) {
    event.preventDefault()
    event.returnValue = ''
  }
}
onMounted(() => window.addEventListener('beforeunload', beforeUnload))
onBeforeUnmount(() => {
  if (textTimer) clearTimeout(textTimer)
  window.removeEventListener('beforeunload', beforeUnload)
})

// 查找详情
onMounted(() => {
  listCard()
})
</script>

<style scoped>
.answer-card {
  min-height: 70vh;
  height: auto;
}
.text-answer {
  margin-top: 24px;
}
.text-answer label {
  display: block;
  color: #606266;
  margin-bottom: 10px;
}
.text-answer :deep(textarea) {
  font-size: 15px;
  line-height: 1.8;
}
.save-status {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 10px;
  font-size: 12px;
  color: #67a941;
}
.save-status.failed {
  color: #f56c6c;
}
.answer-note {
  color: #909399;
  font-size: 13px;
}

.top-opt-box {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.tag-item {
  cursor: pointer;
  margin: 5px;
}

.el-col {
  margin-bottom: 20px;
}

.answer-item {
  border-radius: 5px;
  border: #ddd 1px solid;
  margin-bottom: 10px;
  padding: 10px 10px;
  display: flex;
  align-items: center;
  cursor: pointer;
}

.checked {
  border: #2d8cf0 2px solid !important;
}
</style>
