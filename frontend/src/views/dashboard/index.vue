<script setup>
import { onMounted, ref } from 'vue'
import { dashboardStats } from '../../api'

const stats = ref({})

onMounted(async () => {
  const res = await dashboardStats()
  stats.value = res.data || {}
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">平台概览</div>
    <div class="stat-cards">
      <div class="stat-card"><div class="label">数据源数量</div><div class="value">{{ stats.datasourceCount || 0 }}</div></div>
      <div class="stat-card"><div class="label">数据流任务</div><div class="value">{{ stats.dataflowCount || 0 }}</div></div>
      <div class="stat-card"><div class="label">API 服务</div><div class="value">{{ stats.apiCount || 0 }}</div></div>
      <div class="stat-card"><div class="label">应用数量</div><div class="value">{{ stats.appCount || 0 }}</div></div>
    </div>
    <el-card>
      <template #header>系统说明</template>
      <p>大数据基座平台包含统一门户、数据汇聚、数据治理、数据开发、数据服务、数据资产、基础支撑七大子系统。</p>
      <p>当前版本已实现统一门户 RBAC 权限体系、数据汇聚核心功能及数据服务基础能力，其余模块可按需求逐步扩展。</p>
    </el-card>
  </div>
</template>
