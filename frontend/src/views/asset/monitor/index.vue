<script setup>
import { onMounted, ref } from 'vue'
import { assetMonitorStats } from '../../../api'

const stats = ref({})

onMounted(async () => {
  const res = await assetMonitorStats()
  stats.value = res.data || {}
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">资产监控</div>
    <div class="stat-cards">
      <div class="stat-card"><div class="label">重点资产数</div><div class="value">{{ stats.keyAssets || 0 }}</div></div>
      <div class="stat-card"><div class="label">血缘关系数</div><div class="value">{{ stats.lineageCount || 0 }}</div></div>
      <div class="stat-card"><div class="label">敏感字段数</div><div class="value">{{ stats.sensitiveFields || 0 }}</div></div>
    </div>
    <el-card>
      <p>资产分级监控：对重点资产进行重点监控，敏感字段脱敏展示，血缘关系自动追踪。</p>
    </el-card>
  </div>
</template>
