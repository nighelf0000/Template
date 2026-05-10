<template>
  <div class="preview-container" v-loading="loading">
    <!-- 顶栏 -->
    <div class="top-bar">
      <div class="top-bar-left">
        <h2 style="margin: 0">解析预览</h2>
      </div>
      <div class="top-bar-center" v-if="parseResult">
        <span>模板：<strong>{{ parseResult.templateName }}</strong></span>
        <el-divider direction="vertical" />
        <span>段落总数：<strong>{{ parseResult.paragraphs.length }}</strong></span>
        <el-divider direction="vertical" />
        <span>当前页：<strong>{{ currentPage }} / {{ totalPages }}</strong></span>
      </div>
      <div class="top-bar-right">
        <el-button type="primary" :loading="saving" @click="handleSave" :disabled="!isDirty">
          保存调整
        </el-button>
        <el-button @click="$router.push('/file-upload')">返回文件列表</el-button>
      </div>
    </div>

    <!-- 文件选择（没有选中文件时显示） -->
    <el-card v-if="!parseResult && !loading" class="file-select-card">
      <el-form label-width="100px">
        <el-form-item label="选择文件">
          <el-select
            v-model="selectedFileId"
            placeholder="请先选择一个文件"
            style="width: 400px"
            @change="loadPreview"
          >
            <el-option
              v-for="f in fileOptions"
              :key="f.id"
              :label="f.originalName"
              :value="f.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 主内容区域 -->
    <div v-if="parseResult" class="main-content">
      <!-- 左侧 PDF 预览 -->
      <div class="pdf-area" ref="pdfAreaRef">
        <!-- PDF 全屏视口 -->
        <div class="pdf-viewport" ref="pdfViewportRef">
          <div class="pdf-page-container">
            <!-- PDF Canvas（底层） -->
            <canvas ref="pdfCanvasRef" class="pdf-canvas" />
            <!-- 高亮叠加 Canvas（顶层） -->
            <canvas ref="highlightCanvasRef" class="highlight-canvas" />
          </div>
        </div>

        <!-- 翻页控制 -->
        <div class="page-controls">
          <el-button size="small" :disabled="currentPage <= 1" @click="goToPage(currentPage - 1)">
            上一页
          </el-button>
          <el-input-number
            v-if="totalPages > 0"
            v-model="currentPage"
            :min="1"
            :max="totalPages"
            size="small"
            style="width: 80px; margin: 0 8px"
            @change="goToPage(currentPage)"
          />
          <span style="font-size: 13px; color: #666">/ {{ totalPages }}</span>
          <el-button size="small" :disabled="currentPage >= totalPages" @click="goToPage(currentPage + 1)">
            下一页
          </el-button>
        </div>
      </div>

      <!-- 右侧图例面板 -->
      <div class="legend-panel">
        <h3 class="legend-title">样式图例</h3>
        <div v-if="parseResult.legend && parseResult.legend.length > 0">
          <div
            v-for="item in parseResult.legend"
            :key="item.ruleId"
            class="legend-item"
          >
            <span class="legend-color" :style="{ backgroundColor: item.color }"></span>
            <div class="legend-info">
              <span class="legend-name">{{ item.ruleName }}</span>
              <el-tag size="small" :type="matchTypeTag(item.matchedType)" class="legend-tag">
                {{ item.matchedType }}
              </el-tag>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无图例" :image-size="60" />

        <el-divider />

        <!-- 段落调整区域 -->
        <h3 class="legend-title">段落调整</h3>
        <div class="adjust-section">
          <div
            v-for="(para, index) in parseResult.paragraphs"
            :key="index"
            class="adjust-item"
          >
            <div
              class="adjust-header"
              @click="toggleAdjustItem(index)"
            >
              <span class="adjust-index">#{{ para.index }}</span>
              <el-tag size="small" :type="matchTypeTag(para.matchedType)" style="flex-shrink: 0">
                {{ para.matchedType || '未匹配' }}
              </el-tag>
              <el-icon class="adjust-arrow" :class="{ expanded: expandedAdjustItems[index] }">
                <ArrowDown />
              </el-icon>
            </div>
            <el-tooltip v-if="paragraphStylePreview(para)" :content="para.text" placement="top" :show-after="300">
              <div class="adjust-preview" :style="paragraphInlineStyle(para.style)">
                {{ para.text?.substring(0, 60) }}{{ para.text?.length > 60 ? '...' : '' }}
              </div>
            </el-tooltip>
            <el-collapse-transition>
              <div v-show="expandedAdjustItems[index]" class="adjust-body">
                <el-select
                  v-model="para.ruleId"
                  placeholder="选择样式规则"
                  size="small"
                  style="width: 100%"
                  @change="handleRuleSelect(para, $event)"
                >
                  <el-option v-for="rule in ruleList" :key="rule.id" :label="rule.name" :value="rule.id" />
                </el-select>
                <div class="adjust-style-info">
                  <div v-if="para.style?.fontSize">字号: {{ para.style.fontSize }}</div>
                  <div v-if="para.style?.fontName">字体: {{ para.style.fontName }}</div>
                  <div v-if="para.style?.fontBold">加粗</div>
                  <div v-if="para.style?.fontItalic">斜体</div>
                </div>
              </div>
            </el-collapse-transition>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, shallowRef, markRaw, onMounted, onUnmounted, reactive, nextTick, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import { getFileList, getPreview, saveAdjust } from '@/api/file'
import { getRuleList } from '@/api/template'
import type { ParseResult, UploadFile, ParagraphItem, TemplateRule } from '@/types/api'
import type { PDFDocumentProxy, PDFPageProxy } from 'pdfjs-dist'

// pdfjs-dist 通过 index.html 的 <script> 标签全局加载到 globalThis.pdfjsLib
// 显式取 globalThis 属性，避免 strict mode 下裸标识符作用域链解析失败
const pdfjsLib = (globalThis as any).pdfjsLib as typeof import('pdfjs-dist')

pdfjsLib.GlobalWorkerOptions.workerSrc = '/pdf.worker.min.mjs'

const route = useRoute()
const loading = ref(false)
const saving = ref(false)
const parseResult = ref<ParseResult | null>(null)
const selectedFileId = ref<number | null>(null)
const fileOptions = ref<UploadFile[]>([])
const isDirty = ref(false)
const ruleList = ref<TemplateRule[]>([])

// PDF 状态
const pdfDoc = shallowRef<PDFDocumentProxy | null>(null)
const currentPage = ref(1)
const totalPages = ref(0)
const scale = ref(2.0)

// 所有 PDF 页面扁平化文本项（用于基于段落位置的 highlight 匹配）
const allPagesTextItems = ref<any[]>([])
const allPagesTotalChars = ref(0)

// 生命周期守卫与防重入
const isMounted = ref(true)
const reloadingPdf = ref(false)

// 当前页对象
const currentPageObj = shallowRef<PDFPageProxy | null>(null)
const autoFit = ref(true)

// Canvas 引用
const pdfCanvasRef = ref<HTMLCanvasElement | null>(null)
const highlightCanvasRef = ref<HTMLCanvasElement | null>(null)
const pdfViewportRef = ref<HTMLElement | null>(null)

// 段落调整折叠状态
const expandedAdjustItems = reactive<Record<number, boolean>>({})

function matchTypeTag(type: string | undefined): string {
  const map: Record<string, string> = {
    COVER: 'warning',
    TOC: 'info',
    TITLE: 'primary',
    BODY: 'success',
    SPECIAL: '',
    UNKNOWN: 'danger'
  }
  return map[type || ''] || 'danger'
}

function paragraphStylePreview(para: ParagraphItem): boolean {
  return para.style != null && Object.keys(para.style).length > 0
}

function paragraphInlineStyle(style: Record<string, any>): Record<string, string> {
  const cssMap: Record<string, string> = {}
  if (!style) return cssMap
  for (const [key, value] of Object.entries(style)) {
    if (value === undefined || value === null || value === false) continue
    switch (key) {
      case 'fontSize':
        cssMap['font-size'] = String(value)
        break
      case 'fontName':
        cssMap['font-family'] = String(value)
        break
      case 'fontBold':
        if (value) cssMap['font-weight'] = 'bold'
        break
      case 'fontItalic':
        if (value) cssMap['font-style'] = 'italic'
        break
      default:
        cssMap[key.replace(/([A-Z])/g, '-$1').toLowerCase()] = String(value)
    }
  }
  return cssMap
}

function handleRuleSelect(para: ParagraphItem, ruleId: number) {
  const rule = ruleList.value.find(r => r.id === ruleId)
  if (rule) {
    para.ruleName = rule.name
  }
  isDirty.value = true
}

async function loadRuleList() {
  if (!parseResult.value?.templateId) return
  try {
    const res = await getRuleList(parseResult.value.templateId)
    ruleList.value = res
  } catch (e) {
    // 错误已在拦截器中处理
  }
}

function toggleAdjustItem(index: number) {
  expandedAdjustItems[index] = !expandedAdjustItems[index]
}

async function loadFileOptions() {
  try {
    const res = await getFileList({ page: 1, size: 999 })
    fileOptions.value = res.records || []
  } catch (e) {
    // 错误已在拦截器中处理
  }
}

async function loadPreview() {
  if (!selectedFileId.value) return
  loading.value = true
  try {
    parseResult.value = await getPreview(selectedFileId.value)
    if (!isMounted.value) return

    // 加载模板规则列表
    await loadRuleList()

    Object.keys(expandedAdjustItems).forEach(k => { expandedAdjustItems[Number(k)] = false })
    isDirty.value = false

    // 加载 PDF
    await loadPdf()
  } catch (e) {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
  }
}

async function loadPdf() {
  if (!parseResult.value?.pdfUrl) return

  // 释放旧的 PDF 文档
  if (pdfDoc.value) {
    pdfDoc.value.destroy()
    pdfDoc.value = null
  }

  // 重置跨页文本缓存
  allPagesTextItems.value = []
  allPagesTotalChars.value = 0

  try {
    const url = parseResult.value.pdfUrl
    pdfDoc.value = await pdfjsLib.getDocument(url).promise
    if (!isMounted.value) return

    totalPages.value = pdfDoc.value.numPages
    currentPage.value = 1
    autoFit.value = true

    // 预获取所有页面文本内容（用于基于段落位置的高亮匹配）
    try {
      await prefetchAllPageTexts()
    } catch (e) {
      console.warn('预获取文本内容失败（高亮功能将不可用）:', e)
    }

    // 渲染当前页
    await renderPage(currentPage.value)
  } catch (e) {
    console.error('PDF 加载失败:', e)
    ElMessage.error('PDF 加载失败')
  }
}

function calcFitScale(): number {
  if (!pdfViewportRef.value || !currentPageObj.value) return scale.value
  const vw = pdfViewportRef.value.clientWidth
  if (vw <= 0) return scale.value
  const base = currentPageObj.value.getViewport({ scale: 1 })
  return vw / base.width
}

async function renderPage(pageNum: number) {
  if (!pdfDoc.value) return

  const page = await pdfDoc.value.getPage(pageNum)
  if (!isMounted.value) return

  // 释放旧页面
  if (currentPageObj.value) {
    currentPageObj.value.cleanup()
  }
  currentPageObj.value = markRaw(page)

  const effectiveScale = autoFit.value ? calcFitScale() : scale.value
  const viewport = page.getViewport({ scale: effectiveScale })

  await nextTick()
  if (!isMounted.value || !pdfCanvasRef.value) return

  const canvas = pdfCanvasRef.value
  canvas.width = Math.ceil(viewport.width)
  canvas.height = Math.ceil(viewport.height)

  if (highlightCanvasRef.value) {
    highlightCanvasRef.value.width = canvas.width
    highlightCanvasRef.value.height = canvas.height
  }

  const context = canvas.getContext('2d')
  if (!context) return

  try {
    await page.render({ canvasContext: context, viewport }).promise
  } catch (e) {
    console.error(`渲染第 ${pageNum} 页 Canvas 失败:`, e)
    return
  }

  // 渲染高亮
  await renderHighlights()
}

async function prefetchAllPageTexts() {
  if (!pdfDoc.value) return
  const items: any[] = []
  let totalChars = 0
  for (let p = 1; p <= pdfDoc.value.numPages; p++) {
    if (!isMounted.value) return
    const page = await pdfDoc.value.getPage(p)
    if (!isMounted.value) { page.cleanup(); return }
    try {
      const tc = await page.getTextContent()
      for (const item of tc.items as any[]) {
        items.push({
          str: item.str || '',
          transform: item.transform,
          width: item.width,
          height: item.height,
          pageNum: p
        })
        totalChars += (item.str || '').length
      }
    } finally {
      page.cleanup()
    }
  }
  allPagesTextItems.value = items
  allPagesTotalChars.value = totalChars
}

function binarySearchItemIndex(arr: number[], target: number): number {
  let lo = 0, hi = arr.length - 1
  while (lo < hi) {
    const mid = (lo + hi) >>> 1
    if (arr[mid] < target) lo = mid + 1
    else hi = mid
  }
  return lo
}

async function renderHighlights() {
  const canvas = highlightCanvasRef.value
  if (!canvas || !currentPageObj.value || !parseResult.value) return

  const context = canvas.getContext('2d')
  if (!context) return

  context.clearRect(0, 0, canvas.width, canvas.height)

  const allItems = allPagesTextItems.value
  if (allItems.length === 0 || allPagesTotalChars.value === 0) return

  const effectiveScale = autoFit.value ? calcFitScale() : scale.value
  const viewport = currentPageObj.value.getViewport({ scale: effectiveScale })

  const allParagraphs = parseResult.value.paragraphs

  // 计算所有段落的累计文本长度
  const docTotalLen = allParagraphs.reduce((sum, p) => sum + (p.text || '').trim().length, 0)
  if (docTotalLen === 0) return

  // 计算每个扁平化文本项的累计字符结束位置
  const itemEndPos: number[] = []
  let acc = 0
  for (const item of allItems) {
    acc += item.str.length
    itemEndPos.push(acc)
  }

  // 按段落顺序逐段匹配 PDF 文本项（基于累积文本长度比例，不依赖字符串匹配）
  let paraCumLen = 0
  for (const match of allParagraphs) {
    const paraLen = (match.text || '').trim().length
    if (!match.backgroundColor || paraLen < 3) {
      paraCumLen += paraLen
      continue
    }

    // 将段落的字符范围比例映射到 PDF 文本项索引范围
    const startCharPos = Math.floor((paraCumLen / docTotalLen) * allPagesTotalChars.value)
    const endCharPos = Math.ceil(((paraCumLen + paraLen) / docTotalLen) * allPagesTotalChars.value)

    const startItemIdx = binarySearchItemIndex(itemEndPos, startCharPos)
    const endItemIdx = Math.min(binarySearchItemIndex(itemEndPos, endCharPos), allItems.length - 1)

    // 收集在当前页面上的文本项
    const pageItems: typeof allItems = []
    for (let i = startItemIdx; i <= endItemIdx; i++) {
      if (allItems[i].pageNum === currentPage.value) {
        pageItems.push(allItems[i])
      }
    }

    if (pageItems.length === 0) {
      paraCumLen += paraLen
      continue
    }

    // 计算合并包围盒并绘制高亮矩形
    let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity
    for (const item of pageItems) {
      if (item.transform && item.width != null && item.height != null) {
        const x = item.transform[4] * effectiveScale
        const y = viewport.height - (item.transform[5] * effectiveScale) - item.height * effectiveScale
        const w = item.width * effectiveScale
        const h = item.height * effectiveScale

        minX = Math.min(minX, x)
        minY = Math.min(minY, y)
        maxX = Math.max(maxX, x + w)
        maxY = Math.max(maxY, y + h)
      }
    }

    if (minX < Infinity) {
      context.fillStyle = match.backgroundColor
      context.globalAlpha = 0.35
      context.fillRect(minX, minY, maxX - minX, maxY - minY)
      context.globalAlpha = 1.0
    }

    paraCumLen += paraLen
  }
}

async function goToPage(page: number) {
  if (page < 1 || page > totalPages.value) return
  currentPage.value = page
  loading.value = true
  try {
    await renderPage(page)
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  if (!selectedFileId.value || !parseResult.value) return
  saving.value = true
  try {
    const adjustData = {
      manualAdjustJson: JSON.stringify(
        parseResult.value.paragraphs.map((p) => ({
          index: p.index,
          text: p.text,
          matchedType: p.matchedType,
          matchedLevel: p.matchedLevel,
          ruleId: p.ruleId,
          ruleName: p.ruleName
        }))
      )
    }
    await saveAdjust(selectedFileId.value, adjustData)
    ElMessage.success('保存成功')
    isDirty.value = false

    // 保存后自动刷新
    await loadPreview()
  } catch (e) {
    // 错误已在拦截器中处理
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  const fileId = route.query.fileId
  if (fileId) {
    selectedFileId.value = Number(fileId)
  }
  loadFileOptions().then(() => {
    if (selectedFileId.value) {
      loadPreview()
    }
  })
})

onUnmounted(() => {
  isMounted.value = false
  if (pdfDoc.value) {
    pdfDoc.value.destroy()
    pdfDoc.value = null
  }
})
</script>

<style scoped>
.preview-container {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 顶栏 */
.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  flex-shrink: 0;
}

.top-bar-left {
  flex-shrink: 0;
}

.top-bar-center {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #606266;
}

.top-bar-right {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

/* 文件选择 */
.file-select-card {
  margin: 20px;
}

/* 主内容区域 */
.main-content {
  display: flex;
  flex: 1;
  overflow: hidden;
}

/* 左侧 PDF 区域 */
.pdf-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #f5f7fa;
}

.pdf-viewport {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  text-align: center;
  background: #e0e0e0;
}

.pdf-page-container {
  display: inline-block;
  position: relative;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.15);
  background: #fff;
  line-height: 0;
}

.pdf-canvas {
  display: block;
}

.highlight-canvas {
  position: absolute;
  top: 0;
  left: 0;
  pointer-events: none;
}

/* 翻页控制 */
.page-controls {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 12px;
  background: #fff;
  border-top: 1px solid #e4e7ed;
  flex-shrink: 0;
}

/* 右侧图例面板 */
.legend-panel {
  width: 280px;
  flex-shrink: 0;
  border-left: 1px solid #e4e7ed;
  background: #fff;
  overflow-y: auto;
  padding: 16px;
}

.legend-title {
  margin: 0 0 12px;
  font-size: 15px;
  color: #303133;
}

.legend-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 0;
  border-bottom: 1px solid #f2f2f2;
}

.legend-color {
  display: inline-block;
  width: 18px;
  height: 18px;
  border-radius: 4px;
  border: 1px solid #d9d9d9;
  flex-shrink: 0;
  margin-top: 2px;
}

.legend-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.legend-name {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
}

.legend-tag {
  align-self: flex-start;
}

/* 段落调整区域 */
.adjust-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.adjust-item {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  overflow: hidden;
}

.adjust-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  cursor: pointer;
  background: #fafafa;
  transition: background 0.2s;
}

.adjust-header:hover {
  background: #f0f2f5;
}

.adjust-index {
  font-size: 12px;
  color: #909399;
  font-family: monospace;
  flex-shrink: 0;
}

.adjust-arrow {
  margin-left: auto;
  font-size: 14px;
  color: #c0c4cc;
  transition: transform 0.2s;
}

.adjust-arrow.expanded {
  transform: rotate(180deg);
}

.adjust-preview {
  padding: 4px 10px 8px;
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.adjust-body {
  padding: 8px 10px 10px;
  border-top: 1px solid #ebeef5;
}

.adjust-style-info {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}
</style>
