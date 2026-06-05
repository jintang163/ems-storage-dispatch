<template>
  <el-container class="app-container">
    <el-header class="app-header">
      <div class="header-left">
        <el-icon :size="32" color="#409eff">
          <Lightning />
        </el-icon>
        <h1>EMS储能优化调度系统</h1>
      </div>
      <div class="header-right">
        <el-tag type="success" effect="dark">系统运行中</el-tag>
      </div>
    </el-header>
    <el-container>
      <el-aside width="220px" class="app-aside">
        <el-menu
          :default-active="activeMenu"
          router
          background-color="#001529"
          text-color="#fff"
          active-text-color="#409eff"
        >
          <el-menu-item index="/dashboard">
            <el-icon><DataAnalysis /></el-icon>
            <span>实时监控</span>
          </el-menu-item>
          <el-menu-item index="/device">
            <el-icon><Setting /></el-icon>
            <span>设备管理</span>
          </el-menu-item>
          <el-menu-item index="/price">
            <el-icon><Money /></el-icon>
            <span>电价配置</span>
          </el-menu-item>
          <el-menu-item index="/history">
            <el-icon><Histogram /></el-icon>
            <span>历史数据</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const activeMenu = computed(() => route.path)
</script>

<style scoped>
.app-container {
  height: 100vh;
}

.app-header {
  background: linear-gradient(90deg, #001529 0%, #002140 100%);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  color: #fff;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-left h1 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.app-aside {
  background: #001529;
}

.app-main {
  background: #f0f2f5;
  padding: 20px;
  overflow-y: auto;
}

:deep(.el-menu) {
  border-right: none;
}
</style>
