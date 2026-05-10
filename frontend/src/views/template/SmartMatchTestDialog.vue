<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="$emit('update:visible', $event)"
    title="智能匹配测试"
    width="800px"
    :close-on-click-modal="false"
    @closed="handleClosed"
  >
    <div class="test-upload">
      <el-upload
        ref="uploadRef"
        :auto-upload="false"
        :show-file-list="true"
        :limit="1"
        accept=".docx,.doc"
        :on-exceed="handleExceed"
        @change="handleFileChange"
      >
        <el-button size="small" type="primary">选择 Word 文件</el-button>
        <template #tip>
          <span class="upload-tip">请上传 .docx 格式文件进行测试</span>
        </template>
      </el-upload>
      <el-button
        size="small"
        type="success"
        :loading="testing"
        :disabled="!hasFile"
        @click="handleTest"
        style="margin-top: 12px"
      >
        开始测试
      </el-button>
    </div>

    <el-divider />

    <!-- 测试结果 -->
    <div v-if="results.length > 0" class="test-results">
      <h4 class="results-title">匹配结果（共 {{ results.length }} 个段落）</h4>

      <!-- 统计汇总 -->
      <div class="summary">
        <el-tag size="small" type="warning" class="summary-tag">
          封面: {{ countByType('COVER') }}
        </el-tag>
        <el-tag size="small" class="summary-tag">
          目录: {{ countByType('TOC') }}
        </el-tag>
        <el-tag size="small" type="primary" class="summary-tag">
          标题: {{ countByType('TITLE') }}
        </el-tag>
        <el-tag size="small" type="success" class="summary-tag">
          正文: {{ countByType('BODY') }}
        </el-tag>
        <el-tag size="small" type="danger" class="summary-tag">
          未匹配: {{ countByType('UNKNOWN') }}
        </el-tag>
      </div>

      <el-table :data="results" stripe size="small" style="width: 100%" max-height="400">
        <el-table-column prop="paragraphIndex" label="#" width="50" align="center" />
        <el-table-column prop="text" label="段落文本" min-width="200">
          <template #default="{ row }">
            <el-tooltip :content="row.text" placement="top" :show-after="300">
              <span>{{ row.text.length > 60 ? row.text.substring(0, 60) + '...' : row.text }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="matchedType" label="匹配类型" width="90">
          <template #default="{ row }">
            <el-tag :type="matchTypeTag(row.matchedType)" size="small">{{ row.matchedType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="matchLevel" label="级别" width="50" align="center" />
        <el-table-column prop="ruleName" label="匹配样式" min-width="100" />
        <el-table-column prop="confidence" label="置信度" width="80" align="center">
          <template #default="{ row }">
            <span :style="{ color: confidenceColor(row.confidence) }">
              {{ (row.confidence * 100).toFixed(0) }}%
            </span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-empty v-else-if="tested" description="测试完成，无匹配结果" :image-size="60" />

    <template #footer>
      <el-button @click="$emit('update:visible', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { testSmartMatch } from '@/api/template'
import type { SmartMatchTestResult } from '@/types/api'
import type { UploadInstance, UploadFile } from 'element-plus'

const props = defineProps<{
  visible: boolean
  templateId: number | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
}>()

const uploadRef = ref<UploadInstance | null>(null)
const selectedFile = ref<File | null>(null)
const testing = ref(false)
const testResults = ref<SmartMatchTestResult[]>([])
const tested = ref(false)

const results = computed(() => testResults.value)

const hasFile = computed(() => selectedFile.value !== null)

function matchTypeTag(type: string): string {
  switch (type) {
    case 'COVER': return 'warning'
    case 'TOC': return ''
    case 'TITLE': return 'primary'
    case 'BODY': return 'success'
    default: return 'danger'
  }
}

function confidenceColor(confidence: number): string {
  if (confidence >= 0.8) return '#67c23a'
  if (confidence >= 0.5) return '#e6a23c'
  return '#f56c6c'
}

function countByType(type: string): number {
  return testResults.value.filter(r => r.matchedType === type).length
}

function handleExceed() {
  ElMessage.warning('只能上传一个文件')
}

function handleFileChange(file: UploadFile) {
  selectedFile.value = file.raw || null
}

async function handleTest() {
  if (!props.templateId) {
    ElMessage.warning('请先选择模板')
    return
  }

  if (!selectedFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }

  testing.value = true
  tested.value = false
  try {
    const res = await testSmartMatch(props.templateId, selectedFile.value)
    testResults.value = res || []
    tested.value = true
    if (testResults.value.length === 0) {
      ElMessage.info('测试完成，但未生成匹配结果')
    } else {
      ElMessage.success(`测试完成，共处理 ${testResults.value.length} 个段落`)
    }
  } catch (e) {
    tested.value = true
    // 错误已在拦截器中处理
  } finally {
    testing.value = false
  }
}

function handleClosed() {
  testResults.value = []
  tested.value = false
  selectedFile.value = null
  if (uploadRef.value) {
    uploadRef.value.clearFiles()
  }
}
</script>

<style scoped>
.test-upload {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.upload-tip {
  font-size: 12px;
  color: #909399;
  margin-left: 8px;
}

.test-results {
  margin-top: 8px;
}

.results-title {
  margin: 0 0 12px;
  font-size: 14px;
  color: #303133;
}

.summary {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.summary-tag {
  font-size: 12px;
}
</style>
