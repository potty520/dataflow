<script setup>
import { onMounted, ref, reactive, watch } from 'vue'
import { svcMonitorStats, svcCallStats } from '../../../api'
import * as echarts from 'echarts'

const stats = ref({})
const callStats = ref({})
const timeRange = ref('day')
const chartRef = ref(null)
let chart = null

onMounted(async () => {
  const [res1, res2] = await Promise.all([
    svcMonitorStats(),
    svcCallStats('day')
  ])
  stats.value = res1.data || {}
  callStats.value = res2.data || {}
  renderChart()
})

watch(timeRange, async (val) => {
  const res = await svcCallStats(val)
  callStats.value = res.data || {}
  renderChart()
})

function renderChart() {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)

  const hourly = callStats.value.callsByHour || []
  chart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: hourly.map(h => h.hour) },
    yAxis: { type: 'value', name: '调用次数' },
    series: [{
      type: 'line',
      data: hourly.map(h => h.count),
      smooth: true,
      areaStyle: { opacity: 0.3 },
      itemStyle: { color: '#409eff' }
    }]
  })
}
</script>

<template>
  <div class="page-container">
    <div class="page-header">服务监控</div>

    <!-- Stat Cards -->
    <div class="stat-cards">
      <div class="stat-card"><div class="label">API总数</div><div class="value">{{ stats.apiTotal || 0 }}</div></div>
      <div class="stat-card"><div class="label">已发布API</div><div class="value">{{ stats.publishedApi || 0 }}</div></div>
      <div class="stat-card"><div class="label">今日调用</div><div class="value">{{ callStats.totalCalls || 0 }}</div></div>
      <div class="stat-card"><div class="label">成功率</div><div class="value">{{ callStats.successRate || 100 }}%</div></div>
      <div class="stat-card"><div class="label">平均响应</div><div class="value">{{ callStats.avgResponseTime || 0 }}ms</div></div>
      <div class="stat-card"><div class="label">待审批工单</div><div class="value">{{ stats.pendingWorkorder || 0 }}</div></div>
    </div>

    <!-- Time Range -->
    <el-card header="调用量趋势" style="margin-top:16px">
      <el-radio-group v-model="timeRange" size="small">
        <el-radio-button value="hour">近1小时</el-radio-button>
        <el-radio-button value="day">今日</el-radio-button>
        <el-radio-button value="week">近7天</el-radio-button>
      </el-radio-group>
      <div ref="chartRef" style="height:300px;margin-top:12px" />
    </el-card>

    <!-- Top APIs -->
    <el-card header="调用排行 Top 5" style="margin-top:16px">
      <el-table :data="callStats.topApis || []" border>
        <el-table-column type="index" label="排名" width="60" />
        <el-table-column prop="apiName" label="API名称" />
        <el-table-column prop="count" label="调用次数" width="120" />
      </el-table>
      <el-empty v-if="!(callStats.topApis || []).length" description="暂无调用数据" />
    </el-card>

    <!-- Error Rate Alert -->
    <el-card header="告警配置" style="margin-top:16px">
      <el-row :gutter="16">
        <el-col :span="8">
          <div class="alert-item">
            <span>错误率阈值</span>
            <el-slider v-model="errorThreshold" :max="100" show-input style="width:200px" />
          </div>
        </el-col>
        <el-col :span="8">
          <div class="alert-item">
            <span>响应时间阈值(ms)</span>
            <el-input-number v-model="responseThreshold" :min="100" :max="10000" :step="100" size="small" />
          </div>
        </el-col>
        <el-col :span="8">
          <div class="alert-item">
            <span>当前错误率</span>
            <el-tag :type="(callStats.errorRate || 0) > 10 ? 'danger' : 'success'" size="large">
              {{ callStats.errorRate || 0 }}%
            </el-tag>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script>
export default {
  data() {
    return {
      errorThreshold: 10,
      responseThreshold: 2000
    }
  }
}
</script>

<style scoped>
.alert-item { display: flex; align-items: center; gap: 10px; }
</style>
