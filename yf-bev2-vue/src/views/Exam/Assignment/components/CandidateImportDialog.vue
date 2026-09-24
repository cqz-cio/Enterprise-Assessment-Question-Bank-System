<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadFile, UploadFiles, UploadInstance } from 'element-plus'
import {
  previewCandidates,
  commitCandidates,
  closeCandidateImport,
  restoreCandidateImport,
  downloadCandidateImport
} from '@/api/modules/exam/assignment/candidateImport'
import type { CandidateImportView } from '@/api/modules/exam/assignment/candidateImport'

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{ (e: 'update:visible', value: boolean): void; (e: 'imported'): void }>()
const upload = ref<UploadInstance>()
const file = ref<File>()
const step = ref(0)
const busy = ref(false)
const view = ref<CandidateImportView>()
const filter = ref('ALL')
const error = ref('')
const saved = ref(false)
const uncertain = ref(false)
const columns = [
  '姓名',
  '候选人编号',
  '手机号',
  '邮箱',
  '部门编码',
  '岗位编码',
  '批次',
  '生效时间',
  '截止时间'
]
const labels = { VALID: '可导入', ERROR: '错误', DUPLICATE: '重复', SUCCESS: '已发放' }
const types = {
  VALID: 'success',
  ERROR: 'danger',
  DUPLICATE: 'warning',
  SUCCESS: 'success'
} as const
const rows = computed(() =>
  (view.value?.rows || []).filter((r) =>
    step.value === 2 ? r.status === 'SUCCESS' : filter.value === 'ALL' || r.status === filter.value
  )
)
const entry = `${window.location.origin}/#/exam-entry`
const unsaved = computed(() => uncertain.value)
async function allowLeave() {
  if (busy.value) return false
  if (!unsaved.value) return true
  try {
    await ElMessageBox.confirm(
      '导入响应尚未确认，部分记录可能已发放。结果已保存，建议先重试获取本次结果。确认离开？',
      '保存发放清单',
      { type: 'warning', confirmButtonText: '确认离开', cancelButtonText: '继续保存' }
    )
    return true
  } catch {
    return false
  }
}
async function release() {
  if (view.value) await closeCandidateImport(view.value.taskId).catch(() => undefined)
  view.value = undefined
}
async function close() {
  if (!(await allowLeave())) return
  await release()
  emit('update:visible', false)
}
async function again() {
  if (!(await allowLeave())) return
  await release()
  reset()
}
function reset() {
  step.value = 0
  file.value = undefined
  view.value = undefined
  filter.value = 'ALL'
  error.value = ''
  saved.value = false
  uncertain.value = false
  upload.value?.clearFiles()
}
watch(
  () => props.visible,
  async (visible) => {
    if (!visible) return
    reset()
    busy.value = true
    try {
      const restored = (await restoreCandidateImport()).data
      if (restored) {
        view.value = restored
        step.value = restored.committed ? 2 : 1
      }
    } catch (e: any) {
      error.value = e.message || '历史清单恢复失败，请稍后重新打开或上传原文件重试'
    } finally {
      busy.value = false
    }
  }
)
function choose(item: UploadFile, files: UploadFiles) {
  error.value = ''
  const selected = item.raw
  if (
    !selected ||
    !selected.name.toLowerCase().endsWith('.xlsx') ||
    selected.size > 5 * 1024 * 1024
  ) {
    error.value = '请选择不超过 5 MB 的 .xlsx 文件'
    file.value = undefined
    upload.value?.clearFiles()
    return
  }
  file.value = selected
  if (files.length > 1) files.splice(0, files.length - 1)
}
async function validate() {
  if (!file.value) return
  busy.value = true
  error.value = ''
  try {
    view.value = (await previewCandidates(file.value)).data
    step.value = view.value.committed ? 2 : 1
    filter.value = 'ALL'
  } catch (e: any) {
    error.value = e.message || '校验失败，请检查文件后重试'
  } finally {
    busy.value = false
  }
}
async function commit() {
  if (!view.value || !view.value.validCount) return
  busy.value = true
  error.value = ''
  try {
    view.value = (await commitCandidates(view.value.taskId)).data
    uncertain.value = false
    step.value = 2
    if (view.value.successCount) emit('imported')
  } catch (e: any) {
    uncertain.value = true
    error.value = `${e.message || '请求失败'}。请重试获取本次结果；重复提交不会重复发放。`
  } finally {
    busy.value = false
  }
}
async function download(kind: 'template' | 'error' | 'codes') {
  busy.value = true
  try {
    await downloadCandidateImport(kind, view.value?.taskId)
    if (kind === 'codes') saved.value = true
  } catch (e: any) {
    ElMessage.error(e.message || '下载失败')
  } finally {
    busy.value = false
  }
}
function unload(event: BeforeUnloadEvent) {
  if (props.visible && (busy.value || unsaved.value)) {
    event.preventDefault()
    event.returnValue = ''
  }
}
window.addEventListener('beforeunload', unload)
onBeforeUnmount(() => window.removeEventListener('beforeunload', unload))
onBeforeRouteLeave(async () => {
  if (!props.visible) return true
  if (!(await allowLeave())) return false
  await release()
  return true
})
</script>

<template>
  <el-dialog
    :model-value="visible"
    title="候选人 Excel 批量导入"
    width="1120px"
    top="4vh"
    class="candidate-import-dialog"
    :close-on-click-modal="false"
    :close-on-press-escape="!busy"
    :before-close="close"
    destroy-on-close
  >
    <div class="import-body" v-loading="busy">
      <div class="steps" aria-label="导入进度">
        <template v-for="(title, index) in ['上传文件', '校验预览', '导入结果']" :key="title">
          <span v-if="index" class="step-line"></span>
          <div
            class="step"
            :class="{ active: step === index, done: step > index }"
            :aria-current="step === index ? 'step' : undefined"
            ><b>{{ step > index ? '✓' : index + 1 }}</b
            >{{ title }}</div
          >
        </template>
      </div>
      <el-alert
        v-if="error"
        :title="error"
        type="error"
        :closable="false"
        show-icon
        class="notice"
      />
      <template v-if="step === 0">
        <section class="section">
          <div class="section-head"
            ><strong>使用标准模板填写候选人</strong
            ><el-button :disabled="busy" @click="download('template')"
              >下载 Excel 模板</el-button
            ></div
          >
          <div class="columns"
            ><span v-for="name in columns" :key="name"><b>*</b> {{ name }}</span></div
          >
          <el-alert
            title="模板附填写说明及当前部门 / 岗位编码参考。按部门和岗位自动匹配已启用的面试测评，无需填写测评名称。"
            type="info"
            :closable="false"
          />
          <p class="muted"
            >时间格式：2026-09-21 09:00:00；建议有效期为 14
            天，可按实际安排修改。编号和手机号请按文本填写。</p
          >
        </section>
        <section class="section">
          <strong>上传待导入文件</strong>
          <el-upload
            ref="upload"
            drag
            action="#"
            accept=".xlsx"
            :auto-upload="false"
            :disabled="busy"
            :on-change="choose"
            :on-remove="() => (file = undefined)"
            class="upload"
          >
            <div class="upload-icon">⇧</div><div>将文件拖到此处，或 <em>选择文件</em></div>
            <div class="muted">支持 .xlsx，单次最多 500 行、5 MB</div>
          </el-upload>
          <p>重复规则：同一候选人编号 + 同一批次重复时跳过，不覆盖已有考核。</p>
        </section>
        <el-alert
          title="校验不会创建候选人或生成考核码。下一步核对有效行后，再确认导入并发放。"
          type="warning"
          :closable="false"
        />
      </template>
      <template v-else-if="view">
        <div v-if="step === 1" class="file"
          ><span class="file-icon">XLSX</span
          ><div>{{ view.fileName }}<div class="muted">校验完成 · 下表保留 Excel 原始行号</div></div
          ><el-button link :disabled="busy || uncertain" @click="again"
            >重新选择文件</el-button
          ></div
        >
        <div v-else class="success-head"
          ><span class="check">✓</span
          ><div
            ><strong>导入完成，已发放 {{ view.successCount }} 份面试测评</strong
            ><div class="muted">有效记录已创建，错误行与重复行未新增考核。</div></div
          ></div
        >
        <div class="stats">
          <div
            ><b>{{ view.totalCount }}</b
            ><span>{{ step === 1 ? '读取行数' : '总行数' }}</span></div
          >
          <div class="ok"
            ><b>{{ step === 1 ? view.validCount : view.successCount }}</b
            ><span>{{ step === 1 ? '可导入' : '成功发放' }}</span></div
          >
          <div class="bad"
            ><b>{{ view.failureCount }}</b
            ><span>{{ step === 1 ? '错误行' : '失败' }}</span></div
          >
          <div class="skip"
            ><b>{{ view.duplicateCount }}</b
            ><span>{{ step === 1 ? '重复行' : '重复跳过' }}</span></div
          >
        </div>
        <template v-if="step === 1">
          <div class="tabs"
            ><el-radio-group v-model="filter"
              ><el-radio-button value="ALL" label="ALL"
                >全部 ({{ view.totalCount }})</el-radio-button
              ><el-radio-button value="VALID" label="VALID"
                >可导入 ({{ view.validCount }})</el-radio-button
              ><el-radio-button value="ERROR" label="ERROR"
                >错误 ({{ view.failureCount }})</el-radio-button
              ><el-radio-button value="DUPLICATE" label="DUPLICATE"
                >重复 ({{ view.duplicateCount }})</el-radio-button
              ></el-radio-group
            ><el-button
              link
              type="primary"
              :disabled="busy || (!view.failureCount && !view.duplicateCount)"
              @click="download('error')"
              >下载问题行报告</el-button
            ></div
          >
        </template>
        <template v-else>
          <el-alert
            title="发放清单加密保留 30 天，重新打开可恢复最近一次结果；上传原文件可找回对应清单。已重置、停用或过期的考核码不再提供，请安全保管下载文件。"
            type="warning"
            :closable="false"
          />
          <div class="section-head credentials"
            ><strong>本次发放清单 <small class="muted">仅显示成功记录</small></strong
            ><el-button
              type="primary"
              :disabled="busy || !view.successCount"
              @click="download('codes')"
              >下载发放清单（含考核码）</el-button
            ></div
          >
          <div class="entry"
            >统一入口：{{ entry }}　<span class="muted">候选人使用姓名 + 6 位考核码进入</span></div
          >
        </template>
        <el-table :data="rows" stripe max-height="360" class="import-table">
          <el-table-column v-if="step === 1" prop="rowNumber" label="行号" width="58" />
          <el-table-column label="候选人 / 编号" min-width="145"
            ><template #default="{ row }"
              >{{ row.candidateName }}<div class="muted">{{ row.candidateNo }}</div></template
            ></el-table-column
          >
          <el-table-column label="部门 / 岗位" min-width="135"
            ><template #default="{ row }"
              >{{ row.departName || row.values[4]
              }}<div class="muted">{{ row.positionName || row.values[5] }}</div></template
            ></el-table-column
          >
          <el-table-column label="匹配测评 / 批次" min-width="185"
            ><template #default="{ row }"
              >{{ row.examTitle || '—' }}<div class="muted">{{ row.batchNo }}</div></template
            ></el-table-column
          >
          <el-table-column label="有效期" min-width="165"
            ><template #default="{ row }"
              ><div class="muted"
                >{{ row.validFrom }} 生效<br />{{ row.expireAt }} 截止</div
              ></template
            ></el-table-column
          >
          <el-table-column v-if="step === 1" label="校验状态" width="95"
            ><template #default="{ row }"
              ><el-tag :type="types[row.status]">{{ labels[row.status] }}</el-tag></template
            ></el-table-column
          >
          <el-table-column v-if="step === 1" prop="message" label="说明" min-width="175"
            ><template #default="{ row }"
              ><span :class="{ bad: row.status === 'ERROR', skip: row.status === 'DUPLICATE' }">{{
                row.message
              }}</span></template
            ></el-table-column
          >
          <el-table-column v-else label="考核码" width="120"
            ><template #default="{ row }"
              ><span class="code" :title="row.message">{{
                row.accessCode || '已失效'
              }}</span></template
            ></el-table-column
          >
        </el-table>
        <el-alert
          v-if="step === 1"
          :title="`只导入 ${view.validCount} 条有效记录；错误和重复行会跳过。确认时会再次检查最新状态，最终结果以实际导入为准。`"
          type="warning"
          :closable="false"
          class="notice"
        />
        <div v-else class="section-head credentials"
          ><span class="muted">问题行可修改后重新上传，已存在的同批次记录将跳过。</span
          ><el-button
            link
            type="primary"
            :disabled="busy || (!view.failureCount && !view.duplicateCount)"
            @click="download('error')"
            >下载问题行报告（{{ view.failureCount + view.duplicateCount }} 行）</el-button
          ></div
        >
      </template>
    </div>
    <template #footer
      ><div class="footer"
        ><span class="muted">{{
          step === 0
            ? '先检查文件，校验不会创建数据'
            : step === 1
              ? `预计新增 ${view?.validCount || 0} 名候选人，生成对应考核码`
              : saved
                ? '清单已下载，请安全保管'
                : '清单已加密保存，30 天内可恢复下载'
        }}</span
        ><div>
          <template v-if="step === 0"
            ><el-button :disabled="busy" @click="close">取消</el-button
            ><el-button type="primary" :loading="busy" :disabled="!file" @click="validate"
              >开始校验</el-button
            ></template
          >
          <template v-else-if="step === 1"
            ><el-button :disabled="busy || uncertain" @click="again">上一步</el-button
            ><el-button
              type="primary"
              :loading="busy"
              :disabled="!view?.validCount"
              @click="commit"
              >{{
                uncertain ? '重试获取本次结果' : `确认导入 ${view?.validCount || 0} 条并发放`
              }}</el-button
            ></template
          >
          <template v-else
            ><el-button :disabled="busy" @click="again">继续导入</el-button
            ><el-button type="primary" :disabled="busy" @click="close">完成</el-button></template
          >
        </div></div
      ></template
    >
  </el-dialog>
</template>

<style scoped>
.steps {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18px;
  padding: 8px 0;
}
.step {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--el-text-color-placeholder);
  white-space: nowrap;
}
.step b {
  border: 1px solid currentColor;
  border-radius: 50%;
  width: 25px;
  height: 25px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}
.step.active {
  color: var(--el-color-primary);
  font-weight: 600;
}
.step.active b {
  background: var(--el-color-primary-light-9);
}
.step.done {
  color: var(--el-color-success);
}
.step-line {
  flex: 1;
  max-width: 100px;
  height: 1px;
  background: var(--el-border-color);
}
.import-body {
  max-height: calc(92vh - 140px);
  overflow: auto;
  padding: 0 4px;
}
.steps {
  max-width: 650px;
  margin: 0 auto 24px;
}
.section {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 18px;
  margin-bottom: 16px;
}
.section-head,
.footer,
.file,
.success-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.columns {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 14px;
  margin: 16px 0;
}
.columns b,
.bad {
  color: var(--el-color-danger);
}
.muted {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.8;
}
.upload {
  margin-top: 16px;
}
.upload-icon {
  font-size: 36px;
  color: var(--el-color-primary-light-5);
}
em {
  color: var(--el-color-primary);
  font-style: normal;
}
.file {
  justify-content: flex-start;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 12px 16px;
}
.file .el-button {
  margin-left: auto;
}
.file-icon {
  padding: 8px;
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
  border-radius: 4px;
}
.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  margin: 17px 0;
}
.stats > div {
  padding: 12px 20px;
  border-right: 1px solid var(--el-border-color-lighter);
}
.stats > div:last-child {
  border: 0;
}
.stats b {
  font-size: 26px;
  margin-right: 10px;
}
.stats span {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.ok {
  color: var(--el-color-success);
}
.skip {
  color: var(--el-color-warning);
}
.tabs {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  gap: 10px;
}
.tabs :deep(.el-radio-button__inner) {
  border: 0 !important;
  background: none !important;
  box-shadow: none !important;
  border-radius: 0 !important;
  padding: 12px;
}
.tabs :deep(.is-active .el-radio-button__inner) {
  color: var(--el-color-primary) !important;
  border-bottom: 2px solid var(--el-color-primary) !important;
}
.success-head {
  justify-content: flex-start;
}
.success-head strong {
  font-size: 18px;
  font-weight: 500;
}
.check {
  font-size: 26px;
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
  border-radius: 50%;
  padding: 0 13px;
}
.credentials {
  margin: 20px 0 12px;
}
.entry {
  padding: 10px 14px;
  background: var(--el-fill-color-light);
  margin-bottom: 12px;
}
.code {
  font-family: Consolas, monospace;
  letter-spacing: 2px;
  color: var(--el-color-primary);
  font-weight: 600;
}
.notice {
  margin: 15px 0;
}
.footer {
  text-align: left;
}
.footer > div {
  white-space: nowrap;
}
.import-table :deep(th.el-table__cell) {
  background: var(--el-fill-color-lighter);
}
@media (max-width: 800px) {
  .columns {
    grid-template-columns: repeat(3, 1fr);
  }
  .footer,
  .tabs {
    flex-wrap: wrap;
  }
}
</style>
