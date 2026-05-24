<template>
  <div class="page-header">
    <el-button @click="$router.back()">
      <el-icon><ArrowLeft /></el-icon>
      返回
    </el-button>
    <span class="page-title">训练结果详情 - {{ task?.taskName || '' }}</span>
    <span class="page-subtitle" v-if="task">
      模板: {{ task.templateName || '-' }} | 规则集: {{ selectedRecord?.rulesetName || '-' }} | 引擎版本: {{ selectedRecord?.engineVersion || '-' }}
    </span>
  </div>
</template>

<script setup lang="ts">
import { ArrowLeft } from '@element-plus/icons-vue'
import type { TrainTaskVO, ParseRecordDetailVO } from '@/types/api'

defineProps<{
  task: TrainTaskVO | null
  selectedRecord: ParseRecordDetailVO | null
}>()
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.page-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.page-subtitle {
  font-size: 12px;
  color: #909399;
  margin-left: auto;
}
</style>
