<script setup>
import { onMounted, ref } from 'vue'
import { assetOverview } from '../../../api'

const stats = ref({})
const layerStats = ref({})

onMounted(async () => {
  const res = await assetOverview()
  stats.value = res.data || {}
  layerStats.value = res.data?.layerStats || {}
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">数据资产总览</div>
    <div class="stat-cards">
      <div class="stat-card"><div class="label">数据表数</div><div class="value">{{ stats.tableCount || 0 }}</div></div>
      <div class="stat-card"><div class="label">字段数</div><div class="value">{{ stats.fieldCount || 0 }}</div></div>
      <div class="stat-card"><div class="label">重点资产</div><div class="value">{{ stats.keyAssetCount || 0 }}</div></div>
      <div class="stat-card"><div class="label">数据总量(行)</div><div class="value">{{ stats.totalRows || 0 }}</div></div>
      <div class="stat-card"><div class="label">数据源</div><div class="value">{{ stats.datasourceCount || 0 }}</div></div>
    </div>
    <el-card header="资产分层统计">
      <el-descriptions :column="5" border>
        <el-descriptions-item v-for="(count, layer) in layerStats" :key="layer" :label="layer">{{ count }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>
