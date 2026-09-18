import request from '@/config/axios'

export interface GradingRow {
  id: string
  title: string
  subjectName: string
  subjectType: string
  departName: string
  positionName: string
  sceneType: string
  batchNo: string
  handTime: string
  gradingState: string
  assignmentStatus?: string
  snapshotSource: string
  objectiveScore: number
  subjectiveScore: number
  totalScore: number
  qualifyScore: number
  shortCount: number
  gradedCount: number
  version: number
}
export interface GradingQuestion {
  id: string
  sort: number
  content: string
  referenceAnswer: string
  gradingCriteria: string
  textAnswer: string
  score: number
  actualScore: number
  gradingState: string
  comment: string
  graderName: string
  gradedAt: string
}
export interface GradingLog {
  id: string
  sort: number | null
  action: string
  scoreBefore: number | null
  scoreAfter: number
  commentBefore: string | null
  commentAfter: string | null
  createTime: string
  graderName: string
}
export interface GradingDetail extends GradingRow {
  questions: GradingQuestion[]
  logs: GradingLog[]
}
const post = (path: string, data: unknown) =>
  request.post({ url: `/api/exam/grading/${path}`, data })
export const gradingPagingApi = (data: unknown) => post('paging', data)
export const gradingPositionsApi = () => post('positions', {})
export const gradingDetailApi = (id: string) => post('detail', { id })
export const gradingSaveApi = (data: {
  paperQuId: string
  score: number
  comment: string
  expectedVersion: number
}) => post('question/save', data)
export const gradingFinalizeApi = (data: { paperId: string; expectedVersion: number }) =>
  post('finalize', data)
