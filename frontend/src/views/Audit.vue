<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>审计与告警</h2>
        <div class="sub">操作审计全量留痕（SHA-256 哈希链）、安全告警处置与告警规则</div>
      </div>
      <div class="toolbar" style="margin:0">
        <el-button size="small" :icon="CircleCheck" @click="verify">校验审计链</el-button>
        <el-button size="small" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert v-if="chain" :type="chain.intact ? 'success' : 'error'" :closable="false" style="margin-bottom:12px"
              :title="`审计链校验：${chain.intact ? '完整' : '发现断点'} · 校验 ${chain.checked} 条 · 算法 ${chain.algorithm}`" />

    <div class="card">
      <div class="card-head"><h3>操作审计日志</h3><span class="hint">保留期 {{ logs.retainDays }} 天</span></div>
      <div class="toolbar">
        <el-input v-model="query.username" placeholder="用户名" size="small" clearable style="width:140px" @keyup.enter="search" @clear="search" />
        <el-select v-model="query.module" placeholder="模块" size="small" clearable style="width:130px" @change="search">
          <el-option v-for="m in ['metadata','model','quality','security','service','integration','industry','auth']" :key="m" :label="m" :value="m" />
        </el-select>
        <el-select v-model="query.result" placeholder="结果" size="small" clearable style="width:110px" @change="search">
          <el-option label="SUCCESS" value="SUCCESS" /><el-option label="FAIL" value="FAIL" />
        </el-select>
        <span class="spacer"></span>
        <span class="muted" style="font-size:12px">共 {{ logs.total || 0 }} 条</span>
      </div>
      <el-table :data="logs.records || []" size="small" v-loading="loading">
        <el-table-column prop="logTime" label="时间" width="150"><template #default="{ row }">{{ fmt(row.logTime) }}</template></el-table-column>
        <el-table-column prop="username" label="用户" width="90" />
        <el-table-column prop="action" label="动作" width="90" />
        <el-table-column prop="module" label="模块" width="100" />
        <el-table-column prop="resource" label="资源" min-width="240" show-overflow-tooltip><template #default="{ row }"><span class="mono">{{ row.resource }}</span></template></el-table-column>
        <el-table-column prop="result" label="结果" width="86">
          <template #default="{ row }"><el-tag size="small" :type="row.result === 'SUCCESS' ? 'success' : 'danger'" effect="plain">{{ row.result }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" width="120" />
        <el-table-column prop="traceId" label="链路 ID" width="150"><template #default="{ row }"><span class="mono">{{ (row.traceId || '').slice(0, 14) }}</span></template></el-table-column>
        <el-table-column prop="hash" label="哈希" width="130"><template #default="{ row }"><span class="mono">{{ (row.hash || '').slice(0, 12) }}…</span></template></el-table-column>
      </el-table>
      <div style="display:flex;justify-content:flex-end;margin-top:10px">
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="logs.total || 0"
                       layout="total, prev, pager, next" @change="loadLogs" />
      </div>
    </div>

    <div class="grid grid-2" style="margin-top:12px">
      <div class="card">
        <div class="card-head"><h3>安全告警</h3></div>
        <el-table :data="alerts" size="small" max-height="300">
          <el-table-column prop="title" label="告警" min-width="200" show-overflow-tooltip />
          <el-table-column prop="severity" label="级别" width="86">
            <template #default="{ row }"><el-tag size="small" :type="row.severity === 'CRITICAL' ? 'danger' : row.severity === 'HIGH' ? 'warning' : 'info'" effect="plain">{{ row.severity }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="86" />
          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <el-button v-if="row.status === 'OPEN'" link type="primary" size="small" @click="handleAlert(row)">处置</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div class="card">
        <div class="card-head"><h3>告警规则</h3></div>
        <el-table :data="alertRules" size="small" max-height="300">
          <el-table-column prop="name" label="规则" min-width="180" />
          <el-table-column prop="metric" label="指标" width="150"><template #default="{ row }"><span class="mono">{{ row.metric }}</span></template></el-table-column>
          <el-table-column prop="threshold" label="阈值" width="80" />
          <el-table-column prop="windowMin" label="窗口(分)" width="86" align="right" />
          <el-table-column prop="severity" label="级别" width="86" />
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { CircleCheck } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { securityApi } from '@/api'

const logs = ref({})
const alerts = ref([])
const alertRules = ref([])
const chain = ref(null)
const loading = ref(false)
const query = reactive({ username: '', module: '', result: '', pageNum: 1, pageSize: 15 })

const fmt = (v) => (v ? String(v).replace('T', ' ').slice(0, 19) : '-')

const loadLogs = async () => {
  loading.value = true
  try {
    logs.value = await securityApi.auditLogs({ ...query })
  } catch (e) {
    logs.value = {}
  } finally {
    loading.value = false
  }
}

/** 筛选条件变化统一回到第一页。 */
const search = () => {
  query.pageNum = 1
  loadLogs()
}

const load = async () => {
  loadLogs()
  try {
    alerts.value = (await securityApi.alerts({})).records || []
  } catch (e) {
    alerts.value = []
  }
  try {
    alertRules.value = (await securityApi.alertRules()).records || []
  } catch (e) {
    alertRules.value = []
  }
}

const verify = async () => {
  try {
    chain.value = await securityApi.verifyChain()
    ElMessage[chain.value.intact ? 'success' : 'error'](chain.value.intact ? '哈希链完整' : `断点位于 id=${chain.value.brokenAt}`)
  } catch (e) {
    /* http 层已提示 */
  }
}

const handleAlert = async (row) => {
  try {
    await securityApi.handleAlert(row.id, { comment: '已核实并处置' })
    ElMessage.success('告警已处置')
    load()
  } catch (e) {
    /* http 层已提示 */
  }
}

onMounted(load)
</script>
