<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>血缘分析</h2>
        <div class="sub">表级 / 字段级 / 加工级追溯，支持上下游穿透与 SQL 自动解析</div>
      </div>
      <div class="toolbar" style="margin:0">
        <el-tag effect="plain" size="small">节点 {{ graph.stats?.nodeCount || 0 }}</el-tag>
        <el-tag effect="plain" size="small">边 {{ graph.stats?.edgeCount || 0 }}</el-tag>
      </div>
    </div>

    <div class="card">
      <div class="toolbar" style="margin-bottom:0">
        <el-select v-model="form.objectId" filterable placeholder="选择对象" style="width:230px">
          <el-option v-for="t in tableOptions" :key="t.name" :label="`${t.name} · ${t.cnName || ''}`" :value="t.name" />
        </el-select>
        <el-radio-group v-model="form.granularity" size="small">
          <el-radio-button value="TABLE">表级</el-radio-button>
          <el-radio-button value="COLUMN">字段级</el-radio-button>
        </el-radio-group>
        <el-radio-group v-model="form.direction" size="small">
          <el-radio-button value="downstream">下游</el-radio-button>
          <el-radio-button value="upstream">上游</el-radio-button>
          <el-radio-button value="both">双向</el-radio-button>
        </el-radio-group>
        <el-select v-model="form.level" size="small" style="width:110px">
          <el-option v-for="n in [1,2,3,4]" :key="n" :label="`${n} 跳`" :value="n" />
        </el-select>
        <el-button type="primary" size="small" :icon="Share" @click="load">分析</el-button>
        <span class="spacer"></span>
        <el-button size="small" @click="quick('ods_order')">订单链路</el-button>
        <el-button size="small" @click="quick('ods_device_metric')">设备链路</el-button>
      </div>
    </div>

    <div class="grid" style="grid-template-columns: 1fr 320px; margin-top:12px">
      <div class="card" style="padding:6px">
        <ChartBox :option="graphOption" height="560px" @chart-click="onNodeClick" />
        <div class="muted" style="font-size:11.5px;padding:6px 10px">
          节点颜色：ODS 灰蓝 / DWD 蓝 / DWS 青 / ADS 橙；点击节点可继续穿透，滚轮缩放、拖拽平移
        </div>
      </div>
      <div>
        <div class="card">
          <div class="card-head"><h3>变更影响分析</h3></div>
          <div v-if="impact.objectId">
            <div class="kv"><span class="k">分析对象</span><span class="v mono">{{ impact.objectId }}</span></div>
            <div class="kv"><span class="k">下游表</span><span class="v">{{ impact.tableCount }} 张</span></div>
            <div class="kv"><span class="k">受影响作业</span><span class="v">{{ (impact.jobs || []).join('、') || '-' }}</span></div>
            <div class="kv"><span class="k">质量规则</span><span class="v">{{ impact.ruleCount }} 条</span></div>
            <div class="kv"><span class="k">数据服务</span><span class="v">{{ (impact.affectedApis || []).length }} 个</span></div>
            <div class="kv"><span class="k">结论</span><span class="v">{{ impact.summary }}</span></div>
          </div>
          <div v-else class="empty">选择节点后展示影响范围</div>
        </div>

        <div class="card">
          <div class="card-head"><h3>加工级血缘</h3></div>
          <div v-if="edge.id">
            <div class="kv"><span class="k">来源</span><span class="v mono">{{ edge.srcTable }}.{{ edge.srcColumn }}</span></div>
            <div class="kv"><span class="k">目标</span><span class="v mono">{{ edge.dstTable }}.{{ edge.dstColumn }}</span></div>
            <div class="kv"><span class="k">作业</span><span class="v">{{ edge.job }}</span></div>
            <div class="kv"><span class="k">转换类型</span><span class="v"><el-tag size="small" effect="plain">{{ edge.transformType }}</el-tag></span></div>
            <div class="kv"><span class="k">转换表达式</span><span class="v mono">{{ edge.transformExpr }}</span></div>
          </div>
          <div v-else class="empty">点击连线查看转换规则</div>
        </div>
      </div>
    </div>

    <div class="card" style="margin-top:12px">
      <div class="card-head">
        <h3>SQL 血缘解析</h3>
        <span class="hint">粘贴 INSERT ... SELECT 语句，自动识别源表、目标表与字段映射</span>
      </div>
      <el-input v-model="sql" type="textarea" :rows="5" class="mono"
                placeholder="INSERT INTO dwd_customer_dim (customer_id, cust_name, phone) SELECT customer_id, cust_name, mask(phone) AS phone FROM ods_customer WHERE city IS NOT NULL" />
      <div class="toolbar" style="margin-top:10px;margin-bottom:0">
        <el-checkbox v-model="persist">解析结果写入血缘库</el-checkbox>
        <span class="spacer"></span>
        <el-button size="small" type="primary" :icon="MagicStick" @click="parseSql">解析</el-button>
      </div>
      <div v-if="parsed.target || (parsed.sources || []).length" style="margin-top:10px">
        <div class="kv"><span class="k">目标表</span><span class="v mono">{{ parsed.target || '（纯查询，无写入目标）' }}</span></div>
        <div class="kv"><span class="k">源表</span><span class="v">
          <el-tag v-for="s in parsed.sources" :key="s" size="small" effect="plain" style="margin-right:6px">{{ s }}</el-tag>
        </span></div>
        <div class="kv"><span class="k">字段映射</span><span class="v">
          <span v-for="(c, i) in parsed.columnMappings" :key="i" class="mono" style="margin-right:12px">
            {{ c.srcColumn }} → {{ c.dstColumn }}
          </span>
        </span></div>
        <div class="kv"><span class="k">解析引擎</span><span class="v">{{ parsed.engine }}</span></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { Share, MagicStick } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import ChartBox from '@/components/ChartBox.vue'
import { metadataApi } from '@/api'

const route = useRoute()
const form = reactive({ objectId: route.query.object || 'ods_customer', granularity: 'TABLE', direction: 'downstream', level: 3 })
const graph = ref({ nodes: [], edges: [], stats: {} })
const impact = ref({})
const edge = ref({})
const sql = ref('INSERT INTO dwd_order_fact (order_id, customer_id, amount) SELECT o.order_id, o.customer_id, o.amount FROM ods_order o')
const persist = ref(true)
const parsed = ref({})
const tableOptions = ref([])

const LAYER_COLOR = { ODS: '#64748b', DWD: '#2563eb', DWS: '#0ea5e9', ADS: '#d97706', EXT: '#94a3b8' }

const graphOption = computed(() => {
  const nodes = (graph.value.nodes || []).map((n) => ({
    id: n.id,
    name: n.name,
    value: n.rowCount,
    symbolSize: n.isRoot ? 62 : 46,
    itemStyle: { color: LAYER_COLOR[n.layer] || '#94a3b8', borderColor: n.isRoot ? '#f59e0b' : '#fff', borderWidth: n.isRoot ? 3 : 1 },
    label: { show: true, color: '#111827', fontSize: 11, formatter: '{b}' },
    layer: n.layer
  }))
  const edges = (graph.value.edges || []).map((e) => ({
    source: e.source,
    target: e.target,
    value: e.job,
    label: { show: graph.value.edges.length <= 18, formatter: e.job || '', fontSize: 9, color: '#94a3b8' },
    lineStyle: { color: '#cbd5e1', width: 1.4, curveness: 0.08 },
    transformType: e.transformType,
    srcColumn: e.srcColumn,
    dstColumn: e.dstColumn,
    job: e.job
  }))
  return {
    tooltip: {
      formatter: (p) => {
        if (p.dataType === 'edge') {
          return `${p.data.source} → ${p.data.target}<br/>作业：${p.data.job || '-'}<br/>转换：${p.data.transformType || '-'}`
        }
        return `${p.data.name}<br/>分层：${p.data.layer || '-'}`
      }
    },
    legend: {
      top: 6, left: 10, itemWidth: 10, itemHeight: 10,
      data: ['ODS', 'DWD', 'DWS', 'ADS'],
      textStyle: { fontSize: 11, color: '#6b7280' }
    },
    series: [{
      type: 'graph',
      layout: 'force',
      left: '6%',
      top: '8%',
      width: '88%',
      height: '82%',
      roam: true,
      draggable: true,
      categories: [{ name: 'ODS' }, { name: 'DWD' }, { name: 'DWS' }, { name: 'ADS' }],
      force: { repulsion: 360, edgeLength: [110, 180], gravity: 0.12 },
      emphasis: { focus: 'adjacency', lineStyle: { width: 3, color: '#2563eb' } },
      edgeSymbol: ['none', 'arrow'],
      edgeSymbolSize: 7,
      data: nodes,
      links: edges,
      lineStyle: { opacity: 0.9 }
    }]
  }
})

const load = async () => {
  if (!form.objectId) return
  try {
    graph.value = await metadataApi.lineage({ ...form })
  } catch (e) {
    graph.value = { nodes: [], edges: [], stats: {} }
  }
  try {
    impact.value = (await metadataApi.impact({ objectId: form.objectId, level: form.level })) || {}
  } catch (e) {
    impact.value = {}
  }
}

const quick = (name) => {
  form.objectId = name
  load()
}

const onNodeClick = async (params) => {
  if (params.dataType === 'edge' && params.data) {
    edge.value = {
      srcTable: String(params.data.source).split('.')[0],
      dstTable: String(params.data.target).split('.')[0],
      srcColumn: params.data.srcColumn,
      dstColumn: params.data.dstColumn,
      job: params.data.job,
      transformType: params.data.transformType,
      transformExpr: params.data.label?.formatter || '-'
    }
    return
  }
  const node = graph.value.nodes.find((n) => n.id === params.name)
  if (node && node.layer !== 'EXT') {
    form.objectId = node.id
    await load()
  }
}

const parseSql = async () => {
  try {
    parsed.value = await metadataApi.parseSql({ sql: sql.value, jobName: 'ADHOC-PARSE', persist: persist.value })
    ElMessage.success('解析完成')
  } catch (e) { /* 已由拦截器提示 */ }
}

onMounted(() => {
  load()
  metadataApi
    .tables({ pageSize: 200 })
    .then((t) => {
      tableOptions.value = t.records || []
    })
    .catch(() => {
      tableOptions.value = []
    })
})
</script>
