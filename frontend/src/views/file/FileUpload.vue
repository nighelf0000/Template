<template>
  <div v-loading="loading">
    <div style="margin-bottom: 16px">
      <h2>文件上传</h2>
    </div>

    <!-- 上传区域 -->
    <el-card style="margin-bottom: 20px">
      <template #header><span>上传 Word 文档</span></template>
      <el-form :model="uploadForm" label-width="100px">
        <el-form-item label="关联模板">
          <el-select
            v-model="uploadForm.templateId"
            placeholder="选择关联模板（可选）"
            style="width: 400px"
            clearable
          >
            <el-option
              v-for="tpl in templateList"
              :key="tpl.id"
              :label="tpl.name"
              :value="tpl.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="上传文件">
          <el-upload
            ref="uploadRef"
            drag
            accept=".docx"
            :auto-upload="false"
            :show-file-list="true"
            :limit="1"
            :on-change="handleFileChange"
            :on-exceed="() => ElMessage.warning('每次只能上传一个文件')"
          >
            <el-icon class="el-icon--upload"><upload-filled /></el-icon>
            <div class="el-upload__text">将 .docx 文件拖拽到此处，或 <em>点击上传</em></div>
          </el-upload>
        </el-form-item>
        <el-form-item v-if="selectedFile">
          <el-button type="primary" :loading="uploading" @click="handleUpload">开始上传</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 文件列表 -->
    <el-card>
      <template #header><span>文件列表</span></template>
      <el-table :data="fileList" stripe style="width: 100%">
        <el-table-column prop="originalName" label="文件名" min-width="200" />
        <el-table-column label="大小" width="100">
          <template #default="{ row }">{{ formatSize(row.originalSize) }}</template>
        </el-table-column>
        <el-table-column prop="templateName" label="关联模板" width="150" />
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="上传时间" width="170" />
        <el-table-column label="操作" width="400" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleParse(row)">解析</el-button>
            <el-button type="success" link size="small" @click="handlePreview(row)">预览</el-button>
            <el-button type="warning" link size="small" @click="handleExport(row)">导出</el-button>
            <el-button type="info" link size="small" @click="handleDownload(row)">下载</el-button>
            <el-button link size="small" @click="showEditDialog(row)">修改</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div style="margin-top: 16px; display: flex; justify-content: center">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadFiles"
          @size-change="loadFiles"
        />
      </div>
    </el-card>

    <!-- 修改关联模板对话框 -->
    <el-dialog v-model="editDialogVisible" title="修改关联模板" width="450px">
      <el-form label-width="80px">
        <el-form-item label="文件名">
          <span>{{ editingFile?.originalName }}</span>
        </el-form-item>
        <el-form-item label="关联模板">
          <el-select
            v-model="editTemplateId"
            placeholder="选择模板"
            style="width: 100%"
            clearable
          >
            <el-option
              v-for="tpl in templateList"
              :key="tpl.id"
              :label="tpl.name"
              :value="tpl.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSaving" @click="handleEditSave">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { UploadInstance } from 'element-plus'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { getFileList, uploadFile, parseFile, exportFile, downloadFile, updateFileTemplate, deleteFile } from '@/api/file'
import { getTemplateList } from '@/api/template'
import type { UploadFile, TemplateConfig } from '@/types/api'

const router = useRouter()
const loading = ref(false)
const uploading = ref(false)

const templateList = ref<TemplateConfig[]>([])
const fileList = ref<UploadFile[]>([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const uploadRef = ref<UploadInstance>()
const selectedFile = ref<File | null>(null)

const uploadForm = ref({ templateId: undefined as number | undefined })

// 修改模板对话框
const editDialogVisible = ref(false)
const editSaving = ref(false)
const editingFile = ref<UploadFile | null>(null)
const editTemplateId = ref<number | undefined>(undefined)

function handleFileChange(file: any) {
  selectedFile.value = file.raw
}

async function handleUpload() {
  if (!selectedFile.value) {
    ElMessage.warning('请选择文件')
    return
  }
  uploading.value = true
  try {
    await uploadFile(selectedFile.value, uploadForm.value.templateId)
    ElMessage.success('上传成功')
    selectedFile.value = null
    uploadRef.value?.clearFiles()
    await loadFiles()
  } catch (e) {
    // 错误已在拦截器中处理
  } finally {
    uploading.value = false
  }
}

async function loadFiles() {
  loading.value = true
  try {
    const res = await getFileList({ page: currentPage.value, size: pageSize.value })
    fileList.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
  }
}

async function loadTemplates() {
  try {
    const res = await getTemplateList({ page: 1, size: 999 })
    templateList.value = res.records || []
  } catch (e) {
    // 错误已在拦截器中处理
  }
}

async function handleParse(row: UploadFile) {
  try {
    await parseFile(row.id!)
    ElMessage.success('解析成功')
    await loadFiles()
  } catch (e) {
    // 错误已在拦截器中处理
  }
}

function handlePreview(row: UploadFile) {
  router.push(`/parse-preview?fileId=${row.id}`)
}

async function handleExport(row: UploadFile) {
  try {
    await exportFile(row.id!)
    ElMessage.success('导出成功')
    await loadFiles()
  } catch (e) {
    // 错误已在拦截器中处理
  }
}

async function handleDownload(row: UploadFile) {
  try {
    const blob = await downloadFile(row.id!)
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = row.outputName || row.originalName || 'download.docx'
    a.click()
    window.URL.revokeObjectURL(url)
  } catch (e) {
    // 错误已在拦截器中处理
  }
}

function showEditDialog(row: UploadFile) {
  editingFile.value = row
  editTemplateId.value = row.templateId
  editDialogVisible.value = true
}

async function handleEditSave() {
  if (!editingFile.value) return
  editSaving.value = true
  try {
    await updateFileTemplate(editingFile.value.id!, editTemplateId.value)
    ElMessage.success('修改成功')
    editDialogVisible.value = false
    await loadFiles()
  } catch (e) {
    // 错误已在拦截器中处理
  } finally {
    editSaving.value = false
  }
}

async function handleDelete(row: UploadFile) {
  try {
    await ElMessageBox.confirm(`确定删除文件「${row.originalName}」吗？删除后不可恢复。`, '确认删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteFile(row.id!)
    ElMessage.success('删除成功')
    await loadFiles()
  } catch (e) {
    // 取消删除或错误不处理
  }
}

function statusType(status: string | undefined): string {
  const map: Record<string, string> = {
    UPLOADED: 'primary',
    PARSED: 'success',
    ADJUSTED: 'warning',
    EXPORTED: 'info',
    PARSE_FAILED: 'danger'
  }
  return map[status || ''] || 'info'
}

function formatSize(size: number | undefined): string {
  if (!size) return '-'
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
  return (size / (1024 * 1024)).toFixed(1) + ' MB'
}

onMounted(() => {
  loadFiles()
  loadTemplates()
})
</script>
