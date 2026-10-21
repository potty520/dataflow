<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>数据质量</h2>
        <div class="sub">六维度规则库、全量/增量稽核、评分权重与稽核结果</div>
      </div>
      <div class="toolbar" style="margin:0">
        <el-tag effect="plain" size="small">规则 {{ stats.ruleCount }}</el-tag>
        <el-tag effect="plain" size="small" type="success">综合分 {{ stats.avgScore }}</el-tag>
        <el-button size="small" type="primary" :icon="Plus" @click="dialog = true">新建规则</el-button>
      </div>
    </div>

    <div class="grid grid-4" style="margin-bottom:12px">
      <div class="stat"><div class="label">综合质量分</div><div class="value">{{ scores.overallScore ?? '-' }}<small>{{ scores.level }}</small></div><div class="foot">按维度权重加权汇总</div></div>
      <div class="stat green"><div class="label">启用规则</div><div class="value">{{ stats.enabledRule }}</div><div class="foot">覆盖 6 个质量维度</div></div>
      <div class="stat orange"><div class="label">待处理问题</div><div class="value">{{ stats.issueOpen }}</div><div class="foot">已关闭 {{ stats.issueClosed }} 张</div></div>
      <div class="stat red"><div class="label">规则字段覆盖率</div><div class="value">{{ coverage.fieldCoverage ?? 0 }}<small>%</small></div><div class="foot">目标 ≥ {{ coverage.target }}% · 表覆盖 {{ coverage.tableCoverage }}%</div></div>
    </div>

    <div class="grid grid-2" style="margin-bottom:12px">
      <div class="card">
        <div class="card-head"><h3>维度通过率</h3><span class="hint">基于最近一次稽核结果</span></div>
        <ChartBox :option="dimOption" height="240px" />
      </div>
      <div class="card">
        <div class="card-head"><h3>评分维度权重</h3><span class="hint">权重和须为 1.0</span></div>
        <div v-for="(w, k) in weights" :key="k" class="kv" style="align-items:center">
          <span class="k">{{ dimCn[k] }}</span>
          <span class="v">
            <el-slider v-model="weights[k]" :min="0" :max="0.5" :step="0.05" size="small" show-input
                       :show-input-controls="false" style="max-width:320px" />
          </span>
        </div>
        <el-button size="small" type="primary" style="margin-top:8px" @click="saveWeights">保存权重并重算</el-button>
      </div>
    </div>

    <div class="card">
      <div class="card-head"><h3>质量规则</h3>
        <div class="toolbar" style="margin:0">
          <el-input v-model="query.keyword" placeholder="规则名 / 表名" size="small" clearable style="width:180px" @keyup.enter="search" @clear="search" />
          <el-select v-model="query.dimension" placeholder="维度" size="small" clearable style="width:120px" @change="search">
            <el-option v-for="(v, k) in dimCn" :key="k" :label="v" :value="k" />
          </el-select>
        </div>
      </div>
      <el-table :data="rules" size="small" v-loading="loading">
        <el-table-column prop="name" label="规则名称" min-width="190" show-overflow-tooltip />
        <el-table-column prop="tableName" label="表" width="140"><template #default="{ row }"><span class="mono">{{ row.tableName }}</span></template></el-table-column>
        <el-table-column prop="columnName" label="字段" width="120" />
        <el-table-column prop="dimension" label="维度" width="90"><template #default="{ row }">{{ dimCn[row.dimension] || row.dimension }}</template></el-table-column>
        <el-table-column prop="ruleType" label="校验方式" width="110" />
        <el-table-column prop="severity" label="级别" width="86">
          <template #default="{ row }"><el-tag size="small" :type="row.severity === 'CRITICAL' ? 'danger' : row.severity === 'MAJOR' ? 'warning' : 'info'" effect="plain">{{ row.severity }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="lastTotal" label="总行数" width="90" align="right" />
        <el-table-column prop="lastFailed" label="异常行" width="86" align="right">
          <template #default="{ row }"><span :style="{ color: row.lastFailed > 0 ? '#dc2626' : '#16a34a' }">{{ row.lastFailed ?? '-' }}</span></template>
        </el-table-column>
        <el-table-column prop="lastPassRate" label="通过率" width="90" align="right"><template #default="{ row }">{{ row.lastPassRate ?? '-' }}%</template></el-table-column>
        <el-table-column prop="cycle" label="周期" width="80" />
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }"><el-button link type="primary" size="small" :loading="trying === row.id" @click="tryRun(row)">试运行</el-button></template>
        </el-table-column>
      </el-table>
      <div style="display:flex;justify-content:flex-end;margin-top:10px">
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total"
                       layout="total, prev, pager, next" @change="loadRules" />
      </div>
    </div>

    <div class="card" style="margin-top:12px">
      <div class="card-head"><h3>稽核任务</h3><span class="hint">全量稽核在源数据层真实执行规则 SQL</span></div>
      <el-table :data="checks" size="small">
        <el-table-column prop="name" label="任务名称" min-width="190" />
        <el-table-column prop="description" label="说明" min-width="240" show-overflow-tooltip />
        <el-table-column prop="ruleCount" label="规则数" width="80" align="right" />
        <el-table-column prop="scheduleType" label="调度" width="90" />
        <el-table-column prop="lastScore" label="最近得分" width="96" align="right" />
        <el-table-column prop="lastRunTime" label="最近执行" width="150"><template #default="{ row }">{{ fmt(row.lastRunTime) }}</template></el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" :loading="runningCheck === row.id" @click="runCheck(row)">执行稽核</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="lastCheck" class="section-title">最近一次稽核结果</div>
      <div v-if="lastCheck" class="grid grid-4" style="margin-bottom:8px">
        <div class="stat" style="padding:10px"><div class="label">得分</div><div class="value" style="font-size:20px">{{ lastCheck.score }}</div></div>
        <div class="stat" style="padding:10px"><div class="label">执行规则</div><div class="value" style="font-size:20px">{{ lastCheck.ruleTotal }}</div></div>
        <div class="stat" style="padding:10px"><div class="label">通过规则</div><div class="value" style="font-size:20px">{{ lastCheck.rulePassed }}</div></div>
        <div class="stat" style="padding:10px"><div class="label">耗时(ms)</div><div class="value" style="font-size:20px">{{ lastCheck.durationMs }}</div></div>
      </div>
      <el-table v-if="lastCheck" :data="lastCheck.results || []" size="small" max-height="300">
        <el-table-column prop="ruleName" label="规则" min-width="200" />
        <el-table-column prop="tableName" label="表" width="140" />
        <el-table-column prop="dimension" label="维度" width="90"><template #default="{ row }">{{ dimCn[row.dimension] }}</template></el-table-column>
        <el-table-column prop="total" label="总行数" width="90" align="right" />
        <el-table-column prop="failed" label="异常" width="80" align="right" />
        <el-table-column prop="passRate" label="通过率" width="90" align="right" />
        <el-table-column prop="score" label="得分" width="80" align="right" />
        <el-table-column prop="status" label="结果" width="86"><template #default="{ row }"><el-tag size="small" :type="row.status === 'PASSED' ? 'success' : 'danger'" effect="plain">{{ row.status }}</el-tag></template></el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog" title="新建质量规则" width="640px">
      <el-form :model="form" label-width="96px" size="small">
        <el-form-item label="规则名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="目标表">
          <el-select v-model="form.tableName" filterable style="width:100%" @change="onTableChange">
            <el-option v-for="t in tables" :key="t.name" :label="`${t.name} · ${t.cnName || ''}`" :value="t.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="字段">
          <el-select v-model="form.columnName" clearable filterable style="width:100%">
            <el-option v-for="c in columns" :key="c.name" :label="`${c.name} · ${c.cnName || ''}`" :value="c.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="维度">
          <el-select v-model="form.dimension" style="width:100%">
            <el-option v-for="(v, k) in dimCn" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="校验方式">
          <el-select v-model="form.ruleType" style="width:100%">
            <el-option v-for="t in ['NOT_NULL','UNIQUE','RANGE','REGEX','ENUM','TIMELINESS','CONSISTENCY','CUSTOM_SQL']" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="规则 SQL">
          <el-input v-model="form.expression" type="textarea" :rows="4" class="mono"
                    placeholder="SELECT COUNT(*) FROM 目标表 WHERE 异常条件" />
          <div class="muted" style="font-size:11.5px;margin-top:4px">返回异常行数的 SELECT；可用 ${now-minus-minutes:60} 表示相对时间</div>
        </el-form-item>
        <el-form-item label="容差/级别">
          <el-input-number v-model="form.tolerance" :min="0.01" :max="1" :step="0.05" size="small" />
          <el-select v-model="form.severity" size="small" style="width:130px;margin-left:10px">
            <el-option label="CRITICAL" value="CRITICAL" /><el-option label="MAJOR" value="MAJOR" /><el-option label="MINOR" value="MINOR" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="dialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="createRule">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import ChartBox from '@/components/ChartBox.vue'
import { qualityApi, metadataApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

const store = useAuthStore()

const rules = ref([])
const checks = ref([])
const stats = ref({})
const scores = ref({})
const coverage = ref({})
const weights = ref({})
const total = ref(0)
const loading = ref(false)
const trying = ref(null)
const runningCheck = ref(null)
const lastCheck = ref(null)
const dialog = ref(false)
const tables = ref([])
const columns = ref([])
const query = reactive({ keyword: '', dimension: '', pageNum: 1, pageSize: 10 })
const form = reactive({ name: '', tableName: '', columnName: '', dimension: 'COMPLETENESS', ruleType: 'NOT_NULL', expression: '', tolerance: 0.1, severity: 'MAJOR' })

const dimCn = { COMPLETENESS: '完整性', UNIQUENESS: '唯一性', ACCURACY: '准确性', VALIDITY: '有效性', CONSISTENCY: '一致性', TIMELINESS: '时效性' }
const fmt = (v) => (v ? String(v).replace('T', ' ').slice(0, 16) : '-')

const dimOption = computed(() => {
  // 首屏尚未拿到数据时给占位维度，避免 ECharts radar 因 indicator 为空报错
  const rows = (scores.value.dimensions && scores.value.dimensions.length)
    ? scores.value.dimensions
    : Object.keys(dimCn).map((k) => ({ dimension: k, passRate: 0 }))
  return {
    grid: { left: 30, right: 20, top: 20, bottom: 30 },
    tooltip: {},
    radar: {
      indicator: rows.map((r) => ({ name: dimCn[r.dimension] || r.dimension, max: 100 })),
      radius: '66%',
      splitLine: { lineStyle: { color: '#e5e7eb' } },
      axisName: { color: '#6b7280', fontSize: 10 },
      splitArea: { areaStyle: { color: ['#fbfdff', '#f5f8ff'] } }
    },
    series: [{
      type: 'radar',
      data: [{ value: rows.map((r) => r.passRate), name: '通过率', areaStyle: { color: 'rgba(37,99,235,.18)' }, lineStyle: { color: '#2563eb' }, itemStyle: { color: '#2563eb' } }]
    }]
  }
})

const loadRules = async () => {
  loading.value = true
  try {
    const d = await qualityApi.rules({ ...query })
    rules.value = d.records || []
    total.value = d.total || 0
  } catch (e) {
    rules.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 筛选条件变化统一回到第一页。 */
const search = () => {
  query.pageNum = 1
  loadRules()
}

const load = async () => {
  // KPI / 评分 / 覆盖率 / 稽核任务互不依赖；无权限的直接不发请求，避免 403 噪声
  const [s, c, cov, ch] = await Promise.allSettled([
    store.has('quality:overview:view') ? qualityApi.stats() : Promise.resolve(null),
    store.has('quality:score:view') ? qualityApi.scores() : Promise.resolve(null),
    store.has('quality:score:view') ? qualityApi.coverage() : Promise.resolve(null),
    store.has('quality:check:view') ? qualityApi.checks() : Promise.resolve(null)
  ])
  stats.value = s.status === 'fulfilled' ? s.value || {} : {}
  scores.value = c.status === 'fulfilled' ? c.value || {} : {}
  coverage.value = cov.status === 'fulfilled' ? cov.value || {} : {}
  weights.value = { ...(scores.value.weights || {}) }
  checks.value = ch.status === 'fulfilled' ? (ch.value?.records || []) : []
}

const tryRun = async (row) => {
  trying.value = row.id
  try {
    const r = await qualityApi.tryRun(row.id)
    ElMessage[r.status === 'PASSED' ? 'success' : 'warning'](`${r.status}：${r.failed}/${r.total} 异常，通过率 ${r.passRate}%，得分 ${r.score}`)
    loadRules(); load()
  } catch (e) {
    /* http 层已提示 */
  } finally {
    trying.value = null
  }
}

const runCheck = async (row) => {
  runningCheck.value = row.id
  try {
    lastCheck.value = await qualityApi.runCheck(row.id)
    ElMessage.success(`稽核完成：得分 ${lastCheck.value.score}（${lastCheck.value.scoreLevel}）`)
    load()
  } catch (e) {
    /* http 层已提示 */
  } finally {
    runningCheck.value = null
  }
}

const saveWeights = async () => {
  const sum = Object.values(weights.value).reduce((a, b) => a + Number(b), 0)
  if (Math.abs(sum - 1) > 0.001) return ElMessage.warning(`权重之和必须为 1.0，当前 ${sum.toFixed(2)}`)
  try {
    await qualityApi.saveWeights({ ...weights.value })
    ElMessage.success('权重已保存')
    load()
  } catch (e) {
    /* http 层已提示 */
  }
}

const onTableChange = async (name) => {
  const t = tables.value.find((x) => x.name === name)
  form.columnName = ''
  if (t) {
    try {
      const d = await metadataApi.table(t.id)
      columns.value = d.columns || []
    } catch (e) {
      columns.value = []
    }
  }
}

const createRule = async () => {
  if (!form.name || !form.tableName || !form.expression) return ElMessage.warning('名称、目标表与规则 SQL 必填')
  try {
    await qualityApi.createRule({ ...form })
    ElMessage.success('规则已创建并启用')
    dialog.value = false
    loadRules(); load()
  } catch (e) {
    /* http 层已提示 */
  }
}

onMounted(() => {
  loadRules()
  load()
  // 建规则弹窗的目标表下拉，属于旁路数据
  metadataApi
    .tables({ pageSize: 200 })
    .then((d) => {
      tables.value = d.records || []
    })
    .catch(() => {
      tables.value = []
    })
})
</script>
