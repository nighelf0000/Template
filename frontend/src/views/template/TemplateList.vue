<template>
  <div v-loading="loading">
    <div style="margin-bottom: 16px; display: flex; justify-content: space-between; align-items: center">
      <h2>模板列表</h2>
      <el-button type="primary" @click="showCreateDialog = true">新建模板</el-button>
    </div>

    <el-table :data="templateList" stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" label="模板名称" />
      <el-table-column prop="isActive" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.isActive === 1 ? 'success' : 'info'">
            {{ row.isActive === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="180" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button
            :type="row.isActive === 1 ? 'warning' : 'success'"
            link
            size="small"
            @click="handleToggle(row)"
          >
            {{ row.isActive === 1 ? '禁用' : '启用' }}
          </el-button>
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
        @current-change="loadData"
        @size-change="loadData"
      />
    </div>

    <!-- 新建模板对话框 -->
    <el-dialog v-model="showCreateDialog" title="新建模板" width="400px">
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="模板名称" required>
          <el-input v-model="createForm.name" placeholder="请输入模板名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getTemplateList, createTemplate, toggleTemplate } from '@/api/template'
import type { TemplateConfig } from '@/types/api'

const router = useRouter()
const loading = ref(false)
const creating = ref(false)
const templateList = ref<TemplateConfig[]>([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const showCreateDialog = ref(false)
const createForm = ref({ name: '' })

async function loadData() {
  loading.value = true
  try {
    const res = await getTemplateList({ page: currentPage.value, size: pageSize.value })
    templateList.value = res.records
    total.value = res.total
  } catch (e) {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  if (!createForm.value.name.trim()) {
    ElMessage.warning('请输入模板名称')
    return
  }
  creating.value = true
  try {
    await createTemplate({ name: createForm.value.name })
    ElMessage.success('创建成功')
    showCreateDialog.value = false
    createForm.value.name = ''
    await loadData()
  } catch (e) {
    // 错误已在拦截器中处理
  } finally {
    creating.value = false
  }
}

function handleEdit(row: TemplateConfig) {
  router.push(`/templates/${row.id}`)
}

async function handleToggle(row: TemplateConfig) {
  try {
    await toggleTemplate(row.id!)
    ElMessage.success(row.isActive === 1 ? '已禁用' : '已启用')
    await loadData()
  } catch (e) {
    // 错误已在拦截器中处理
  }
}

onMounted(() => {
  loadData()
})
</script>
