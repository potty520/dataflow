<template>
  <div class="page">
    <el-tabs v-model="tab">
      <!-- ================= 远程数据源 ================= -->
      <el-tab-pane label="远程数据源（跨网络）" name="ds">
        <el-card shadow="never">
          <div class="bar">
            <span class="hint">远程 MySQL / PostgreSQL，支持 DIRECT / SOCKS5 / HTTP(CONNECT) 代理隧道</span>
            <div>
              <el-input v-model="dsKeyword" size="small" placeholder="搜索编码/名称/主机" style="width:200px" clearable @keyup.enter="loadDs" />
              <el-button size="small" @click="loadDs">查询</el-button>
              <el-button v-if="can('migration:ds:edit')" size="small" type="primary" @click="openDs()">新建数据源</el-button>
            </div>
          </div>
          <el-table :data="dsList" size="small" v-loading="dsLoading">
            <el-table-column prop="code" label="编码" width="150" />
            <el-table-column prop="name" label="名称" min-width="120" />
            <el-table-column prop="dbType" label="类型" width="100" />
            <el-table-column label="地址" min-width="170">
              <template #default="{ row }">{{ row.host }}:{{ row.port }}/{{ row.dbName }}</template>
            </el-table-column>
            <el-table-column label="代理" width="190">
              <template #default="{ row }">
                <el-tag v-if="row.proxyType && row.proxyType !== 'DIRECT'" size="small" type="warning">
                  {{ row.proxyType }} {{ row.proxyHost }}:{{ row.proxyPort }}</el-tag>
                <span v-else class="hint">直连</span>
              </template>
            </el-table-column>
            <el-table-column label="最近测试" width="190">
              <template #default="{ row }">
                <el-tag v-if="row.lastTestOk === 1" size="small" type="success">成功</el-tag>
                <el-tag v-else-if="row.lastTestOk === 0" size="small" type="danger">失败</el-tag>
                <span v-else class="hint">未测试</span>
                <span class="hint" v-if="row.lastTestTime"> {{ row.lastTestTime }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220">
              <template #default="{ row }">
                <el-button v-if="can('migration:ds:test')" link type="primary" size="small" @click="testDs(row)">测试连接</el-button>
                <el-button v-if="can('migration:ds:edit')" link type="primary" size="small" @click="openDs(row)">编辑</el-button>
                <el-button v-if="can('migration:ds:edit')" link type="danger" size="small" @click="delDs(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- ================= FTP 文件迁移 ================= -->
      <el-tab-pane label="FTP 文件迁移" name="ftp">
        <el-card shadow="never">
          <div class="bar">
            <span class="hint">表 → CSV → FTP（可经代理）→ 远端；或 FTP → CSV → 目标表，用于跨网摆渡</span>
            <div>
              <el-button v-if="can('migration:file:edit')" size="small" type="primary" @click="openTask()">新建文件任务</el-button>
            </div>
          </div>
          <el-table :data="taskList" size="small" v-loading="taskLoading">
            <el-table-column prop="code" label="编码" width="150" />
            <el-table-column prop="name" label="名称" min-width="130" />
            <el-table-column label="方向" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.direction === 'EXPORT' ? 'success' : 'warning'">
                  {{ row.direction === 'EXPORT' ? '导出' : '导入' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="表" min-width="190">
              <template #default="{ row }">
                <span v-if="row.direction === 'EXPORT'">{{ row.sourceTable || '(项目源表)' }}</span>
                <span v-else>{{ row.targetTable || '(项目目标表)' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="FTP" min-width="200">
              <template #default="{ row }">
                {{ row.remoteHost }}:{{ row.remotePort }} / {{ row.remoteDir }}{{ row.remoteFile }}
                <el-tag v-if="row.proxyType && row.proxyType !== 'DIRECT'" size="small" type="warning">代理</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="runCount" label="执行次数" width="90" />
            <el-table-column label="操作" width="240">
              <template #default="{ row }">
                <el-button v-if="can('migration:file:exec')" link type="primary" size="small" @click="runTask(row)">执行</el-button>
                <el-button link type="primary" size="small" @click="showRuns(row)">记录</el-button>
                <el-button v-if="can('migration:file:edit')" link type="primary" size="small" @click="openTask(row)">编辑</el-button>
                <el-button v-if="can('migration:file:edit')" link type="danger" size="small" @click="delTask(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 远程数据源对话框 -->
    <el-dialog v-model="dsDialog" :title="dsForm.id ? '编辑远程数据源' : '新建远程数据源'" width="640px">
      <el-form label-width="110px" size="small">
        <el-form-item label="编码"><el-input v-model="dsForm.code" :disabled="!!dsForm.id" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="dsForm.name" /></el-form-item>
        <el-form-item label="数据库类型">
          <el-select v-model="dsForm.dbType" style="width:160px">
            <el-option label="MySQL" value="MYSQL" /><el-option label="PostgreSQL" value="POSTGRESQL" />
          </el-select>
        </el-form-item>
        <el-form-item label="主机"><el-input v-model="dsForm.host" placeholder="远端库 IP/域名" /></el-form-item>
        <el-form-item label="端口"><el-input-number v-model="dsForm.port" :min="1" :max="65535" /></el-form-item>
        <el-form-item label="库名"><el-input v-model="dsForm.dbName" /></el-form-item>
        <el-form-item label="用户名"><el-input v-model="dsForm.username" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="dsForm.password" type="password" show-password placeholder="留空表示不修改" /></el-form-item>
        <el-divider content-position="left">网络代理（跨网访问时填写）</el-divider>
        <el-form-item label="代理类型">
          <el-select v-model="dsForm.proxyType" style="width:200px">
            <el-option label="直连 DIRECT" value="DIRECT" />
            <el-option label="SOCKS5" value="SOCKS5" />
            <el-option label="HTTP CONNECT" value="HTTP" />
          </el-select>
        </el-form-item>
        <template v-if="dsForm.proxyType !== 'DIRECT'">
          <el-form-item label="代理主机"><el-input v-model="dsForm.proxyHost" /></el-form-item>
          <el-form-item label="代理端口"><el-input-number v-model="dsForm.proxyPort" :min="1" :max="65535" /></el-form-item>
          <el-form-item label="代理账号"><el-input v-model="dsForm.proxyUser" /></el-form-item>
          <el-form-item label="代理密码"><el-input v-model="dsForm.proxyPassword" type="password" show-password /></el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button size="small" @click="dsDialog = false">取消</el-button>
        <el-button size="small" :disabled="!can('migration:ds:test')" @click="testFormDs">测试连接</el-button>
        <el-button size="small" type="primary" :disabled="!can('migration:ds:edit')" @click="saveDs">保存</el-button>
      </template>
    </el-dialog>

    <!-- FTP 任务对话框 -->
    <el-dialog v-model="taskDialog" :title="taskForm.id ? '编辑文件迁移任务' : '新建文件迁移任务'" width="660px">
      <el-form label-width="110px" size="small">
        <el-form-item label="编码"><el-input v-model="taskForm.code" :disabled="!!taskForm.id" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="taskForm.name" /></el-form-item>
        <el-form-item label="方向">
          <el-select v-model="taskForm.direction" style="width:160px">
            <el-option label="导出（表→FTP）" value="EXPORT" /><el-option label="导入（FTP→表）" value="IMPORT" />
          </el-select>
        </el-form-item>
        <el-form-item label="迁移项目">
          <el-select v-model="taskForm.projectId" clearable placeholder="可选：使用项目映射/源表" style="width:100%">
            <el-option v-for="p in projects" :key="p.id" :label="`${p.code} ${p.name}`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="taskForm.direction === 'EXPORT'" label="源表">
          <el-input v-model="taskForm.sourceTable" placeholder="不填则用项目源表" />
        </el-form-item>
        <el-form-item v-else label="目标表"><el-input v-model="taskForm.targetTable" /></el-form-item>
        <el-divider content-position="left">FTP</el-divider>
        <el-form-item label="主机"><el-input v-model="taskForm.remoteHost" /></el-form-item>
        <el-form-item label="端口"><el-input-number v-model="taskForm.remotePort" :min="1" :max="65535" /></el-form-item>
        <el-form-item label="账号"><el-input v-model="taskForm.remoteUser" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="taskForm.remotePassword" type="password" show-password placeholder="留空表示不修改" /></el-form-item>
        <el-form-item label="远端目录"><el-input v-model="taskForm.remoteDir" /></el-form-item>
        <el-form-item label="文件名"><el-input v-model="taskForm.remoteFile" placeholder="如 ods_order.csv" /></el-form-item>
        <el-form-item label="压缩"><el-switch v-model="taskForm.compressBool" active-text="gzip" /></el-form-item>
        <el-divider content-position="left">FTP 代理（可选）</el-divider>
        <el-form-item label="代理类型">
          <el-select v-model="taskForm.proxyType" style="width:200px">
            <el-option label="直连 DIRECT" value="DIRECT" />
            <el-option label="SOCKS5" value="SOCKS5" />
            <el-option label="HTTP CONNECT" value="HTTP" />
          </el-select>
        </el-form-item>
        <template v-if="taskForm.proxyType !== 'DIRECT'">
          <el-form-item label="代理主机"><el-input v-model="taskForm.proxyHost" /></el-form-item>
          <el-form-item label="代理端口"><el-input-number v-model="taskForm.proxyPort" :min="1" :max="65535" /></el-form-item>
          <el-form-item label="代理账号"><el-input v-model="taskForm.proxyUser" /></el-form-item>
          <el-form-item label="代理密码"><el-input v-model="taskForm.proxyPassword" type="password" show-password /></el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button size="small" @click="taskDialog = false">取消</el-button>
        <el-button size="small" @click="testFormFtp">测试 FTP</el-button>
        <el-button size="small" type="primary" @click="saveTask">保存</el-button>
      </template>
    </el-dialog>

    <!-- 执行记录 -->
    <el-dialog v-model="runsDialog" title="执行记录" width="720px">
      <el-table :data="runList" size="small">
        <el-table-column prop="id" label="#" width="60" />
        <el-table-column prop="direction" label="方向" width="90" />
        <el-table-column prop="status" label="状态" width="90" />
        <el-table-column prop="rowsCount" label="行数" width="90" />
        <el-table-column prop="bytesCount" label="字节" width="110" />
        <el-table-column prop="checksum" label="SHA-256" min-width="140" show-overflow-tooltip />
        <el-table-column prop="message" label="说明" min-width="150" show-overflow-tooltip />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { migrationApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const can = (p) => auth.has(p)
const tab = ref('ds')

const dsLoading = ref(false)
const dsKeyword = ref('')
const dsList = ref([])
const dsDialog = ref(false)
const dsForm = reactive({ id: null, code: '', name: '', dbType: 'MYSQL', host: '', port: 3306, dbName: '',
  username: '', password: '', proxyType: 'DIRECT', proxyHost: '', proxyPort: 1080, proxyUser: '', proxyPassword: '' })

const taskLoading = ref(false)
const taskList = ref([])
const taskDialog = ref(false)
const projects = ref([])
const taskForm = reactive({ id: null, code: '', name: '', direction: 'EXPORT', projectId: null,
  sourceTable: '', targetTable: '', remoteHost: '', remotePort: 21, remoteUser: '', remotePassword: '',
  remoteDir: '/', remoteFile: '', compressBool: false,
  proxyType: 'DIRECT', proxyHost: '', proxyPort: 1080, proxyUser: '', proxyPassword: '' })

const runsDialog = ref(false)
const runList = ref([])

async function loadDs() {
  dsLoading.value = true
  try {
    const r = await migrationApi.remoteDsList({ keyword: dsKeyword.value })
    dsList.value = r.records || []
  } finally { dsLoading.value = false }
}
async function loadTasks() {
  taskLoading.value = true
  try {
    const r = await migrationApi.fileTasks({})
    taskList.value = r.records || []
  } finally { taskLoading.value = false }
}
async function loadProjects() {
  const r = await migrationApi.projects({ pageSize: 100 })
  projects.value = r.records || []
}

function openDs(row) {
  Object.assign(dsForm, { id: null, code: '', name: '', dbType: 'MYSQL', host: '', port: 3306, dbName: '',
    username: '', password: '', proxyType: 'DIRECT', proxyHost: '', proxyPort: 1080, proxyUser: '', proxyPassword: '' })
  if (row) Object.assign(dsForm, { ...row, password: '', proxyPassword: '' })
  dsDialog.value = true
}
async function saveDs() {
  const body = { ...dsForm }
  delete body.id
  if (dsForm.id) await migrationApi.updateRemoteDs(dsForm.id, body)
  else await migrationApi.createRemoteDs(body)
  ElMessage.success('已保存')
  dsDialog.value = false
  loadDs()
}
async function delDs(row) {
  await ElMessageBox.confirm(`删除远程数据源 ${row.code}？`, '提示', { type: 'warning' })
  await migrationApi.deleteRemoteDs(row.id)
  ElMessage.success('已删除')
  loadDs()
}
async function testDs(row) {
  try {
    const r = await migrationApi.testRemoteDs(row.id)
    ElMessage.success(`连接成功 ${r.latencyMs}ms，版本 ${r.version}`)
  } catch (e) { ElMessage.error(e.message || '连接失败') }
  loadDs()
}
async function testFormDs() {
  try {
    const r = await migrationApi.testRemoteDsAdhoc({ ...dsForm })
    ElMessage.success(`连接成功 ${r.latencyMs}ms，版本 ${r.version}`)
  } catch (e) { ElMessage.error(e.message || '连接失败') }
}

function openTask(row) {
  Object.assign(taskForm, { id: null, code: '', name: '', direction: 'EXPORT', projectId: null,
    sourceTable: '', targetTable: '', remoteHost: '', remotePort: 21, remoteUser: '', remotePassword: '',
    remoteDir: '/', remoteFile: '', compressBool: false,
    proxyType: 'DIRECT', proxyHost: '', proxyPort: 1080, proxyUser: '', proxyPassword: '' })
  if (row) Object.assign(taskForm, { ...row, remotePassword: '', proxyPassword: '', compressBool: row.compress === 1 })
  taskDialog.value = true
  loadProjects()
}
async function saveTask() {
  const body = { ...taskForm, compress: taskForm.compressBool ? 1 : 0 }
  delete body.id
  delete body.compressBool
  if (taskForm.id) await migrationApi.updateFileTask(taskForm.id, body)
  else await migrationApi.createFileTask(body)
  ElMessage.success('已保存')
  taskDialog.value = false
  loadTasks()
}
async function delTask(row) {
  await ElMessageBox.confirm(`删除文件任务 ${row.code}？`, '提示', { type: 'warning' })
  await migrationApi.deleteFileTask(row.id)
  ElMessage.success('已删除')
  loadTasks()
}
async function runTask(row) {
  try {
    const r = await migrationApi.runFileTask(row.id)
    ElMessage.success(`${r.direction} 完成：${r.rows} 行 / ${r.bytes} 字节，SHA-256 ${String(r.checksum).slice(0, 12)}…`)
    loadTasks()
  } catch (e) { ElMessage.error(e.message || '执行失败') }
}
async function showRuns(row) {
  const r = await migrationApi.fileTaskRuns(row.id)
  runList.value = r.records || []
  runsDialog.value = true
}
async function testFormFtp() {
  try {
    const r = await migrationApi.testFtp({ ...taskForm, remotePassword: taskForm.remotePassword })
    ElMessage.success(`FTP 连接成功 ${r.latencyMs}ms，目录 ${r.workingDir}，文件 ${r.fileCount} 个`)
  } catch (e) { ElMessage.error(e.message || 'FTP 连接失败') }
}

onMounted(() => { loadDs(); loadTasks() })
</script>

<style scoped>
.page { padding: 4px; }
.bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; gap: 10px; }
.hint { color: #909399; font-size: 12px; }
</style>
