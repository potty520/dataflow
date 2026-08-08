<script setup>
import { computed, onMounted, ref } from 'vue'
import { monitorStats } from '../../../api'

const stats = ref({})
const statusMeta = {
  NOT_STARTED: { label: '未开始', type: 'info' },
  WAITING: { label: '等待执行', type: 'warning' },
  RUNNING: { label: '运行中', type: 'primary' },
  SUCCESS: { label: '成功', type: 'success' },
  FAILED: { label: '失败', type: 'danger' }
}

const statusEntries = computed(() =>
  Object.entries(stats.value.statusCount || {}).map(([status, count]) => ({
    status,
    count,
    label: statusMeta[status]?.label || status,
    type: statusMeta[status]?.type || 'info'
  }))
)

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
      <div v-if="statusEntries.length" class="status-grid">
        <div v-for="item in statusEntries" :key="item.status" class="status-item">
          <el-tag :type="item.type" size="large" effect="light">{{ item.label }}</el-tag>
          <div class="status-count">{{ item.count }}</div>
        </div>
      </div>
      <el-empty v-else description="暂无状态数据" :image-size="80" />
    </el-card>
  </div>
</template>

<style scoped>
.status-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
}

.status-item {
  min-height: 86px;
  border: 1px solid var(--app-border);
  border-radius: 8px;
  background: var(--app-surface-soft);
  padding: 14px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.status-count {
  margin-top: 12px;
  font-size: 28px;
  line-height: 1;
  font-weight: 700;
  color: var(--app-text);
}
</style>
