<template>
  <div class="tab-diff-compare">
    <!-- 子 Tab 切换 -->
    <el-tabs v-model="activeSubTab" type="card">
      <el-tab-pane label="任务间对比" name="task">
        <div class="diff-selector">
          <el-select v-model="recordIdA" placeholder="记录A" size="small" style="width:220px" filterable>
            <el-option
              v-for="r in parseRecords"
              :key="r.id"
              :label="`#${r.id} ${r.sourceFile} (${r.status})`"
              :value="r.id"
            />
          </el-select>
          <span class="diff-arrow">↔</span>
          <el-select v-model="recordIdB" placeholder="记录B" size="small" style="width:220px" filterable>
            <el-option
              v-for="r in parseRecords"
              :key="r.id"
              :label="`#${r.id} ${r.sourceFile} (${r.status})`"
              :value="r.id"
            />
          </el-select>
          <el-button type="primary" size="small" :disabled="!recordIdA || !recordIdB || recordIdA === recordIdB" @click="handleCompareTask">
            对比
          </el-button>
        </div>
      </el-tab-pane>
      <el-tab-pane label="标准答案对比" name="standard">
        <div class="diff-selector">
          <el-select v-model="recordIdA" placeholder="解析记录" size="small" style="width:220px" filterable>
            <el-option
              v-for="r in parseRecords"
              :key="r.id"
              :label="`#${r.id} ${r.sourceFile}`"
              :value="r.id"
            />
          </el-select>
          <span class="diff-arrow">↔</span>
          <el-select v-model="standardAnswerId" placeholder="标准答案" size="small" style="width:220px" filterable>
            <el-option
              v-for="a in standardAnswers"
              :key="a.id"
              :label="a.answerName + ' (' + a.sourceFile + ')'"
              :value="a.id"
            />
          </el-select>
          <el-button type="primary" size="small" :disabled="!recordIdA || !standardAnswerId" @click="handleCompareStandard">
            对比
          </el-button>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 对比结果 -->
    <div v-if="diffResult" class="diff-result">
      <!-- 概览统计 -->
      <div class="diff-summary">
        <el-card shadow="never" class="summary-card">
          <div class="summary-stats">
            <div class="stat-item">
              <span class="stat-label">总元素</span>
              <span class="stat-value">{{ diffResult.summary.totalA }} → {{ diffResult.summary.totalB }}</span>
              <span class="stat-diff" :class="getChangeClass(diffResult.summary.totalB - diffResult.summary.totalA)">
                ({{ diffResult.summary.totalB - diffResult.summary.totalA > 0 ? '+' : '' }}{{ diffResult.summary.totalB - diffResult.summary.totalA }})
              </span>
            </div>
            <div class="stat-item">
              <span class="stat-label">新增</span>
              <span class="stat-value" style="color:#67C23A">{{ diffResult.summary.added }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">删除</span>
              <span class="stat-value" style="color:#F56C6C">{{ diffResult.summary.removed }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">修改</span>
              <span class="stat-value" style="color:#E6A23C">{{ diffResult.summary.changed }}</span>
            </div>
            <div class="stat-item" v-if="diffResult.summary.accuracy != null">
              <span class="stat-label">准确率</span>
              <el-progress
                :percentage="Math.round(diffResult.summary.accuracy * 100)"
                :stroke-width="14"
                style="width:100px"
              />
            </div>
          </div>
        </el-card>
      </div>

      <!-- 差异列表 -->
      <div class="diff-changes">
        <div class="section-title">差异列表</div>
        <el-table :data="diffResult.structuralChanges" stripe size="small" style="width:100%" max-height="400px">
          <el-table-column label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="getChangeTagType(row.changeType)" size="small">
                {{ getChangeLabel(row.changeType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="elementId" label="元素ID" width="120" />
          <el-table-column label="类型" width="80" align="center">
            <template #default="{ row }">
              {{ row.elementType || row.elementTypeB || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="内容" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.contentText || row.contentB || row.contentA || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="A置信度" width="90" align="center">
            <template #default="{ row }">
              {{ row.confidenceA != null ? (row.confidenceA * 100).toFixed(1) + '%' : (row.confidence != null ? (row.confidence * 100).toFixed(1) + '%' : '-') }}
            </template>
          </el-table-column>
          <el-table-column label="B置信度" width="90" align="center">
            <template #default="{ row }">
              {{ row.confidenceB != null ? (row.confidenceB * 100).toFixed(1) + '%' : (row.confidence != null ? (row.confidence * 100).toFixed(1) + '%' : '-') }}
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!diffResult.structuralChanges?.length" description="无差异项" :image-size="40" />
      </div>
    </div>

    <div v-else-if="!loading" class="diff-empty">
      <el-empty description="请选择对比对象并点击对比" :image-size="50" />
    </div>
    <div v-else class="diff-loading">
      <el-skeleton :rows="4" animated />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { compareTaskDiff, compareStandardDiff, getStandardAnswerList } from '@/api/train'
import type { ParseDiffReportVO, ParseStandardAnswerVO, ParseRecordSimpleVO } from '@/types/api'

const props = defineProps<{
  templateId: number
  parseRecords: ParseRecordSimpleVO[]
}>()

const activeSubTab = ref('task')
const recordIdA = ref<number | null>(null)
const recordIdB = ref<number | null>(null)
const standardAnswerId = ref<number | null>(null)
const standardAnswers = ref<ParseStandardAnswerVO[]>([])
const diffResult = ref<ParseDiffReportVO | null>(null)
const loading = ref(false)

function getChangeTagType(changeType: string): string {
  switch (changeType) {
    case 'added': return 'success'
    case 'removed': return 'danger'
    case 'changed': return 'warning'
    case 'type_changed': return 'info'
    default: return 'info'
  }
}

function getChangeLabel(changeType: string): string {
  switch (changeType) {
    case 'added': return '新增'
    case 'removed': return '删除'
    case 'changed': return '修改'
    case 'type_changed': return '类型变化'
    case 'unchanged': return '未变'
    default: return changeType
  }
}

function getChangeClass(diff: number): string {
  if (diff > 0) return 'text-success'
  if (diff < 0) return 'text-danger'
  return ''
}

async function loadStandardAnswers() {
  if (!props.templateId) return
  try {
    standardAnswers.value = await getStandardAnswerList({ templateId: props.templateId })
  } catch {
    standardAnswers.value = []
  }
}

async function handleCompareTask() {
  if (!recordIdA.value || !recordIdB.value) return
  loading.value = true
  try {
    diffResult.value = await compareTaskDiff({
      recordIdA: recordIdA.value,
      recordIdB: recordIdB.value,
    })
  } catch {
    diffResult.value = null
  } finally {
    loading.value = false
  }
}

async function handleCompareStandard() {
  if (!recordIdA.value || !standardAnswerId.value) return
  loading.value = true
  try {
    diffResult.value = await compareStandardDiff({
      recordId: recordIdA.value,
      standardAnswerId: standardAnswerId.value,
    })
  } catch {
    diffResult.value = null
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadStandardAnswers()
})
</script>

<style scoped>
.tab-diff-compare {
  min-height: 300px;
}
.diff-selector {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.diff-arrow {
  font-size: 18px;
  color: #909399;
}
.diff-result {
  margin-top: 8px;
}
.diff-summary {
  margin-bottom: 16px;
}
.summary-card {
  border: 1px solid #e4e7ed;
}
.summary-stats {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
}
.stat-item {
  display: flex;
  align-items: center;
  gap: 6px;
}
.stat-label {
  font-size: 12px;
  color: #909399;
}
.stat-value {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.stat-diff {
  font-size: 12px;
}
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #e4e7ed;
}
.diff-empty, .diff-loading {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 200px;
}
.text-success { color: #67C23A; }
.text-danger { color: #F56C6C; }
</style>
