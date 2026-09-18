// 用户对象
export type RepoDataType = {
  id?: string
  title?: string
  catId?: string
  departId?: string
  positionId?: string
  sceneType?: string
  targetGradeId?: string
  status?: number
}

// 试题对象
export type QuDataType = {
  id?: string
  title?: string
  externalCode?: string
  quType?: string
  repoId?: string
  difficultyLevel?: string
  content?: string
  analysis?: string
  referenceAnswer?: string
  gradingCriteria?: string
  tags?: string
  status?: number
  answerList?: AnswerDataType[]
}

export type QuestionImportIssueType = {
  rowNumber: number
  questionCode?: string
  content?: string
  field: string
  message: string
  issueType: 'ERROR' | 'DUPLICATE'
}

export type QuestionImportPreviewType = {
  totalCount: number
  validCount: number
  questions?: {
    paragraph: number
    questionCode: string
    questionType: string
    content: string
    options: string[]
    answer: string
    difficulty: string
    explanation: string
    gradingCriteria: string
    status: 'VALID' | 'ERROR' | 'DUPLICATE'
  }[]
  duplicateCount: number
  failureCount: number
  templateVersion: string
  issues: QuestionImportIssueType[]
}

export type QuestionImportResultType = {
  totalCount: number
  successCount: number
  errorReportBase64?: string
  duplicateCount: number
  failureCount: number
  templateVersion: string
  issues: QuestionImportIssueType[]
}

// 选项
export type AnswerDataType = {
  id?: string
  isRight?: boolean
  content?: string
}
