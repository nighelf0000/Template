import request from './request'
import type { TemplateConfig, TemplateRule, EngineConfig, PageResult, SmartMatchTask, SmartMatchRule, SmartMatchTestResult } from '@/types/api'

// 分页查询模板列表
export function getTemplateList(params: { page?: number; size?: number }) {
  return request.get<any, PageResult<TemplateConfig>>('/template/list', { params })
}

// 获取模板详情
export function getTemplateDetail(id: number) {
  return request.get<any, TemplateConfig>(`/template/${id}`)
}

// 创建模板
export function createTemplate(data: { name: string }) {
  return request.post<any, TemplateConfig>('/template', data)
}

// 更新模板
export function updateTemplate(id: number, data: { name: string }) {
  return request.put<any, TemplateConfig>(`/template/${id}`, data)
}

// 切换模板启用状态
export function toggleTemplate(id: number) {
  return request.put<any, void>(`/template/${id}/toggle`)
}

// 创建规则
export function createRule(templateId: number, data: TemplateRule) {
  return request.post<any, TemplateRule>(`/template/${templateId}/rule`, data)
}

// 获取规则详情
export function getRuleDetail(templateId: number, ruleId: number) {
  return request.get<any, TemplateRule>(`/template/${templateId}/rule/${ruleId}`)
}

// 获取规则列表
export function getRuleList(templateId: number) {
  return request.get<any, TemplateRule[]>(`/template/${templateId}/rule/list`)
}

// 更新规则
export function updateRule(templateId: number, ruleId: number, data: TemplateRule) {
  return request.put<any, TemplateRule>(`/template/${templateId}/rule/${ruleId}`, data)
}

// 删除规则
export function deleteRule(templateId: number, ruleId: number) {
  return request.delete<any, void>(`/template/${templateId}/rule/${ruleId}`)
}

// 保存/更新引擎配置（旧接口，已废弃，请使用 createEngineConfig / updateEngineConfig）
/** @deprecated 旧接口已废弃，请使用 engine-config 系列接口 */
export function saveEngineConfig(templateId: number, data: any) {
  return request.put<any, any>(`/template/${templateId}/engine`, data)
}

// 获取引擎配置（旧接口，已废弃，请使用 getEngineConfigList）
/** @deprecated 旧接口已废弃，请使用 engine-config 系列接口 */
export function getEngineConfig(templateId: number) {
  return request.get<any, any>(`/template/${templateId}/engine`)
}

// ========== 引擎配置 CRUD（新接口） ==========

// 新增引擎配置
export function createEngineConfig(templateId: number, data: EngineConfig) {
  return request.post<any, EngineConfig>(`/template/${templateId}/engine-config`, data)
}

// 获取引擎配置列表（分页）
export function getEngineConfigList(templateId: number, params?: { page?: number; size?: number }) {
  return request.get<any, PageResult<EngineConfig>>(`/template/${templateId}/engine-config/list`, { params })
}

// 获取单条引擎配置
export function getEngineConfigDetail(templateId: number, id: number) {
  return request.get<any, EngineConfig>(`/template/${templateId}/engine-config/${id}`)
}

// 更新引擎配置
export function updateEngineConfig(templateId: number, id: number, data: EngineConfig) {
  return request.put<any, EngineConfig>(`/template/${templateId}/engine-config/${id}`, data)
}

// 删除引擎配置
export function deleteEngineConfig(templateId: number, id: number) {
  return request.delete<any, void>(`/template/${templateId}/engine-config/${id}`)
}

// ========== 智能匹配 API ==========

// 触发智能匹配训练
export function startSmartTrain(templateId: number, data?: { taskName?: string }) {
  return request.post<any, SmartMatchTask>(`/smart-match/${templateId}/train`, data || {})
}

// 查询训练任务列表（分页）
export function getSmartTaskList(templateId: number, params?: { page?: number; size?: number }) {
  return request.get<any, PageResult<SmartMatchTask>>(`/smart-match/${templateId}/tasks`, { params })
}

// 查询训练任务详情
export function getSmartTaskDetail(templateId: number, taskId: number) {
  return request.get<any, SmartMatchTask>(`/smart-match/${templateId}/tasks/${taskId}`)
}

// 查询智能匹配规则列表（分页）
export function getSmartRuleList(templateId: number, params?: { page?: number; size?: number }) {
  return request.get<any, PageResult<SmartMatchRule>>(`/smart-match/${templateId}/rules`, { params })
}

// 切换规则启用状态
export function toggleSmartRule(templateId: number, ruleId: number) {
  return request.put<any, void>(`/smart-match/${templateId}/rules/${ruleId}/toggle`)
}

// 编辑智能匹配规则
export function updateSmartRule(templateId: number, ruleId: number, data: Partial<SmartMatchRule>) {
  return request.put<any, SmartMatchRule>(`/smart-match/${templateId}/rules/${ruleId}`, data)
}

// 删除智能匹配规则
export function deleteSmartRule(templateId: number, ruleId: number) {
  return request.delete<any, void>(`/smart-match/${templateId}/rules/${ruleId}`)
}

// 测试智能匹配
export function testSmartMatch(templateId: number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<any, SmartMatchTestResult[]>(`/smart-match/${templateId}/test`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 查询模板的智能匹配规则列表（不分页，通过 TemplateController）
export function getSmartRulesByTemplate(templateId: number) {
  return request.get<any, SmartMatchRule[]>(`/template/${templateId}/smart-rules`)
}
