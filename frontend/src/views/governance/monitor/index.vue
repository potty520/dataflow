<script setup>
import { onMounted, ref } from 'vue'
import { govMonitorStats } from '../../../api'

const stats = ref({})

onMounted(async () => {
  const res = await govMonitorStats()
  stats.value = res.data || {}
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">治理监控</div>
    <div class="stat-cards">
      <div class="stat-card"><div class="label">标准目录</div><div class="value">{{ stats.totalStandards || 0 }}</div></div>
      <div class="stat-card"><div class="label">数据模型</div><div class="value">{{ stats.totalModels || 0 }}</div></div>
      <div class="stat-card"><div class="label">指标数量</div><div class="value">{{ stats.totalIndicators || 0 }}</div></div>
      <div class="stat-card"><div class="label">代理节点</div><div class="value">{{ stats.totalProxyNodes || 0 }}</div></div>
      <div class="stat-card"><div class="label">活跃节点</div><div class="value">{{ stats.activeProxyNodes || 0 }}</div></div>
    </div>
    <el-card header="最近变更记录">
      <el-timeline>
        <el-timeline-item v-for="(item, index) in (stats.recentChanges || [])" :key="index" :timestamp="item.time">
          {{ item.detail }}
        </el-timeline-item>
        <el-empty v-if="!(stats.recentChanges || []).length" description="暂无变更记录" />
      </el-timeline>
    </el-card>
    <el-card header="治理统计数据" style="margin-top:16px">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="代码集数量">{{ stats.codeSetCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="数据词条">{{ stats.wordCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="派生指标">{{ stats.derivedCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="原子指标">{{ stats.atomicCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="业务限定">{{ stats.limitCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="统计周期">{{ stats.periodCount || 0 }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>
