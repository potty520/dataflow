<script setup>
import { onMounted, ref, computed } from 'vue'
import { devScheduleLogs, devSchedulePage } from '../../../api'

const loading = ref(false)
const ganttItems = ref([])

function formatDate(time) {
  if (!time) return '-'
  return new Date(time).toLocaleString()
}

function formatDuration(ms) {
  if (!ms) return '-'
  if (ms < 1000) return ms + 'ms'
  if (ms < 60000) return (ms / 1000).toFixed(1) + 's'
  return (ms / 60000).toFixed(1) + 'min'
}

const totalDuration = computed(() => {
  if (!ganttItems.value.length) return 3600000
  const min = Math.min(...ganttItems.value.map(i => i.start.getTime()))
  const max = Math.max(...ganttItems.value.map(i => i.end.getTime()))
  return max - min
})

const chartStart = computed(() => {
  if (!ganttItems.value.length) return Date.now() - 3600000
  return Math.min(...ganttItems.value.map(i => i.start.getTime()))
})

function barStyle(item) {
  const offset = ((item.start.getTime() - chartStart.value) / totalDuration.value) * 100
  const width = (item.duration / totalDuration.value) * 100
  let bg = '#67c23a'
  if (item.status === 'FAILED') bg = '#f56c6c'
  if (item.status === 'RUNNING') bg = '#409eff'
  if (item.status === 'PENDING') bg = '#909399'
  return {
    left: offset + '%',
    width: Math.max(width, 2) + '%',
    backgroundColor: bg
  }
}

async function buildGanttFromLogs() {
  loading.value = true
  try {
    // Try to load schedule execution logs from API
    const logRes = await devScheduleLogs()
    const logs = logRes.data || []
    // Try to load schedule tasks for names
    let taskMap = {}
    try {
      const taskRes = await devSchedulePage({ page: 1, size: 1000 })
      ;(taskRes.data.records || []).forEach(t => { taskMap[t.id] = t.name })
    } catch (e) { /* ignore */ }

    if (logs.length > 0) {
      // Build gantt items from real logs
      ganttItems.value = logs.map((log, idx) => ({
        id: idx + 1,
        name: taskMap[log.taskId] || `任务 #${log.taskId}`,
        start: log.startTime ? new Date(log.startTime) : new Date(Date.now() - 3600000),
        end: log.endTime ? new Date(log.endTime) : new Date(Date.now() - 1800000),
        status: log.status || 'SUCCESS',
        duration: log.durationMs || (log.endTime && log.startTime ? new Date(log.endTime).getTime() - new Date(log.startTime).getTime() : 300000)
      }))
    } else {
      // Fallback: no logs available
      ganttItems.value = []
    }
  } catch (e) {
    console.error('Failed to load schedule logs:', e)
    ganttItems.value = []
  } finally {
    loading.value = false
  }
}

onMounted(buildGanttFromLogs)
</script>

<template>
  <div class="page-container">
    <div class="page-header">工作流甘特图</div>
    <div class="gantt-chart" v-loading="loading">
      <div class="gantt-header">
        <div class="gantt-label-header">节点名称</div>
        <div class="gantt-timeline-header">时间线</div>
      </div>
      <div class="gantt-rows">
        <div v-for="item in ganttItems" :key="item.id" class="gantt-row">
          <div class="gantt-label">
            <span :class="'status-dot status-' + item.status"></span>
            {{ item.name }}
          </div>
          <div class="gantt-bar-container">
            <div class="gantt-bar" :style="barStyle(item)" :title="item.name + ': ' + formatDuration(item.duration)">
              <span class="bar-label">{{ formatDuration(item.duration) }}</span>
            </div>
          </div>
        </div>
        <el-empty v-if="!ganttItems.length" description="暂无调度执行记录" />
      </div>
    </div>
    <div class="gantt-table" style="margin-top:20px">
      <div class="page-header" style="font-size:16px">节点详情</div>
      <el-table :data="ganttItems" border>
        <el-table-column prop="name" label="节点" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCESS' ? 'success' : row.status === 'FAILED' ? 'danger' : row.status === 'RUNNING' ? 'primary' : 'info'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="开始时间" width="180">
          <template #default="{ row }">{{ formatDate(row.start) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" width="180">
          <template #default="{ row }">{{ formatDate(row.end) }}</template>
        </el-table-column>
        <el-table-column label="耗时" width="120">
          <template #default="{ row }">{{ formatDuration(row.duration) }}</template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<style scoped>
.page-container { padding: 16px; }
.page-header { font-size: 18px; font-weight: 600; margin-bottom: 16px; }
.gantt-chart { border: 1px solid var(--el-border-color); border-radius: 4px; overflow: hidden; }
.gantt-header { display: flex; background: var(--el-fill-color-light); border-bottom: 1px solid var(--el-border-color); }
.gantt-label-header { width: 200px; padding: 8px 12px; font-weight: 600; border-right: 1px solid var(--el-border-color); }
.gantt-timeline-header { flex: 1; padding: 8px 12px; font-weight: 600; }
.gantt-rows { }
.gantt-row { display: flex; border-bottom: 1px solid var(--el-border-color); }
.gantt-label { width: 200px; padding: 8px 12px; border-right: 1px solid var(--el-border-color); font-size: 13px; display: flex; align-items: center; gap: 6px; flex-shrink: 0; }
.gantt-bar-container { flex: 1; padding: 2px 4px; position: relative; min-height: 36px; display: flex; align-items: center; }
.gantt-bar { height: 28px; border-radius: 4px; position: absolute; top: 4px; display: flex; align-items: center; justify-content: center; min-width: 4px; transition: all 0.2s; }
.gantt-bar:hover { opacity: 0.8; transform: scaleY(1.1); }
.bar-label { color: white; font-size: 11px; padding: 0 4px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.status-dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; flex-shrink: 0; }
.status-SUCCESS { background: #67c23a; }
.status-FAILED { background: #f56c6c; }
.status-RUNNING { background: #409eff; animation: pulse 1s infinite; }
.status-PENDING { background: #909399; }
@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.4; } }
</style>
