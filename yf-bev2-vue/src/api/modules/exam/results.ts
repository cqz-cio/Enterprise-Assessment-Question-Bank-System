import request from '@/config/axios'
import service from '@/config/axios/service'
import { ElMessage } from 'element-plus'
import type { AxiosResponse } from 'axios'

export interface ResultQuery {
  current: number
  size: number
  state: string
  keyword?: string
  subjectType?: string
  departId?: string
  positionId?: string
  sceneType?: string
  batchNo?: string
  title?: string
  passed?: boolean | null
  scoreMin?: number | null
  scoreMax?: number | null
  submittedFrom?: string | null
  submittedTo?: string | null
}
export interface ResultRow {
  id: string
  subjectName: string
  subjectType: string
  subjectNo: string
  departName: string
  positionName: string
  sceneType: string
  batchNo: string
  title: string
  objectiveScore: number
  subjectiveScore: number | null
  userScore: number | null
  totalScore: number
  qualifyScore: number
  passed: boolean | null
  gradingState: string
  gradedCount: number
  handTime: string
  graderName: string
  gradedAt: string
}
export interface ResultCounts {
  total: number
  pending: number
  limit: number
}
export interface ResultPage {
  records: ResultRow[]
  total: number
  all: number
  completed: number
  pending: number
}
export interface ResultOptions {
  departments: { id: string; name: string }[]
  positions: { id: string; name: string }[]
}
export const resultPaging = (data: ResultQuery) =>
  request.post<ResultPage>({ url: '/api/exam/results/paging', data })
export const resultOptions = () => request.post<ResultOptions>({ url: '/api/exam/results/options' })
export const resultDetail = (id: string) =>
  request.post<ResultRow>({ url: '/api/exam/results/detail', data: { id } })
export const resultPreview = (data: ResultQuery) =>
  request.post<ResultCounts>({ url: '/api/exam/results/export-preview', data })
export async function downloadResults(data: ResultQuery) {
  const response = (await service.request({
    url: '/api/exam/results/export',
    method: 'post',
    data,
    responseType: 'blob',
    timeout: 60000
  })) as AxiosResponse<Blob>
  if (!response.data.type.includes('spreadsheetml')) {
    let message = '导出失败，请刷新后重试'
    try {
      message = JSON.parse(await response.data.text()).msg || message
    } catch {
      /* non-JSON server error */
    }
    ElMessage.error(message)
    throw new Error(message)
  }
  const url = URL.createObjectURL(response.data)
  const link = document.createElement('a')
  link.href = url
  link.download = `考核成绩-${new Date().toISOString().slice(0, 10)}.xlsx`
  link.click()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}
