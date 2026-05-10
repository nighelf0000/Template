<template>
  <el-menu
    :default-active="activeMenu"
    router
    background-color="#304156"
    text-color="#bfcbd9"
    active-text-color="#409EFF"
  >
    <div class="logo">Template 系统</div>
    <el-menu-item index="/templates">
      <el-icon><Document /></el-icon>
      <span>模板列表</span>
    </el-menu-item>
    <el-menu-item index="/engine-config">
      <el-icon><Setting /></el-icon>
      <span>引擎配置</span>
    </el-menu-item>
    <el-menu-item index="/file-upload">
      <el-icon><Upload /></el-icon>
      <span>文件上传</span>
    </el-menu-item>
    <el-menu-item index="/parse-preview">
      <el-icon><View /></el-icon>
      <span>解析预览</span>
    </el-menu-item>
  </el-menu>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Document, Setting, Upload, View } from '@element-plus/icons-vue'

const route = useRoute()
const activeMenu = computed(() => {
  if (route.path.startsWith('/templates')) return '/templates'
  return route.path
})
</script>

<style scoped>
.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  color: #fff;
  font-size: 18px;
  font-weight: bold;
  border-bottom: 1px solid rgba(255,255,255,0.1);
}
.el-menu { border-right: none; height: 100%; }
</style>
