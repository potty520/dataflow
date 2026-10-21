<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>调度与工作流</h2>
        <div class="sub">按依赖编排作业，支持手工触发、实例监控、重试与补数</div>
      </div>
      <div class="toolbar" style="margin:0">
        <el-tag effect="plain" size="small">定义 {{ stats.definitionCount }}</el-tag>
        <el-tag effect="plain" size="small" type="success">实例成功率 {{ stats.successRate ?? '-' }}%</el-tag>
      </div>
    </div>

    <div class="card">
      <div class="card-head">
        <h3>工作流定义</h3>
        <span class="hint">DAG 依赖由节点 upstream 声明，触发时按拓扑序执行</span>
      </div>
      <el-table :data="definitions" size="small">
        <el-table-column prop="code" label="编码" width="150"><template #default="{ row }"><span class="mono">{{ row.code }}</span></template></el-table-column>
        <el-table-column prop="cnName" label="名称" min-width="180" />
        <el-table-column prop="nodeCount" label="节点数" width="80" align="right" />
        <el-table-column prop="cron" label="调度" width="120"><template #default="{ row }"><span class="mono">{{ row.cron }}</span></template></el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }"><el-tag size="small" :type="row.status === 'PUBLISHED' ? 'success' : 'info'" effect="plain">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="lastInstanceStatus" label="最近实例" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.lastInstanceStatus" size="small" :type="row.lastInstanceStatus === 'SUCCESS' ? 'success' : 'danger'" effect="plain">{{ row.lastInstanceStatus }}</el-tag>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="lastRunTime" label="最近运行" width="150"><template #default="{ row }">{{ fmt(row.lastRunTime) }}</template></el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" :loading="triggering === row.id" @click="trigger(row)">触发</el-button>
            <el-button link type="primary" size="small" @click="showNodes(row)">依赖图</el-button>
            <el-button v-if="row.status !== 'PUBLISHED'" link type="success" size="small" @click="online(row)">上线</el-button>
            <el-button v-else link type="warning" size="small" @click="offline(row)">下线</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="card" style="margin-top:12px">
      <div class="card-head">
        <h3>实例监控</h3>
        <el-button size="small" @click="loadInstances">刷新</el-button>
      </div>
      <el-table :data="instances" size="small">
        <el-table-column prop="jobName" label="工作流" min-width="180" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }"><el-tag size="small" :type="row.status === 'SUCCESS' ? 'success' : row.status === 'RUNNING' ? 'warning' : 'danger'" effect="plain">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="triggerType" label="触发" width="90" />
        <el-table-column prop="startTime" label="开始" width="150"><template #default="{ row }">{{ fmt(row.startTime) }}</template></el-table-column>
        <el-table-column prop="durationMs" label="耗时(ms)" width="90" align="right" />
        <el-table-column prop="rowsWritten" label="写入行数" width="100" align="right" />
        <el-table-column prop="operator" label="操作人" width="90" />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="showDetail(row)">详情</el-button>
            <el-button link type="warning" size="small" @click="retry(row)">重试</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="nodesVisible" title="依赖图与节点状态" width="680px">
      <div v-if="nodeRows.length" class="dep">
        <div v-for="n in nodeRows" :key="n.key" class="dep-node">
          <div class="nt">{{ n.jobCode }}</div>
          <div class="nu">上游：{{ (n.upstream || []).join('、') || '无' }}</div>
        </div>
      </div>
      <el-empty v-else description="该工作流未配置节点" :image-size="60" />
    </el-dialog>

    <el-dialog v-model="detailVisible" title="实例详情" width="760px">
      <div class="kv"><span class="k">实例 ID</span><span class="v">{{ detail.id }}</span></div>
      <div class="kv"><span class="k">状态</span><span class="v">{{ detail.status }} · {{ detail.message }}</span></div>
      <div class="kv"><span class="k">耗时</span><span class="v">{{ detail.durationMs }} ms</span></div>
      <div class="section-title">执行日志</div>
      <div class="logbox">
        <div v-for="l in detail.logs || []" :key="l.id" class="logline">
          <span class="lt">{{ fmt(l.logTime) }}</span><span class="lv">{{ l.logLevel }}</span><span>{{ l.message }}</span>
        </div>
        <div v-if="!(detail.logs || []).length" class="empty">无日志</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { workflowApi } from '@/api'

const definitions = ref([])
const instances = ref([])
const stats = ref({})
const triggering = ref(null)
const nodesVisible = ref(false)
const nodeRows = ref([])
const detailVisible = ref(false)
const detail = ref({})

const fmt = (v) => (v ? String(v).replace('T', ' ').slice(0, 19) : '-')

const load = async () => {
  // 定义列表是主数据，统计与实例监控各自独立失败
  try {
    const d = await workflowApi.definitions()
    definitions.value = d.records || []
  } catch (e) {
    definitions.value = []
  }
  try {
    stats.value = (await workflowApi.stats()) || {}
  } catch (e) {
    stats.value = {}
  }
  loadInstances()
}

const loadInstances = async () => {
  try {
    const d = await workflowApi.instances({ pageSize: 20 })
    instances.value = d.records || []
  } catch (e) {
    instances.value = []
  }
}

const trigger = async (row) => {
  const name = row.cnName || row.code
  try {
    await ElMessageBox.confirm(
      `确认立即触发工作流「${name}」？将按 DAG 拓扑序真实执行全部节点并写入目标表。`,
      '触发确认',
      { confirmButtonText: '立即触发', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (e) {
    return
  }
  triggering.value = row.id
  try {
    const r = await workflowApi.trigger(row.id)
    ElMessage[r.status === 'SUCCESS' ? 'success' : 'warning'](`实例 ${r.instanceId} ${r.status}：${r.nodeCount} 个节点，写入 ${r.rowsWritten} 行`)
    load()
  } catch (e) {
    /* http 层已提示 */
  } finally {
    triggering.value = null
  }
}

const online = async (row) => { await workflowApi.online(row.id); ElMessage.success('已上线'); load() }

const offline = async (row) => {
  const name = row.cnName || row.code
  try {
    await ElMessageBox.confirm(`确认下线工作流「${name}」？下线后不再按调度自动运行，手工触发仍可用。`, '下线确认', {
      confirmButtonText: '确认下线',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    await workflowApi.offline(row.id)
    ElMessage.success('已下线')
    load()
  } catch (e) {
    /* http 层已提示 */
  }
}

const showNodes = async (row) => {
  try {
    const d = await workflowApi.definition(row.id)
    nodeRows.value = d.dslJson?.nodes || []
    nodesVisible.value = true
  } catch (e) {
    nodeRows.value = []
    nodesVisible.value = true
  }
}

const showDetail = async (row) => {
  try {
    detail.value = await workflowApi.instance(row.id)
    detailVisible.value = true
  } catch (e) {
    detail.value = {}
  }
}

const retry = async (row) => {
  const r = await workflowApi.retry(row.id)
  ElMessage.success(`已重试，新实例 ${r.retryInstanceId}：${r.status}`)
  load()
}

onMounted(load)
</script>

<style scoped>
.dep { display: flex; flex-wrap: wrap; gap: 10px; }
.dep-node { border: 1px solid #e2e8f0; border-radius: 8px; padding: 10px 12px; background: #f8fafc; min-width: 170px; }
.nt { font-weight: 600; font-size: 12.5px; }
.nu { font-size: 11px; color: #94a3b8; margin-top: 3px; }
.logbox { background: #0f172a; border-radius: 6px; padding: 10px; max-height: 320px; overflow: auto; font-family: Consolas, monospace; font-size: 11.5px; }
.logline { color: #cbd5e1; display: flex; gap: 8px; padding: 2px 0; }
.lt { color: #64748b; flex: none; }
.lv { color: #38bdf8; flex: none; width: 44px; }
</style>
