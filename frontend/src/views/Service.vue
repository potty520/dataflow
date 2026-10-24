<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>数据服务</h2>
        <div class="sub">将治理后的数据发布为标准化 API，含认证、限流、黑白名单、脱敏与调用计量</div>
      </div>
      <el-button size="small" type="primary" :icon="Plus" @click="openCreate">创建 API</el-button>
    </div>

    <div class="grid grid-4" style="margin-bottom:12px">
      <div class="stat"><div class="label">API 总数</div><div class="value">{{ stats.apiCount }}</div><div class="foot">已发布 {{ stats.publishedCount }} · 待审核 {{ stats.pendingCount }}</div></div>
      <div class="stat green"><div class="label">调用总量</div><div class="value">{{ num(metrics.summary?.totalCalls) }}</div><div class="foot">成功 {{ num(metrics.summary?.successCalls) }} 次</div></div>
      <div class="stat orange"><div class="label">成功率</div><div class="value">{{ metrics.summary?.successRate ?? 0 }}<small>%</small></div><div class="foot">平均 {{ metrics.summary?.avgMs ?? '-' }} ms</div></div>
      <div class="stat"><div class="label">接入应用</div><div class="value">{{ stats.appCount }}</div><div class="foot">编排服务 {{ stats.orchestrationCount }} 个</div></div>
    </div>

    <div class="card">
      <div class="card-head"><h3>API 生命周期管理</h3><span class="hint">草稿 → 待审核 → 已发布 → 已下线</span></div>
      <el-table :data="apis" size="small" v-loading="loading">
        <el-table-column prop="code" label="编码" width="160"><template #default="{ row }"><span class="mono">{{ row.code }}</span></template></el-table-column>
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="method" label="方法" width="70"><template #default="{ row }"><el-tag size="small" effect="plain">{{ row.method }}</el-tag></template></el-table-column>
        <el-table-column prop="apiPath" label="开放路径" min-width="170"><template #default="{ row }"><span class="mono">/open/v1{{ row.apiPath }}</span></template></el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }"><el-tag size="small" :type="statusType(row.status)" effect="plain">{{ statusCn(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="rateLimit" label="限流/分" width="90" align="right" />
        <el-table-column prop="callCount" label="调用量" width="90" align="right"><template #default="{ row }">{{ Number(row.callCount || 0).toLocaleString() }}</template></el-table-column>
        <el-table-column prop="avgMs" label="平均耗时" width="96" align="right"><template #default="{ row }">{{ row.avgMs ?? '-' }} ms</template></el-table-column>
        <el-table-column prop="maskEnabled" label="脱敏" width="70" align="center"><template #default="{ row }">{{ row.maskEnabled ? '开' : '关' }}</template></el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openTest(row)">调试</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="warning" size="small" @click="submit(row)">提交审核</el-button>
            <el-button v-if="row.status === 'PENDING'" link type="warning" size="small" @click="audit(row)">审核</el-button>
            <el-button v-if="['APPROVED','DRAFT'].includes(row.status)" link type="success" size="small" @click="publish(row)">发布</el-button>
            <el-button v-if="row.status === 'PUBLISHED'" link type="danger" size="small" @click="offline(row)">下线</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="grid grid-2" style="margin-top:12px">
      <div class="card">
        <div class="card-head"><h3>调用趋势</h3><span class="hint">近 14 天</span></div>
        <ChartBox :option="trendOption" height="240px" />
      </div>
      <div class="card">
        <div class="card-head"><h3>Top API 调用量</h3></div>
        <el-table :data="topApis" size="small" max-height="240">
          <el-table-column prop="name" label="API" min-width="160" />
          <el-table-column prop="callCount" label="调用量" width="100" align="right"><template #default="{ row }">{{ Number(row.callCount || 0).toLocaleString() }}</template></el-table-column>
          <el-table-column prop="avgMs" label="平均耗时" width="100" align="right" />
          <el-table-column prop="category" label="分类" width="110" />
        </el-table>
      </div>
    </div>

    <div class="grid grid-2" style="margin-top:12px">
      <div class="card">
        <div class="card-head"><h3>接入应用与配额</h3>
          <div><el-button size="small" type="primary" @click="openApp()">新建应用</el-button></div>
        </div>
        <el-table :data="apps" size="small" max-height="240">
          <el-table-column prop="appCode" label="应用编码" width="120"><template #default="{ row }"><span class="mono">{{ row.appCode }}</span></template></el-table-column>
          <el-table-column prop="appName" label="应用名称" min-width="150" />
          <el-table-column prop="quotaDay" label="日配额" width="90" align="right" />
          <el-table-column prop="owner" label="负责人" width="110" />
          <el-table-column prop="status" label="状态" width="80">
            <template #default="{ row }"><el-tag size="small" :type="row.status === 'ENABLED' ? 'success' : 'info'">{{ row.status === 'ENABLED' ? '启用' : '停用' }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="usedToday" label="今日调用" width="90" align="right" />
          <el-table-column prop="appSecretMasked" label="密钥" width="130"><template #default="{ row }"><span class="mono">{{ row.appSecretMasked }}</span></template></el-table-column>
          <el-table-column label="操作" width="230" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openApp(row)">编辑</el-button>
              <el-button link type="warning" size="small" @click="resetSecretApp(row)">重置密钥</el-button>
              <el-button link type="warning" size="small" @click="toggleApp(row)">{{ row.status === 'ENABLED' ? '停用' : '启用' }}</el-button>
              <el-button link type="danger" size="small" @click="removeApp(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div class="card">
        <div class="card-head"><h3>服务编排</h3></div>
        <el-table :data="orchestrations" size="small" max-height="240">
          <el-table-column prop="name" label="编排名称" min-width="170" />
          <el-table-column prop="status" label="状态" width="100" />
          <el-table-column prop="callCnt" label="调用" width="80" align="right" />
          <el-table-column label="操作" width="90">
            <template #default="{ row }"><el-button link type="primary" size="small" @click="runOrch(row)">执行</el-button></template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <el-dialog v-model="testVisible" :title="`在线调试 · ${current.name || ''}`" width="760px">
      <el-alert type="info" :closable="false" style="margin-bottom:12px"
                :title="`开放通道：GET /open/v1${current.apiPath} ｜ 认证头：X-App-Code / X-App-Secret ｜ 限流 ${current.rateLimit} 次/分钟`" />
      <el-form label-width="110px" size="small">
        <el-form-item v-for="p in paramDefs" :key="p.name" :label="`${p.name}${p.required ? ' *' : ''}`">
          <el-input v-model="testParams[p.name]" :placeholder="p.desc" />
        </el-form-item>
      </el-form>
      <div class="toolbar">
        <el-button size="small" type="primary" :loading="testing" @click="doTest">执行调用</el-button>
        <span class="muted" style="font-size:12px">调试接口不计量、不计入配额；结果按脱敏策略输出</span>
      </div>
      <pre v-if="testResult" class="result">{{ JSON.stringify(testResult, null, 2) }}</pre>
    </el-dialog>

    <el-dialog v-model="createVisible" title="创建数据服务 API" width="700px">
      <el-form :model="form" label-width="100px" size="small">
        <el-form-item label="编码"><el-input v-model="form.code" placeholder="API-XXX" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="开放路径"><el-input v-model="form.apiPath" placeholder="/customer/profile" /></el-form-item>
        <el-form-item label="方法">
          <el-radio-group v-model="form.method"><el-radio-button value="GET">GET</el-radio-button><el-radio-button value="POST">POST</el-radio-button></el-radio-group>
        </el-form-item>
        <el-form-item label="分类"><el-input v-model="form.category" placeholder="客户服务 / 经营分析" /></el-form-item>
        <el-form-item label="查询 SQL">
          <el-input v-model="form.sqlText" type="textarea" :rows="5" class="mono"
                    placeholder="SELECT ... FROM 表 WHERE id='${id}'  使用 ${参数名} 声明参数" />
        </el-form-item>
        <el-form-item label="参数定义">
          <el-input v-model="form.params" type="textarea" :rows="3" class="mono"
                    placeholder='[{"name":"id","type":"string","required":true,"desc":"主键"}]' />
        </el-form-item>
        <el-form-item label="限流/分"><el-input-number v-model="form.rateLimit" :min="1" :max="10000" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="createVisible = false">取消</el-button>
        <el-button size="small" type="primary" @click="create">保存为草稿</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="appDialog" :title="appForm.id ? '编辑应用' : '新建应用'" width="520px">
      <el-form label-width="100px" size="small">
        <el-form-item label="应用编码"><el-input v-model="appForm.appCode" :disabled="!!appForm.id" placeholder="如 DEMO_APP" /></el-form-item>
        <el-form-item label="应用名称"><el-input v-model="appForm.appName" /></el-form-item>
        <el-form-item label="负责人"><el-input v-model="appForm.owner" /></el-form-item>
        <el-form-item label="日配额"><el-input-number v-model="appForm.quotaDay" :min="0" :max="100000000" /></el-form-item>
        <el-form-item v-if="appForm.id" label="状态">
          <el-radio-group v-model="appForm.status"><el-radio label="ENABLED">启用</el-radio><el-radio label="DISABLED">停用</el-radio></el-radio-group>
        </el-form-item>
      </el-form>
      <el-alert :closable="false" type="warning" show-icon title="AppSecret 仅在创建/重置时显示一次，请立即保存" />
      <template #footer>
        <el-button size="small" @click="appDialog = false">取消</el-button>
        <el-button size="small" type="primary" :loading="savingApp" @click="saveApp">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="secretDialog" title="AppSecret（仅显示一次）" width="560px">
      <el-alert :closable="false" type="error" show-icon title="关闭后无法再次查看，只能重置；请立即复制保存" />
      <el-input v-model="secretValue" readonly class="mono" style="margin-top:12px">
        <template #append><el-button @click="copySecret">复制</el-button></template>
      </el-input>
      <template #footer><el-button size="small" type="primary" @click="secretDialog = false">我已保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ChartBox from '@/components/ChartBox.vue'
import { serviceApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

const store = useAuthStore()
/** 只有具备对应权限才发请求，避免非管理员页面出现 403 噪声 */
const can = (p) => store.has(p)

const apis = ref([])
const apps = ref([])
const orchestrations = ref([])
const topApis = ref([])
const metrics = ref({})
const stats = ref({})
const loading = ref(false)
const testVisible = ref(false)
const testing = ref(false)
const testParams = reactive({})
const paramDefs = ref([])
const testResult = ref(null)
const current = ref({})
const createVisible = ref(false)
const form = reactive({ code: '', name: '', apiPath: '', method: 'GET', category: '', sqlText: '', params: '[]', rateLimit: 100, description: '' })

const statusCn = (s) => ({ DRAFT: '草稿', PENDING: '待审核', APPROVED: '已审核', PUBLISHED: '已发布', OFFLINE: '已下线' }[s] || s)
const statusType = (s) => ({ PUBLISHED: 'success', PENDING: 'warning', APPROVED: 'primary', OFFLINE: 'info', DRAFT: 'info' }[s] || 'info')
const num = (v) => Number(v || 0).toLocaleString()

const trendOption = computed(() => {
  const rows = metrics.value.trend || []
  return {
    grid: { left: 46, right: 40, top: 26, bottom: 26 },
    tooltip: { trigger: 'axis' },
    legend: { top: 0, itemWidth: 8, itemHeight: 8, textStyle: { fontSize: 10 } },
    xAxis: { type: 'category', data: rows.map((r) => String(r.statDate).slice(5)), axisLabel: { fontSize: 10, color: '#6b7280' }, axisLine: { lineStyle: { color: '#e5e7eb' } } },
    yAxis: [
      { type: 'value', splitLine: { lineStyle: { color: '#f1f5f9' } }, axisLabel: { fontSize: 10, color: '#6b7280' } },
      { type: 'value', splitLine: { show: false }, axisLabel: { fontSize: 10, color: '#6b7280' } }
    ],
    series: [
      { name: '调用量', type: 'bar', barWidth: 12, data: rows.map((r) => r.callCnt), itemStyle: { color: '#2563eb', borderRadius: [3, 3, 0, 0] } },
      { name: '失败数', type: 'line', yAxisIndex: 1, symbol: 'none', smooth: true, data: rows.map((r) => r.failCnt), itemStyle: { color: '#dc2626' } }
    ]
  }
})


const appDialog = ref(false)
const secretDialog = ref(false)
const secretValue = ref('')
const savingApp = ref(false)
const appForm = reactive({ id: null, appCode: '', appName: '', owner: '', quotaDay: 10000, status: 'ENABLED' })

const openApp = (row) => {
  Object.assign(appForm, { id: null, appCode: '', appName: '', owner: '', quotaDay: 10000, status: 'ENABLED' })
  if (row) Object.assign(appForm, { id: row.id, appCode: row.appCode, appName: row.appName, owner: row.owner, quotaDay: row.quotaDay || 10000, status: row.status || 'ENABLED' })
  appDialog.value = true
}
const saveApp = async () => {
  savingApp.value = true
  try {
    if (appForm.id) {
      await serviceApi.updateApp(appForm.id, { appName: appForm.appName, owner: appForm.owner, quotaDay: appForm.quotaDay, status: appForm.status })
      ElMessage.success('应用已更新')
    } else {
      const d = await serviceApi.createApp({ appCode: appForm.appCode, appName: appForm.appName, owner: appForm.owner, quotaDay: appForm.quotaDay })
      secretValue.value = d.appSecret
      secretDialog.value = true
      ElMessage.success('应用已创建')
    }
    appDialog.value = false
    load()
  } catch (e) { /* http 层已提示 */ } finally { savingApp.value = false }
}
const resetSecretApp = async (row) => {
  try {
    await ElMessageBox.confirm('重置 ' + row.appCode + ' 的密钥？旧密钥立即失效', '提示', { type: 'warning' })
    const d = await serviceApi.resetAppSecret(row.id)
    secretValue.value = d.appSecret
    secretDialog.value = true
    load()
  } catch (e) { /* 取消 */ }
}
const toggleApp = async (row) => {
  try {
    await serviceApi.updateApp(row.id, { status: row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED' })
    ElMessage.success(row.status === 'ENABLED' ? '已停用' : '已启用')
    load()
  } catch (e) { /* http 层已提示 */ }
}
const removeApp = async (row) => {
  try {
    await ElMessageBox.confirm('删除应用 ' + row.appCode + '？', '提示', { type: 'warning' })
    await serviceApi.deleteApp(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) { /* 取消 */ }
}
const copySecret = async () => {
  try { await navigator.clipboard.writeText(secretValue.value); ElMessage.success('已复制') } catch (e) { ElMessage.warning('浏览器不允许剪贴板，请手动选择复制') }
}

const load = async () => {
  // 1) 主列表先落地，KPI/图表/附属清单全部旁路加载，单个 403/5xx 不阻塞页面
  loading.value = true
  try {
    const d = await serviceApi.list({ pageSize: 50 })
    apis.value = d.records || []
  } catch (e) {
    apis.value = []
  } finally {
    loading.value = false
  }
  const [s, m, t, a, o] = await Promise.allSettled([
    can('service:overview:view') ? serviceApi.stats() : Promise.resolve(null),
    can('service:metrics:view') ? serviceApi.metrics({ days: 14 }) : Promise.resolve(null),
    can('service:metrics:view') ? serviceApi.topn({ limit: 8 }) : Promise.resolve(null),
    can('service:app:view') ? serviceApi.apps() : Promise.resolve(null),
    can('service:orchestration:view') ? serviceApi.orchestrations() : Promise.resolve(null)
  ])
  stats.value = s.status === 'fulfilled' ? s.value || {} : {}
  metrics.value = m.status === 'fulfilled' ? m.value || {} : {}
  topApis.value = t.status === 'fulfilled' ? (t.value?.records || []) : []
  apps.value = a.status === 'fulfilled' ? (a.value?.records || []) : []
  orchestrations.value = o.status === 'fulfilled' ? (o.value?.records || []) : []
}

const openTest = async (row) => {
  current.value = await serviceApi.detail(row.id)
  paramDefs.value = Array.isArray(current.value.paramDefs) ? current.value.paramDefs
    : (current.value.paramDefs?.value || JSON.parse(current.value.params || '[]'))
  Object.keys(testParams).forEach((k) => delete testParams[k])
  for (const p of paramDefs.value) testParams[p.name] = ''
  testResult.value = null
  testVisible.value = true
}

const doTest = async () => {
  testing.value = true
  try {
    testResult.value = await serviceApi.test(current.value.id, { ...testParams })
  } finally {
    testing.value = false
  }
}

const submit = async (row) => { await serviceApi.submit(row.id); ElMessage.success('已提交审核'); load() }

/** 审核必须显式选择通过/驳回，绝不默认通过，也不静默硬编码 APPROVED。 */
const audit = async (row) => {
  let pass = true
  try {
    await ElMessageBox.confirm(
      `将审核数据服务「${row.name}」（${row.code}）。通过后状态为已审核，驳回则退回草稿。`,
      '服务审核',
      { confirmButtonText: '通过', cancelButtonText: '驳回', distinguishCancelAndClose: true, type: 'warning' }
    )
  } catch (action) {
    if (action !== 'cancel') return
    try {
      await ElMessageBox.confirm(`确认驳回数据服务「${row.name}」？`, '驳回确认', {
        confirmButtonText: '确认驳回',
        cancelButtonText: '取消',
        type: 'warning'
      })
    } catch (e) {
      return
    }
    pass = false
  }
  try {
    await serviceApi.audit(row.id, { result: pass ? 'APPROVED' : 'REJECTED', comment: pass ? '审核通过' : '审核驳回' })
    ElMessage.success(pass ? '审核通过' : '已驳回')
    load()
  } catch (e) {
    /* http 层已提示 */
  }
}

const publish = async (row) => { const r = await serviceApi.publish(row.id); ElMessage.success(`已发布：${r.openPath}`); load() }
const offline = async (row) => {
  try {
    await ElMessageBox.confirm(`确认下线数据服务「${row.name}」（${row.code}）？下线后开放通道将返回 26101。`, '下线确认', {
      confirmButtonText: '确认下线',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    await serviceApi.offline(row.id)
    ElMessage.warning('已下线')
    load()
  } catch (e) {
    /* http 层已提示 */
  }
}

const runOrch = async (row) => {
  const r = await serviceApi.runOrchestration(row.id)
  ElMessage.success(`编排执行完成：${r.stepCount} 步，耗时 ${r.durationMs} ms`)
}

const openCreate = () => { createVisible.value = true }

const create = async () => {
  if (!form.code || !form.name || !form.apiPath || !form.sqlText) return ElMessage.warning('编码、名称、路径与 SQL 必填')
  await serviceApi.create({ ...form })
  ElMessage.success('已创建（DRAFT）')
  createVisible.value = false
  load()
}

onMounted(load)
</script>

<style scoped>
.result { background: #0f172a; color: #a5f3fc; border-radius: 6px; padding: 12px; max-height: 320px; overflow: auto; font-size: 11.5px; font-family: Consolas, monospace; }
</style>
