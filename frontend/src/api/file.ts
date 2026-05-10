import request from './request'
import type { UploadFile, ParseResult, PageResult } from '@/types/api'

// 文件列表
export function getFileList(params: { page?: number; size?: number }) {
  return request.get<any, PageResult<UploadFile>>('/word/list', { params })
}

// 上传文件
export function uploadFile(file: File, templateId?: number) {
  const formData = new FormData()
  formData.append('file', file)
  if (templateId) formData.append('templateId', String(templateId))
  return request.post<any, UploadFile>('/word/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 解析文件
export function parseFile(id: number) {
  return request.post<any, UploadFile>(`/word/${id}/parse`)
}

// 预览解析结果
export function getPreview(id: number) {
  return request.get<any, ParseResult>(`/word/${id}/preview`)
}

// 保存人工调整
export function saveAdjust(id: number, data: any) {
  return request.put<any, void>(`/word/${id}/adjust`, data)
}

// 导出文件
export function exportFile(id: number) {
  return request.post<any, UploadFile>(`/word/${id}/export`)
}

// 修改关联模板
export function updateFileTemplate(id: number, templateId: number | undefined) {
  return request.put<any, void>(`/word/${id}/template`, { templateId })
}

// 获取 PDF 预览 URL（用于 PDF.js 加载）
export function getPdfUrl(id: number): string {
  return `${request.defaults.baseURL || ''}/word/${id}/preview/pdf`
}

// 删除文件
export function deleteFile(id: number) {
  return request.delete<any, void>(`/word/${id}`)
}

// 下载文件（返回 blob）
export function downloadFile(id: number) {
  return request.get(`/word/${id}/download`, { responseType: 'blob' })
}
