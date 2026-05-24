<template>
  <div class="tab-structure-tree">
    <div v-if="!structureTree" class="empty-tip">
      <el-empty description="暂无结构树数据" :image-size="60" />
    </div>
    <template v-else>
      <div class="tree-layout">
        <!-- 左侧树 -->
        <div class="tree-panel">
          <div class="tree-panel-header">
            <span class="tree-title">元素层级树</span>
            <el-button text size="small" @click="expandAll">展开全部</el-button>
            <el-button text size="small" @click="collapseAll">折叠</el-button>
          </div>
          <el-input
            v-model="filterText"
            placeholder="搜索元素..."
            size="small"
            clearable
            style="margin-bottom: 8px"
          />
          <div class="tree-scroll">
            <el-tree
              ref="treeRef"
              :data="treeData"
              :props="treeProps"
              node-key="id"
              :filter-node-method="filterNode"
              :default-expanded-keys="defaultExpandedKeys"
              highlight-current
              @node-click="handleNodeClick"
            >
              <template #default="{ node, data }">
                <span class="custom-tree-node" :style="{ color: data.color }">
                  <el-icon :size="14" :style="{ color: data.color }">
                    <component :is="data.iconComponent" />
                  </el-icon>
                  <el-tag
                    :color="data.color"
                    size="small"
                    style="color:#fff;border:0;margin:0 4px;padding:0 4px;min-width:32px;text-align:center;font-size:10px;line-height:16px;height:16px"
                  >
                    {{ data.typeLabel }}
                  </el-tag>
                  <span class="node-label">{{ data.label }}</span>
                  <span class="node-confidence" v-if="data.confidence != null && data.confidence > 0">
                    {{ (data.confidence * 100).toFixed(0) }}%
                  </span>
                </span>
              </template>
            </el-tree>
          </div>
        </div>
        <!-- 右侧详情 -->
        <div class="detail-panel">
          <ElementDetailPanel :element="selectedElement" />
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, shallowRef } from 'vue'
import type { ParseElementVO } from '@/types/api'
import { getTypeConfig, getConfidenceConfig } from './elementTypeConfig'
import ElementDetailPanel from './ElementDetailPanel.vue'

import {
  Folder, Document, Grid, Picture, List,
  ChatSquare, Guide, Link, Edit, EditPen,
  Top, Bottom, MoreFilled, QuestionFilled, Cpu
} from '@element-plus/icons-vue'

const props = defineProps<{
  structureTree: any
  parseElements: ParseElementVO[]
}>()

const emit = defineEmits<{
  (e: 'locate-element', elementId: string): void
}>()

const filterText = ref('')
const treeRef = ref<any>(null)
const defaultExpandedKeys = ref<string[]>([])
const selectedElement = ref<ParseElementVO | null>(null)

const treeProps = {
  children: 'children',
  label: 'label',
}

// 图标组件映射
const iconMap: Record<string, any> = {
  Folder, Document, Grid, Picture, List,
  Cpu, ChatSquare, Guide, Link, Edit,
  Top, Bottom, MoreFilled, QuestionFilled, EditPen
}

interface TreeNode {
  id: string
  label: string
  elementType: string
  typeLabel: string
  elementId: string
  level: number
  confidence: number
  color: string
  iconComponent: any
  children: TreeNode[]
}

function convertToTreeData(structureTree: any): TreeNode[] {
  const root = structureTree?.root
  if (!root) return []

  function walk(node: any): TreeNode {
    const config = getTypeConfig(node.type || '')
    const contentText = node.content?.text || node.contentText || ''
    const label = truncateText(contentText, 40)
    return {
      id: node.id || node.elementId,
      label: label || `(${config.label})`,
      elementType: node.type,
      typeLabel: config.label,
      elementId: node.elementId || node.id,
      level: node.level || 0,
      confidence: node.confidence || 0,
      color: config.color,
      iconComponent: iconMap[config.icon] || QuestionFilled,
      children: node.children?.map(walk) || [],
    }
  }

  return [walk(root)]
}

function truncateText(text: string, maxLen: number): string {
  if (!text) return ''
  return text.length > maxLen ? text.slice(0, maxLen) + '...' : text
}

const treeData = computed(() => {
  if (!props.structureTree) return []
  return convertToTreeData(props.structureTree)
})

// 构建 elementId -> ParseElementVO 映射
const elementMap = computed(() => {
  const map = new Map<string, ParseElementVO>()
  for (const el of props.parseElements) {
    map.set(el.elementId, el)
  }
  // Also try mapping from elementId in structure tree
  return map
})

// 收集默认展开的 key（前两层）
function collectExpandedKeys(nodes: TreeNode[], depth: number = 0): string[] {
  const keys: string[] = []
  for (const node of nodes) {
    if (depth < 2) {
      keys.push(node.id)
    }
    if (node.children?.length) {
      keys.push(...collectExpandedKeys(node.children, depth + 1))
    }
  }
  return keys
}

watch(treeData, (val) => {
  if (val.length) {
    defaultExpandedKeys.value = collectExpandedKeys(val)
  }
}, { immediate: true })

function filterNode(value: string, data: any): boolean {
  if (!value) return true
  return data.label.toLowerCase().includes(value.toLowerCase())
}

watch(filterText, (val) => {
  treeRef.value?.filter(val)
})

function handleNodeClick(data: TreeNode) {
  // Look up the element in parseElements by elementId
  const element = elementMap.value.get(data.elementId)
  if (element) {
    selectedElement.value = element
  } else {
    // Build a minimal VO from tree data
    selectedElement.value = {
      id: 0,
      recordId: 0,
      elementId: data.elementId,
      elementType: data.elementType,
      level: data.level,
      contentText: data.label,
      confidence: data.confidence,
      sortOrder: 0,
    } as ParseElementVO
  }
}

function expandAll() {
  const expand = (nodes: TreeNode[]) => {
    for (const node of nodes) {
      treeRef.value?.store?.setCurrentNodeKey?.(node.id)
      if (node.children?.length) {
        treeRef.value?.store?.setExpandedKeys?.(
          [...(treeRef.value?.store?.getExpandedKeys?.() || []), node.id]
        )
        expand(node.children)
      }
    }
  }
  // Simpler: set all expanded based on collected keys
  defaultExpandedKeys.value = collectExpandedKeys(treeData.value, 0)
}

function collapseAll() {
  defaultExpandedKeys.value = []
}

// Expose method for parent to locate element
function locateElement(elementId: string) {
  // Find the tree node and expand to it
  function findPath(nodes: TreeNode[], target: string): string[] | null {
    for (const node of nodes) {
      if (node.elementId === target) return [node.id]
      if (node.children?.length) {
        const path = findPath(node.children, target)
        if (path) return [node.id, ...path]
      }
    }
    return null
  }
  const path = findPath(treeData.value, elementId)
  if (path && treeRef.value) {
    defaultExpandedKeys.value = path
    nextTick(() => {
      treeRef.value?.setCurrentKey(path[path.length - 1])
    })
  }
}

defineExpose({ locateElement })
</script>

<style scoped>
.tab-structure-tree {
  min-height: 400px;
}
.empty-tip {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 300px;
}
.tree-layout {
  display: flex;
  gap: 16px;
  height: calc(100vh - 300px);
  min-height: 400px;
}
.tree-panel {
  width: 380px;
  min-width: 300px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 12px;
  display: flex;
  flex-direction: column;
}
.tree-panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.tree-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  flex: 1;
}
.tree-scroll {
  flex: 1;
  overflow-y: auto;
}
.detail-panel {
  flex: 1;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 12px;
  overflow-y: auto;
}
.custom-tree-node {
  display: flex;
  align-items: center;
  gap: 2px;
  font-size: 12px;
  padding: 1px 0;
}
.node-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.node-confidence {
  font-size: 10px;
  color: #909399;
  margin-left: 4px;
}
</style>
