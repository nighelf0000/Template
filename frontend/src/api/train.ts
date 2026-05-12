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
