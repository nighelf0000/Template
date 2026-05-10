<template>
  <div v-loading="loading">
    <div style="margin-bottom: 16px; display: flex; justify-content: space-between; align-items: center">
      <h2>引擎配置</h2>
      <el-button @click="$router.push('/templates')">返回模板列表</el-button>
    </div>

    <el-card>
      <el-form label-width="120px">
        <el-form-item label="选择模板">
          <el-select
            v-model="selectedTemplateId"
            placeholder="请选择模板"
            style="width: 400px"
            @change="onTemplateChange"
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
    </el-card>

    <el-card v-if="selectedTemplateId" style="margin-top: 20px">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>正则表达式配置列表</span>
          <el-button type="primary" size="small" @click="openCreateDialog">新增配置</el-button>
        </div>
      </template>

      <el-table :data="configList" stripe style="width: 100%">
        <el-table-column prop="configName" label="名称" min-width="120" />
        <el-table-column prop="pattern" label="正则表达式" min-width="200">
          <template #default="{ row }">
            <el-tooltip :content="row.pattern" placement="top" :show-after="500">
              <span>{{ row.pattern && row.pattern.length > 40 ? row.pattern.substring(0, 40) + '...' : row.pattern }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="matchType" label="匹配类型" width="100">
          <template #default="{ row }">
            <el-tag :type="matchTypeTag(row.matchType)" size="small">{{ row.matchType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="matchLevel" label="标题级别" width="80" align="center" />
        <el-table-column prop="ruleName" label="关联样式" min-width="120" />
        <el-table-column prop="sortOrder" label="排序" width="60" align="center" />
        <el-table-column prop="isActive" label="状态" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isActive === 1 ? 'success' : 'info'" size="small">
              {{ row.isActive === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEditDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="total > 0" style="margin-top: 16px; display: flex; justify-content: center">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadConfigList"
          @size-change="loadConfigList"
        />
      </div>

      <el-empty v-if="!configList.length" description="暂无引擎配置，请点击上方「新增配置」按钮添加" />
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEditing ? '编辑引擎配置' : '新增引擎配置'"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="120px">
        <el-form-item label="配置名称" prop="configName">
          <el-input v-model="formData.configName" placeholder="如：封面匹配、一级标题" maxlength="200" />
        </el-form-item>
        <el-form-item label="正则表达式" prop="pattern">
          <el-input v-model="formData.pattern" type="textarea" :rows="2" placeholder="请输入正则表达式" />
        </el-form-item>
        <el-form-item label="匹配类型" prop="matchType">
          <el-select v-model="formData.matchType" placeholder="请选择匹配类型" style="width: 100%">
            <el-option label="COVER — 封面" value="COVER" />
            <el-option label="TOC — 目录" value="TOC" />
            <el-option label="TITLE — 标题" value="TITLE" />
            <el-option label="BODY — 正文" value="BODY" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题级别" prop="matchLevel" v-if="formData.matchType === 'TITLE'">
          <el-input-number v-model="formData.matchLevel" :min="1" :max="9" :step="1" />
        </el-form-item>
        <el-form-item label="关联样式" prop="ruleId">
          <el-select v-model="formData.ruleId" placeholder="选择样式规则（可选）" style="width: 100%" clearable>
            <el-option
              v-for="rule in ruleList"
              :key="rule.id"
              :label="rule.name"
              :value="rule.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="排序序号" prop="sortOrder">
          <el-input-number v-model="formData.sortOrder" :min="0" :max="999" :step="1" />
        </el-form-item>
        <el-form-item label="启用状态" prop="isActive">
          <el-switch v-model="formData.isActive" :active-value="1" :inactive-value="0" active-text="启用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getTemplateList,
  getEngineConfigList,
  createEngineConfig,
  updateEngineConfig,
  deleteEngineConfig,
  getRuleList
} from '@/api/template'
import type { TemplateConfig, EngineConfig, TemplateRule } from '@/types/api'

const route = useRoute()
const loading = ref(false)
const submitting = ref(false)
const templateList = ref<TemplateConfig[]>([])
const ruleList = ref<TemplateRule[]>([])
const selectedTemplateId = ref<number | null>(null)
const configList = ref<EngineConfig[]>([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const dialogVisible = ref(false)
const isEditing = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<any>(null)

const defaultForm = (): EngineConfig => ({
  configName: '',
  pattern: '',
  matchType: 'COVER',
  matchLevel: undefined,
  ruleId: undefined,
  sortOrder: 1,
  isActive: 1
})

const formData = reactive<EngineConfig>(defaultForm())

const formRules = {
  configName: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  pattern: [{ required: true, message: '请输入正则表达式', trigger: 'blur' }],
  matchType: [{ required: true, message: '请选择匹配类型', trigger: 'change' }]
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

async function loadTemplates() {
  try {
    const res = await getTemplateList({ page: 1, size: 999 })
    templateList.value = res.records || []
    const tid = route.query.templateId
    if (tid) {
      selectedTemplateId.value = Number(tid)
      await loadConfigList()
    }
  } catch (e) {
    // 错误已在拦截器中处理
  }
}

async function loadRuleList() {
  if (!selectedTemplateId.value) return
  try {
    const res = await getRuleList(selectedTemplateId.value)
    ruleList.value = res || []
  } catch (e) {
    ruleList.value = []
  }
}

async function loadConfigList() {
  if (!selectedTemplateId.value) return
  loading.value = true
  try {
    const res = await getEngineConfigList(selectedTemplateId.value, { page: currentPage.value, size: pageSize.value })
    configList.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    configList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function onTemplateChange() {
  configList.value = []
  currentPage.value = 1
  total.value = 0
  if (selectedTemplateId.value) {
    await loadConfigList()
    await loadRuleList()
  }
}

function openCreateDialog() {
  isEditing.value = false
  editingId.value = null
  Object.assign(formData, defaultForm())
  dialogVisible.value = true
}

function openEditDialog(row: EngineConfig) {
  isEditing.value = true
  editingId.value = row.id!
  Object.assign(formData, {
    configName: row.configName,
    pattern: row.pattern,
    matchType: row.matchType,
    matchLevel: row.matchLevel,
    ruleId: row.ruleId,
    sortOrder: row.sortOrder ?? 1,
    isActive: row.isActive ?? 1
  })
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  if (!selectedTemplateId.value) {
    ElMessage.warning('请先选择模板')
    return
  }

  submitting.value = true
  try {
    if (isEditing.value && editingId.value) {
      await updateEngineConfig(selectedTemplateId.value, editingId.value, { ...formData })
      ElMessage.success('更新成功')
    } else {
      await createEngineConfig(selectedTemplateId.value, { ...formData })
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    await loadConfigList()
  } catch (e) {
    // 错误已在拦截器中处理
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: EngineConfig) {
  if (!selectedTemplateId.value || !row.id) return
  try {
    await ElMessageBox.confirm('确定删除该配置吗？', '确认删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteEngineConfig(selectedTemplateId.value, row.id)
    ElMessage.success('删除成功')
    await loadConfigList()
  } catch (e) {
    // 取消删除或错误不处理
  }
}

onMounted(() => {
  loadTemplates()
})
</script>
