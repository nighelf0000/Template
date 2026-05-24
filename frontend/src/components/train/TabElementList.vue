<template>
  <div class="tab-element-list">
    <!-- 筛选栏 -->
    <div class="filter-bar">
      <el-select
        v-model="filters.elementType"
        placeholder="元素类型"
        size="small"
        clearable
        style="width:130px"
        @change="handleSearch"
      >
        <el-option
          v-for="item in typeOptions"
          :key="item.elementType"
          :label="getTypeLabel(item.elementType) + ' (' + item.count + ')'"
          :value="item.elementType"
        />
      </el-select>

      <div class="confidence-filter">
        <span class="confidence-label">置信度:</span>
        <el-slider
          v-model="confidenceRange"
          range
          :min="0"
          :max="100"
          :step="1"
          style="width:160px"
          @change="handleSearch"
        />
        <span class="confidence-value">{{ confidenceRange[0] }}% ~ {{ confidenceRange[1] }}%</span>
      </div>

      <el-input
        v-model="filters.keyword"
        placeholder="搜索内容..."
        size="small"
        clearable
        style="width:200px"
        @keyup.enter="handleSearch"
      />

      <el-button type="primary" size="small" @click="handleSearch">查询</el-button>
      <el-button size="small" @click="handleReset">重置</el-button>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" stripe size="small" style="width:100%" v-loading="loading" height="calc(100vh - 400px)">
      <el-table-column prop="elementId" label="元素ID" width="120" show-overflow-tooltip />
      <el-table-column label="类型" width="80" align="center">
        <template #default="{ row }">
          <el-tag
            :color="getTypeColor(row.elementType)"
            size="small"
            style="color:#fff;border:0"
          >
            {{ getTypeLabel(row.elementType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="内容预览" min-width="300" show-overflow-tooltip>
        <template #default="{ row }">
          {{ getContentPreview(row) }}
        </template>
      </el-table-column>
      <el-table-column label="置信度" width="160" align="center">
        <template #default="{ row }">
          <div style="display:flex;align-items:center;gap:6px">
            <el-progress
              :percentage="Math.round((row.confidence || 0) * 100)"
              :stroke-width="12"
              :color="getConfidenceColor(row.confidence)"
              style="width:80px"
            />
            <el-tag :type="getConfidenceTagType(row.confidence)" size="small">
              {{ (row.confidence * 100).toFixed(1) }}%
            </el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="level" label="级别" width="60" align="center">
        <template #default="{ row }">
          {{ row.level != null && row.level > 0 ? 'H' + row.level : '-' }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90" align="center" fixed="right">
        <template #default="{ row }">
          <el-button text size="small" type="primary" @click="handleLocateTree(row)">
            查看树
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-wrapper" v-if="total > 0">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        size="small"
        @current-change="fetchData"
        @size-change="fetchData"
      />
    </div>

    <el-empty v-if="!loading && tableData.length === 0" description="暂无元素数据" :image-size="40" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { getParseElementList, getParseElementTypes } from '@/api/parseElement'
import { getTypeConfig, getConfidenceConfig } from './elementTypeConfig'
import type { ParseElementVO, ElementTypeCountVO } from '@/types/api'

const props = defineProps<{
  recordId: number
}>()

const emit = defineEmits<{
  (e: 'locate-element', elementId: string): void
  (e: 'switch-tab', tab: string): void
}>()

const loading = ref(false)
const tableData = ref<ParseElementVO[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const typeOptions = ref<ElementTypeCountVO[]>([])

const filters = reactive({
  elementType: '',
  keyword: '',
})
const confidenceRange = ref([0, 100])

function getTypeLabel(type: string): string {
  return getTypeConfig(type).label
}

function getTypeColor(type: string): string {
  return getTypeConfig(type).color
}

function getConfidenceColor(confidence: number): string {
  return getConfidenceConfig(confidence).color
}

function getConfidenceTagType(confidence: number): string {
  return getConfidenceConfig(confidence).tagType
}

function getContentPreview(row: ParseElementVO): string {
  const text = row.contentText
  if (!text) return '-'
  if (text.length > 100) return text.slice(0, 100) + '...'
  return text
}

// 加载类型统计
async function loadTypeOptions() {
  if (!props.recordId) return
  try {
    typeOptions.value = await getParseElementTypes(props.recordId)
  } catch {
    typeOptions.value = []
  }
}

// 加载数据
async function fetchData() {
  if (!props.recordId) return
  loading.value = true
  try {
    const params: any = {
      recordId: props.recordId,
      page: currentPage.value,
      size: pageSize.value,
    }
    if (filters.elementType) params.elementType = filters.elementType
    if (filters.keyword) params.keyword = filters.keyword
    if (confidenceRange.value[0] > 0) params.confidenceMin = confidenceRange.value[0] / 100
    if (confidenceRange.value[1] < 100) params.confidenceMax = confidenceRange.value[1] / 100

    const res: any = await getParseElementList(params)
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  fetchData()
}

function handleReset() {
  filters.elementType = ''
  filters.keyword = ''
  confidenceRange.value = [0, 100]
  currentPage.value = 1
  fetchData()
}

function handleLocateTree(row: ParseElementVO) {
  emit('locate-element', row.elementId)
  emit('switch-tab', 'structure-tree')
}

watch(() => props.recordId, (val) => {
  if (val) {
    currentPage.value = 1
    loadTypeOptions()
    fetchData()
  }
}, { immediate: true })
</script>

<style scoped>
.tab-element-list {
  min-height: 300px;
}
.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.confidence-filter {
  display: flex;
  align-items: center;
  gap: 6px;
}
.confidence-label {
  font-size: 12px;
  color: #606266;
  white-space: nowrap;
}
.confidence-value {
  font-size: 11px;
  color: #909399;
  white-space: nowrap;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  padding: 12px 0;
}
</style>
