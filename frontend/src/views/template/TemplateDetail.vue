<template>
  <div v-loading="loading">
    <div style="margin-bottom: 16px; display: flex; justify-content: space-between; align-items: center">
      <h2>模板详情</h2>
      <div>
        <el-button type="info" @click="goEngineConfig">引擎配置</el-button>
        <el-button @click="goBack">返回</el-button>
      </div>
    </div>

    <!-- 模板基本信息 -->
    <el-card style="margin-bottom: 20px">
      <template #header><span>基本信息</span></template>
      <el-form :model="templateInfo" label-width="100px">
        <el-form-item label="模板名称">
          <el-input v-model="templateInfo.name" style="max-width: 400px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 规则列表 -->
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>规则列表</span>
          <el-button type="primary" size="small" @click="showRuleDialog(null)">新增规则</el-button>
        </div>
      </template>

      <el-table :data="ruleList" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="规则名称" min-width="120" />
        <el-table-column prop="fontName" label="字体" width="100" />
        <el-table-column label="字号" width="80">
          <template #default="{ row }">{{ row.fontSize || '-' }}</template>
        </el-table-column>
        <el-table-column label="加粗" width="80">
          <template #default="{ row }">{{ row.fontBold == null ? '-' : row.fontBold === 1 ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="斜体" width="80">
          <template #default="{ row }">{{ row.fontItalic == null ? '-' : row.fontItalic === 1 ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="showRuleDialog(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDeleteRule(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 规则编辑对话框 -->
    <el-dialog
      v-model="ruleDialogVisible"
      :title="editingRuleId ? '编辑规则' : '新增规则'"
      width="720px"
    >
      <el-form :model="ruleForm" label-width="120px">
        <el-form-item label="规则名称" required>
          <el-input v-model="ruleForm.name" placeholder="规则名称" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="字体名称">
              <el-input v-model="ruleForm.fontName" placeholder="如：宋体" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="字号(磅)">
              <el-input-number v-model="ruleForm.fontSize" :min="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="加粗">
              <el-switch v-model="ruleForm.fontBoldBool" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="斜体">
              <el-switch v-model="ruleForm.fontItalicBool" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="下划线">
              <el-switch v-model="ruleForm.fontUnderlineBool" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="删除线">
              <el-switch v-model="ruleForm.fontStrikeBool" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="字体颜色">
              <el-color-picker v-model="ruleForm.fontColor" show-alpha="false" color-format="hex" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="文本对齐">
          <el-select v-model="ruleForm.textAlign" placeholder="请选择" style="width: 100%">
            <el-option label="左对齐" value="left" />
            <el-option label="居中" value="center" />
            <el-option label="右对齐" value="right" />
            <el-option label="两端对齐" value="both" />
          </el-select>
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="首行缩进(字符)">
              <el-input-number v-model="ruleForm.textIndent" :min="0" :step="0.1" :precision="1"
                controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="行距(倍)">
              <el-input-number v-model="ruleForm.lineSpacing" :min="0" :step="0.1" :precision="1"
                controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="段前距(磅)">
              <el-input-number v-model="ruleForm.spaceBefore" :min="0" :step="0.1" :precision="1"
                controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="段后距(磅)">
              <el-input-number v-model="ruleForm.spaceAfter" :min="0" :step="0.1" :precision="1"
                controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="高亮颜色">
          <el-color-picker v-model="ruleForm.highlightColor" show-alpha="false" color-format="hex" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="ruleSaving" @click="handleSaveRule">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getTemplateDetail, updateTemplate, getRuleList, createRule, updateRule, deleteRule } from '@/api/template'
import type { TemplateConfig, TemplateRule } from '@/types/api'

const route = useRoute()
const router = useRouter()
const templateId = Number(route.params.id)

const loading = ref(false)
const saving = ref(false)
const templateInfo = reactive<TemplateConfig>({ name: '' })
const ruleList = ref<TemplateRule[]>([])

// 规则对话框
const ruleDialogVisible = ref(false)
const ruleSaving = ref(false)
const editingRuleId = ref<number | null>(null)
const ruleForm = reactive({
  name: '',
  fontName: '',
  fontSize: undefined as number | undefined,
  fontBoldBool: 0,
  fontItalicBool: 0,
  fontUnderlineBool: 0,
  fontStrikeBool: 0,
  fontColor: '',
  textAlign: '',
  textIndent: undefined as number | undefined,
  lineSpacing: undefined as number | undefined,
  spaceBefore: undefined as number | undefined,
  spaceAfter: undefined as number | undefined,
  highlightColor: ''
})

async function loadTemplate() {
  loading.value = true
  try {
    const res = await getTemplateDetail(templateId)
    Object.assign(templateInfo, res)
  } catch (e) {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
  }
}

async function loadRules() {
  try {
    ruleList.value = await getRuleList(templateId)
  } catch (e) {
    // 错误已在拦截器中处理
  }
}

async function handleSave() {
  if (!templateInfo.name.trim()) {
    ElMessage.warning('模板名称不能为空')
    return
  }
  saving.value = true
  try {
    await updateTemplate(templateId, { name: templateInfo.name })
    ElMessage.success('保存成功')
  } catch (e) {
    // 错误已在拦截器中处理
  } finally {
    saving.value = false
  }
}

function showRuleDialog(row: TemplateRule | null) {
  editingRuleId.value = row ? row.id! : null
  if (row) {
    ruleForm.name = row.name || ''
    ruleForm.fontName = row.fontName || ''
    ruleForm.fontSize = row.fontSize
    ruleForm.fontBoldBool = row.fontBold ?? 0
    ruleForm.fontItalicBool = row.fontItalic ?? 0
    ruleForm.fontUnderlineBool = row.fontUnderline ?? 0
    ruleForm.fontStrikeBool = row.fontStrike ?? 0
    ruleForm.fontColor = row.fontColor || ''
    ruleForm.textAlign = row.textAlign || ''
    ruleForm.textIndent = row.textIndent
    ruleForm.lineSpacing = row.lineSpacing
    ruleForm.spaceBefore = row.spaceBefore
    ruleForm.spaceAfter = row.spaceAfter
    ruleForm.highlightColor = row.highlightColor || ''
  } else {
    ruleForm.name = ''
    ruleForm.fontName = ''
    ruleForm.fontSize = undefined
    ruleForm.fontBoldBool = 0
    ruleForm.fontItalicBool = 0
    ruleForm.fontUnderlineBool = 0
    ruleForm.fontStrikeBool = 0
    ruleForm.fontColor = ''
    ruleForm.textAlign = ''
    ruleForm.textIndent = undefined
    ruleForm.lineSpacing = undefined
    ruleForm.spaceBefore = undefined
    ruleForm.spaceAfter = undefined
    ruleForm.highlightColor = ''
  }
  ruleDialogVisible.value = true
}

async function handleSaveRule() {
  if (!ruleForm.name.trim()) {
    ElMessage.warning('请输入规则名称')
    return
  }
  ruleSaving.value = true
  try {
    const data: Record<string, any> = {
      name: ruleForm.name,
    }
    if (ruleForm.fontName)                 data.fontName = ruleForm.fontName
    if (ruleForm.fontSize != null)         data.fontSize = ruleForm.fontSize
    if (ruleForm.fontBoldBool != null)     data.fontBold = ruleForm.fontBoldBool
    if (ruleForm.fontItalicBool != null)   data.fontItalic = ruleForm.fontItalicBool
    if (ruleForm.fontUnderlineBool != null) data.fontUnderline = ruleForm.fontUnderlineBool
    if (ruleForm.fontStrikeBool != null)   data.fontStrike = ruleForm.fontStrikeBool
    if (ruleForm.textAlign)                data.textAlign = ruleForm.textAlign
    if (ruleForm.textIndent != null)       data.textIndent = ruleForm.textIndent
    if (ruleForm.lineSpacing != null)      data.lineSpacing = ruleForm.lineSpacing
    if (ruleForm.spaceBefore != null)      data.spaceBefore = ruleForm.spaceBefore
    if (ruleForm.spaceAfter != null)       data.spaceAfter = ruleForm.spaceAfter
    if (ruleForm.fontColor)                data.fontColor = ruleForm.fontColor
    if (ruleForm.highlightColor)           data.highlightColor = ruleForm.highlightColor

    if (editingRuleId.value) {
      await updateRule(templateId, editingRuleId.value, data)
      ElMessage.success('更新成功')
    } else {
      await createRule(templateId, data)
      ElMessage.success('创建成功')
    }
    ruleDialogVisible.value = false
    await loadRules()
  } catch (e) {
    // 错误已在拦截器中处理
  } finally {
    ruleSaving.value = false
  }
}

async function handleDeleteRule(row: TemplateRule) {
  try {
    await ElMessageBox.confirm('确定删除该规则吗？', '提示', { type: 'warning' })
    await deleteRule(templateId, row.id!)
    ElMessage.success('删除成功')
    await loadRules()
  } catch (e) {
    // 取消或错误，不处理
  }
}

function goEngineConfig() {
  router.push(`/engine-config?templateId=${templateId}`)
}

function goBack() {
  router.push('/templates')
}

onMounted(() => {
  loadTemplate()
  loadRules()
})
</script>
