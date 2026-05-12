<template>
  <div class="train-task-page">
    <!-- 顶部：模板选择器 + 操作按钮 -->
    <div class="page-header">
      <div class="header-left">
        <el-select
          v-model="selectedTemplateId"
          placeholder="请选择模板"
          style="width: 240px"
          @change="handleTemplateChange"
        >
          <el-option
            v-for="tpl in templateList"
            :key="tpl.id"
            :label="tpl.name"
            :value="tpl.id"
          />
        </el-select>
      </div>
      <div class="header-right">
        <el-button type="primary" :disabled="!selectedTemplateId" @click="handleUpload">
          <el-icon><Upload /></el-icon>
          上传文件
        </el-button>
        <el-button type="success" :disabled="!selectedTemplateId || trainingLoading" :loading="trainingLoading" @click="handleStartTrain">
          <el-icon><VideoPlay /></el-icon>
          开始训练
        </el-button>
        <el-button :disabled="!selectedTemplateId" @click="refreshAll">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <!-- 主内容区：两栏布局 -->
    <div class="page-body">
      <!-- 左侧：训练任务列表 -->
      <div class="left-panel">
        <div class="panel-title">训练任务</div>
        <div class="task-list">
          <div
            v-for="task in taskList"
            :key="task.id"
            class="task-item"
            :class="{ active: selectedTaskId === task.id }"
            @click="selectTask(task)"
          >
            <div class="task-item-header">
              <span class="task-name">{{ task.taskName }}</span>
              <el-tag :type="taskStatusTag(task.status)" size="small">
                {{ taskStatusText(task.status) }}
              </el-tag>
            </div>
            <div class="task-item-info">
              <span v-if="task.status === 'RUNNING'" class="task-progress">
                进度 {{ task.progress || 0 }}%
              </span>
              <span class="task-time">{{ task.createdAt }}</span>
            </div>
            <el-progress
              v-if="task.status === 'RUNNING'"
              :percentage="task.progress || 0"
              :stroke-width="4"
              style="margin-top: 4px"
            />
          </div>
          <el-empty v-if="!taskList.length" description="暂无训练任务" :image-size="40" />
        </div>
        <!-- 底部分页 -->
        <div class="pagination-wrapper" v-if="taskTotal > taskPageSize">
          <el-pagination
            v-model:current-page="taskPage"
            :page-size="taskPageSize"
            :total="taskTotal"
            layout="prev, pager, next"
            small
            @current-change="loadTasks"
          />
        </div>
      </div>

      <!-- 右侧：任务详情 -->
      <div class="right-panel">
        <template v-if="selectedTask">
          <!-- 基本信息区域 -->
          <div class="detail-section">
            <div class="section-title">基本信息</div>
            <el-descriptions :column="2" size="small" border>
              <el-descriptions-item label="任务名称">{{ selectedTask.taskName }}</el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag :type="taskStatusTag(selectedTask.status)" size="small">
                  {{ taskStatusText(selectedTask.status) }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="模板ID">{{ selectedTask.templateId }}</el-descriptions-item>
              <el-descriptions-item label="进度">
                <el-progress
                  :percentage="selectedTask.status === 'SUCCESS' ? 100 : (selectedTask.progress || 0)"
                  :stroke-width="12"
                  style="width: 160px"
                />
              </el-descriptions-item>
              <el-descriptions-item label="总文件数">{{ selectedTask.totalFiles }}</el-descriptions-item>
              <el-descriptions-item label="已处理">{{ selectedTask.fileCount }}</el-descriptions-item>
              <el-descriptions-item label="开始时间">{{ selectedTask.startedAt || '-' }}</el-descriptions-item>
              <el-descriptions-item label="完成时间">{{ selectedTask.completedAt || '-' }}</el-descriptions-item>
              <el-descriptions-item v-if="selectedTask.errorMessage" label="错误信息" :span="2">
                <span style="color: #f56c6c">{{ selectedTask.errorMessage }}</span>
              </el-descriptions-item>
            </el-descriptions>
          </div>

          <!-- 文件列表/识别结果区域 -->
          <div class="detail-section">
            <div class="section-title">训练文件列表</div>
            <el-table :data="trainFileList" stripe size="small" style="width: 100%" max-height="300" v-loading="fileListLoading">
              <el-table-column prop="originalName" label="文件名" min-width="200" show-overflow-tooltip />
              <el-table-column prop="originalSize" label="大小" width="100" align="center">
                <template #default="{ row }">
                  {{ formatFileSize(row.originalSize) }}
                </template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="100" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.status === 'UPLOADED' ? 'success' : 'info'" size="small">
                    {{ row.status === 'UPLOADED' ? '已上传' : row.status }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="createdAt" label="上传时间" width="160" />
              <el-table-column label="操作" width="100" align="center" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" text type="danger" @click="handleDeleteFile(row)">
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-if="!trainFileList.length" description="暂无训练文件" :image-size="40" />
          </div>
        </template>

        <!-- 未选中任务时的提示 -->
        <div v-else class="empty-tip">
          <el-empty description="请从左侧选择一个训练任务" :image-size="60" />
        </div>
      </div>
    </div>

    <!-- 上传文件对话框 -->
    <el-dialog
      v-model="uploadDialogVisible"
      title="上传训练文件"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-upload
        ref="uploadRef"
        drag
        multiple
        :auto-upload="false"
        accept=".docx"
        :file-list="uploadFileList"
        :on-change="handleFileChange"
        :limit="20"
      >
        <el-icon class="upload-icon" :size="40"><UploadFilled /></el-icon>
        <div class="upload-text">将 .docx 文件拖拽到此处，或点击选择文件</div>
        <template #tip>
          <div class="upload-tip">仅支持 .docx 格式文件，单次最多上传 20 个文件</div>
        </template>
      </el-upload>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="submitUpload">
          开始上传
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, VideoPlay, Refresh, UploadFilled } from '@element-plus/icons-vue'
import { getTemplateList } from '@/api/template'
import {
  uploadTrainFile,
  getTrainFileList,
  deleteTrainFile,
  startTrainTask,
  getTrainTaskPage,
  getTrainTaskDetail
} from '@/api/train'
import type { TemplateConfig, TrainTaskVO, TrainFileVO } from '@/types/api'

// 模板列表
const templateList = ref<TemplateConfig[]>([])
const selectedTemplateId = ref<number | null>(null)

// 训练任务
const taskList = ref<TrainTaskVO[]>([])
const selectedTaskId = ref<number | null>(null)
const selectedTask = ref<TrainTaskVO | null>(null)
const taskPage = ref(1)
const taskPageSize = 10
const taskTotal = ref(0)

// 训练文件
const trainFileList = ref<TrainFileVO[]>([])
const fileListLoading = ref(false)

// 上传相关
const uploadDialogVisible = ref(false)
const uploading = ref(false)
const uploadFileList = ref<any[]>([])
const uploadRef = ref<any>(null)

// 训练加载状态
const trainingLoading = ref(false)

// 轮询定时器
let pollTimer: ReturnType<typeof setInterval> | null = null

// 状态标签样式
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

function formatFileSize(size: number): string {
  if (!size) return '-'
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
  return (size / (1024 * 1024)).toFixed(1) + ' MB'
}

// 加载模板列表
async function loadTemplates() {
  try {
    const res = await getTemplateList({ page: 1, size: 100 })
    templateList.value = res.records || []
  } catch (e) {
    templateList.value = []
  }
}

// 加载训练任务列表
async function loadTasks() {
  if (!selectedTemplateId.value) return
  try {
    const params: any = { page: taskPage.value, size: taskPageSize }
    if (selectedTemplateId.value) {
      params.templateId = selectedTemplateId.value
    }
    const res = await getTrainTaskPage(params)
    taskList.value = res.records || []
    taskTotal.value = res.total || 0
  } catch (e) {
    taskList.value = []
  }
}

// 加载训练文件列表
async function loadFiles() {
  if (!selectedTemplateId.value) return
  fileListLoading.value = true
  try {
    const res = await getTrainFileList({ templateId: selectedTemplateId.value, page: 1, size: 100 })
    trainFileList.value = res.records || []
  } catch (e) {
    trainFileList.value = []
  } finally {
    fileListLoading.value = false
  }
}

// 选择任务
async function selectTask(task: TrainTaskVO) {
  selectedTaskId.value = task.id!
  try {
    const res = await getTrainTaskDetail(task.id!)
    selectedTask.value = res
  } catch (e) {
    selectedTask.value = task
  }
}

// 刷新所有数据
async function refreshAll() {
  await loadTasks()
  await loadFiles()
}

// 模板切换
function handleTemplateChange(val: number | null) {
  selectedTemplateId.value = val
  selectedTaskId.value = null
  selectedTask.value = null
  taskPage.value = 1
  if (val) {
    refreshAll()
  } else {
    taskList.value = []
    trainFileList.value = []
  }
}

// 上传文件
function handleUpload() {
  uploadFileList.value = []
  uploadDialogVisible.value = true
}

function handleFileChange(uploadFile: any) {
  // 校验文件格式
  const name = uploadFile.name || ''
  if (!name.toLowerCase().endsWith('.docx')) {
    ElMessage.warning('仅支持 .docx 格式文件')
    uploadRef.value?.handleRemove(uploadFile)
    return
  }
}

async function submitUpload() {
  if (!selectedTemplateId.value) {
    ElMessage.warning('请先选择模板')
    return
  }
  const files = uploadRef.value?.uploadFiles || []
  if (files.length === 0) {
    ElMessage.warning('请选择要上传的文件')
    return
  }

  uploading.value = true
  let successCount = 0
  let failCount = 0

  for (const file of files) {
    try {
      await uploadTrainFile(file.raw, selectedTemplateId.value)
      successCount++
    } catch (e) {
      failCount++
    }
  }

  uploading.value = false
  uploadDialogVisible.value = false

  if (successCount > 0) {
    ElMessage.success(`成功上传 ${successCount} 个文件${failCount > 0 ? `，${failCount} 个失败` : ''}`)
    await loadFiles()
  } else {
    ElMessage.error('上传失败')
  }
}

// 删除文件
async function handleDeleteFile(row: TrainFileVO) {
  try {
    await ElMessageBox.confirm(`确定删除文件「${row.originalName}」吗？`, '确认删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteTrainFile(row.id)
    ElMessage.success('文件已删除')
    await loadFiles()
  } catch (e) {
    // 取消或错误不处理
  }
}

// 开始训练
async function handleStartTrain() {
  if (!selectedTemplateId.value) {
    ElMessage.warning('请先选择模板')
    return
  }
  try {
    await ElMessageBox.confirm('确定开始训练吗？将使用当前模板下所有已上传的文件进行批量识别。', '确认训练', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'info'
    })
  } catch (e) {
    return
  }

  trainingLoading.value = true
  try {
    await startTrainTask({
      templateId: selectedTemplateId.value,
      taskName: `模板训练-${selectedTemplateId.value}-${new Date().toLocaleString('zh-CN', { hour12: false })}`
    })
    ElMessage.success('训练任务已创建')
    await loadTasks()
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
    const running = taskList.value.find(t => t.status === 'PENDING' || t.status === 'RUNNING')
    if (!running) {
      stopPolling()
      // 如果有选中的任务，刷新详情
      if (selectedTaskId.value) {
        try {
          const res = await getTrainTaskDetail(selectedTaskId.value)
          selectedTask.value = res
        } catch (e) {
          // ignore
        }
      }
    }
  }, 3000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

onMounted(() => {
  loadTemplates()
})

onUnmounted(() => {
  stopPolling()
})
</script>

<style scoped>
.train-task-page {
  padding: 16px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.header-left,
.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.page-body {
  flex: 1;
  display: flex;
  gap: 16px;
  min-height: 0;
}

.left-panel {
  width: 320px;
  min-width: 280px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  display: flex;
  flex-direction: column;
}

.panel-title {
  padding: 12px 16px;
  font-weight: 600;
  font-size: 14px;
  border-bottom: 1px solid #e4e7ed;
  background: #f5f7fa;
}

.task-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.task-item {
  padding: 10px 12px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.task-item:hover {
  border-color: #409eff;
  background: #ecf5ff;
}

.task-item.active {
  border-color: #409eff;
  background: #ecf5ff;
}

.task-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.task-name {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  margin-right: 8px;
}

.task-item-info {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #909399;
}

.task-progress {
  color: #e6a23c;
}

.pagination-wrapper {
  padding: 8px 16px;
  border-top: 1px solid #e4e7ed;
  display: flex;
  justify-content: center;
}

.right-panel {
  flex: 1;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 16px;
  overflow-y: auto;
}

.detail-section {
  margin-bottom: 20px;
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
  height: 100%;
}

.upload-icon {
  margin-bottom: 8px;
}

.upload-text {
  font-size: 14px;
  color: #606266;
}

.upload-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
</style>
