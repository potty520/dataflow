<script setup>
import { onMounted, ref } from 'vue'
import { monitorStats } from '../../../api'

const stats = ref({})

onMounted(async () => {
  const res = await monitorStats()
  stats.value = res.data || {}
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">数据流监控</div>
    <div class="stat-cards">
      <div class="stat-card"><div class="label">执行总次数</div><div class="value">{{ stats.totalExecutions || 0 }}</div></div>
      <div class="stat-card"><div class="label">读取总条数</div><div class="value">{{ stats.totalRead || 0 }}</div></div>
      <div class="stat-card"><div class="label">写入总条数</div><div class="value">{{ stats.totalWrite || 0 }}</div></div>
      <div class="stat-card"><div class="label">数据源数</div><div class="value">{{ stats.datasourceCount || 0 }}</div></div>
    </div>
    <el-card header="状态分布">
      <el-descriptions :column="3" border>
        <el-descriptions-item v-for="(count, status) in stats.statusCount || {}" :key="status" :label="status">{{ count }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>
