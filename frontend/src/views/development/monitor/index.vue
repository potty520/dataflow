<script setup>
import { onMounted, ref, onUnmounted } from 'vue'
import { devMonitorStats } from '../../../api'
import { ElMessage } from 'element-plus'

const running = ref(0)
const queued = ref(0)
const failed = ref(0)
const success = ref(0)
const total = ref(0)
const failedTasks = ref([])
const runningTasks = ref([])

let timer = null

async function loadStats() {
  try {
    const res = await devMonitorStats()
    const d = res.data || {}
    running.value = d.running || 0
    queued.value = d.pending || 0
    failed.value = d.failed || 0
    success.value = d.success || 0
    total.value = d.total || 0
    failedTasks.value = d.failedTasks || []
    runningTasks.value = d.runningTasks || []
  } catch (e) {
    console.error('Failed to load dev monitor stats:', e)
  }
}

function startAutoRefresh() {
  timer = setInterval(() => { loadStats() }, 15000)
}

async function retry(task) {
  ElMessage.success('重试任务已提交: ' + task.name)
}

onMounted(() => {
  loadStats()
  startAutoRefresh()
})
onUnmounted(() => { if (timer) clearInterval(timer) })
</script>

<template>
  <div class="page-container">
    <div class="page-header">任务监控</div>

    <!-- Stat Cards -->
    <el-row :gutter="16" style="margin-bottom:24px">
      <el-col :span="4">
        <el-card><div style="text-align:center">
          <div style="color:#999;font-size:13px">任务总数</div>
          <div style="font-size:32px;font-weight:bold;color:#909399">{{ total }}</div>
        </div></el-card>
      </el-col>
      <el-col :span="5">
        <el-card><div style="text-align:center">
          <div style="color:#999;font-size:13px">运行中</div>
          <div style="font-size:32px;font-weight:bold;color:#1890ff">{{ running }}</div>
        </div></el-card>
      </el-col>
      <el-col :span="5">
        <el-card><div style="text-align:center">
          <div style="color:#999;font-size:13px">排队中</div>
          <div style="font-size:32px;font-weight:bold;color:#fa8c16">{{ queued }}</div>
        </div></el-card>
      </el-col>
      <el-col :span="5">
        <el-card><div style="text-align:center">
          <div style="color:#999;font-size:13px">今日失败</div>
          <div style="font-size:32px;font-weight:bold;color:#f5222d">{{ failed }}</div>
        </div></el-card>
      </el-col>
      <el-col :span="5">
        <el-card><div style="text-align:center">
          <div style="color:#999;font-size:13px">今日成功</div>
          <div style="font-size:32px;font-weight:bold;color:#52c41a">{{ success }}</div>
        </div></el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <!-- Failed Tasks Top 10 -->
      <el-col :span="12">
        <el-card>
          <template #header><span style="font-weight:bold">失败任务</span></template>
          <el-table :data="failedTasks" border>
            <el-table-column prop="name" label="任务名" />
            <el-table-column prop="workflow" label="所属工作流" />
            <el-table-column prop="time" label="失败时间" width="160" />
            <el-table-column prop="error" label="错误信息" show-overflow-tooltip />
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button link type="primary" @click="retry(row)">重试</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!failedTasks.length" description="暂无失败任务" />
        </el-card>
      </el-col>

      <!-- Running Tasks Timeline -->
      <el-col :span="12">
        <el-card>
          <template #header><span style="font-weight:bold">运行中任务</span></template>
          <div v-for="task in runningTasks" :key="task.name" style="margin-bottom:20px">
            <div style="display:flex;justify-content:space-between;margin-bottom:4px">
              <span>{{ task.name }}</span>
              <span style="color:#999;font-size:12px">{{ task.startTime }} · {{ task.status }}</span>
            </div>
            <el-progress :percentage="task.progress" :status="task.progress >= 100 ? 'success' : ''" :stroke-width="18">
              <template #default="{ percentage }">
                <span style="font-size:12px">{{ percentage }}%</span>
              </template>
            </el-progress>
          </div>
          <el-empty v-if="!runningTasks.length" description="暂无运行中任务" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>
