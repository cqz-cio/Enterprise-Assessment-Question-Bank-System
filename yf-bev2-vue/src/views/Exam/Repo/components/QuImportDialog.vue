<script lang="ts" setup>
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadFile, UploadFiles, UploadInstance } from 'element-plus'
import {
  downloadImportErrorReportApi,
  downloadImportTemplateApi,
  importQuestionsApi,
  validateImportApi
} from '@/api/modules/exam/qu'
import { detailApi as repoDetailApi } from '@/api/modules/exam/repo'
import type {
  QuestionImportIssueType,
  QuestionImportPreviewType,
  QuestionImportResultType
} from '@/views/Exam/Repo/types'

const props = defineProps<{ visible: boolean; repoId?: string | number }>()
const emit = defineEmits<{
  (event: 'update:visible', value: boolean): void
  (event: 'imported'): void
}>()

const uploadRef = ref<UploadInstance>()
const activeStep = ref(0)
const selectedFile = ref<File>()
const selectedFileName = ref('')
const repoTitle = ref('')
const loading = ref(false)
const downloading = ref(false)
const preview = ref<QuestionImportPreviewType>()
const result = ref<QuestionImportResultType>()
const exampleVisible = ref(false)

const canImport = computed(() => (preview.value?.validCount || 0) > 0)
const issueRows = computed<QuestionImportIssueType[]>(() => preview.value?.issues || [])

const resetState = () => {
  activeStep.value = 0
  selectedFile.value = undefined
  selectedFileName.value = ''
  preview.value = undefined
  result.value = undefined
  uploadRef.value?.clearFiles()
}

watch(
  () => props.visible,
  async (visible) => {
    if (!visible) return
    resetState()
    if (!props.repoId) return
    try {
      const response = await repoDetailApi({ id: props.repoId })
      repoTitle.value = response.data?.title || `题库 ${props.repoId}`
    } catch {
      repoTitle.value = `题库 ${props.repoId}`
    }
  }
)

const closeDialog = () => {
  if (!loading.value) emit('update:visible', false)
}

const beforeClose = (done: () => void) => {
  if (!loading.value) done()
}

const handleFileChange = (uploadFile: UploadFile, uploadFiles: UploadFiles) => {
  const file = uploadFile.raw
  if (!file) return
  if (!file.name.toLowerCase().endsWith('.xlsx')) {
    ElMessage.error('仅支持 .xlsx 格式文件')
    uploadRef.value?.clearFiles()
    selectedFile.value = undefined
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.error('文件不能超过 10MB')
    uploadRef.value?.clearFiles()
    selectedFile.value = undefined
    return
  }
  selectedFile.value = file
  selectedFileName.value = file.name
  if (uploadFiles.length > 1) uploadFiles.splice(0, uploadFiles.length - 1)
}

const handleFileRemove = () => {
  selectedFile.value = undefined
  selectedFileName.value = ''
}

const buildFormData = () => {
  const formData = new FormData()
  if (selectedFile.value) formData.append('file', selectedFile.value)
  formData.append('repoId', String(props.repoId || ''))
  return formData
}

const validateFile = async () => {
  if (!props.repoId) return ElMessage.warning('请先选择要导入的目标题库')
  if (!selectedFile.value) return ElMessage.warning('请先选择 Excel 文件')
  loading.value = true
  try {
    preview.value = (await validateImportApi(buildFormData())).data
    activeStep.value = 1
  } finally {
    loading.value = false
  }
}

const confirmImport = async () => {
  if (!canImport.value || !selectedFile.value) return
  try {
    await ElMessageBox.confirm(
      `确认导入 ${preview.value?.validCount || 0} 道有效试题？重复题和错误行会自动跳过。`,
      '确认导入',
      { type: 'warning', confirmButtonText: '确认导入', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  loading.value = true
  try {
    result.value = (await importQuestionsApi(buildFormData())).data
    activeStep.value = 2
    if ((result.value?.successCount || 0) > 0) emit('imported')
  } finally {
    loading.value = false
  }
}

const downloadBlob = (response: any, fallbackName: string) => {
  const blob = response.data instanceof Blob ? response.data : new Blob([response.data])
  const disposition = response.headers?.['content-disposition'] || ''
  const matched = disposition.match(/filename\*=UTF-8''([^;]+)/i)
  const fileName = matched ? decodeURIComponent(matched[1]) : fallbackName
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fileName
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}

const downloadTemplate = async () => {
  downloading.value = true
  try {
    downloadBlob(await downloadImportTemplateApi(), '试题导入模板.xlsx')
  } finally {
    downloading.value = false
  }
}

const downloadErrorReport = async () => {
  if (!selectedFile.value) return
  downloading.value = true
  try {
    downloadBlob(await downloadImportErrorReportApi(buildFormData()), '试题导入错误报告.xlsx')
  } finally {
    downloading.value = false
  }
}

const backToUpload = () => {
  activeStep.value = 0
  preview.value = undefined
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    title="Excel 批量导入试题"
    width="920px"
    :close-on-click-modal="false"
    :before-close="beforeClose"
    destroy-on-close
    @close="emit('update:visible', false)"
  >
    <el-steps :active="activeStep" finish-status="success" align-center class="import-steps">
      <el-step title="上传文件" description="选择模板文件" />
      <el-step title="校验确认" description="检查有效、重复和错误行" />
      <el-step title="导入完成" description="查看处理结果" />
    </el-steps>

    <div v-if="activeStep === 0" class="step-panel">
      <section class="target-card">
        <div>
          <div class="section-label">导入到</div>
          <div class="repo-title">{{ repoTitle || '正在读取题库…' }}</div>
        </div>
        <el-tag type="success">导入后默认启用</el-tag>
      </section>

      <section class="template-card">
        <div>
          <div class="section-title">先下载标准模板</div>
          <div class="section-desc">
            一个模板支持单选题、多选题、判断题和简答题，内含填写说明、示例和字段字典。
          </div>
        </div>
        <div class="template-actions">
          <el-button :loading="downloading" @click="downloadTemplate">下载 Excel 模板</el-button>
          <el-button link type="primary" @click="exampleVisible = true">查看填写示例</el-button>
        </div>
      </section>

      <el-upload
        ref="uploadRef"
        drag
        action="#"
        accept=".xlsx"
        :auto-upload="false"
        :limit="1"
        :on-change="handleFileChange"
        :on-remove="handleFileRemove"
      >
        <div class="upload-icon">XLSX</div>
        <div class="el-upload__text">拖拽文件到这里，或 <em>点击选择文件</em></div>
        <template #tip>
          <div class="el-upload__tip">仅支持 .xlsx，单次最多 1000 道题，文件不超过 10MB</div>
        </template>
      </el-upload>

      <el-alert
        class="import-alert"
        title="重复题将跳过，不覆盖已有内容；有效行会继续导入，错误行可下载报告修改后重试。"
        type="info"
        :closable="false"
        show-icon
      />
    </div>

    <div v-else-if="activeStep === 1" class="step-panel">
      <div class="summary-grid">
        <div class="summary-card">
          <span>读取总数</span><strong>{{ preview?.totalCount || 0 }}</strong>
        </div>
        <div class="summary-card success">
          <span>可导入</span><strong>{{ preview?.validCount || 0 }}</strong>
        </div>
        <div class="summary-card warning">
          <span>重复跳过</span><strong>{{ preview?.duplicateCount || 0 }}</strong>
        </div>
        <div class="summary-card danger">
          <span>格式错误</span><strong>{{ preview?.failureCount || 0 }}</strong>
        </div>
      </div>

      <div class="review-header">
        <div>
          <div class="section-title">校验结果</div>
          <div class="section-desc">
            文件：{{ selectedFileName }} · 模板版本 {{ preview?.templateVersion }}
          </div>
        </div>
        <el-button
          v-if="(preview?.duplicateCount || 0) + (preview?.failureCount || 0) > 0"
          :loading="downloading"
          @click="downloadErrorReport"
        >
          下载错误报告
        </el-button>
      </div>

      <el-table v-if="issueRows.length" :data="issueRows" border height="310">
        <el-table-column prop="rowNumber" label="行号" width="72" align="center" />
        <el-table-column prop="questionCode" label="题目编号" width="120" show-overflow-tooltip />
        <el-table-column prop="content" label="题干" min-width="190" show-overflow-tooltip />
        <el-table-column prop="field" label="字段" width="110" />
        <el-table-column prop="message" label="说明" min-width="230" show-overflow-tooltip />
        <el-table-column label="处理" width="92" align="center">
          <template #default="{ row }">
            <el-tag :type="row.issueType === 'DUPLICATE' ? 'warning' : 'danger'">
              {{ row.issueType === 'DUPLICATE' ? '跳过' : '不导入' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="全部校验通过，可以直接导入" :image-size="82" />
    </div>

    <div v-else class="result-panel">
      <el-result
        icon="success"
        title="导入处理完成"
        :sub-title="`已成功导入 ${result?.successCount || 0} 道试题`"
      >
        <template #extra>
          <div class="result-meta">
            重复跳过 {{ result?.duplicateCount || 0 }} 道，格式错误
            {{ result?.failureCount || 0 }} 道
          </div>
        </template>
      </el-result>
    </div>

    <template #footer>
      <div v-if="activeStep === 0">
        <el-button @click="closeDialog">取消</el-button>
        <el-button
          type="primary"
          :loading="loading"
          :disabled="!selectedFile"
          @click="validateFile"
        >
          上传并校验
        </el-button>
      </div>
      <div v-else-if="activeStep === 1">
        <el-button :disabled="loading" @click="backToUpload">重新选择</el-button>
        <el-button type="primary" :loading="loading" :disabled="!canImport" @click="confirmImport">
          确认导入 {{ preview?.validCount || 0 }} 道
        </el-button>
      </div>
      <div v-else>
        <el-button @click="resetState">继续导入</el-button>
        <el-button type="primary" @click="closeDialog">完成</el-button>
      </div>
    </template>
  </el-dialog>

  <el-dialog v-model="exampleVisible" title="填写示例" width="760px" append-to-body>
    <el-table
      :data="[
        { type: '单选题', options: 'A-D 连续填写', answer: 'A', extra: '解析可选' },
        { type: '多选题', options: 'A-F 连续填写', answer: 'A,C,D', extra: '使用英文逗号分隔' },
        { type: '判断题', options: '不填写', answer: '正确', extra: '也可填写 true/false' },
        { type: '简答题', options: '不填写', answer: '不填写', extra: '参考答案、评分要点可选' }
      ]"
      border
    >
      <el-table-column prop="type" label="题型" width="100" />
      <el-table-column prop="options" label="选项填写" width="160" />
      <el-table-column prop="answer" label="正确答案" width="160" />
      <el-table-column prop="extra" label="补充说明" />
    </el-table>
    <el-alert
      class="example-alert"
      title="以下载模板中的说明和示例工作表为准。不要修改表头、合并数据区单元格或使用公式。"
      type="warning"
      :closable="false"
      show-icon
    />
  </el-dialog>
</template>

<style scoped>
.import-steps {
  margin: 4px 20px 28px;
}
.step-panel {
  min-height: 410px;
}
.target-card,
.template-card,
.review-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}
.target-card {
  padding: 15px 18px;
  margin-bottom: 14px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
}
.template-card {
  padding: 17px 18px;
  margin-bottom: 18px;
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: 8px;
  background: var(--el-color-primary-light-9);
}
.section-label,
.section-desc {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.repo-title,
.section-title {
  margin-top: 4px;
  color: var(--el-text-color-primary);
  font-weight: 600;
}
.template-actions {
  display: flex;
  align-items: center;
  white-space: nowrap;
}
.upload-icon {
  display: inline-flex;
  width: 64px;
  height: 50px;
  align-items: center;
  justify-content: center;
  margin-bottom: 8px;
  border-radius: 7px;
  background: var(--el-color-success-light-8);
  color: var(--el-color-success);
  font-weight: 700;
}
.import-alert,
.example-alert {
  margin-top: 18px;
}
.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 20px;
}
.summary-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 15px 17px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
}
.summary-card span {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.summary-card strong {
  font-size: 25px;
}
.summary-card.success strong {
  color: var(--el-color-success);
}
.summary-card.warning strong {
  color: var(--el-color-warning);
}
.summary-card.danger strong {
  color: var(--el-color-danger);
}
.review-header {
  margin-bottom: 12px;
}
.result-panel {
  min-height: 410px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.result-meta {
  color: var(--el-text-color-secondary);
}
@media (max-width: 768px) {
  .summary-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .template-card {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
