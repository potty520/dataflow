<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>数据集成</h2>
        <div class="sub">图形化 ETL 与脚本双模式，支持 DSL ↔ SQL 转换、编译、试运行与真实执行</div>
      </div>
      <div class="toolbar" style="margin:0">
        <el-tag effect="plain" size="small">作业 {{ stats.jobCount }}</el-tag>
        <el-tag effect="plain" size="small" type="success">成功率 {{ stats.successRate ?? '-' }}%</el-tag>
        <el-button size="small" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="grid" style="grid-template-columns: 300px 1fr">
      <div class="card" style="padding:0">
        <div class="card-head" style="padding:12px 12px 0"><h3>作业清单</h3></div>
        <div class="job-list">
          <div v-for="j in jobs" :key="j.id" class="job-item" :class="{ active: current.id === j.id }" @click="select(j)">
            <div class="jt">
              <span class="mono">{{ j.code }}</span>
              <el-tag size="small" :type="j.status === 'PUBLISHED' ? 'success' : 'info'" effect="plain">{{ j.status }}</el-tag>
            </div>
            <div class="jn">{{ j.cnName || j.name }}</div>
            <div class="jm">{{ j.jobType }} · 每 {{ j.intervalMin }} 分钟 · 成功 {{ j.successCount }} / 失败 {{ j.failCount }}</div>
          </div>
        </div>
      </div>

      <div>
        <div class="card">
          <div class="card-head">
            <h3>{{ current.cnName || '请选择作业' }} <span class="muted mono" style="font-size:11px">{{ current.code }}</span></h3>
            <div>
              <el-button size="small" :icon="VideoPlay" type="primary" :loading="running" @click="run">手动执行</el-button>
              <el-button size="small" @click="publish" v-if="current.status !== 'PUBLISHED'">发布</el-button>
            </div>
          </div>

          <el-tabs v-model="tab">
            <el-tab-pane label="图形化流程" name="design">
              <div v-if="nodes.length" class="canvas">
                <div class="flow">
                  <template v-for="(n, i) in nodes" :key="n.id">
                    <div class="node" :class="n.type.toLowerCase()">
                      <div class="ntype">{{ n.type }}</div>
                      <div class="nname">{{ n.name }}</div>
                    </div>
                    <div v-if="i < nodes.length - 1" class="arrow">→</div>
                  </template>
                </div>
                <div class="muted" style="font-size:11.5px;margin-top:12px">
                  该流程由 DSL 驱动，节点=算子、边=数据流；可一键转为 SQL 脚本后在脚本模式微调。
                </div>
              </div>
              <div v-else class="empty">该作业无图形化 DSL（脚本作业）</div>
              <div class="toolbar" style="margin-top:12px;margin-bottom:0" v-if="nodes.length">
                <el-button size="small" @click="toScript">DSL → 脚本</el-button>
                <el-button size="small" @click="compile">编译执行计划</el-button>
                <el-button size="small" @click="dryRun">试运行采样</el-button>
              </div>
            </el-tab-pane>

            <el-tab-pane label="脚本模式" name="script">
              <el-input v-model="script" type="textarea" :rows="9" class="mono" />
              <div class="toolbar" style="margin-top:10px;margin-bottom:0">
                <el-button size="small" @click="formatSql">格式化</el-button>
                <el-button size="small" @click="validateSql">语法校验</el-button>
                <el-button size="small" @click="fromScript">脚本 → DSL</el-button>
                <span class="spacer"></span>
                <span class="muted" style="font-size:11.5px">沙箱策略：仅允许 SELECT / INSERT / UPDATE / DELETE / MERGE</span>
              </div>
              <div v-if="validMsg" style="margin-top:8px">
                <el-alert :title="validMsg" :type="validOk ? 'success' : 'error'" :closable="false" />
              </div>
            </el-tab-pane>

            <el-tab-pane label="运行记录" name="instances">
              <el-table :data="instances" size="small">
                <el-table-column prop="status" label="状态" width="90">
                  <template #default="{ row }"><el-tag size="small" :type="row.status === 'SUCCESS' ? 'success' : row.status === 'RUNNING' ? 'warning' : 'danger'" effect="plain">{{ row.status }}</el-tag></template>
                </el-table-column>
                <el-table-column prop="triggerType" label="触发" width="90" />
                <el-table-column prop="startTime" label="开始" width="150"><template #default="{ row }">{{ fmt(row.startTime) }}</template></el-table-column>
                <el-table-column prop="durationMs" label="耗时(ms)" width="90" align="right" />
                <el-table-column prop="rowsWritten" label="写入行数" width="100" align="right" />
                <el-table-column prop="message" label="结果" min-width="260" show-overflow-tooltip />
                <el-table-column prop="operator" label="操作人" width="90" />
              </el-table>
            </el-tab-pane>

            <el-tab-pane label="运行日志" name="logs">
              <div class="logbox">
                <div v-for="l in logs" :key="l.id" class="logline">
                  <span class="lt">{{ fmt(l.logTime) }}</span>
                  <span :class="['lv', l.logLevel === 'ERROR' ? 'err' : '']">{{ l.logLevel }}</span>
                  <span>{{ l.message }}</span>
                </div>
                <div v-if="!logs.length" class="empty">暂无日志，点击「手动执行」后查看</div>
              </div>
            </el-tab-pane>
          </el-tabs>
        </div>

        <div class="card" style="margin-top:12px">
          <div class="card-head">
            <h3>自助取数（Ad-hoc 查询）</h3>
            <span class="hint">仅允许单条 SELECT，自动追加 LIMIT</span>
          </div>
          <el-input v-model="adhocSql" type="textarea" :rows="3" class="mono" />
          <div class="toolbar" style="margin-top:8px;margin-bottom:0">
            <el-button size="small" type="primary" @click="runAdhoc">执行查询</el-button>
            <span class="muted" style="font-size:12px" v-if="adhocRows.length">返回 {{ adhocRows.length }} 行 · {{ adhocMs }} ms</span>
          </div>
          <el-table v-if="adhocRows.length" :data="adhocRows" size="small" max-height="260" style="margin-top:10px">
            <el-table-column v-for="c in Object.keys(adhocRows[0])" :key="c" :prop="c" :label="c" min-width="110" show-overflow-tooltip />
          </el-table>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { VideoPlay } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { integrationApi } from '@/api'

const jobs = ref([])
const current = ref({})
const tab = ref('design')
const nodes = ref([])
const script = ref('')
const instances = ref([])
const logs = ref([])
const stats = ref({})
const running = ref(false)
const validMsg = ref('')
const validOk = ref(false)
const adhocSql = ref('SELECT region, COUNT(*) AS cnt, ROUND(SUM(amount),2) AS amt FROM ods_order GROUP BY region ORDER BY amt DESC')
const adhocRows = ref([])
const adhocMs = ref(0)

const fmt = (v) => (v ? String(v).replace('T', ' ').slice(0, 19) : '-')

const load = async () => {
  try {
    const d = await integrationApi.jobs({ pageSize: 50 })
    jobs.value = d.records || []
  } catch (e) {
    jobs.value = []
  }
  try {
    stats.value = (await integrationApi.stats()) || {}
  } catch (e) {
    stats.value = {}
  }
  if (!current.value.id && jobs.value.length) select(jobs.value[0])
}

const select = async (row) => {
  current.value = row
  tab.value = 'design'
  logs.value = []
  validMsg.value = ''
  let detail
  try {
    detail = await integrationApi.job(row.id)
  } catch (e) {
    return
  }
  current.value = detail
  script.value = detail.script || ''
  try {
    const dsl = typeof detail.dsl === 'string' ? JSON.parse(detail.dsl) : detail.dsl
    nodes.value = dsl?.nodes || []
  } catch (e) {
    nodes.value = []
  }
  try {
    const i = await integrationApi.instances(row.id, { pageSize: 20 })
    instances.value = i.records || []
  } catch (e) {
    instances.value = []
  }
  try {
    const l = await integrationApi.logs(row.id)
    logs.value = l.records || []
  } catch (e) {
    logs.value = []
  }
}

const run = async () => {
  running.value = true
  try {
    const r = await integrationApi.runJob(current.value.id)
    ElMessage[r.status === 'SUCCESS' ? 'success' : 'error'](`${r.status}：影响 ${r.rowsWritten} 行，耗时 ${r.durationMs} ms`)
    await select(current.value)
  } finally {
    running.value = false
  }
}

const publish = async () => {
  await integrationApi.publishJob(current.value.id)
  ElMessage.success('作业已发布，进入调度队列')
  load()
}

const toScript = async () => {
  const r = await integrationApi.toScript(current.value.id)
  script.value = r.script
  tab.value = 'script'
  ElMessage.success('已转换并回写脚本')
}

const fromScript = async () => {
  const r = await integrationApi.fromScript(current.value.id)
  ElMessage.success('已转换为 DSL：' + (r.dsl?.nodes?.length || 0) + ' 个节点')
  await select(current.value)
}

const compile = async () => {
  const r = await integrationApi.compile(current.value.id)
  ElMessageBox.alert(`执行计划 ${r.stepCount} 步（${r.engine}）：
` + r.plan.map((p) => `${p.step}. ${p.operator} → ${p.target}`).join('\n'), '编译结果')
}

const dryRun = async () => {
  const r = await integrationApi.dryRun(current.value.id, { limit: 5 })
  ElMessageBox.alert(`目标表 ${r.sinkTable} 采样 ${r.sampled} 行（未落任务实例、未提交事务）`, '试运行')
}

const formatSql = async () => {
  const r = await integrationApi.formatSql(script.value)
  script.value = r.formatted
  ElMessage.success('已格式化')
}

const validateSql = async () => {
  try {
    const r = await integrationApi.validateSql(script.value)
    validMsg.value = r.message
    validOk.value = true
  } catch (e) {
    validMsg.value = e.message || '语法校验失败'
    validOk.value = false
  }
}

const runAdhoc = async () => {
  try {
    const r = await integrationApi.adhoc(adhocSql.value)
    adhocRows.value = r.rows || []
    adhocMs.value = r.costMs
    ElMessage.success(`返回 ${r.rowCount} 行`)
  } catch (e) { /* ignore */ }
}

onMounted(load)
</script>

<style scoped>
.job-list { max-height: 520px; overflow: auto; padding: 8px; }
.job-item { padding: 9px 10px; border-radius: 6px; cursor: pointer; border: 1px solid transparent; }
.job-item:hover { background: #f8fafc; }
.job-item.active { background: #eff6ff; border-color: #bfdbfe; }
.jt { display: flex; align-items: center; justify-content: space-between; font-size: 11.5px; color: #6b7280; }
.jn { font-size: 13px; font-weight: 600; margin: 3px 0; }
.jm { font-size: 11px; color: #94a3b8; }
.canvas { background: #f8fafc; border: 1px dashed #dbeafe; border-radius: 8px; padding: 26px 18px; }
.flow { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.node { min-width: 118px; border-radius: 8px; padding: 10px 12px; background: #fff; border: 1px solid #e2e8f0; box-shadow: 0 1px 2px rgba(0,0,0,.04); }
.node.source { border-left: 3px solid #64748b; }
.node.sink { border-left: 3px solid #d97706; }
.node.filter, .node.field_map, .node.join, .node.aggregate, .node.validate, .node.type_convert { border-left: 3px solid #2563eb; }
.ntype { font-size: 10px; color: #94a3b8; letter-spacing: .5px; }
.nname { font-size: 12.5px; font-weight: 600; margin-top: 2px; }
.arrow { color: #94a3b8; font-size: 16px; }
.logbox { background: #0f172a; border-radius: 6px; padding: 10px; max-height: 300px; overflow: auto; font-family: Consolas, monospace; font-size: 11.5px; }
.logline { color: #cbd5e1; display: flex; gap: 8px; padding: 2px 0; }
.lt { color: #64748b; flex: none; }
.lv { color: #38bdf8; flex: none; width: 44px; }
.lv.err { color: #f87171; }
</style>
