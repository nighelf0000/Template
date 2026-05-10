<template>
  <el-container style="height: 100vh">
    <el-aside width="220px" style="background: #304156">
      <SidebarMenu />
    </el-aside>
    <el-container>
      <el-header style="background: #fff; border-bottom: 1px solid #dcdfe6; display: flex; align-items: center; padding: 0 20px">
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
          <el-breadcrumb-item v-if="route.meta.title">{{ route.meta.title }}</el-breadcrumb-item>
        </el-breadcrumb>
      </el-header>
      <el-main style="background: #f0f2f5; padding: 20px">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import SidebarMenu from './SidebarMenu.vue'

const route = useRoute()
</script>
