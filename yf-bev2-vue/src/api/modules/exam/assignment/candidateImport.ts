import service from '@/config/axios/service'

export interface CandidateImportRow {
  rowNumber: number
  values: string[]
  candidateName: string
  candidateNo: string
  departName: string
  positionName: string
  examTitle: string
  batchNo: string
  validFrom: string
  expireAt: string
  status: 'VALID' | 'ERROR' | 'DUPLICATE' | 'SUCCESS'
  message: string
  assignmentId?: string
  accessCode?: string
}
export interface CandidateImportView {
  taskId: string
  fileName: string
  expiresAt: number
  committed: boolean
  totalCount: number
  validCount: number
  successCount: number
  failureCount: number
  duplicateCount: number
  rows: CandidateImportRow[]
}
const call = (action: string, data?: unknown, blob = false) =>
  service.request({
    url: `/api/exam/assignment/candidate/${action}`,
    method: 'POST',
    data,
    timeout: 120000,
    responseType: blob ? 'blob' : 'json'
  }) as Promise<any>

export const previewCandidates = (file: File): Promise<IResponse<CandidateImportView>> => {
  const form = new FormData()
  form.append('file', file)
  return call('import-validate', form)
}
export const commitCandidates = (taskId: string): Promise<IResponse<CandidateImportView>> =>
  call('import', { taskId })
export const closeCandidateImport = (taskId: string) => call('import-close', { taskId })

export async function downloadCandidateImport(
  kind: 'template' | 'error' | 'codes',
  taskId?: string
) {
  const response = await call(`import-${kind}`, taskId ? { taskId } : undefined, true)
  const blob: Blob = response.data
  if (blob.type.includes('json')) {
    const error = JSON.parse(await blob.text())
    throw new Error(error.msg || '下载失败，请重试')
  }
  const names = { template: '候选人导入模板', error: '候选人导入问题行', codes: '候选人考核码清单' }
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${names[kind]}.xlsx`
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
