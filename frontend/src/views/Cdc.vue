<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>CDC / 整库同步</h2>
        <div class="sub">全量搬迁 + 增量字段水位同步；支持源/目标数据源、自动建表与位点续传</div>
      </div>
      <el-button type="primary" size="small" @click="openTask()">新建同步任务</el-button>
    </div>

    <el-table :data="tasks" size="small" v-loading="loading">
      <el-table-column prop="code" label="编码" width="170"><template #default="{ row }"><span class="mono">{{ row.code }}</span></template></el-table-column>
      <el-table-column prop="name" label="名称" width="160" />
      <el-table-column label="源 → 目标" min-width="260">
        <template #default="{ row }">
          <span class="mono">{{ row.sourceTable }}</span>
          <span class="muted"> → </span>
          <span class="mono">{{ row.targetTable }}</span>
          <span class="hint">（DS {{ row.sourceDsId }} → {{ row.targetDsId }}）</span>
        </template>
      </el-table-column>
      <el-table-column prop="syncMode" label="模式" width="80" />
      <el-table-column prop="incrColumn" label="增量字段" width="130" />
      <el-table-column prop="watermark" label="当前水位" width="180" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="90"><template #default="{ row }"><el-tag size="small" :type="row.status === 'ENABLED' ? 'success' : 'info'">{{ row.status }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="success" size="small" :loading="running === row.id" @click="run(row)">运行</el-button>
          <el-button link type="primary" size="small" @click="showRuns(row)">记录</el-button>
          <el-button link type="primary" size="small" @click="openTask(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="removeTask(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="taskDialog" :title="taskForm.id ? '编辑同步任务' : '新建同步任务'" width="620px">
      <el-form label-width="110px" size="small">
        <el-form-item label="编码"><el-input v-model="taskForm.code" :disabled="!!taskForm.id" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="taskForm.name" /></el-form-item>
        <el-form-item label="源数据源 ID"><el-input-number v-model="taskForm.sourceDsId" :min="0" /><span class="hint">0 = 平台内置库</span></el-form-item>
        <el-form-item label="源表"><el-input v-model="taskForm.sourceTable" placeholder="ods_customer" /></el-form-item>
        <el-form-item label="目标数据源 ID"><el-input-number v-model="taskForm.targetDsId" :min="0" /><span class="hint">0 = 平台内置库</span></el-form-item>
        <el-form-item label="目标表"><el-input v-model="taskForm.targetTable" placeholder="cdc_customer_copy" /></el-form-item>
        <el-form-item label="同步模式"><el-radio-group v-model="taskForm.syncMode"><el-radio label="FULL">全量</el-radio><el-radio label="INCR">增量</el-radio></el-radio-group></el-form-item>
        <el-form-item label="增量字段"><el-input v-model="taskForm.incrColumn" placeholder="update_time / id" :disabled="taskForm.syncMode !== 'INCR'" /></el-form-item>
        <el-form-item label="批次大小"><el-input-number v-model="taskForm.batchSize" :min="1" :max="100000" /></el-form-item>
        <el-form-item label="自动建表"><el-switch v-model="taskForm.autoCreate" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="冲突策略"><el-select v-model="taskForm.conflictStrategy" style="width:100%"><el-option label="全量覆盖" value="OVERWRITE" /><el-option label="仅追加" value="APPEND" /></el-select></el-form-item>
        <el-form-item label="状态"><el-radio-group v-model="taskForm.status"><el-radio label="ENABLED">启用</el-radio><el-radio label="PAUSED">暂停</el-radio></el-radio-group></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="taskDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="saveTask">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="runsDrawer" :title="'同步记录 · ' + (currentTask.name || '')" size="60%">
      <el-table :data="runs" size="small">
        <el-table-column prop="startTime" label="开始" width="160"><template #default="{ row }">{{ fmt(row.startTime) }}</template></el-table-column>
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="rowsRead" label="读取" width="90" align="right" />
        <el-table-column prop="rowsWritten" label="写入" width="90" align="right" />
        <el-table-column prop="watermarkAfter" label="水位" width="180" show-overflow-tooltip />
        <el-table-column prop="message" label="说明" min-width="220" show-overflow-tooltip />
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cdcApi } from '@/api'

const loading = ref(false)
const tasks = ref([])
const runs = ref([])
const running = ref(null)
const taskDialog = ref(false)
const taskForm = reactive({ id: null, code: '', name: '', sourceDsId: 0, sourceTable: '', targetDsId: 0, targetTable: '', syncMode: 'FULL', incrColumn: '', batchSize: 1000, autoCreate: 1, conflictStrategy: 'OVERWRITE', status: 'ENABLED' })
const runsDrawer = ref(false)
const currentTask = ref({})

const fmt = (v) => (v ? String(v).replace('T', ' ').slice(0, 19) : '-')
const loadTasks = async () => {
  loading.value = true
  try { tasks.value = (await cdcApi.tasks()).records || [] } catch (e) { tasks.value = [] } finally { loading.value = false }
}
const openTask = (row) => {
  Object.assign(taskForm, { id: null, code: '', name: '', sourceDsId: 0, sourceTable: '', targetDsId: 0, targetTable: '', syncMode: 'FULL', incrColumn: '', batchSize: 1000, autoCreate: 1, conflictStrategy: 'OVERWRITE', status: 'ENABLED' })
  if (row) Object.assign(taskForm, { id: row.id, code: row.code, name: row.name, sourceDsId: row.sourceDsId, sourceTable: row.sourceTable, targetDsId: row.targetDsId, targetTable: row.targetTable, syncMode: row.syncMode, incrColumn: row.incrColumn, batchSize: row.batchSize, autoCreate: row.autoCreate, conflictStrategy: row.conflictStrategy, status: row.status })
  taskDialog.value = true
}
const saveTask = async () => {
  try { await cdcApi.saveTask({ ...taskForm }); ElMessage.success('任务已保存'); taskDialog.value = false; loadTasks() }
  catch (e) { /* http 层已提示 */ }
}
const removeTask = async (row) => {
  try { await ElMessageBox.confirm('删除同步任务 ' + row.code + '？', '提示', { type: 'warning' }); await cdcApi.deleteTask(row.id); loadTasks() }
  catch (e) { /* 取消 */ }
}
const run = async (row) => {
  running.value = row.id
  try {
    const d = await cdcApi.run(row.id)
    ElMessage.success('同步完成：读取 ' + d.rowsRead + '，写入 ' + d.rowsWritten)
    loadTasks()
  } catch (e) { /* http 层已提示 */ } finally { running.value = null }
}
const showRuns = async (row) => {
  currentTask.value = row
  try { runs.value = (await cdcApi.runs({ taskId: row.id })).records || [] } catch (e) { runs.value = [] }
  runsDrawer.value = true
}

onMounted(loadTasks)
</script>

<style scoped>
.page { padding: 4px; }
.hint { color: #909399; font-size: 12px; margin-left: 8px; }
</style>
