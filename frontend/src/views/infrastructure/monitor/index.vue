<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { resourceMonitor } from '../../../api'

const loading = ref(false)
const monitorData = ref({})
const hosts = ref([])
const cpuRef = ref(null)
const memRef = ref(null)
const diskRef = ref(null)
const charts = []

const statusMeta = {
  NORMAL: { label: '正常', type: 'success' },
  WARNING: { label: '告警', type: 'warning' },
  ONLINE: { label: '在线', type: 'success' },
  OFFLINE: { label: '离线', type: 'info' }
}

function statusLabel(value) {
  return statusMeta[value]?.label || value || '未知'
}

function statusType(value) {
  return statusMeta[value]?.type || 'info'
}

function makeGauge(el, title, value, color) {
  if (!el) return
  const chart = echarts.getInstanceByDom(el) || echarts.init(el)
  chart.setOption({
    series: [{
      type: 'gauge',
      startAngle: 210,
      endAngle: -30,
      min: 0,
      max: 100,
      radius: '86%',
      progress: { show: true, width: 12, itemStyle: { color } },
      axisLine: { lineStyle: { width: 12, color: [[1, '#e7edf7']] } },
      axisTick: { distance: -18, length: 5, lineStyle: { color: '#9aa8bb' } },
      splitLine: { distance: -18, length: 10, lineStyle: { color: '#9aa8bb' } },
      axisLabel: { distance: 14, color: '#6b7a90', fontSize: 10 },
      pointer: { width: 4, length: '58%', itemStyle: { color } },
      anchor: { show: true, size: 8, itemStyle: { color } },
      title: { offsetCenter: [0, '58%'], color: '#5f6f85', fontSize: 12 },
      detail: { valueAnimation: true, formatter: '{value}%', offsetCenter: [0, '30%'], fontSize: 24, fontWeight: 700, color: '#162235' },
      data: [{ value, name: title }]
    }]
  })
  charts.push(chart)
}

function renderCharts() {
  charts.splice(0).forEach(chart => chart.dispose())
  makeGauge(cpuRef.value, 'CPU', monitorData.value.cpuUsagePercent || 0, '#2563eb')
  makeGauge(memRef.value, '内存', monitorData.value.memoryUsagePercent || 0, '#0f766e')
  makeGauge(diskRef.value, '磁盘', monitorData.value.diskUsagePercent || 0, '#d97706')
}

async function loadData() {
  loading.value = true
  try {
    const res = await resourceMonitor()
    monitorData.value = res.data || {}
    hosts.value = monitorData.value.hosts || []
    requestAnimationFrame(renderCharts)
  } finally {
    loading.value = false
  }
}

function refreshCharts() {
  renderCharts()
}

function usagePercent(host) {
  if (!host.diskGb) return 0
  return Math.round((host.diskUsed / host.diskGb) * 100)
}

function formatPercent(value) {
  return `${value || 0}%`
}

function handleResize() {
  charts.forEach(chart => chart.resize())
}

onMounted(() => {
  loadData()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  charts.splice(0).forEach(chart => chart.dispose())
})
</script>

<template>
  <div class="page-container">
    <div class="page-top">
      <div>
        <p class="page-kicker">基础支撑 / 运行监控</p>
        <h1 class="page-title">资源监控</h1>
        <p class="page-subtitle">查看 CPU、内存、磁盘与主机利用率，定位资源压力。</p>
      </div>
      <div class="page-actions">
        <el-button :loading="loading" @click="loadData">
          <el-icon><Refresh /></el-icon>
          <span>刷新</span>
        </el-button>
      </div>
    </div>

    <div class="metric-grid">
      <div class="metric-card">
        <div class="metric-label">CPU 总核数</div>
        <div class="metric-value">{{ monitorData.cpuTotalCores || 0 }}</div>
        <div class="metric-note">已分配 {{ monitorData.cpuUsedCores || 0 }} 核</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">内存总量</div>
        <div class="metric-value">{{ monitorData.memoryTotalGb || 0 }} GB</div>
        <div class="metric-note">已使用 {{ monitorData.memoryUsedGb || 0 }} GB</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">磁盘总量</div>
        <div class="metric-value">{{ monitorData.diskTotalGb || 0 }} GB</div>
        <div class="metric-note">已使用 {{ monitorData.diskUsedGb || 0 }} GB</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">主机数量</div>
        <div class="metric-value">{{ monitorData.hostCount || 0 }}</div>
        <div class="metric-note">在线 {{ monitorData.onlineHosts || 0 }} 台</div>
      </div>
    </div>

    <div class="overview-grid">
      <el-card class="surface-card" shadow="never">
        <template #header>资源仪表盘</template>
        <div class="gauge-grid">
          <div class="gauge-card"><div ref="cpuRef" class="gauge-box" /></div>
          <div class="gauge-card"><div ref="memRef" class="gauge-box" /></div>
          <div class="gauge-card"><div ref="diskRef" class="gauge-box" /></div>
        </div>
      </el-card>

      <el-card class="surface-card" shadow="never">
        <template #header>
          <div class="section-heading">
            <span>快捷入口</span>
          </div>
        </template>
        <div class="shortcut-list">
          <router-link class="shortcut-item" to="/infrastructure/host">主机管理</router-link>
          <router-link class="shortcut-item" to="/infrastructure/resource-group">资源组</router-link>
          <router-link class="shortcut-item" to="/infrastructure/storage-quota">存储配额</router-link>
          <router-link class="shortcut-item" to="/infrastructure/repository">组件仓库</router-link>
          <router-link class="shortcut-item" to="/infrastructure/config">服务配置</router-link>
          <router-link class="shortcut-item" to="/infrastructure/alert">告警中心</router-link>
        </div>
      </el-card>
    </div>

    <el-card class="surface-card" shadow="never">
      <template #header>
        <div class="section-heading">
          <span>主机利用率</span>
          <el-button link type="primary" @click="refreshCharts">重绘图表</el-button>
        </div>
      </template>
      <div v-for="h in hosts" :key="h.hostname" class="host-item">
        <div class="host-item-head">
          <span>{{ h.hostname }} <span class="subtle">({{ h.ip }})</span></span>
          <el-tag :type="statusType(h.status)" size="small">{{ statusLabel(h.status) }}</el-tag>
        </div>
        <div class="host-item-meta">
          <span>磁盘 {{ h.diskUsed || 0 }} / {{ h.diskGb || 0 }} GB</span>
          <span>{{ formatPercent(usagePercent(h)) }}</span>
        </div>
        <el-progress :percentage="usagePercent(h)" :status="usagePercent(h) > 80 ? 'exception' : ''" />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.overview-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(260px, 0.9fr);
  gap: 16px;
}

.gauge-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.gauge-card {
  min-width: 0;
  background: var(--app-surface-soft);
  border: 1px solid var(--app-border);
  border-radius: 8px;
}

.gauge-box {
  width: 100%;
  height: 220px;
}

.shortcut-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.shortcut-item {
  display: flex;
  align-items: center;
  min-height: 44px;
  padding: 0 14px;
  background: var(--app-surface-soft);
  border: 1px solid var(--app-border);
  border-radius: 8px;
  color: var(--app-text);
  font-weight: 600;
}

.host-item + .host-item {
  margin-top: 16px;
}

.host-item-head,
.host-item-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.host-item-head {
  margin-bottom: 4px;
  font-weight: 600;
}

.host-item-meta {
  margin-bottom: 6px;
  font-size: 12px;
  color: var(--app-muted);
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

@media (max-width: 1150px) {
  .overview-grid,
  .gauge-grid {
    grid-template-columns: 1fr;
  }
}
</style>
