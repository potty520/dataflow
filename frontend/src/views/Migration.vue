<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>数据迁移</h2>
        <div class="sub">源表 → 目标表字段映射（直接/表达式/常量/默认值）→ 预检 → 执行（INSERT…SELECT）→ 对账差异</div>
      </div>
      <div class="toolbar" style="margin:0">
        <el-tag effect="plain" size="small">项目 {{ overview.projectCount || 0 }}</el-tag>
        <el-tag effect="plain" size="small" type="success">就绪 {{ overview.readyCount || 0 }}</el-tag>
        <el-button size="small" :icon="Refresh" @click="loadAll">刷新</el-button>
        <el-button v-if="can('migration:project:create')" size="small" type="primary" :icon="Plus" @click="openProject()">新建迁移项目</el-button>
      </div>
    </div>

    <div class="grid grid-4" style="margin-bottom:12px">
      <div class="stat"><div class="label">迁移项目</div><div class="value">{{ overview.projectCount || 0 }}</div><div class="foot">映射 {{ overview.mappingCount || 0 }} 条</div></div>
      <div class="stat green"><div class="label">已完成迁移</div><div class="value">{{ overview.doneCount || 0 }}</div><div class="foot">失败 {{ overview.failedCount || 0 }}</div></div>
      <div class="stat orange"><div class="label">近 24h 执行</div><div class="value">{{ overview.runCount24h || 0 }}</div><div class="foot">写入 {{ overview.inserted24h || 0 }} 行</div></div>
      <div class="stat red"><div class="label">对账失败项</div><div class="value">{{ overview.failedChecks24h || 0 }}</div><div class="foot">行数/主键/空值/合计</div></div>
    </div>

    <div class="card" style="margin-bottom:12px">
      <div class="card-head"><h3>迁移项目</h3>
        <div class="toolbar" style="margin:0">
          <el-input v-model="query.keyword" placeholder="名称 / 编码 / 表名" size="small" clearable style="width:200px" @keyup.enter="loadProjects" @clear="loadProjects" />
          <el-select v-model="query.status" placeholder="状态" size="small" clearable style="width:120px" @change="loadProjects">
            <el-option label="DRAFT" value="DRAFT" /><el-option label="READY" value="READY" />
            <el-option label="DONE" value="DONE" /><el-option label="FAILED" value="FAILED" />
          </el-select>
          <el-button size="small" @click="loadProjects">查询</el-button>
        </div>
      </div>
      <el-table :data="projects" size="small" highlight-current-row empty-text="暂无迁移项目" @current-change="selectProject">
        <el-table-column prop="code" label="编码" width="170" />
        <el-table-column prop="name" label="名称" min-width="170" show-overflow-tooltip />
        <el-table-column label="源 → 目标" min-width="260">
          <template #default="{ row }"><code>{{ row.sourceTable }}</code> → <code>{{ row.targetTable }}</code></template>
        </el-table-column>
        <el-table-column prop="mode" label="模式" width="110" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'DONE' ? 'success' : row.status === 'FAILED' ? 'danger' : row.status === 'READY' ? 'warning' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="mappingCount" label="映射" width="80" />
        <el-table-column label="最近执行" width="170"><template #default="{ row }">{{ fmt(row.lastRunTime) }}</template></el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click.stop="selectProject(row)">映射</el-button>
            <el-button v-if="can('migration:project:edit')" link type="danger" size="small" @click.stop="delProject(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div v-if="current" class="card">
      <div class="card-head">
        <h3>{{ current.name }} <span class="hint">{{ current.code }}</span></h3>
        <div class="toolbar" style="margin:0">
          <el-tag size="small" effect="plain">{{ current.mode }}</el-tag>
          <el-tag size="small" effect="plain">{{ current.sourceTable }} → {{ current.targetTable }}</el-tag>
          <el-button v-if="can('migration:project:view')" size="small" @click="precheck">预检</el-button>
          <el-button v-if="can('migration:project:exec')" size="small" type="danger" @click="runMigration">执行迁移</el-button>
          <el-switch v-model="replaceTarget" active-text="执行前清空目标表" size="small" />
        </div>
      </div>

      <el-tabs v-model="tab">
        <el-tab-pane label="字段映射" name="mapping">
          <div class="toolbar" style="margin:0 0 8px">
            <el-button v-if="can('migration:project:view')" size="small" @click="recommend">自动推荐映射</el-button>
            <el-button v-if="can('migration:project:edit')" size="small" @click="addMappingRow">新增一行</el-button>
            <el-button v-if="can('migration:project:edit')" size="small" type="primary" @click="saveMappings">保存映射</el-button>
            <span class="hint">转换类型：DIRECT 直接 / EXPR 表达式 / CONST 常量 / DEFAULT 空值默认</span>
          </div>
          <el-table :data="mappings" size="small" empty-text="暂无映射，点击「自动推荐映射」或手工新增">
            <el-table-column label="源字段" width="180">
              <template #default="{ row }"><el-input v-model="row.sourceColumn" size="small" placeholder="source_col" /></template>
            </el-table-column>
            <el-table-column label="目标字段" width="180">
              <template #default="{ row }"><el-input v-model="row.targetColumn" size="small" placeholder="target_col" /></template>
            </el-table-column>
            <el-table-column label="转换类型" width="130">
              <template #default="{ row }">
                <el-select v-model="row.transformType" size="small">
                  <el-option label="DIRECT" value="DIRECT" /><el-option label="EXPR" value="EXPR" />
                  <el-option label="CONST" value="CONST" /><el-option label="DEFAULT" value="DEFAULT" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="转换表达式 / 默认值" min-width="240">
              <template #default="{ row }">
                <el-input v-if="row.transformType === 'EXPR'" v-model="row.transformExpr" size="small" placeholder="CONCAT('CK', customer_id)" />
                <el-input v-else-if="row.transformType === 'CONST' || row.transformType === 'DEFAULT'" v-model="row.defaultValue" size="small" placeholder="常量/默认值" />
                <span v-else class="hint">—</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="70">
              <template #default="{ $index }">
                <el-button link type="danger" size="small" @click="mappings.splice($index, 1)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="预检结果" name="precheck">
          <div v-if="!pre.ok" class="hint" style="padding:12px 0">点击右上角「预检」查看映射/目标表/类型/样例数据检查结果。</div>
          <template v-else>
            <el-alert :type="pre.ready ? 'success' : 'error'" :title="pre.ready ? '预检通过，可以执行' : '预检未通过，请先修正'" show-icon :closable="false" style="margin-bottom:10px" />
            <div class="grid grid-2">
              <div>
                <div class="kv"><span class="k">目标表已存在</span><span class="v">{{ pre.targetExists ? '是' : '否（可按建议 DDL 建表）' }}</span></div>
                <div class="kv"><span class="k">映射条数</span><span class="v">{{ pre.mappingCount }}</span></div>
                <div v-for="(e, i) in pre.errors" :key="'e' + i" style="color:#f56c6c">✗ {{ e }}</div>
                <div v-for="(w, i) in pre.warnings" :key="'w' + i" style="color:#e6a23c">! {{ w }}</div>
                <div v-if="pre.suggestDdl" style="margin-top:8px">
                  <div class="hint">建议建表语句：</div>
                  <el-input :model-value="pre.suggestDdl" type="textarea" :rows="4" readonly />
                </div>
              </div>
              <div>
                <div class="hint" style="margin-bottom:6px">转换后样例（最多 3 行）</div>
                <el-table :data="pre.sample || []" size="small" max-height="220">
                  <el-table-column v-for="c in sampleColumns" :key="c" :prop="c" :label="c" show-overflow-tooltip />
                </el-table>
              </div>
            </div>
          </template>
        </el-tab-pane>

        <el-tab-pane label="执行与对账" name="runs">
          <el-table :data="runs" size="small" empty-text="尚未执行迁移">
            <el-table-column prop="id" label="批次" width="80" />
            <el-table-column label="开始时间" width="170"><template #default="{ row }">{{ fmt(row.startTime) }}</template></el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }"><el-tag size="small" :type="row.status === 'DONE' ? 'success' : row.status === 'FAILED' ? 'danger' : 'warning'">{{ row.status }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="sourceRows" label="源行数" width="100" />
            <el-table-column prop="insertedRows" label="写入行数" width="100" />
            <el-table-column prop="targetRows" label="目标行数" width="100" />
            <el-table-column prop="message" label="说明" min-width="180" show-overflow-tooltip />
            <el-table-column label="操作" width="110">
              <template #default="{ row }">
                <el-button v-if="can('migration:project:reconcile')" link type="primary" size="small" @click="reconcile(row)">对账</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="diffs.length" style="margin-top:12px">
            <div class="card-head"><h3>对账差异明细</h3><span class="hint">批次 #{{ diffRunId }}</span></div>
            <el-table :data="diffs" size="small">
              <el-table-column prop="checkType" label="检查项" width="120" />
              <el-table-column prop="columnName" label="字段" width="140" />
              <el-table-column prop="expectedVal" label="期望" width="140" />
              <el-table-column prop="actualVal" label="实际" width="140" />
              <el-table-column prop="diffVal" label="差异" width="120" />
              <el-table-column label="结果" width="100">
                <template #default="{ row }"><el-tag size="small" :type="row.status === 'PASS' ? 'success' : 'danger'">{{ row.status }}</el-tag></template>
              </el-table-column>
              <el-table-column prop="detail" label="说明" min-width="220" show-overflow-tooltip />
            </el-table>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 新建项目 -->
    <el-dialog v-model="dialog" title="新建数据迁移项目" width="600px">
      <el-form :model="form" label-width="110px" size="small">
        <el-form-item label="项目编码"><el-input v-model="form.code" placeholder="MIG-CUST-DIM" /></el-form-item>
        <el-form-item label="项目名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="源表">
          <el-select v-model="form.sourceTable" filterable style="width:100%">
            <el-option v-for="t in tableOptions" :key="t.name" :label="t.name + ' · ' + (t.cnName || '')" :value="t.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标表">
          <el-select v-model="form.targetTable" filterable allow-create default-first-option style="width:100%">
            <el-option v-for="t in tableOptions" :key="t.name" :label="t.name" :value="t.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="迁移模式">
          <el-radio-group v-model="form.mode">
            <el-radio label="FULL">全量</el-radio><el-radio label="INCREMENTAL">增量（按水位字段）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="水位字段"><el-input v-model="form.watermarkCol" placeholder="增量模式必填，如 update_time" /></el-form-item>
        <el-form-item label="执行前清空"><el-switch v-model="form.replaceTargetBool" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer><el-button size="small" @click="dialog = false">取消</el-button><el-button size="small" type="primary" @click="createProject">创建</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { migrationApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

const store = useAuthStore()
const can = (p) => store.has(p)

const overview = ref({})
const projects = ref([])
const current = ref(null)
const mappings = ref([])
const runs = ref([])
const diffs = ref([])
const diffRunId = ref(null)
const pre = ref({ ok: false })
const tab = ref('mapping')
const replaceTarget = ref(true)
const query = reactive({ keyword: '', status: '' })
const dialog = ref(false)
const tableOptions = ref([])
const form = reactive({ code: '', name: '', sourceTable: '', targetTable: '', mode: 'FULL', watermarkCol: '', replaceTargetBool: true, description: '' })

const fmt = (v) => (v ? String(v).replace('T', ' ').slice(0, 19) : '-')
const sampleColumns = computed(() => (pre.value.sample && pre.value.sample.length ? Object.keys(pre.value.sample[0]) : []))

async function loadAll() {
  try {
    overview.value = (await migrationApi.overview()) || {}
    await loadProjects()
    if (current.value) await selectProject({ id: current.value.id })
  } catch (e) { /* http 层已提示 */ }
}
async function loadProjects() {
  const r = await migrationApi.projects({ pageSize: 50, keyword: query.keyword || undefined, status: query.status || undefined })
  projects.value = (r && r.records) || []
}
async function selectProject(row) {
  if (!row || !row.id) return
  try {
    const d = await migrationApi.project(row.id)
    current.value = d
    current.value.id = row.id
    mappings.value = (d.mappings || []).map((m) => ({ ...m }))
    runs.value = d.runs || []
    pre.value = { ok: false }
    diffs.value = []
    replaceTarget.value = String(d.replaceTarget ?? 1) === '1' || d.replaceTarget === 1
  } catch (e) { ElMessage.error(e.message || '加载项目失败') }
}
async function recommend() {
  if (!current.value) return
  const r = await migrationApi.recommend(current.value.id)
  mappings.value = (r.candidates || []).filter((c) => c.targetColumn).map((c) => ({
    sourceColumn: c.sourceColumn, sourceType: c.sourceType, targetColumn: c.targetColumn,
    targetType: c.targetType, transformType: c.transformType, transformExpr: c.transformExpr || '', defaultValue: ''
  }))
  ElMessage.success(`已推荐 ${mappings.value.length} 条映射（匹配 ${r.matched} 个字段），可继续手工调整`)
}
function addMappingRow() { mappings.value.push({ sourceColumn: '', targetColumn: '', transformType: 'DIRECT', transformExpr: '', defaultValue: '' }) }
async function saveMappings() {
  if (!current.value) return
  await migrationApi.saveMappings(current.value.id, mappings.value)
  ElMessage.success('映射已保存')
  loadAll()
}
async function precheck() {
  if (!current.value) return
  const r = await migrationApi.precheck(current.value.id)
  pre.value = { ...r, ok: true }
  tab.value = 'precheck'
}
async function runMigration() {
  if (!current.value) return
  try {
    const r = await migrationApi.run(current.value.id, replaceTarget.value)
    ElMessage.success(`迁移完成：源 ${r.sourceRows} 行，写入 ${r.insertedRows} 行`)
    tab.value = 'runs'
    await loadAll()
    await selectProject({ id: current.value.id })
  } catch (e) { ElMessage.error('迁移失败：' + (e.message || '')) }
}
async function reconcile(row) {
  const r = await migrationApi.reconcile(row.id)
  diffs.value = r.diffs || []
  diffRunId.value = row.id
  ElMessage[r.failedCount ? 'warning' : 'success'](`对账完成：${r.checkCount} 项，失败 ${r.failedCount} 项`)
}
async function delProject(row) {
  await ElMessageBox.confirm(`确认删除迁移项目「${row.name}」及其映射/执行记录？`, '提示', { type: 'warning' })
  await migrationApi.deleteProject(row.id)
  ElMessage.success('已删除')
  if (current.value && current.value.id === row.id) current.value = null
  loadAll()
}
async function openProject() {
  if (!tableOptions.value.length) tableOptions.value = ((await migrationApi.tables()) || {}).records || []
  Object.assign(form, { code: '', name: '', sourceTable: '', targetTable: '', mode: 'FULL', watermarkCol: '', replaceTargetBool: true, description: '' })
  dialog.value = true
}
async function createProject() {
  try {
    await migrationApi.createProject({ ...form, replaceTarget: form.replaceTargetBool ? 1 : 0 })
    ElMessage.success('已创建迁移项目')
    dialog.value = false
    loadAll()
  } catch (e) { ElMessage.error(e.message || '创建失败') }
}

onMounted(loadAll)
</script>
