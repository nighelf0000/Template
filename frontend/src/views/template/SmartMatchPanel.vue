<template>
  <div class="smart-match-panel">
    <el-divider content-position="left">智能匹配算法</el-divider>

    <!-- 操作按钮 -->
    <div class="panel-actions">
      <el-button type="primary" size="small" :loading="trainingLoading" @click="handleStartTrain">
        开始训练
      </el-button>
      <el-button size="small" :disabled="!selectedTemplateId" @click="refreshTasks">
        刷新任务
      </el-button>
      <el-button size="small" :disabled="!selectedTemplateId" @click="handleTest">
        测试匹配
      </el-button>
    </div>

    <!-- 训练任务列表 -->
    <div class="section">
      <h4 class="section-title">训练任务</h4>
      <el-table :data="taskList" stripe size="small" style="width: 100%" max-height="200">
        <el-table-column prop="taskName" label="任务名称" min-width="120" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="taskStatusTag(row.status)" size="small">
              {{ taskStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="progress" label="进度" width="80" align="center">
          <template #default="{ row }">
            <span v-if="row.status === 'RUNNING'">{{ row.progress || 0 }}%</span>
            <span v-else-if="row.status === 'SUCCESS'">100%</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="fileCount" label="文件数" width="60" align="center" />
        <el-table-column prop="ruleCount" label="规则数" width="60" align="center" />
        <el-table-column prop="errorMessage" label="错误信息" min-width="150">
          <template #default="{ row }">
            <el-tooltip v-if="row.errorMessage" :content="row.errorMessage" placement="top" :show-after="300">
              <span class="error-text">{{ row.errorMessage.substring(0, 30) }}{{ row.errorMessage.length > 30 ? '...' : '' }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160" />
      </el-table>
      <el-empty v-if="!taskList.length" description="暂无训练任务" :image-size="40" />
    </div>

    <!-- 规则列表 -->
    <div class="section">
      <h4 class="section-title">生成规则</h4>
      <el-table :data="ruleList" stripe size="small" style="width: 100%" max-height="300">
        <el-table-column prop="ruleName" label="规则名称" min-width="100" />
        <el-table-column prop="matchType" label="匹配类型" width="80">
          <template #default="{ row }">
            <el-tag :type="matchTypeTag(row.matchType)" size="small">{{ row.matchType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="matchLevel" label="级别" width="50" align="center" />
        <el-table-column prop="keywords" label="关键词" min-width="150">
          <template #default="{ row }">
            <el-tooltip :content="row.keywords" placement="top" :show-after="300">
              <span>{{ row.keywords && row.keywords.length > 30 ? row.keywords.substring(0, 30) + '...' : row.keywords }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="threshold" label="阈值" width="60" align="center" />
        <el-table-column prop="isActive" label="启用" width="60" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.isActive"
              :active-value="1"
              :inactive-value="0"
              size="small"
              @change="handleToggle(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text @click="handleEditRule(row)">编辑</el-button>
            <el-button size="small" text type="danger" @click="handleDeleteRule(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!ruleList.length" description="暂无生成规则" :image-size="40" />
    </div>

    <!-- 编辑规则对话框 -->
    <el-dialog
      v-model="editDialogVisible"
      title="编辑规则"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form ref="editFormRef" :model="editForm" label-width="100px">
        <el-form-item label="规则名称" prop="ruleName">
          <el-input v-model="editForm.ruleName" maxlength="200" />
        </el-form-item>
        <el-form-item label="匹配类型" prop="matchType">
          <el-select v-model="editForm.matchType" style="width: 100%">
            <el-option label="COVER — 封面" value="COVER" />
            <el-option label="TOC — 目录" value="TOC" />
            <el-option label="TITLE — 标题" value="TITLE" />
            <el-option label="BODY — 正文" value="BODY" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题级别" prop="matchLevel" v-if="editForm.matchType === 'TITLE'">
          <el-input-number v-model="editForm.matchLevel" :min="1" :max="9" :step="1" />
        </el-form-item>
        <el-form-item label="关联样式" prop="styleRuleId">
          <el-select v-model="editForm.styleRuleId" placeholder="选择样式规则" style="width: 100%" clearable>
            <el-option
              v-for="rule in styleRuleList"
              :key="rule.id"
              :label="rule.name"
              :value="rule.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="相似度阈值" prop="threshold">
          <el-slider
            v-model="editForm.threshold"
            :min="0.1"
            :max="1.0"
            :step="0.05"
            show-input
            input-size="small"
          />
        </el-form-item>
        <el-form-item label="启用状态" prop="isActive">
          <el-switch v-model="editForm.isActive" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSubmitting" @click="handleEditSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 测试对话框 -->
    <SmartMatchTestDialog
      v-if="testDialogVisible"
      v-model:visible="testDialogVisible"
      :template-id="selectedTemplateId"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  startSmartTrain,
  getSmartTaskList,
  getSmartRuleList,
  toggleSmartRule,
  updateSmartRule,
  deleteSmartRule,
  getRuleList
} from '@/api/template'
import type { SmartMatchTask, SmartMatchRule, TemplateRule } from '@/types/api'
import SmartMatchTestDialog from './SmartMatchTestDialog.vue'

const props = defineProps<{
  selectedTemplateId: number | null
}>()

const emit = defineEmits<{
  (e: 'update:selectedTemplateId', val: number | null): void
}>()

// 状态
const trainingLoading = ref(false)
const taskList = ref<SmartMatchTask[]>([])
const ruleList = ref<SmartMatchRule[]>([])
const styleRuleList = ref<TemplateRule[]>([])

// 编辑对话框
const editDialogVisible = ref(false)
const editSubmitting = ref(false)
const editFormRef = ref<any>(null)
const editingRuleId = ref<number | null>(null)
const editForm = ref({
  ruleName: '',
  matchType: 'TITLE',
  matchLevel: 1,
  styleRuleId: undefined as number | undefined,
  threshold: 0.3,
  isActive: 1
})

// 测试对话框
const testDialogVisible = ref(false)

// 轮询定时器
let pollTimer: ReturnType<typeof setInterval> | null = null

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

function matchTypeTag(type: string): string {
  switch (type) {
    case 'COVER': return 'warning'
    case 'TOC': return ''
    case 'TITLE': return 'primary'
    case 'BODY': return 'success'
    default: return 'info'
  }
}

async function loadStyleRules() {
  if (!props.selectedTemplateId) return
  try {
    const res = await getRuleList(props.selectedTemplateId)
    styleRuleList.value = res || []
  } catch (e) {
    styleRuleList.value = []
  }
}

async function loadTasks() {
  if (!props.selectedTemplateId) return
  try {
    const res = await getSmartTaskList(props.selectedTemplateId, { page: 1, size: 20 })
    taskList.value = res.records || []
  } catch (e) {
    taskList.value = []
  }
}

async function loadRules() {
  if (!props.selectedTemplateId) return
  try {
    const res = await getSmartRuleList(props.selectedTemplateId, { page: 1, size: 50 })
    ruleList.value = res.records || []
  } catch (e) {
    ruleList.value = []
  }
}

async function refreshAll() {
  await loadTasks()
  await loadRules()
  await loadStyleRules()
}

// 开始训练
async function handleStartTrain() {
  if (!props.selectedTemplateId) {
    ElMessage.warning('请先选择模板')
    return
  }
  trainingLoading.value = true
  try {
    const task = await startSmartTrain(props.selectedTemplateId)
    ElMessage.success('训练任务已创建')
    await loadTasks()
    // 开始轮询任务状态
    startPolling()
  } catch (e) {
    // 错误已在拦截器中处理
  } finally {
    trainingLoading.value = false
  }
}

// 轮询任务状态
function startPolling() {
  stopPolling()
  pollTimer = setInterval(async () => {
    await loadTasks()
    // 检查是否有正在运行的任务
    const running = taskList.value.find(t => t.status === 'PENDING' || t.status === 'RUNNING')
    if (!running) {
      // 所有任务已完成，停止轮询并刷新规则
      stopPolling()
      await loadRules()
    }
  }, 3000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function refreshTasks() {
  loadTasks()
}

// 测试
function handleTest() {
  if (!props.selectedTemplateId) {
    ElMessage.warning('请先选择模板')
    return
  }
  testDialogVisible.value = true
}

// 切换启用状态
async function handleToggle(row: SmartMatchRule) {
  if (!props.selectedTemplateId || !row.id) return
  try {
    await toggleSmartRule(props.selectedTemplateId, row.id)
    ElMessage.success('状态已更新')
  } catch (e) {
    // 回滚
    row.isActive = row.isActive === 1 ? 0 : 1
  }
}

// 编辑规则
function handleEditRule(row: SmartMatchRule) {
  editingRuleId.value = row.id!
  editForm.value = {
    ruleName: row.ruleName || '',
    matchType: row.matchType || 'TITLE',
    matchLevel: row.matchLevel ?? 1,
    styleRuleId: row.styleRuleId ?? undefined,
    threshold: row.threshold ?? 0.3,
    isActive: row.isActive ?? 1
  }
  editDialogVisible.value = true
}

async function handleEditSubmit() {
  if (!props.selectedTemplateId || !editingRuleId.value) return
  editSubmitting.value = true
  try {
    await updateSmartRule(props.selectedTemplateId, editingRuleId.value, { ...editForm.value })
    ElMessage.success('规则已更新')
    editDialogVisible.value = false
    await loadRules()
  } catch (e) {
    // 错误已在拦截器中处理
  } finally {
    editSubmitting.value = false
  }
}

// 删除规则
async function handleDeleteRule(row: SmartMatchRule) {
  if (!props.selectedTemplateId || !row.id) return
  try {
    await ElMessageBox.confirm('确定删除该规则吗？', '确认删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteSmartRule(props.selectedTemplateId, row.id)
    ElMessage.success('规则已删除')
    await loadRules()
  } catch (e) {
    // 取消删除或错误不处理
  }
}

watch(() => props.selectedTemplateId, (val) => {
  if (val) {
    refreshAll()
  } else {
    taskList.value = []
    ruleList.value = []
  }
})

onMounted(() => {
  if (props.selectedTemplateId) {
    refreshAll()
  }
})

onUnmounted(() => {
  stopPolling()
})
</script>

<style scoped>
.smart-match-panel {
  margin-top: 8px;
}

.panel-actions {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.section {
  margin-bottom: 16px;
}

.section-title {
  margin: 0 0 8px;
  font-size: 14px;
  color: #606266;
  font-weight: 500;
}

.error-text {
  color: #f56c6c;
  font-size: 12px;
}
</style>
