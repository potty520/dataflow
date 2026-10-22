<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>流式质检</h2>
        <div class="sub">水位窗口 + 微批校验：贴源层准实时质检，超容差自动告警（每 5 秒自动执行一个窗口）</div>
      </div>
      <div class="toolbar" style="margin:0">
        <el-tag effect="plain" size="small">流源 {{ overview.sourceCount || 0 }}</el-tag>
        <el-tag effect="plain" size="small" type="success">规则 {{ overview.ruleCount || 0 }}</el-tag>
        <el-button size="small" :icon="Refresh" @click="loadAll(true)">刷新</el-button>
        <el-button v-if="can('quality:stream:exec')" size="small" type="warning" @click="runAll">执行全部窗口</el-button>
        <el-button v-if="can('quality:stream:create')" size="small" type="primary" :icon="Plus" @click="openSource()">新建流源</el-button>
      </div>
    </div>

    <div class="grid grid-4" style="margin-bottom:12px">
      <div class="stat"><div class="label">启用流源</div><div class="value">{{ overview.enabledSource || 0 }}<small>/ {{ overview.sourceCount || 0 }}</small></div><div class="foot">水位窗口自动推进</div></div>
      <div class="stat green"><div class="label">近 24h 平均通过率</div><div class="value">{{ overview.avgPassRate ?? 100 }}<small>%</small></div><div class="foot">窗口 {{ overview.windowCount24h || 0 }} 个</div></div>
      <div class="stat orange"><div class="label">失败窗口（24h）</div><div class="value">{{ overview.failWindow24h || 0 }}</div><div class="foot">超容差窗口数</div></div>
      <div class="stat red"><div class="label">未处置告警</div><div class="value">{{ overview.openAlerts || 0 }}</div><div class="foot">最近执行 {{ fmt(overview.lastRunTime) }}</div></div>
    </div>

    <div class="grid grid-2" style="margin-bottom:12px">
      <div class="card">
        <div class="card-head"><h3>24h 窗口通过率趋势</h3><span class="hint">按小时聚合</span></div>
        <ChartBox :option="trendOption" height="220px" />
      </div>
      <div class="card">
        <div class="card-head"><h3>按维度通过率</h3><span class="hint">近 24h</span></div>
        <ChartBox :option="dimOption" height="220px" />
      </div>
    </div>

    <div class="card" style="margin-bottom:12px">
      <div class="card-head"><h3>流源与健康度</h3>
        <span class="hint">窗口长度到点自动执行；也可手工立即执行</span>
      </div>
      <el-table :data="sources" size="small" empty-text="暂无流源">
        <el-table-column prop="code" label="编码" width="170" />
        <el-table-column prop="name" label="名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="tableName" label="源表" width="160" />
        <el-table-column prop="watermarkCol" label="水位字段" width="130" />
        <el-table-column label="窗口" width="90"><template #default="{ row }">{{ row.windowSeconds }}s</template></el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><el-tag size="small" :type="row.status === 'ENABLED' ? 'success' : 'info'">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="lastWatermark" label="当前水位" width="170" show-overflow-tooltip />
        <el-table-column label="近1h通过率" width="120">
          <template #default="{ row }"><span :style="{ color: (row.recentPassRate ?? 100) < 99 ? '#e6a23c' : '#67c23a' }">{{ row.recentPassRate ?? 100 }}%</span></template>
        </el-table-column>
        <el-table-column prop="recentFailRows" label="失败行(1h)" width="110" />
        <el-table-column label="告警" width="80"><template #default="{ row }">{{ row.openAlerts }}</template></el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-if="can('quality:stream:exec')" link type="primary" size="small" @click="runSource(row)">执行</el-button>
            <el-button v-if="can('quality:stream:exec')" link type="warning" size="small" @click="resetWatermark(row)">重置水位</el-button>
            <el-button v-if="can('quality:stream:edit')" link type="primary" size="small" @click="openPush(row)">推送接入</el-button>
            <el-button v-if="can('quality:stream:create')" link type="primary" size="small" @click="openRule(row)">加规则</el-button>
            <el-button v-if="can('quality:stream:edit')" link type="danger" size="small" @click="delSource(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="grid grid-2" style="margin-bottom:12px">
      <div class="card">
        <div class="card-head"><h3>质量规则（不合格条件）</h3>
          <el-select v-model="ruleFilter" placeholder="全部流源" size="small" clearable style="width:160px" @change="loadRules">
            <el-option v-for="s in sources" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </div>
        <el-table :data="rules" size="small" empty-text="暂无规则">
          <el-table-column prop="name" label="规则" min-width="150" show-overflow-tooltip />
          <el-table-column prop="dimension" label="维度" width="110" />
          <el-table-column prop="expression" label="不合格条件" min-width="180" show-overflow-tooltip />
          <el-table-column label="容差" width="80"><template #default="{ row }">{{ (row.tolerance * 100).toFixed(1) }}%</template></el-table-column>
          <el-table-column prop="severity" label="级别" width="90" />
          <el-table-column label="最近通过率" width="110">
            <template #default="{ row }"><span :style="{ color: (row.lastPassRate ?? 100) < 99 ? '#f56c6c' : '#67c23a' }">{{ row.lastPassRate ?? '-' }}%</span></template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button v-if="can('quality:stream:edit')" link type="danger" size="small" @click="delRule(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div class="card">
        <div class="card-head"><h3>实时告警</h3><span class="hint">超容差自动生成，可处置</span></div>
        <el-table :data="alerts" size="small" empty-text="暂无告警">
          <el-table-column prop="title" label="告警" min-width="180" show-overflow-tooltip />
          <el-table-column prop="severity" label="级别" width="90" />
          <el-table-column label="通过率" width="90"><template #default="{ row }">{{ row.passRate }}%</template></el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }"><el-tag size="small" :type="row.status === 'OPEN' ? 'danger' : 'info'">{{ row.status }}</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button v-if="can('quality:stream:handle') && row.status === 'OPEN'" link type="primary" size="small" @click="handle(row)">处置</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <div class="card">
      <div class="card-head"><h3>最近窗口结果</h3>
        <div class="toolbar" style="margin:0">
          <el-select v-model="resultStatus" placeholder="全部状态" size="small" clearable style="width:110px" @change="loadResults">
            <el-option label="通过" value="PASS" />
            <el-option label="失败" value="FAIL" />
          </el-select>
          <el-button size="small" @click="loadResults">查询</el-button>
        </div>
      </div>
      <el-table :data="results.records" size="small" empty-text="暂无结果">
        <el-table-column label="时间" width="170"><template #default="{ row }">{{ fmt(row.runTime) }}</template></el-table-column>
        <el-table-column prop="sourceName" label="流源" width="160" show-overflow-tooltip />
        <el-table-column prop="ruleName" label="规则" min-width="160" show-overflow-tooltip />
        <el-table-column prop="rowCount" label="窗口行数" width="100" />
        <el-table-column prop="failCount" label="失败行" width="90" />
        <el-table-column label="通过率" width="100"><template #default="{ row }">{{ row.passRate }}%</template></el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><el-tag size="small" :type="row.status === 'PASS' ? 'success' : 'danger'">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="durationMs" label="耗时ms" width="90" />
      </el-table>
    </div>

    <!-- 新建流源 -->
    <el-dialog v-model="sourceDialog" title="新建流式质检源" width="560px">
      <el-form :model="sourceForm" label-width="110px" size="small">
        <el-form-item label="编码"><el-input v-model="sourceForm.code" placeholder="SS-DEVICE-METRIC" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="sourceForm.name" /></el-form-item>
        <el-form-item label="源表">
          <el-select v-model="sourceForm.tableName" filterable placeholder="选择表" style="width:100%" @change="loadColumns(sourceForm.tableName, 'source')">
            <el-option v-for="t in tableOptions" :key="t.name" :label="t.name + ' · ' + (t.cnName || '')" :value="t.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="水位字段">
          <el-select v-model="sourceForm.watermarkCol" filterable placeholder="用于增量取数的时间/序号字段" style="width:100%">
            <el-option v-for="c in sourceColumns" :key="c.name" :label="c.name + ' · ' + c.type" :value="c.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="水位类型">
          <el-radio-group v-model="sourceForm.watermarkType">
            <el-radio label="TIMESTAMP">时间戳</el-radio><el-radio label="NUMBER">数值/序号</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="窗口长度"><el-input-number v-model="sourceForm.windowSeconds" :min="5" :max="3600" /> <span class="hint" style="margin-left:8px">秒</span></el-form-item>
      </el-form>
      <template #footer><el-button size="small" @click="sourceDialog = false">取消</el-button><el-button size="small" type="primary" @click="createSource">创建</el-button></template>
    </el-dialog>

    <!-- 新建规则 -->
    <el-dialog v-model="ruleDialog" title="新建流式质检规则" width="620px">
      <el-form :model="ruleForm" label-width="110px" size="small">
        <el-form-item label="所属流源"><el-input :model-value="ruleForm.sourceName" disabled /></el-form-item>
        <el-form-item label="编码"><el-input v-model="ruleForm.code" placeholder="SR-METRIC-RANGE" /></el-form-item>
        <el-form-item label="规则名"><el-input v-model="ruleForm.name" /></el-form-item>
        <el-form-item label="维度">
          <el-select v-model="ruleForm.dimension" style="width:100%">
            <el-option v-for="(v, k) in dims" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="不合格条件">
          <el-input v-model="ruleForm.expression" type="textarea" :rows="3" placeholder="布尔谓词，例如：metric_value < -100 OR metric_value > 200" />
        </el-form-item>
        <el-form-item label="容差">
          <el-input-number v-model="ruleForm.tolerance" :min="0" :max="1" :step="0.01" :precision="2" />
          <span class="hint" style="margin-left:8px">窗口内失败行占比超过该值即告警</span>
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="ruleForm.severity" style="width:140px">
            <el-option label="CRITICAL" value="CRITICAL" /><el-option label="MAJOR" value="MAJOR" /><el-option label="MINOR" value="MINOR" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer><el-button size="small" @click="ruleDialog = false">取消</el-button><el-button size="small" type="primary" @click="createRule">创建</el-button></template>
    </el-dialog>

    <!-- 推送接入（Kafka/HTTP 生产者事件级实时校验） -->
    <el-dialog v-model="pushDialog" title="流式推送接入配置" width="680px">
      <el-form label-width="110px" size="small">
        <el-form-item label="流源"><el-input :model-value="pushForm.sourceName" disabled /></el-form-item>
        <el-form-item label="字段映射">
          <el-input v-model="pushForm.fieldMapping" type="textarea" :rows="5"
                    placeholder='JSON：表字段 -> 报文键名，例如 {"device_id":"deviceId","metric_value":"value"}' />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="pushForm.enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="接口地址">
          <el-input :model-value="'POST /api/v1/quality/stream/push/' + pushForm.code" readonly />
        </el-form-item>
        <el-alert type="info" :closable="false" show-icon
                  title="Kafka 消费者/外部系统只需把消息体按映射 POST 到该地址即可实现事件级实时质检；流源需配置主键字段（pk_col）。" />
      </el-form>
      <template #footer>
        <el-button size="small" @click="pushDialog = false">关闭</el-button>
        <el-button size="small" :disabled="!can('quality:stream:exec')" @click="simulatePush">模拟推送一条</el-button>
        <el-button size="small" type="primary" :disabled="!can('quality:stream:edit')" @click="savePush">保存配置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { streamApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import ChartBox from '@/components/ChartBox.vue'

const store = useAuthStore()
const can = (p) => store.has(p)

const overview = ref({})
const sources = ref([])
const rules = ref([])
const alerts = ref([])
const results = ref({ records: [], total: 0 })
const resultStatus = ref('')
const ruleFilter = ref(null)
const tableOptions = ref([])
const sourceColumns = ref([])

const dims = { COMPLETENESS: '完整性', UNIQUENESS: '唯一性', ACCURACY: '准确性', VALIDITY: '有效性', CONSISTENCY: '一致性', TIMELINESS: '时效性' }

const trendOption = computed(() => ({
  grid: { left: 40, right: 16, top: 16, bottom: 24 },
  tooltip: { trigger: 'axis' },
  xAxis: { type: 'category', data: (overview.value.trend || []).map((x) => x.hour), axisLabel: { color: '#6b7280', fontSize: 10 } },
  yAxis: { type: 'value', min: 0, max: 100, axisLabel: { color: '#6b7280', fontSize: 10, formatter: '{value}%' } },
  series: [{ type: 'line', smooth: true, data: (overview.value.trend || []).map((x) => x.passRate), areaStyle: { opacity: 0.15 }, itemStyle: { color: '#409eff' } }]
}))
const dimOption = computed(() => ({
  grid: { left: 60, right: 16, top: 16, bottom: 24 },
  tooltip: { trigger: 'axis' },
  xAxis: { type: 'category', data: (overview.value.byDimension || []).map((x) => dims[x.dimension] || x.dimension), axisLabel: { color: '#6b7280', fontSize: 10 } },
  yAxis: { type: 'value', min: 0, max: 100, axisLabel: { color: '#6b7280', fontSize: 10, formatter: '{value}%' } },
  series: [{ type: 'bar', barWidth: 22, data: (overview.value.byDimension || []).map((x) => x.passRate), itemStyle: { color: '#67c23a', borderRadius: [4, 4, 0, 0] } }]
}))

const fmt = (v) => (v ? String(v).replace('T', ' ').slice(0, 19) : '-')

async function loadAll(manual) {
  try {
    const [ov, hl, al, rs] = await Promise.all([
      streamApi.overview(), streamApi.health(), streamApi.alerts({ pageSize: 10 }),
      streamApi.results({ pageSize: 10, status: resultStatus.value || undefined })
    ])
    overview.value = ov || {}
    sources.value = (hl && hl.sources) || []
    alerts.value = (al && al.records) || []
    results.value = rs || { records: [], total: 0 }
    await loadRules()
    if (manual) ElMessage.success('已刷新')
  } catch (e) {
    /* 错误提示由 http 层处理 */
  }
}
async function loadRules() {
  const r = await streamApi.rules({ pageSize: 50, sourceId: ruleFilter.value || undefined })
  rules.value = (r && r.records) || []
}
async function loadResults() { results.value = (await streamApi.results({ pageSize: 10, status: resultStatus.value || undefined })) || { records: [] } }

async function runAll() {
  const r = await streamApi.runAll()
  ElMessage.success(`已执行 ${r.total} 个流源，失败源 ${r.failedSources}`)
  loadAll()
}
async function resetWatermark(row) {
  await streamApi.resetWatermark(row.id)
  ElMessage.success('水位已重置，下次执行将按窗口长度回溯取数')
  loadAll()
}
async function runSource(row) {
  try {
    const r = await streamApi.runSource(row.id)
    ElMessage[r.failedRules ? 'warning' : 'success'](`窗口 ${r.windowFrom} ~ ${r.windowTo}：规则 ${r.ruleCount} 条，失败 ${r.failedRules || 0}`)
  } catch (e) {
    ElMessage.error('执行失败：' + (e.message || ''))
  }
  loadAll()
}
async function delSource(row) {
  await ElMessageBox.confirm(`确认删除流源「${row.name}」及其规则与结果？`, '提示', { type: 'warning' })
  await streamApi.deleteSource(row.id)
  ElMessage.success('已删除')
  loadAll()
}
async function delRule(row) {
  await ElMessageBox.confirm(`确认删除规则「${row.name}」？`, '提示', { type: 'warning' })
  await streamApi.deleteRule(row.id)
  ElMessage.success('已删除')
  loadAll()
}
async function handle(row) {
  await streamApi.handleAlert(row.id)
  ElMessage.success('已处置')
  loadAll()
}

const sourceDialog = ref(false)
const sourceForm = reactive({ code: '', name: '', tableName: '', watermarkCol: '', watermarkType: 'TIMESTAMP', windowSeconds: 60 })
async function openSource() {
  if (!tableOptions.value.length) tableOptions.value = ((await streamApi.tables()) || {}).records || []
  Object.assign(sourceForm, { code: '', name: '', tableName: '', watermarkCol: '', watermarkType: 'TIMESTAMP', windowSeconds: 60 })
  sourceColumns.value = []
  sourceDialog.value = true
}
async function loadColumns(table, target) {
  const r = await streamApi.columns(table)
  sourceColumns.value = (r && r.records) || []
}
async function createSource() {
  try {
    await streamApi.createSource({ ...sourceForm })
    ElMessage.success('已创建流源')
    sourceDialog.value = false
    loadAll()
  } catch (e) { ElMessage.error(e.message || '创建失败') }
}

const pushDialog = ref(false)
const pushForm = reactive({ sourceId: null, sourceName: '', code: '', fieldMapping: '', enabled: true })
async function openPush(row) {
  Object.assign(pushForm, { sourceId: row.id, sourceName: row.name, code: row.code, fieldMapping: '', enabled: true })
  try {
    const cfg = await streamApi.pushConfig(row.id)
    if (cfg) { pushForm.fieldMapping = cfg.fieldMapping || ''; pushForm.enabled = cfg.status === 'ENABLED' }
  } catch (e) { /* 未配置时不报错 */ }
  pushDialog.value = true
}
async function savePush() {
  try {
    await streamApi.savePushConfig(pushForm.sourceId, { fieldMapping: pushForm.fieldMapping, status: pushForm.enabled ? 'ENABLED' : 'DISABLED' })
    ElMessage.success('推送配置已保存')
    pushDialog.value = false
    loadAll()
  } catch (e) { ElMessage.error(e.message || '保存失败') }
}
async function simulatePush() {
  try {
    const now = new Date()
    const pad = (n) => String(n).padStart(2, '0')
    const ts = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
    const r = await streamApi.pushEvent(pushForm.code, {
      id: 900000 + Math.floor(Math.random() * 90000),
      deviceId: 'DEV-PUSH-01', deviceName: '推送测试设备', workshop: '推送车间',
      metricName: 'push_metric', value: 999.9, unit: 'unit', collectTime: ts
    })
    ElMessage[`${r.failedRules ? 'warning' : 'success'}`](`已推送：规则 ${r.ruleCount} 条，失败 ${r.failedRules || 0}`)
    loadAll()
  } catch (e) { ElMessage.error(e.message || '推送失败') }
}

const ruleDialog = ref(false)
const ruleForm = reactive({ sourceId: null, sourceName: '', code: '', name: '', dimension: 'VALIDITY', expression: '', tolerance: 0.01, severity: 'MAJOR' })
async function openRule(row) {
  Object.assign(ruleForm, { sourceId: row.id, sourceName: row.name, code: '', name: '', dimension: 'VALIDITY', expression: '', tolerance: 0.01, severity: 'MAJOR' })
  ruleDialog.value = true
}
async function createRule() {
  try {
    await streamApi.createRule({ ...ruleForm, sourceId: ruleForm.sourceId })
    ElMessage.success('已创建规则')
    ruleDialog.value = false
    loadAll()
  } catch (e) { ElMessage.error(e.message || '创建失败') }
}

let timer = null
onMounted(async () => {
  await loadAll()
  timer = setInterval(() => loadAll(), 5000)
})
onUnmounted(() => { if (timer) clearInterval(timer) })
</script>
