<template>
  <div class="element-detail-panel">
    <div v-if="!element" class="empty-hint">
      <el-empty description="请选择一个元素" :image-size="40" />
    </div>
    <template v-else>
      <!-- 元素基本信息 -->
      <el-descriptions :column="2" size="small" border>
        <el-descriptions-item label="元素ID" :span="2">{{ element.elementId }}</el-descriptions-item>
        <el-descriptions-item label="类型">
          <el-tag :color="getTypeColor(element.elementType)" size="small" style="color:#fff;border:0">
            {{ getTypeLabel(element.elementType) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="级别">
          {{ element.level != null && element.level > 0 ? 'H' + element.level : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="置信度" :span="2">
          <div style="display:flex;align-items:center;gap:8px">
            <el-progress
              :percentage="Math.round((element.confidence || 0) * 100)"
              :stroke-width="14"
              :color="getConfidenceColor(element.confidence)"
              style="width:120px"
            />
            <el-tag :type="getConfidenceTagType(element.confidence)" size="small">
              {{ getConfidenceLabel(element.confidence) }}
            </el-tag>
            <span style="color:#606266;font-size:12px">{{ (element.confidence * 100).toFixed(1) }}%</span>
          </div>
        </el-descriptions-item>
        <el-descriptions-item label="排序序号">{{ element.sortOrder }}</el-descriptions-item>
        <el-descriptions-item label="父元素ID">{{ element.parentElementId || '-' }}</el-descriptions-item>
      </el-descriptions>

      <!-- 内容预览 -->
      <div class="section-block">
        <div class="section-block-title">内容预览</div>
        <div class="content-preview-box">
          <pre v-if="element.contentText">{{ element.contentText }}</pre>
          <span v-else class="no-content">无文本内容</span>
        </div>
      </div>

      <!-- 元数据 -->
      <div class="section-block" v-if="element.metadata">
        <div class="section-block-title">元数据</div>
        <el-descriptions :column="2" size="small" border>
          <el-descriptions-item
            v-for="(val, key) in flatMetadata(element.metadata)"
            :key="key"
            :label="key"
          >
            {{ String(val) }}
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <!-- 样式特征 -->
      <div class="section-block" v-if="element.styleFeatures">
        <div class="section-block-title">样式特征</div>
        <pre class="json-preview">{{ element.styleFeatures }}</pre>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ELEMENT_TYPE_CONFIG, getConfidenceConfig } from './elementTypeConfig'
import type { ParseElementVO } from '@/types/api'

const props = defineProps<{
  element: ParseElementVO | null
}>()

function getTypeColor(type: string): string {
  return ELEMENT_TYPE_CONFIG[type]?.color || '#909399'
}

function getTypeLabel(type: string): string {
  return ELEMENT_TYPE_CONFIG[type]?.label || type
}

function getConfidenceColor(confidence: number): string {
  const cfg = getConfidenceConfig(confidence)
  return cfg.color
}

function getConfidenceTagType(confidence: number): string {
  const cfg = getConfidenceConfig(confidence)
  return cfg.tagType
}

function getConfidenceLabel(confidence: number): string {
  const cfg = getConfidenceConfig(confidence)
  return cfg.label
}

function flatMetadata(meta: any): Record<string, string> {
  if (!meta) return {}
  const result: Record<string, string> = {}
  for (const [key, value] of Object.entries(meta)) {
    if (typeof value === 'object' && value !== null) {
      result[key] = JSON.stringify(value)
    } else {
      result[key] = String(value)
    }
  }
  return result
}
</script>

<style scoped>
.element-detail-panel {
  padding: 0 0 0 12px;
  min-height: 300px;
}
.empty-hint {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 300px;
}
.section-block {
  margin-top: 16px;
}
.section-block-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
  padding-bottom: 4px;
  border-bottom: 1px solid #e4e7ed;
}
.content-preview-box {
  background: #fafafa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 12px;
  max-height: 200px;
  overflow-y: auto;
}
.content-preview-box pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 13px;
  color: #303133;
  font-family: inherit;
}
.no-content {
  color: #c0c4cc;
  font-size: 12px;
}
.json-preview {
  background: #fafafa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 12px;
  margin: 0;
  font-size: 12px;
  max-height: 200px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
