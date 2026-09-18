import request from '@/config/axios'

export interface AssignmentRow {
  id: string
  subjectName?: string
  employeeNo?: string
  examTitle: string
  departName: string
  positionName: string
  sceneType: string
  batchNo: string
  totalTime: number
  validFrom: string
  expireAt: string
  paperDeadline?: string
  paperId?: string
  disabledReason?: string
  status: string
  passed: boolean | number | null
}
export const scenes: Record<string, string> = {
  INTERVIEW: '面试',
  REGULARIZATION: '转正',
  PROMOTION: '晋升'
}
export const statuses: Record<string, string> = {
  UPCOMING: '待开始',
  ASSIGNED: '待考核',
  STARTED: '进行中',
  SETTLING: '结算中',
  PENDING_REVIEW: '待阅卷',
  COMPLETED: '已完成',
  EXPIRED: '已过期',
  DISABLED: '已停用'
}
export const statusType = (status: string) => {
  if (status === 'COMPLETED') return 'success'
  if (['PENDING_REVIEW', 'SETTLING'].includes(status)) return 'warning'
  if (['DISABLED', 'EXPIRED', 'UPCOMING'].includes(status)) return 'info'
  return 'primary'
}
const post = (path: string, data: unknown) =>
  request.post({ url: `/api/exam/assignment/${path}`, data })
export const employeePagingApi = (data: unknown) => post('employee/paging', data)
export const employeeOptionsApi = (data: unknown) => post('employee/options', data)
export const employeeTemplatesApi = (data: unknown) => post('employee/templates', data)
export const employeeCreateApi = (data: unknown) => post('employee/create', data)
export const employeeStatusApi = (data: unknown) => post('employee/change-status', data)
export const employeeResultApi = (data: unknown) => post('employee/result-detail', data)
export const myAssignmentsApi = (data: unknown) => post('my-paging', data)
