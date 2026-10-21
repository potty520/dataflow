<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>治理概览</h2>
        <div class="sub">覆盖元数据、建模、标准、集成、质量、安全、资产与服务的统一运营视图</div>
      </div>
      <div class="toolbar" style="margin:0">
        <el-tag effect="plain" size="small">采集覆盖率 {{ data.collectHealth?.coverage ?? '-' }}%</el-tag>
        <el-tag effect="plain" size="small" type="success">最近采集 {{ fmt(data.collectHealth?.lastCollect) }}</el-tag>
        <el-button size="small" :icon="Refresh" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="grid grid-4">
      <div class="stat">
        <div class="icon"><el-icon><Coin /></el-icon></div>
        <div class="label">元数据对象</div>
        <div class="value">{{ kpi.tableCount }} <small>张表 / {{ kpi.columnCount }} 字段</small></div>
        <div class="foot">总行数 {{ num(kpi.totalRows) }} · 数据源 {{ kpi.datasourceCount }} 个</div>
      </div>
      <div class="stat green">
        <div class="icon"><el-icon><CircleCheck /></el-icon></div>
        <div class="label">综合质量分</div>
        <div class="value">{{ kpi.qualityScore }} <small>{{ level(kpi.qualityScore) }}</small></div>
        <div class="foot">规则 {{ kpi.ruleCount }} 条 · 问题闭环率 {{ metrics.closedRate }}%</div>
      </div>
      <div class="stat orange">
        <div class="icon"><el-icon><Warning /></el-icon></div>
        <div class="label">待处理问题</div>
        <div class="value">{{ kpi.issueOpen }} <small>单</small></div>
        <div class="foot">已关闭 {{ kpi.issueClosed }} · 超期 {{ todos.overdueIssues }}</div>
      </div>
      <div class="stat red">
        <div class="icon"><el-icon><Promotion /></el-icon></div>
        <div class="label">数据服务</div>
        <div class="value">{{ kpi.apiCount }} <small>个已发布</small></div>
        <div class="foot">累计调用 {{ num(kpi.callCount) }} 次</div>
      </div>
    </div>

    <div class="grid grid-4" style="margin-top:12px">
      <div class="stat">
        <div class="icon"><el-icon><Lock /></el-icon></div>
        <div class="label">敏感字段</div>
        <div class="value">{{ kpi.sensitiveColumns }} <small>个</small></div>
        <div class="foot">已配置脱敏策略并审批生效</div>
      </div>
      <div class="stat">
        <div class="icon"><el-icon><Operation /></el-icon></div>
        <div class="label">集成作业</div>
        <div class="value">{{ kpi.jobCount }} <small>个</small></div>
        <div class="foot">失败实例 {{ todos.failedJobs }} 次（近全量）</div>
      </div>
      <div class="stat">
        <div class="icon"><el-icon><Box /></el-icon></div>
        <div class="label">行业配置包</div>
        <div class="value">{{ kpi.packageCount }} <small>个</small></div>
        <div class="foot">覆盖通信/金融/工业/政务/零售</div>
      </div>
      <div class="stat">
        <div class="icon"><el-icon><Files /></el-icon></div>
        <div class="label">资产价值总分</div>
        <div class="value">{{ kpi.assetValue }} <small>分</small></div>
        <div class="foot">质量 45% + 热度 30% + 调用 25%</div>
      </div>
    </div>

    <div class="grid grid-2" style="margin-top:12px">
      <div class="card">
        <div class="card-head">
          <h3>质量评分趋势（近 14 天）</h3>
          <span class="hint">按表评分的日均值</span>
        </div>
        <ChartBox :option="trendOption" height="260px" />
      </div>
      <div class="card">
        <div class="card-head">
          <h3>六维度通过率</h3>
          <span class="hint">完整性 / 唯一性 / 准确性 / 有效性 / 一致性 / 时效性</span>
        </div>
        <ChartBox :option="dimOption" height="260px" />
      </div>
    </div>

    <div class="grid grid-3" style="margin-top:12px">
      <div class="card">
        <div class="card-head"><h3>问题单状态分布</h3></div>
        <ChartBox :option="issueOption" height="220px" />
      </div>
      <div class="card">
        <div class="card-head"><h3>资产分层分布</h3></div>
        <ChartBox :option="layerOption" height="220px" />
      </div>
      <div class="card">
        <div class="card-head"><h3>服务调用趋势</h3></div>
        <ChartBox :option="callOption" height="220px" />
      </div>
    </div>

    <div class="grid grid-2" style="margin-top:12px">
      <div class="card">
        <div class="card-head">
          <h3>最近质量问题</h3>
          <router-link to="/quality/issues" style="font-size:12px">查看全部</router-link>
        </div>
        <el-table :data="data.recentIssues || []" size="small" :show-header="true">
          <el-table-column prop="ruleName" label="规则" min-width="150" show-overflow-tooltip />
          <el-table-column prop="tableName" label="表" width="130" />
          <el-table-column prop="severity" label="级别" width="80">
            <template #default="{ row }">
              <el-tag size="small" :type="row.severity === 'CRITICAL' ? 'danger' : row.severity === 'MAJOR' ? 'warning' : 'info'">
                {{ row.severity }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="90">
            <template #default="{ row }">{{ statusCn(row.status) }}</template>
          </el-table-column>
          <el-table-column prop="assignee" label="责任人" width="80" />
        </el-table>
      </div>
      <div class="card">
        <div class="card-head">
          <h3>安全告警与作业动态</h3>
          <router-link to="/security/audit" style="font-size:12px">审计详情</router-link>
        </div>
        <el-timeline style="padding-left:6px">
          <el-timeline-item v-for="(a, idx) in data.recentAlerts || []" :key="a.id ?? a.title ?? idx"
                            :timestamp="fmt(a.alertTime)" :type="a.severity === 'HIGH' ? 'danger' : 'warning'">
            {{ a.title }} <el-tag size="small" effect="plain" style="margin-left:6px">{{ a.status }}</el-tag>
          </el-timeline-item>
        </el-timeline>
        <div class="section-title">最近作业实例</div>
        <div v-for="j in data.recentJobs || []" :key="j.jobName + j.startTime" class="kv">
          <span class="k" style="width:130px">{{ j.jobName }}</span>
          <span class="v">
            <el-tag size="small" :type="j.status === 'SUCCESS' ? 'success' : 'danger'" effect="plain">{{ j.status }}</el-tag>
            <span class="muted" style="margin-left:8px">{{ fmt(j.startTime) }} · {{ j.rowsWritten }} 行</span>
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import ChartBox from '@/components/ChartBox.vue'
import { overviewApi, qualityApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

const store = useAuthStore()

const data = ref({})
const metrics = ref({})
const kpi = computed(() => data.value.kpi || {})
const todos = computed(() => data.value.alerts || {})

const num = (v) => {
  const n = Number(v || 0)
  return n >= 10000 ? (n / 10000).toFixed(2) + ' 万' : n.toLocaleString()
}
const fmt = (v) => (v ? String(v).replace('T', ' ').slice(0, 16) : '-')
const level = (s) => (s >= 95 ? '优秀' : s >= 85 ? '良好' : s >= 70 ? '一般' : s >= 60 ? '较差' : '差')
const statusCn = (s) => ({ DISCOVERED: '待派单', DISPATCHED: '已派单', PROCESSING: '处理中', REVIEWING: '待复核', CLOSED: '已关闭', OVERDUE: '已超期', IGNORED: '已忽略', RECURRED: '复发' }[s] || s)

const dimCn = { COMPLETENESS: '完整性', UNIQUENESS: '唯一性', ACCURACY: '准确性', VALIDITY: '有效性', CONSISTENCY: '一致性', TIMELINESS: '时效性' }

const trendOption = computed(() => {
  const rows = data.value.qualityTrend || []
  return {
    grid: { left: 40, right: 18, top: 24, bottom: 28 },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: rows.map((r) => String(r.statDate).slice(5)), axisLine: { lineStyle: { color: '#e5e7eb' } }, axisLabel: { color: '#6b7280', fontSize: 10 } },
    yAxis: { type: 'value', min: 60, max: 100, splitLine: { lineStyle: { color: '#f1f5f9' } }, axisLabel: { color: '#6b7280', fontSize: 10 } },
    series: [{
      type: 'line', smooth: true, symbolSize: 5, data: rows.map((r) => r.score),
      itemStyle: { color: '#2563eb' }, lineStyle: { width: 2.5 },
      areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(37,99,235,.28)' }, { offset: 1, color: 'rgba(37,99,235,0)' }] } }
    }]
  }
})

const dimOption = computed(() => {
  const rows = (data.value.byDimension || []).slice().sort((a, b) => a.passRate - b.passRate)
  return {
    grid: { left: 62, right: 30, top: 16, bottom: 20 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: { type: 'value', max: 100, splitLine: { lineStyle: { color: '#f1f5f9' } }, axisLabel: { color: '#6b7280', fontSize: 10 } },
    yAxis: { type: 'category', data: rows.map((r) => dimCn[r.dimension] || r.dimension), axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: '#4b5563', fontSize: 11 } },
    series: [{
      type: 'bar', barWidth: 14, data: rows.map((r) => ({ value: r.passRate, itemStyle: { color: r.passRate >= 90 ? '#16a34a' : r.passRate >= 75 ? '#2563eb' : '#d97706', borderRadius: [0, 7, 7, 0] } })),
      label: { show: true, position: 'right', formatter: '{c}%', fontSize: 10, color: '#6b7280' }
    }]
  }
})

const issueOption = computed(() => {
  const map = { DISCOVERED: '待派单', DISPATCHED: '已派单', PROCESSING: '处理中', REVIEWING: '待复核', CLOSED: '已关闭', OVERDUE: '已超期' }
  const colors = { DISCOVERED: '#94a3b8', DISPATCHED: '#3b82f6', PROCESSING: '#f59e0b', REVIEWING: '#8b5cf6', CLOSED: '#16a34a', OVERDUE: '#dc2626' }
  const rows = data.value.issueByStatus || []
  return {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, itemWidth: 8, itemHeight: 8, textStyle: { fontSize: 10, color: '#6b7280' } },
    series: [{
      type: 'pie', radius: ['42%', '66%'], center: ['50%', '44%'], avoidLabelOverlap: true,
      itemStyle: { borderColor: '#fff', borderWidth: 2 },
      label: { show: true, formatter: '{c}', fontSize: 10, color: '#4b5563' },
      data: rows.map((r) => ({ name: map[r.status] || r.status, value: r.cnt, itemStyle: { color: colors[r.status] || '#94a3b8' } }))
    }]
  }
})

const layerOption = computed(() => {
  const rows = data.value.byLayer || []
  return {
    grid: { left: 40, right: 16, top: 16, bottom: 24 },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: rows.map((r) => r.name), axisLine: { lineStyle: { color: '#e5e7eb' } }, axisLabel: { color: '#6b7280', fontSize: 11 } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#f1f5f9' } }, axisLabel: { color: '#6b7280', fontSize: 10 } },
    series: [{ type: 'bar', barWidth: 26, data: rows.map((r) => r.cnt), itemStyle: { color: '#3b82f6', borderRadius: [4, 4, 0, 0] }, label: { show: true, position: 'top', fontSize: 10, color: '#6b7280' } }]
  }
})

const callOption = computed(() => {
  const rows = data.value.callTrend || []
  return {
    grid: { left: 44, right: 16, top: 16, bottom: 24 },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: rows.map((r) => String(r.statDate).slice(5)), axisLine: { lineStyle: { color: '#e5e7eb' } }, axisLabel: { color: '#6b7280', fontSize: 10 } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#f1f5f9' } }, axisLabel: { color: '#6b7280', fontSize: 10 } },
    series: [{
      type: 'line', smooth: true, data: rows.map((r) => r.calls), symbol: 'none',
      itemStyle: { color: '#0ea5e9' },
      areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(14,165,233,.3)' }, { offset: 1, color: 'rgba(14,165,233,0)' }] } }
    }]
  }
})

const load = async () => {
  // 看板主体优先；问题闭环指标是旁路，无权限时不发请求（避免 403 噪声）
  try {
    data.value = (await overviewApi.dashboard()) || {}
  } catch (e) {
    data.value = {}
  }
  if (!store.has('quality:issue:view')) {
    metrics.value = {}
    return
  }
  try {
    metrics.value = (await qualityApi.issueMetrics()) || {}
  } catch (e) {
    metrics.value = {}
  }
}

onMounted(load)
</script>
