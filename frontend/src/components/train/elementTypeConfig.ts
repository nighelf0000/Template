/**
 * 元素类型图标、颜色、标签映射配置
 */

export interface ElementTypeConfigItem {
  icon: string
  color: string
  label: string
}

export const ELEMENT_TYPE_CONFIG: Record<string, ElementTypeConfigItem> = {
  document:      { icon: 'Folder',      color: '#909399', label: '文档' },
  heading:       { icon: 'EditPen',    color: '#409EFF', label: '标题' },
  paragraph:     { icon: 'Document',    color: '#606266', label: '段落' },
  table:         { icon: 'Grid',        color: '#67C23A', label: '表格' },
  image:         { icon: 'Picture',     color: '#E6A23C', label: '图片' },
  list_ordered:   { icon: 'List',        color: '#409EFF', label: '有序列表' },
  list_unordered: { icon: 'List',        color: '#409EFF', label: '无序列表' },
  code_block:    { icon: 'Cpu',         color: '#8B5CF6', label: '代码块' },
  block_quote:   { icon: 'ChatSquare',  color: '#909399', label: '引用' },
  toc:           { icon: 'Guide',       color: '#E6A23C', label: '目录' },
  toc_item:      { icon: 'Link',        color: '#409EFF', label: '目录项' },
  page_break:    { icon: 'MoreFilled',  color: '#DCDFE6', label: '分页符' },
  caption:       { icon: 'Edit',        color: '#67C23A', label: '标题' },
  header:        { icon: 'Top',         color: '#909399', label: '页眉' },
  footer:        { icon: 'Bottom',      color: '#909399', label: '页脚' },
}

/** 默认配置（未知类型） */
export const DEFAULT_TYPE_CONFIG: ElementTypeConfigItem = {
  icon: 'QuestionFilled',
  color: '#909399',
  label: '未知',
}

export function getTypeConfig(type: string): ElementTypeConfigItem {
  return ELEMENT_TYPE_CONFIG[type] || DEFAULT_TYPE_CONFIG
}

/**
 * 置信度配置
 */
export interface ConfidenceConfig {
  color: string
  tagType: 'success' | 'warning' | 'danger'
  label: string
  level: 'high' | 'medium' | 'low'
}

export function getConfidenceConfig(confidence: number): ConfidenceConfig {
  if (confidence >= 0.9) {
    return { color: '#67C23A', tagType: 'success', label: '高', level: 'high' }
  } else if (confidence >= 0.7) {
    return { color: '#E6A23C', tagType: 'warning', label: '中', level: 'medium' }
  } else {
    return { color: '#F56C6C', tagType: 'danger', label: '低', level: 'low' }
  }
}
