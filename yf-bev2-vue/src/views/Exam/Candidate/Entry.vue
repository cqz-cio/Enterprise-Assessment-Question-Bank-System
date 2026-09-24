<template>
  <div class="candidate-page">
    <div class="entry-card">
      <header class="brand">
        <img :src="BRAND_LOGO" alt="TRIPEER" class="brand-logo brand-logo--light" />
        <img :src="BRAND_LOGO_LIGHT" alt="TRIPEER" class="brand-logo brand-logo--dark" />
        <div class="company-name">宁波全品轩国际贸易有限公司</div>
        <div class="system-name">企业人才测评中心</div>
      </header>
      <template v-if="!assignment">
        <h1>候选人测评入口</h1><p class="subtitle">请输入 HR 提供的姓名与 6 位测评口令</p>
        <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="verify">
          <el-form-item prop="name"
            ><el-input v-model.trim="form.name" placeholder="姓名" maxlength="64"
          /></el-form-item>
          <el-form-item prop="accessCode"
            ><el-input
              v-model.trim="form.accessCode"
              placeholder="6 位测评口令"
              maxlength="6"
              class="code-input"
          /></el-form-item>
          <el-button type="primary" size="large" class="full" :loading="loading" @click="verify"
            >验证并进入</el-button
          >
        </el-form>
      </template>
      <template v-else>
        <el-result
          icon="success"
          title="身份验证成功"
          :sub-title="`${assignment.candidateName || assignment.subjectName} · ${assignment.positionName}`"
        />
        <div class="detail"
          ><span>测评名称</span><strong>{{ assignment.examTitle }}</strong
          ><span>截止时间</span><strong>{{ assignment.expireAt }}</strong></div
        >
        <el-alert
          v-if="
            ['COMPLETED', 'PENDING_REVIEW'].includes(
              assignment.assignmentStatus || assignment.status
            )
          "
          title="本次测评已完成，不能重复作答"
          type="success"
          :closable="false"
        />
        <el-button
          v-if="
            !['COMPLETED', 'PENDING_REVIEW'].includes(
              assignment.assignmentStatus || assignment.status
            )
          "
          type="primary"
          size="large"
          class="full"
          :loading="starting"
          @click="start"
          >{{ assignment.paperId ? '继续测评' : '开始测评' }}</el-button
        >
        <el-button v-else type="primary" plain size="large" class="full" @click="viewResult"
          >查看结果</el-button
        >
        <el-button link class="full reset" @click="reset">使用其他身份</el-button>
      </template>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import {
  verifyCandidateApi,
  currentAssignmentApi,
  createPaperByAssignmentApi
} from '@/api/modules/exam/assignment'
import { useUserStore } from '@/store/modules/user'
import { useStorage } from '@/hooks/web/useStorage'
import { BRAND_LOGO, BRAND_LOGO_LIGHT } from '@/utils/branding'

const router = useRouter()
const userStore = useUserStore()
const storage = useStorage()
const formRef = ref<FormInstance>()
const loading = ref(false)
const starting = ref(false)
const form = reactive({ name: '', accessCode: '' })
const assignment = ref<any>(storage.getStorage('candidateAssignment') || null)
const rules: FormRules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  accessCode: [
    { required: true, len: 6, message: '请输入 6 位测评口令', trigger: 'blur' },
    { pattern: /^[A-Z0-9]{6}$/, message: '口令仅包含大写字母和数字', trigger: 'blur' }
  ]
}
async function verify() {
  if (!(await formRef.value?.validate())) return
  loading.value = true
  try {
    const res = await verifyCandidateApi({
      candidateName: form.name,
      accessCode: form.accessCode.toUpperCase()
    })
    userStore.setCandidateSession(res.data)
    assignment.value = res.data
    if (['COMPLETED', 'PENDING_REVIEW'].includes(res.data.assignmentStatus)) viewResult()
  } finally {
    loading.value = false
  }
}
async function start() {
  starting.value = true
  try {
    const assignmentId = assignment.value.assignmentId
    const current = (await currentAssignmentApi({ id: assignmentId })).data
    assignment.value = { ...assignment.value, ...current, assignmentStatus: current.status }
    storage.setStorage('candidateAssignment', assignment.value)
    if (['COMPLETED', 'PENDING_REVIEW'].includes(current.status)) {
      viewResult()
      return
    }
    const res = await createPaperByAssignmentApi({ id: assignmentId })
    await router.push({ name: 'CandidateExamEnter', query: { id: res.data.paperId, assignmentId } })
  } catch (e) {
    ElMessage.error('暂时无法进入测评，请联系 HR')
  } finally {
    starting.value = false
  }
}
function viewResult() {
  router.push({
    name: 'CandidateExamResult',
    query: { assignmentId: assignment.value.assignmentId }
  })
}
function reset() {
  storage.removeStorage('token')
  storage.removeStorage('userInfo')
  storage.removeStorage('candidateAssignment')
  assignment.value = null
  form.name = ''
  form.accessCode = ''
}
onMounted(async () => {
  if (!assignment.value?.assignmentId) return
  starting.value = true
  try {
    const current = (await currentAssignmentApi({ id: assignment.value.assignmentId })).data
    assignment.value = { ...assignment.value, ...current, assignmentStatus: current.status }
    storage.setStorage('candidateAssignment', assignment.value)
  } catch {
    reset()
  } finally {
    starting.value = false
  }
})
</script>
<style scoped>
.candidate-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background: linear-gradient(135deg, #eef4ff, #f8fafc);
}
.entry-card {
  width: min(460px, 100%);
  padding: 38px 42px 42px;
  background: #fff;
  border: 1px solid rgba(226, 232, 240, 0.75);
  border-radius: 18px;
  box-shadow: 0 22px 70px rgba(32, 64, 128, 0.14);
}
.brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-bottom: 27px;
  margin-bottom: 27px;
  border-bottom: 1px solid #edf0f5;
}
.brand-logo {
  display: block;
  width: 176px;
  max-width: 100%;
  height: auto;
  margin-bottom: 15px;
}
.brand-logo--dark {
  display: none;
}
.dark .brand-logo--light {
  display: none;
}
.dark .brand-logo--dark {
  display: block;
}
.dark .candidate-page {
  background: var(--el-bg-color-page);
}
.dark .entry-card {
  background: var(--el-bg-color-overlay);
  border-color: var(--el-border-color-light);
}
.dark .brand {
  border-color: var(--el-border-color-light);
}
.dark .company-name,
.dark .detail strong {
  color: var(--el-text-color-primary);
}
.dark .detail {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
}
.company-name {
  color: #303640;
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 0.06em;
  line-height: 1.5;
  text-align: center;
}
.system-name {
  margin-top: 6px;
  color: var(--el-color-primary);
  font-size: 13px;
  letter-spacing: 0.14em;
}
h1 {
  color: var(--el-text-color-primary);
  font-size: 28px;
  margin: 0 0 10px;
}
.subtitle {
  color: #909399;
  margin: 0 0 28px;
}
.full {
  width: 100%;
  margin-top: 12px;
}
.reset {
  margin-left: 0;
}
.detail {
  display: grid;
  grid-template-columns: 90px 1fr;
  gap: 12px;
  padding: 18px;
  margin-bottom: 20px;
  background: #f7f9fc;
  border-radius: 10px;
  color: #606266;
}
.detail strong {
  color: #303133;
}
.code-input :deep(input) {
  letter-spacing: 6px;
  text-transform: uppercase;
}
</style>
