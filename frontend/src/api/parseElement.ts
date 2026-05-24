import request from './request'
import type { PageResult } from '@/types/api'
import type { ParseElementVO, ElementTypeCountVO } from '@/types/api'

/**
 * 分页查询元素明细
 */
export function getParseElementList(params: {
  recordId: number
  elementType?: string
  confidenceMin?: number
  confidenceMax?: number
  keyword?: string
  parentElementId?: string
  page?: number
  size?: number
}) {
  return request.get('/parse-element/list', { params })
}

/**
 * 获取元素类型统计
 */
export function getParseElementTypes(recordId: number) {
  return request.get('/parse-element/types', { params: { recordId } })
}
