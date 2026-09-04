import request from '@/config/axios'

export const createCandidateApi = (data: any) =>
  request.post({ url: '/api/exam/assignment/candidate/create', data })

export const verifyCandidateApi = (data: any) =>
  request.post({ url: '/api/exam/assignment/candidate/verify', data })

export const resetCandidateCodeApi = (data: any) =>
  request.post({ url: '/api/exam/assignment/candidate/reset-code', data })

export const candidateResultDetailApi = (data: any) =>
  request.post({ url: '/api/exam/assignment/candidate/result-detail', data })

export const changeAssignmentStatusApi = (data: any) =>
  request.post({ url: '/api/exam/assignment/change-status', data })

export const currentAssignmentApi = (data: any) =>
  request.post({ url: '/api/exam/assignment/current', data })

export const createPaperByAssignmentApi = (data: any) =>
  request.post({ url: '/api/exam/paper/paper/create-by-assignment', data })

export const assignmentResultApi = (data: any) =>
  request.post({ url: '/api/exam/assignment/my-result', data })
