<template>
  <div class="train-result-detail">
    <div class="page-header">
      <el-button @click="$router.back()">
        <el-icon><ArrowLeft /></el-icon>
        返回
      </el-button>
      <span class="page-title">训练结果详情 - {{ task?.taskName || '' }}</span>
    </div>

    <div v-if="loading" class="loading-wrapper">
      <el-skeleton :rows="10" animated />
    </div>

    <template v-else-if="task">
      <!-- 基本信息 -->
      <el-descriptions :column="3" size="small" border style="margin-bottom: 16px">
        <el-descriptions-item label="任务名称">{{ task.taskName }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="taskStatusTag(task.status)" size="small">
            {{ taskStatusText(task.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="进度">
          <el-progress
            :percentage="task.status === 'SUCCESS' ? 100 : (task.progress || 0)"
            :stroke-width="12"
            style="width: 160px"
          />
        </el-descriptions-item>
        <el-descriptions-item label="总文件数">{{ task.totalFiles }}</el-descriptions-item>
        <el-descriptions-item label="已处理">{{ task.fileCount }}</el-descriptions-item>
        <el-descriptions-item label="解析记录数">{{ task.parseRecordCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ task.startedAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="完成时间">{{ task.completedAt || '-' }}</el-descriptions-item>
        <el-descriptions-item v-if="task.errorMessage" label="错误信息" :span="3">
          <span style="color: #f56c6c">{{ task.errorMessage }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <!-- 解析结果列表 -->
      <div class="result-section">
        <div class="section-title">解析记录</div>
        <el-table :data="parseRecords" stripe size="small" style="width: 100%">
          <el-table-column prop="id" label="ID" width="60" align="center" />
          <el-table-column prop="sourceFile" label="源文件名" min-width="200" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 'success' ? 'success' : 'danger'" size="small">
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="parsedAt" label="解析时间" width="160" />
        </el-table>
        <el-empty v-if="!parseRecords.length" description="暂无解析记录" :image-size="40" />
      </div>
    </template>

    <div v-else class="empty-tip">
      <el-empty description="任务不存在" :image-size="60" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getTrainTaskDetail } from '@/api/train'
import type { TrainTaskVO, ParseRecordSimpleVO } from '@/types/api'

const route = useRoute()
const taskId = Number(route.params.id || 0)

const loading = ref(true)
const task = ref<TrainTaskVO | null>(null)
const parseRecords = ref<ParseRecordSimpleVO[]>([])

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

onMounted(async () => {
  if (!taskId) {
    loading.value = false
    return
  }
  try {
    const res = await getTrainTaskDetail(taskId)
    task.value = res
    parseRecords.value = res.parseRecords || []
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

.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.loading-wrapper {
  padding: 20px;
}

.result-section {
  margin-top: 8px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #e4e7ed;
}

.empty-tip {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 300px;
}
</style>
