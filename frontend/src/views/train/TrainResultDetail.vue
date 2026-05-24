<template>
  <div class="train-result-detail">
    <!-- 顶部导航 -->
    <PageHeader :task="task" :selected-record="selectedRecord" />

    <div v-if="loading" class="loading-wrapper">
      <el-skeleton :rows="10" animated />
    </div>

    <template v-else-if="task">
      <!-- 基本信息 -->
      <el-descriptions :column="4" size="small" border style="margin-bottom: 16px">
        <el-descriptions-item label="任务名称">{{ task.taskName }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="taskStatusTag(task.status)" size="small">
            {{ taskStatusText(task.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="总文件数">{{ task.totalFiles }}</el-descriptions-item>
        <el-descriptions-item label="已处理">{{ task.fileCount }}</el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ task.startedAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="完成时间">{{ task.completedAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="解析记录数">{{ task.parseRecordCount || 0 }}</el-descriptions-item>
        <el-descriptions-item v-if="task.errorMessage" label="错误信息" :span="2">
          <span style="color: #f56c6c">{{ task.errorMessage }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <!-- 解析记录列表 -->
      <div class="record-selector">
        <span class="section-title">选择解析记录</span>
        <template v-if="parseRecords.length > 0">
          <el-radio-group v-model="selectedRecordId" size="small" @change="handleRecordChange">
            <el-radio-button
              v-for="rec in parseRecords"
              :key="rec.id"
              :value="rec.id"
            >
              #{{ rec.id }} {{ rec.sourceFile }}
              <el-tag
                :type="rec.status === 'success' ? 'success' : 'danger'"
                size="small"
                style="margin-left:4px"
              >
                {{ rec.status }}
              </el-tag>
            </el-radio-button>
          </el-radio-group>
        </template>
        <el-empty v-else description="该任务暂无解析记录，可能训练尚未完成或训练失败" :image-size="40" />
      </div>

      <!-- Tab 切换 -->
      <template v-if="selectedRecordId && selectedRecord">
        <el-tabs v-model="activeTab" type="border-card" style="margin-top:12px">
        <el-tab-pane label="结构树视图" name="structure-tree">
          <TabStructureTree
            ref="structureTreeRef"
            :structure-tree="selectedRecord?.structureTree"
            :parse-elements="parseElementList"
          />
        </el-tab-pane>
        <el-tab-pane label="元素明细" name="element-list">
          <TabElementList
            ref="elementListRef"
            :record-id="selectedRecordId"
            @locate-element="handleLocateElement"
            @switch-tab="activeTab = $event"
          />
        </el-tab-pane>
        <el-tab-pane label="差异对比" name="diff-compare">
          <TabDiffCompare
            :template-id="task.templateId"
            :parse-records="parseRecords"
          />
        </el-tab-pane>
      </el-tabs>
      </template>
      <el-empty v-else-if="!selectedRecordId && parseRecords.length > 0" description="请选择一个解析记录查看详情" :image-size="40" style="margin-top:20px" />
    </template>

    <div v-else class="empty-tip">
      <el-empty description="任务不存在" :image-size="60" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getTrainTaskDetail, getParseRecordDetail } from '@/api/train'
import { getParseElementList } from '@/api/parseElement'
import type { TrainTaskVO, ParseRecordSimpleVO, ParseRecordDetailVO, ParseElementVO } from '@/types/api'

import PageHeader from '@/components/train/PageHeader.vue'
import TabStructureTree from '@/components/train/TabStructureTree.vue'
import TabElementList from '@/components/train/TabElementList.vue'
import TabDiffCompare from '@/components/train/TabDiffCompare.vue'

const route = useRoute()
const taskId = Number(route.params.id || 0)

const loading = ref(true)
const task = ref<TrainTaskVO | null>(null)
const parseRecords = ref<ParseRecordSimpleVO[]>([])
const selectedRecordId = ref<number>(0)
const selectedRecord = ref<ParseRecordDetailVO | null>(null)
const parseElementList = ref<ParseElementVO[]>([])
const activeTab = ref('structure-tree')
const structureTreeRef = ref<any>(null)
const elementListRef = ref<any>(null)

function taskStatusTag(status: string): string {
  switch (status) {
    case 'PENDING': return 'info'
    case 'RUNNING': return 'warning'
    case 'SUCCESS': return 'success'
    case 'FAILED': return 'danger'
    default: return 'info'
  }
}

function taskStatusText(status: string): string {
  switch (status) {
    case 'PENDING': return '等待中'
    case 'RUNNING': return '训练中'
    case 'SUCCESS': return '已完成'
    case 'FAILED': return '失败'
    default: return status
  }
}

async function loadRecordDetail(recordId: number) {
  if (!recordId) return
  try {
    selectedRecord.value = await getParseRecordDetail(recordId)
    // Also load parse elements for tree detail panel
    const res: any = await getParseElementList({ recordId, page: 1, size: 9999 })
    parseElementList.value = res.records || []
  } catch {
    selectedRecord.value = null
    parseElementList.value = []
  }
}

function handleRecordChange(recordId: number) {
  loadRecordDetail(recordId)
}

function handleLocateElement(elementId: string) {
  if (structureTreeRef.value) {
    structureTreeRef.value.locateElement(elementId)
  }
}

onMounted(async () => {
  if (!taskId) {
    loading.value = false
    return
  }
  try {
    const res: any = await getTrainTaskDetail(taskId)
    task.value = res
    parseRecords.value = res.parseRecords || []
    // Auto-select first parse record
    if (parseRecords.value.length > 0) {
      selectedRecordId.value = parseRecords.value[0].id
      await loadRecordDetail(selectedRecordId.value)
    }
  } catch (e) {
    task.value = null
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.train-result-detail {
  padding: 16px;
}

.loading-wrapper {
  padding: 20px;
}

.record-selector {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  white-space: nowrap;
}

.empty-tip {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 300px;
}
</style>
