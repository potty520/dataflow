<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>数据安全</h2>
        <div class="sub">敏感数据识别（字段名/内容/注释）、动态脱敏策略与访问审批</div>
      </div>
      <div class="toolbar" style="margin:0">
        <el-button size="small" :icon="Search" :loading="scanning" @click="scan">全域敏感扫描</el-button>
      </div>
    </div>

    <div class="grid grid-4" style="margin-bottom:12px">
      <div class="stat red"><div class="label">敏感字段</div><div class="value">{{ stats.sensitiveColumns }}</div><div class="foot">L4 最高敏感级 {{ stats.l4Columns }} 个</div></div>
      <div class="stat orange"><div class="label">脱敏策略</div><div class="value">{{ stats.maskPolicies }}</div><div class="foot">待审批 {{ stats.pendingPolicies }} 条</div></div>
      <div class="stat"><div class="label">审计日志</div><div class="value">{{ stats.auditLogs }}</div><div class="foot">保留 190 天（≥6 个月）</div></div>
      <div class="stat"><div class="label">未处置告警</div><div class="value">{{ stats.openAlerts }}</div><div class="foot">异常访问与脱敏变更</div></div>
    </div>

    <el-tabs v-model="tab" type="border-card" style="border-radius:8px">
      <el-tab-pane label="敏感识别" name="scan">
        <div class="grid grid-2">
          <div class="card" style="margin:0">
            <div class="card-head"><h3>识别规则</h3><span class="hint">字段名 / 内容正则 / 注释关键词</span></div>
            <el-table :data="rules" size="small" max-height="330">
              <el-table-column prop="name" label="规则" min-width="150" />
              <el-table-column prop="matchType" label="方式" width="90" />
              <el-table-column prop="pattern" label="模式" min-width="180" show-overflow-tooltip><template #default="{ row }"><span class="mono">{{ row.pattern }}</span></template></el-table-column>
              <el-table-column prop="securityLevel" label="级别" width="70" />
              <el-table-column prop="hitColumns" label="命中" width="70" align="right" />
            </el-table>
          </div>
          <div class="card" style="margin:0">
            <div class="card-head"><h3>敏感字段清单</h3><span class="hint">共 {{ results.length }} 个</span></div>
            <el-table :data="results" size="small" max-height="330">
              <el-table-column prop="tableName" label="表" width="140"><template #default="{ row }"><span class="mono">{{ row.tableName }}</span></template></el-table-column>
              <el-table-column prop="columnName" label="字段" width="120" />
              <el-table-column prop="securityLevel" label="级别" width="70">
                <template #default="{ row }"><el-tag size="small" :type="row.securityLevel === 'L4' ? 'danger' : 'warning'" effect="plain">{{ row.securityLevel }}</el-tag></template>
              </el-table-column>
              <el-table-column prop="matchType" label="识别方式" width="90" />
              <el-table-column prop="sampleValue" label="样例（已脱敏）" min-width="150" show-overflow-tooltip />
            </el-table>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="脱敏策略" name="mask">
        <div class="toolbar">
          <el-select v-model="maskTable" placeholder="按表过滤" clearable filterable style="width:220px" @change="loadPolicies">
            <el-option v-for="t in tables" :key="t.name" :label="t.name" :value="t.name" />
          </el-select>
          <span class="spacer"></span>
          <el-button size="small" @click="openPreview">脱敏效果预览</el-button>
        </div>
        <el-table :data="policies" size="small">
          <el-table-column prop="tableName" label="表" width="150"><template #default="{ row }"><span class="mono">{{ row.tableName }}</span></template></el-table-column>
          <el-table-column prop="columnName" label="字段" width="130" />
          <el-table-column prop="algorithm" label="算法" width="120">
            <template #default="{ row }"><el-tag size="small" effect="plain">{{ row.algorithm }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="securityLevel" label="级别" width="70" />
          <el-table-column prop="roleCodes" label="适用角色" min-width="200" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'ENABLED' ? 'success' : row.status === 'PENDING' ? 'warning' : 'info'" effect="plain">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="approver" label="审批人" width="90" />
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 'PENDING'" link type="primary" size="small" @click="approve(row)">审批通过</el-button>
              <el-button v-else link type="warning" size="small" @click="toggle(row)">{{ row.status === 'ENABLED' ? '停用' : '启用' }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="访问审批" name="access">
        <div class="grid grid-2">
          <div class="card" style="margin:0">
            <div class="card-head"><h3>访问申请</h3></div>
            <el-table :data="applies" size="small" max-height="330">
              <el-table-column prop="applicant" label="申请人" width="86" />
              <el-table-column prop="assetName" label="数据对象" min-width="150"><template #default="{ row }"><span class="mono">{{ row.assetName }}</span></template></el-table-column>
              <el-table-column prop="durationDays" label="期限(天)" width="86" align="right" />
              <el-table-column prop="status" label="状态" width="96">
                <template #default="{ row }"><el-tag size="small" :type="row.status === 'APPROVED' ? 'success' : row.status === 'PENDING' ? 'warning' : 'info'" effect="plain">{{ row.status }}</el-tag></template>
              </el-table-column>
              <el-table-column label="操作" width="90">
                <template #default="{ row }">
                  <el-button v-if="row.status === 'PENDING'" link type="primary" size="small" @click="approveApply(row)">审批</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
          <div class="card" style="margin:0">
            <div class="card-head"><h3>已授予权限</h3></div>
            <el-table :data="grants" size="small" max-height="330">
              <el-table-column prop="subject" label="主体" width="86" />
              <el-table-column prop="assetName" label="对象" min-width="150"><template #default="{ row }"><span class="mono">{{ row.assetName }}</span></template></el-table-column>
              <el-table-column prop="scope" label="范围" width="80" />
              <el-table-column prop="expireTime" label="到期" width="140"><template #default="{ row }">{{ fmt(row.expireTime) }}</template></el-table-column>
              <el-table-column prop="status" label="状态" width="90" />
            </el-table>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="previewVisible" title="脱敏效果预览（原文不回传）" width="560px">
      <el-form label-width="80px" size="small">
        <el-form-item label="表">
          <el-select v-model="previewForm.tableName" filterable style="width:100%" @change="loadPreviewColumns">
            <el-option v-for="t in tables" :key="t.name" :label="t.name" :value="t.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="字段">
          <el-select v-model="previewForm.columnName" filterable style="width:100%">
            <el-option v-for="c in previewColumns" :key="c.name" :label="c.name" :value="c.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="算法">
          <el-select v-model="previewForm.algorithm" style="width:100%">
            <el-option v-for="a in ['MASK','REPLACE','TRUNCATE','HASH','ENCRYPT','GENERALIZE']" :key="a" :label="a" :value="a" />
          </el-select>
        </el-form-item>
        <el-button size="small" type="primary" @click="doPreview">预览</el-button>
      </el-form>
      <el-table :data="previewRows" size="small" style="margin-top:10px">
        <el-table-column prop="raw" label="原文样例" min-width="180" />
        <el-table-column prop="masked" label="脱敏结果" min-width="180" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { securityApi, metadataApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

const store = useAuthStore()
/** 只有具备对应权限才发请求，避免非安全管理员页面出现 403 噪声 */
const can = (p) => store.has(p)

const tab = ref('scan')
const stats = ref({})
const rules = ref([])
const results = ref([])
const policies = ref([])
const applies = ref([])
const grants = ref([])
const tables = ref([])
const maskTable = ref('')
const scanning = ref(false)
const previewVisible = ref(false)
const previewColumns = ref([])
const previewRows = ref([])
const previewForm = reactive({ tableName: '', columnName: '', algorithm: 'MASK' })

const fmt = (v) => (v ? String(v).replace('T', ' ').slice(0, 16) : '-')

const load = async () => {
  // 统计/规则/敏感字段/申请/授权互不依赖，单个 403 或 5xx 只影响自己的区域
  const [s, r, res, ap, gr] = await Promise.allSettled([
    can('security:overview:view') ? securityApi.stats() : Promise.resolve(null),
    can('security:sensitive:view') ? securityApi.sensitiveRules() : Promise.resolve(null),
    can('security:sensitive:view') ? securityApi.sensitiveResults({ pageSize: 100 }) : Promise.resolve(null),
    can('security:access:view') ? securityApi.applies({}) : Promise.resolve(null),
    can('security:access:view') ? securityApi.grants() : Promise.resolve(null)
  ])
  stats.value = s.status === 'fulfilled' ? s.value || {} : {}
  rules.value = r.status === 'fulfilled' ? (r.value?.records || []) : []
  results.value = res.status === 'fulfilled' ? (res.value?.records || []) : []
  applies.value = ap.status === 'fulfilled' ? (ap.value?.records || []) : []
  grants.value = gr.status === 'fulfilled' ? (gr.value?.records || []) : []
  loadPolicies()
}

const loadPolicies = async () => {
  try {
    const d = await securityApi.maskPolicies({ tableName: maskTable.value || undefined })
    policies.value = d.records || []
  } catch (e) {
    policies.value = []
  }
}

const scan = async () => {
  scanning.value = true
  try {
    const r = await securityApi.scan({})
    ElMessage.success(`扫描 ${r.scannedColumns} 个字段，识别敏感字段 ${r.sensitiveColumns} 个，耗时 ${r.durationMs} ms`)
    load()
  } catch (e) {
    /* http 层已提示 */
  } finally {
    scanning.value = false
  }
}

const approve = async (row) => {
  const label = `${row.tableName}.${row.columnName}`
  try {
    await ElMessageBox.confirm(`确认审批通过脱敏策略「${label}」（${row.algorithm}）？通过后立即生效。`, '策略审批', {
      confirmButtonText: '通过',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    const r = await securityApi.approvePolicy(row.id, { result: 'APPROVED' })
    if (r && r.status && r.status !== 'ENABLED') ElMessage.error(`策略未生效（当前 ${r.status}）`)
    else ElMessage.success('策略已审批生效')
    load()
  } catch (e) {
    /* http 层已提示 */
  }
}

/** 真实的启停开关：走 enable 接口，并且只有状态确实变化后才提示成功。 */
const toggle = async (row) => {
  const enable = row.status !== 'ENABLED'
  const action = enable ? '启用' : '停用'
  const label = `${row.tableName}.${row.columnName}`
  try {
    await ElMessageBox.confirm(
      `确认${action}脱敏策略「${label}」？${enable ? '启用后该字段按算法脱敏输出。' : '停用后该字段将不再脱敏。'}`,
      '策略启停',
      { confirmButtonText: `确认${action}`, cancelButtonText: '取消', type: 'warning' }
    )
  } catch (e) {
    return
  }
  const expected = enable ? 'ENABLED' : 'DISABLED'
  try {
    const r = await securityApi.enablePolicy(row.id, enable)
    if (r && r.status && r.status !== expected) {
      ElMessage.error(`策略状态未变更（当前 ${r.status}）`)
      loadPolicies()
      return
    }
    await loadPolicies()
    const latest = policies.value.find((p) => String(p.id) === String(row.id))
    if (latest && latest.status !== expected) {
      ElMessage.error(`策略状态未变更（当前 ${latest.status}）`)
      return
    }
    ElMessage.success(`策略已${action}`)
  } catch (e) {
    /* http 层已提示 */
  }
}

const approveApply = async (row) => {
  try {
    await ElMessageBox.confirm(
      `将批准「${row.applicant}」对「${row.assetName}」的访问申请（${row.durationDays} 天），批准后立即生成真实访问授权。`,
      '访问审批',
      { confirmButtonText: '批准并授权', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (e) {
    return
  }
  try {
    await securityApi.approveApply(row.id, { result: 'APPROVED' })
    ElMessage.success('已批准并生成访问授权')
    load()
  } catch (e) {
    /* http 层已提示 */
  }
}

const openPreview = () => {
  previewForm.tableName = tables.value[0]?.name || ''
  previewVisible.value = true
  loadPreviewColumns()
}

const loadPreviewColumns = async () => {
  const t = tables.value.find((x) => x.name === previewForm.tableName)
  if (!t) return
  try {
    const d = await metadataApi.table(t.id)
    previewColumns.value = (d.columns || []).filter((c) => ['phone', 'id_card', 'email', 'addr', 'amount', 'pay_amount'].includes(c.name))
  } catch (e) {
    previewColumns.value = []
  }
  previewForm.columnName = previewColumns.value[0]?.name || ''
}

const doPreview = async () => {
  if (!previewForm.columnName) return ElMessage.warning('请选择字段')
  try {
    const r = await securityApi.previewMask({ ...previewForm })
    previewRows.value = r.samples || []
  } catch (e) {
    previewRows.value = []
  }
}

onMounted(() => {
  load()
  // 表清单用于策略过滤与脱敏预览，属于旁路数据
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
