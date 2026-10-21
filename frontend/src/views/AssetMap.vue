<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>资产地图</h2>
        <div class="sub">数据中心整体数据形态：体量、分布、更新频率、资源占用与质量水位</div>
      </div>
      <el-button size="small" @click="load">刷新</el-button>
    </div>

    <div class="grid grid-4">
      <div class="stat"><div class="label">数据表</div><div class="value">{{ map.totalTables }}<small>张</small></div><div class="foot">字段 {{ map.columnCount }} 个</div></div>
      <div class="stat"><div class="label">数据总量</div><div class="value">{{ num(map.totalRows) }}<small>行</small></div><div class="foot">存储 {{ map.totalSizeMb }} MB</div></div>
      <div class="stat orange"><div class="label">数据源</div><div class="value">{{ map.datasourceCount }}<small>个</small></div><div class="foot">含关系库/消息/工业协议/API</div></div>
      <div class="stat red"><div class="label">高敏表</div><div class="value">{{ map.sensitiveTables }}<small>张</small></div><div class="foot">L3 及以上敏感等级</div></div>
    </div>

    <div class="grid grid-2" style="margin-top:12px">
      <div class="card">
        <div class="card-head"><h3>业务域数据分布</h3><span class="hint">按表数量</span></div>
        <ChartBox :option="domainOption" height="260px" />
      </div>
      <div class="card">
        <div class="card-head"><h3>分层存储占比</h3><span class="hint">ODS / DWD / DWS / ADS</span></div>
        <ChartBox :option="layerOption" height="260px" />
      </div>
    </div>

    <div class="grid grid-3" style="margin-top:12px">
      <div class="card">
        <div class="card-head"><h3>质量水位</h3></div>
        <ChartBox :option="qualityOption" height="220px" />
      </div>
      <div class="card">
        <div class="card-head"><h3>更新频率分布</h3></div>
        <ChartBox :option="freqOption" height="220px" />
      </div>
      <div class="card">
        <div class="card-head"><h3>运营指标趋势</h3></div>
        <ChartBox :option="trendOption" height="220px" />
      </div>
    </div>

    <div class="card" style="margin-top:12px">
      <div class="card-head"><h3>体量 Top 10 表</h3><span class="hint">按存储占用排序</span></div>
      <el-table :data="map.topTables || []" size="small">
        <el-table-column prop="name" label="表名" min-width="170"><template #default="{ row }"><span class="mono">{{ row.name }}</span></template></el-table-column>
        <el-table-column prop="cnName" label="中文名" min-width="150" />
        <el-table-column prop="domain" label="业务域" width="110" />
        <el-table-column prop="rowCount" label="行数" width="110" align="right"><template #default="{ row }">{{ Number(row.rowCount || 0).toLocaleString() }}</template></el-table-column>
        <el-table-column prop="dataSizeMb" label="存储(MB)" width="110" align="right" />
        <el-table-column prop="qualityScore" label="质量分" width="100" align="right">
          <template #default="{ row }"><span :style="{ color: row.qualityScore >= 85 ? '#16a34a' : '#d97706' }">{{ row.qualityScore }}</span></template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import ChartBox from '@/components/ChartBox.vue'
import { assetApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

const store = useAuthStore()

const map = ref({})
const dist = ref({})
const trend = ref([])

const num = (v) => {
  const n = Number(v || 0)
  return n >= 10000 ? (n / 10000).toFixed(1) + '万' : n.toLocaleString()
}

const domainOption = computed(() => {
  const rows = map.value.domains || []
  return {
    grid: { left: 70, right: 26, top: 12, bottom: 20 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: { type: 'value', splitLine: { lineStyle: { color: '#f1f5f9' } }, axisLabel: { fontSize: 10, color: '#6b7280' } },
    yAxis: { type: 'category', data: rows.map((r) => r.domain), axisTick: { show: false }, axisLine: { show: false }, axisLabel: { fontSize: 11, color: '#4b5563' } },
    series: [{
      type: 'bar', barWidth: 13,
      data: rows.map((r) => r.tableCnt),
      itemStyle: { color: '#2563eb', borderRadius: [0, 7, 7, 0] },
      label: { show: true, position: 'right', fontSize: 10, color: '#6b7280' }
    }]
  }
})

const layerOption = computed(() => {
  const rows = map.value.layers || []
  const colors = ['#64748b', '#2563eb', '#0ea5e9', '#d97706']
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} 张 ({d}%)' },
    legend: { bottom: 0, itemWidth: 8, itemHeight: 8, textStyle: { fontSize: 10 } },
    series: [{
      type: 'pie', radius: ['45%', '68%'], center: ['50%', '44%'],
      label: { fontSize: 10, formatter: '{b}\n{c}' },
      data: rows.map((r, i) => ({ name: r.layer, value: r.tableCnt, itemStyle: { color: colors[i % colors.length] } }))
    }]
  }
})

const qualityOption = computed(() => {
  const rows = dist.value.byQuality || []
  const colors = { '优秀(≥90)': '#16a34a', '良好(80-90)': '#2563eb', '一般(70-80)': '#f59e0b', '待改进(<70)': '#dc2626' }
  return {
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie', radius: ['38%', '64%'], center: ['50%', '50%'],
      label: { fontSize: 10, formatter: '{b}\n{c} 张' },
      data: rows.map((r) => ({ name: r.name, value: r.cnt, itemStyle: { color: colors[r.name] || '#94a3b8' } }))
    }]
  }
})

const freqOption = computed(() => {
  const rows = dist.value.byFreq || []
  return {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, itemWidth: 8, itemHeight: 8, textStyle: { fontSize: 10 } },
    series: [{
      type: 'pie', radius: ['42%', '66%'], center: ['50%', '44%'],
      label: { fontSize: 10, formatter: '{b}:{c}' },
      data: rows.map((r) => ({ name: r.name, value: r.cnt }))
    }]
  }
})

const trendOption = computed(() => {
  const rows = trend.value || []
  return {
    grid: { left: 44, right: 40, top: 26, bottom: 24 },
    tooltip: { trigger: 'axis' },
    legend: { top: 0, itemWidth: 8, itemHeight: 8, textStyle: { fontSize: 10 } },
    xAxis: { type: 'category', data: rows.map((r) => String(r.statDate).slice(5)), axisLabel: { fontSize: 9, color: '#6b7280' }, axisLine: { lineStyle: { color: '#e5e7eb' } } },
    yAxis: [
      { type: 'value', name: '访问', nameTextStyle: { fontSize: 10 }, splitLine: { lineStyle: { color: '#f1f5f9' } }, axisLabel: { fontSize: 9, color: '#6b7280' } },
      { type: 'value', name: '价值', nameTextStyle: { fontSize: 10 }, splitLine: { show: false }, axisLabel: { fontSize: 9, color: '#6b7280' } }
    ],
    series: [
      { name: '访问量', type: 'line', smooth: true, symbol: 'none', data: rows.map((r) => r.accessCnt), itemStyle: { color: '#2563eb' } },
      { name: '资产价值', type: 'line', yAxisIndex: 1, smooth: true, symbol: 'none', data: rows.map((r) => r.assetValue), itemStyle: { color: '#d97706' } }
    ]
  }
})

const load = async () => {
  // 三块数据互不依赖：任一失败保留其余图表，避免整页空白；无权限的接口不发请求
  const [m, d, t] = await Promise.allSettled([
    assetApi.map(),
    assetApi.distribution(),
    store.has('asset:metrics:view') ? assetApi.trend({ days: 30 }) : Promise.resolve(null)
  ])
  map.value = m.status === 'fulfilled' ? m.value || {} : {}
  dist.value = d.status === 'fulfilled' ? d.value || {} : {}
  trend.value = t.status === 'fulfilled' ? (t.value?.records || []) : []
}

onMounted(load)
</script>
