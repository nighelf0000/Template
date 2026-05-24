import request from './request'

// 上传训练文件
export function uploadTrainFile(file: File, templateId: number) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('templateId', String(templateId))
  return request.post('/train-file/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 查询训练文件列表（分页）
export function getTrainFileList(params: {
  templateId: number
  page?: number
  size?: number
}) {
  return request.get('/train-file/list', { params })
}

// 删除训练文件
export function deleteTrainFile(id: number) {
  return request.delete(`/train-file/${id}`)
}

// 创建并启动训练任务
export function startTrainTask(data: {
  templateId: number
  taskName?: string
}) {
  return request.post('/train-task/start', data)
}

// 分页查询训练任务
export function getTrainTaskPage(params: {
  templateId?: number
  status?: string
  keyword?: string
  page?: number
  size?: number
}) {
  return request.get('/train-task/page', { params })
}

// 查询训练任务详情
export function getTrainTaskDetail(id: number) {
  return request.get(`/train-task/${id}`)
}

// ====== 解析记录详情 ======

/**
 * 获取解析记录完整详情（含结构树）
 */
export function getParseRecordDetail(id: number) {
  return request.get(`/parse-record/${id}/detail`)
}

// ====== 差异对比 ======

/**
 * 任务间差异对比
 */
export function compareTaskDiff(data: { recordIdA: number; recordIdB: number }) {
  return request.post('/parse-diff/compare/task', data)
}

/**
 * 与标准答案对比
 */
export function compareStandardDiff(data: { recordId: number; standardAnswerId: number }) {
  return request.post('/parse-diff/compare/standard', data)
}

/**
 * 获取差异对比报告
 */
export function getDiffReport(id: number) {
  return request.get(`/parse-diff/report/${id}`)
}

/**
 * 删除差异对比报告
 */
export function deleteDiffReport(id: number) {
  return request.delete(`/parse-diff/report/${id}`)
}

// ====== 标准答案管理 ======

/**
 * 上传标准答案
 */
export function uploadStandardAnswer(data: FormData) {
  return request.post('/parse-standard-answer/upload', data, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/**
 * 查询标准答案列表
 */
export function getStandardAnswerList(params: {
  templateId?: number
  sourceFile?: string
  page?: number
  size?: number
}) {
  return request.get('/parse-standard-answer/list', { params })
}

/**
 * 删除标准答案
 */
export function deleteStandardAnswer(id: number) {
  return request.delete(`/parse-standard-answer/${id}`)
}
