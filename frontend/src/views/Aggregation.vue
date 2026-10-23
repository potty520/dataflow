<template>
  <div class="page">
    <el-card shadow="never">
      <div class="bar">
        <div>
          <span class="title">聚合汇总</span>
          <span class="hint">聚合视图迁移向导 + 定时汇总刷新（FULL / UPSERT）</span>
        </div>
        <div>
          <el-input v-model="keyword" size="small" placeholder="搜索编码/名称/源表" style="width:200px" clearable @keyup.enter="load" />
          <el-button size="small" @click="load">查询</el-button>
          <el-button v-if="can('migration:agg:create')" size="small" type="primary" @click="openEdit()">新建汇总</el-button>
        </div>
      </div>
      <el-table :data="rows" size="small" v-loading="loading">
        <el-table-column prop="code" label="编码" width="150" show-overflow-tooltip />
        <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
        <el-table-column label="源 → 目标" min-width="230" show-overflow-tooltip>
          <template #default="{ row }">{{ row.sourceTable }} → {{ row.targetTable }}</template>
        </el-table-column>
        <el-table-column label="维度" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ (row.dimList || []).join('、') }}</template>
        </el-table-column>
        <el-table-column label="指标" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ metricsText(row) }}</template>
        </el-table-column>
        <el-table-column label="模式" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.refreshMode === 'UPSERT' ? 'success' : 'info'">{{ row.refreshMode }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="刷新" width="110">
          <template #default="{ row }">
            <span v-if="row.refreshMinutes > 0">{{ row.refreshMinutes }} 分钟</span>
            <span v-else class="hint">手动</span>
          </template>
        </el-table-column>
        <el-table-column label="上次" width="150">
          <template #default="{ row }">
            <el-tag v-if="row.lastStatus === 'DONE'" size="small" type="success">成功 {{ row.lastRows }}</el-tag>
            <el-tag v-else-if="row.lastStatus === 'FAILED'" size="small" type="danger">失败</el-tag>
            <span v-else class="hint">未刷新</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button v-if="can('migration:agg:edit')" link type="primary" size="small" @click="build(row)">生成</el-button>
            <el-button v-if="can('migration:agg:exec')" link type="primary" size="small" @click="refresh(row)">刷新</el-button>
            <el-button link type="primary" size="small" @click="preview(row)">预览</el-button>
            <el-button link type="primary" size="small" @click="showRuns(row)">记录</el-button>
            <el-button v-if="can('migration:agg:edit')" link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="can('migration:agg:edit')" link type="danger" size="small" @click="del(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建/编辑 -->
    <el-dialog v-model="editVisible" :title="form.id ? '编辑聚合汇总' : '新建聚合汇总'" width="760px">
      <el-form label-width="110px" size="small">
        <el-form-item label="编码"><el-input v-model="form.code" :disabled="!!form.id" placeholder="如 AGG-ORDER-CUSTOMER" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="源数据源">
          <el-select v-model="form.sourceDsId" style="width:100%" @change="loadColumns">
            <el-option label="本库（平台数据库）" :value="0" />
            <el-option v-for="d in remoteDsList" :key="d.id" :label="`${d.code} ${d.name} (${d.host})`" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="源表/视图">
          <el-input v-model="form.sourceTable" placeholder="如 ods_order（或已有视图名）" @blur="loadColumns" />
        </el-form-item>
        <el-form-item label="维度字段">
          <el-select v-model="form.dims" multiple filterable allow-create style="width:100%" placeholder="GROUP BY 维度，如 customer_id">
            <el-option v-for="c in columns" :key="c.name" :label="c.name + ' (' + (c.type || '') + ')'" :value="c.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="指标">
          <div style="width:100%">
            <div v-for="(m, i) in form.metrics" :key="i" class="metric-row">
              <el-select v-model="m.fn" style="width:130px">
                <el-option v-for="f in ['COUNT', 'COUNT_DISTINCT', 'SUM', 'AVG', 'MAX', 'MIN']" :key="f" :label="f" :value="f" />
              </el-select>
              <el-select v-model="m.column" filterable allow-create style="width:180px" placeholder="字段（COUNT 可用 *）">
                <el-option label="*（COUNT 专用）" value="*" />
                <el-option v-for="c in columns" :key="c.name" :label="c.name" :value="c.name" />
              </el-select>
              <el-input v-model="m.alias" style="width:150px" placeholder="输出列名" />
              <el-button link type="danger" size="small" @click="form.metrics.splice(i, 1)">删除</el-button>
            </div>
            <el-button link type="primary" size="small" @click="form.metrics.push({ fn: 'SUM', column: '', alias: '' })">+ 添加指标</el-button>
          </div>
        </el-form-item>
        <el-form-item label="过滤条件"><el-input v-model="form.whereClause" placeholder="可选，如 order_time >= '2026-01-01'（禁止写操作）" /></el-form-item>
        <el-form-item label="目标数据源">
          <el-select v-model="form.targetDsId" style="width:100%">
            <el-option label="本库（平台数据库）" :value="0" />
            <el-option v-for="d in remoteDsList" :key="d.id" :label="`${d.code} ${d.name} (${d.host})`" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标汇总表"><el-input v-model="form.targetTable" placeholder="如 order_summary_customer" /></el-form-item>
        <el-form-item label="刷新模式">
          <el-radio-group v-model="form.refreshMode">
            <el-radio label="FULL">FULL（清空重写）</el-radio>
            <el-radio label="UPSERT">UPSERT（按维度更新，需唯一键）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="定时刷新">
          <el-input-number v-model="form.refreshMinutes" :min="0" :max="10080" />
          <span class="hint" style="margin-left:8px">分钟；0 表示手动刷新</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="editVisible = false">取消</el-button>
        <el-button size="small" @click="previewForm">预览 SQL</el-button>
        <el-button size="small" type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 预览 -->
    <el-dialog v-model="previewVisible" title="生成的 SQL 预览" width="820px">
      <el-alert :closable="false" type="info" show-icon title="生成时会：1）在源库创建/替换视图；2）在目标库建汇总表；3）创建一个 FULL 迁移项目（视图→汇总表）" />
      <div class="sql-title">源库视图</div>
      <pre class="sql">{{ previewData.viewSql }}</pre>
      <div class="sql-title">目标表 DDL</div>
      <pre class="sql">{{ previewData.targetDdl }}</pre>
    </el-dialog>

    <!-- 刷新记录 -->
    <el-drawer v-model="runsVisible" title="刷新记录" size="560px">
      <el-table :data="runList" size="small">
        <el-table-column prop="id" label="#" width="60" />
        <el-table-column prop="triggerType" label="触发" width="90" />
        <el-table-column prop="status" label="状态" width="90" />
        <el-table-column prop="rowsCount" label="行数" width="90" />
        <el-table-column prop="startTime" label="开始时间" min-width="150" />
        <el-table-column prop="message" label="说明" min-width="150" show-overflow-tooltip />
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { migrationApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

const store = useAuthStore()
const can = (p) => store.has(p)

const loading = ref(false)
const keyword = ref('')
const rows = ref([])
const remoteDsList = ref([])

const editVisible = ref(false)
const columns = ref([])
const form = reactive({
  id: null, code: '', name: '', sourceDsId: 0, sourceTable: '', targetDsId: 0, targetTable: '',
  dims: [], metrics: [{ fn: 'COUNT', column: '*', alias: 'row_cnt' }],
  whereClause: '', refreshMode: 'FULL', refreshMinutes: 0
})

const previewVisible = ref(false)
const previewData = ref({ viewSql: '', targetDdl: '', viewName: '', targetTable: '' })
const runsVisible = ref(false)
const runList = ref([])

function metricsText(row) {
  return (row.metricList || []).map(m => `${m.fn}(${m.column || '*'})→${m.alias}`).join('，')
}

async function load() {
  loading.value = true
  try {
    const r = await migrationApi.aggList({ keyword: keyword.value })
    rows.value = r.records || []
  } finally { loading.value = false }
}
async function loadRemoteDs() {
  try { remoteDsList.value = (await migrationApi.remoteDsList({})).records || [] } catch (e) { remoteDsList.value = [] }
}
async function loadColumns() {
  columns.value = []
  if (!form.sourceTable) return
  try {
    if (form.sourceDsId > 0) {
      const r = await migrationApi.remoteDsColumns(form.sourceDsId, form.sourceTable)
      columns.value = r.records || []
    } else {
      const r = await migrationApi.columns(form.sourceTable)
      columns.value = r.records || (Array.isArray(r) ? r : [])
    }
  } catch (e) { columns.value = [] }
}
function openEdit(row) {
  Object.assign(form, {
    id: null, code: '', name: '', sourceDsId: 0, sourceTable: '', targetDsId: 0, targetTable: '',
    dims: [], metrics: [{ fn: 'COUNT', column: '*', alias: 'row_cnt' }],
    whereClause: '', refreshMode: 'FULL', refreshMinutes: 0
  })
  if (row) {
    Object.assign(form, {
      id: row.id, code: row.code, name: row.name, sourceDsId: row.sourceDsId || 0,
      sourceTable: row.sourceTable, targetDsId: row.targetDsId || 0, targetTable: row.targetTable,
      dims: row.dimList || [], metrics: (row.metricList || []).map(m => ({ ...m })),
      whereClause: row.whereClause || '', refreshMode: row.refreshMode || 'FULL',
      refreshMinutes: row.refreshMinutes || 0
    })
  }
  editVisible.value = true
  loadColumns()
  loadRemoteDs()
}
async function save() {
  const body = {
    code: form.code, name: form.name, sourceDsId: form.sourceDsId, targetDsId: form.targetDsId,
    sourceTable: form.sourceTable, targetTable: form.targetTable,
    dims: form.dims, metrics: form.metrics.map(m => ({ fn: m.fn, column: m.column, alias: m.alias })),
    whereClause: form.whereClause, refreshMode: form.refreshMode, refreshMinutes: form.refreshMinutes
  }
  try {
    if (form.id) await migrationApi.aggUpdate(form.id, body)
    else await migrationApi.aggCreate(body)
    ElMessage.success('已保存')
    editVisible.value = false
    load()
  } catch (e) { ElMessage.error(e.message || '保存失败') }
}
async function build(row) {
  try {
    const r = await migrationApi.aggBuild(row.id)
    ElMessage.success(`已生成：视图 ${r.viewName}，目标表 ${r.targetTable}，迁移项目 #${r.projectId}`)
    load()
  } catch (e) { ElMessage.error(e.message || '生成失败') }
}
async function refresh(row) {
  try {
    const r = await migrationApi.aggRefresh(row.id)
    ElMessage.success(`刷新完成（${r.mode}）：${r.rows} 行`)
    load()
  } catch (e) { ElMessage.error(e.message || '刷新失败') }
}
async function preview(row) {
  try {
    const r = await migrationApi.aggPreview(row.id)
    previewData.value = { ...r, viewSql: r.viewSql, targetDdl: r.targetDdl }
    previewVisible.value = true
  } catch (e) { ElMessage.error(e.message || '预览失败') }
}
async function previewForm() {
  if (!form.id) { ElMessage.warning('请先保存后再预览'); return }
  await preview(form)
}
async function showRuns(row) {
  const r = await migrationApi.aggRuns(row.id)
  runList.value = r.records || []
  runsVisible.value = true
}
async function del(row) {
  await ElMessageBox.confirm(`删除聚合汇总 ${row.code}？`, '提示', { type: 'warning' })
  await migrationApi.aggDelete(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(() => { load(); loadRemoteDs() })
</script>

<style scoped>
.page { padding: 4px; }
.bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; gap: 10px; }
.title { font-size: 15px; font-weight: 600; margin-right: 8px; }
.hint { color: #909399; font-size: 12px; }
.metric-row { display: flex; gap: 8px; align-items: center; margin-bottom: 6px; }
.sql-title { font-weight: 600; margin: 10px 0 4px; }
.sql { background: #f6f8fa; border: 1px solid #e4e7ed; border-radius: 4px; padding: 10px; font-size: 12px; white-space: pre-wrap; }
</style>
