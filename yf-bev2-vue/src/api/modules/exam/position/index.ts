import request from '@/config/axios'

export const saveApi = (data: any) =>
  request.post({ url: '/api/exam/position/save', data })

export const detailApi = (data: any) =>
  request.post({ url: '/api/exam/position/detail', data })

export const listEnabledApi = () =>
  request.post({ url: '/api/exam/position/list-enabled' })

export const listByDepartmentApi = (departmentId: string) =>
  request.post({ url: '/api/exam/position/list-by-department', data: { id: departmentId } })

export const changeStatusApi = (data: any) =>
  request.post({ url: '/api/exam/position/change-status', data })
