// 通用后端响应结构
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
}


// ====== 模板相关类型 ======

export interface TemplateConfig {
  id?: number
  name: string
  isActive?: number
  createdAt?: string
  updatedAt?: string
  rules?: TemplateRule[]
  engineConfigs?: EngineConfig[]
}

export interface TemplateRule {
  id?: number
  templateId?: number
  name: string
  fontName?: string
  fontSize?: number
  fontBold?: number
  fontItalic?: number
  fontUnderline?: number
  fontColor?: string
  fontStrike?: number
  textAlign?: string
  textIndent?: number
  lineSpacing?: number
  spaceBefore?: number
  spaceAfter?: number
  highlightColor?: string
}

export interface EngineConfig {
  id?: number
  templateId?: number
  configName: string           // 配置名称
  pattern: string              // 正则表达式
  matchType: string            // 匹配类型：COVER/TOC/TITLE/BODY
  matchLevel?: number          // 标题级别（TITLE专用）
  ruleId?: number              // 关联样式规则ID
  ruleName?: string            // 关联样式规则名称（只读，服务端返回）
  sortOrder?: number           // 排序序号
  isActive?: number            // 是否启用
}

// ====== 文件相关类型 ======

export interface UploadFile {
  id?: number
  templateId?: number
  templateName?: string
  originalName?: string
  originalSize?: number
  status?: string
  errorMessage?: string
  createdAt?: string
  parsedJson?: any
  manualAdjustJson?: any
  outputName?: string
}

export interface ParagraphItem {
  index: number
  text: string
  matchedType: string
  matchedLevel?: number
  ruleId?: number
  ruleName?: string
  style: Record<string, any>
  backgroundColor?: string
  startOffset: number
  endOffset: number
  matchedEngineConfigId?: number
}

export interface ParseResult {
  templateId: number
  templateName: string
  pdfUrl?: string           // PDF 预览 URL
  paragraphs: ParagraphItem[]
  legend?: LegendItem[]
}

export interface LegendItem {
  ruleId: number
  ruleName: string
  matchedType: string
  color: string
}

// ====== 分页类型 ======

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

// ====== 智能匹配相关类型 ======

export interface SmartMatchTask {
  id?: number
  templateId?: number
  taskName: string
  status: string
  progress?: number
  fileCount?: number
  errorMessage?: string
  ruleCount?: number
  startedAt?: string
  completedAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface SmartMatchRule {
  id?: number
  templateId?: number
  taskId?: number
  ruleName: string
  matchType: string
  matchLevel?: number
  keywords?: string
  featureVector?: string
  styleRuleId?: number
  threshold?: number
  isActive?: number
  matchOrder?: number
  createdAt?: string
  updatedAt?: string
}

export interface SmartMatchTestResult {
  paragraphIndex: number
  text: string
  matchedType: string
  matchLevel?: number
  styleRuleId?: number
  ruleName?: string
  confidence: number
}

// ====== 模板训练相关类型 ======

export interface TrainFileVO {
  id: number
  templateId: number
  templateName?: string
  originalName: string
  originalSize: number
  status: string
  createdAt: string
}

export interface TrainTaskVO {
  id: number
  templateId: number
  templateName?: string
  taskName: string
  status: string
  progress: number
  totalFiles: number
  fileCount: number
  parseRecordCount?: number
  errorMessage?: string
  startedAt?: string
  completedAt?: string
  createdAt: string
  updatedAt: string
  parseRecords?: ParseRecordSimpleVO[]
}

export interface ParseRecordSimpleVO {
  id: number
  sourceFile: string
  status: string
  parsedAt: string
}

// ====== 解析记录详情类型 ======

export interface ParseRecordDetailVO {
  id: number
  templateId: number
  templateName?: string
  sourceFile: string
  status: string
  parsedAt: string
  engineVersion?: string
  rulesetName?: string
  processingTimeMs?: number
  structureTree?: any
  documentMeta?: any
  elementSummary?: any
}

// ====== 解析元素类型 ======

export interface ParseElementVO {
  id: number
  recordId: number
  elementId: string
  elementType: string
  level?: number
  contentText?: string
  confidence: number
  parentElementId?: string
  sortOrder: number
  metadata?: any
  styleFeatures?: string
}

export interface ElementTypeCountVO {
  elementType: string
  count: number
}

// ====== 差异对比类型 ======

export interface DiffChangeVO {
  changeType: 'added' | 'removed' | 'changed' | 'type_changed' | 'unchanged'
  elementId: string
  elementType?: string
  elementTypeA?: string
  elementTypeB?: string
  contentText?: string
  contentA?: string
  contentB?: string
  confidence?: number
  confidenceA?: number
  confidenceB?: number
  levelA?: number
  levelB?: number
}

export interface ParseDiffReportVO {
  id: number
  diffType: 'TASK' | 'STANDARD'
  summary: {
    totalA: number
    totalB: number
    added: number
    removed: number
    changed: number
    unchanged: number
    accuracy?: number
    recall?: number
    f1Score?: number
  }
  structuralChanges: DiffChangeVO[]
  statisticsDiff: any
  createdAt: string
}

// ====== 标准答案类型 ======

export interface ParseStandardAnswerVO {
  id: number
  templateId: number
  sourceFile: string
  answerName: string
  description?: string
  elementSummary?: any
  createdAt: string
}
