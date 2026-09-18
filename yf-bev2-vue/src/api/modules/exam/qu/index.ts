import request from '@/config/axios'

export const saveApi = (data: any) => {
  return request.post({
    url: '/api/exam/repo/qu/save',
    data
  })
}

export const detailApi = (data: any) => {
  return request.post({
    url: '/api/exam/repo/qu/detail',
    data
  })
}

export const validateImportApi = (data: FormData) => {
  return request.post({
    url: '/api/exam/repo/qu/import/validate',
    data,
    headersType: 'multipart/form-data'
  })
}

export const importQuestionsApi = (data: FormData) => {
  return request.post({
    url: '/api/exam/repo/qu/import',
    data,
    headersType: 'multipart/form-data'
  })
}

export const downloadImportTemplateApi = () => {
  return request.get({
    url: '/api/exam/repo/qu/import-template',
    responseType: 'blob'
  })
}

export const downloadImportErrorReportApi = (data: FormData) => {
  return request.post({
    url: '/api/exam/repo/qu/import-error-report',
    data,
    headersType: 'multipart/form-data',
    responseType: 'blob'
  })
}

export const validateWordImportApi = (data: FormData) =>
  request.post({
    url: '/api/exam/repo/qu/import-word/validate',
    data,
    headersType: 'multipart/form-data'
  })
export const importWordQuestionsApi = (data: FormData) =>
  request.post({
    url: '/api/exam/repo/qu/import-word',
    data,
    headersType: 'multipart/form-data'
  })
export const downloadWordTemplateApi = () =>
  request.get({
    url: '/api/exam/repo/qu/import-word-template',
    responseType: 'blob'
  })
export const downloadWordErrorReportApi = (data: FormData) =>
  request.post({
    url: '/api/exam/repo/qu/import-word-error-report',
    data,
    headersType: 'multipart/form-data',
    responseType: 'blob'
  })
