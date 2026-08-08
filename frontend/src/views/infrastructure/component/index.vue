<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  batchComponentStatus,
  createInfraComponent,
  deleteInfraComponent,
  infraComponentPage,
  infraHostPage,
  restartInfraComponent,
  startInfraComponent,
  stopInfraComponent,
  updateInfraComponent
} from '../../../api'

const types = ['HDFS', 'YARN', 'ZooKeeper', 'Kafka', 'Flink', 'Hive', 'Kerberos', 'Ranger', 'Hudi']
const loading = ref(false)
const tableData = ref([])
const hosts = ref([])
const selectedRows = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, componentType: '', status: '' })
const dialogVisible = ref(false)
const editingId = ref(null)
const form = ref({})

const statusMeta = {
  RUNNING: { label: '运行中', type: 'success' },
  STOPPED: { label: '已停止', type: 'info' },
  FAILED: { label: '异常', type: 'danger' },
  STARTING: { label: '启动中', type: 'warning' }
}

function resetForm() {
  form.value = { componentType: 'HDFS', hostId: null, version: '', status: 'RUNNING' }
}

function openCreate() {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  form.value = { ...row }
  dialogVisible.value = true
}

async function loadData() {
  loading.value = true
  try {
    const res = await infraComponentPage(query)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

async function loadHosts() {
  const res = await infraHostPage({ page: 1, size: 200 })
  hosts.value = res.data.records || []
}

function handleSelectionChange(rows) {
  selectedRows.value = rows
}

async function save() {
  if (editingId.value) {
    await updateInfraComponent(editingId.value, form.value)
  } else {
    await createInfraComponent(form.value)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadData()
}

async function changeStatus(row) {
  if (row.status === 'RUNNING') {
    await stopInfraComponent(row.id)
    ElMessage.success('组件已停止')
  } else {
    await startInfraComponent(row.id)
    ElMessage.success('组件已启动')
  }
  loadData()
}

async function restart(row) {
  await restartInfraComponent(row.id)
  ElMessage.success('组件已重启')
  loadData()
}

async function batchStatus(status) {
  if (!selectedRows.value.length) {
    ElMessage.warning('请先选择组件')
    return
  }
  await batchComponentStatus({ ids: selectedRows.value.map(row => row.id), status })
  ElMessage.success(status === 'RUNNING' ? '批量启动成功' : '批量停止成功')
  loadData()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除组件「${row.componentType} ${row.version || ''}」？`, '删除确认', { type: 'warning' })
  await deleteInfraComponent(row.id)
  ElMessage.success('删除成功')
  loadData()
}

function hostName(hostId) {
  const host = hosts.value.find(item => item.id === hostId)
  return host ? `${host.hostname} (${host.ip || '-'})` : hostId || '-'
}

function tagType(status) {
  return statusMeta[status]?.type || 'info'
}

function tagLabel(status) {
  return statusMeta[status]?.label || status || '未知'
}

onMounted(() => {
  resetForm()
  loadHosts()
  loadData()
})
</script>

<template>
  <div class="page-container">
    <div class="page-top">
      <div>
        <p class="page-kicker">基础支撑 / 服务组件</p>
        <h1 class="page-title">组件管理</h1>
        <p class="page-subtitle">部署和控制 HDFS、YARN、Kafka、Flink、Hive 等服务实例。</p>
      </div>
      <div class="page-actions">
        <el-button :loading="loading" @click="loadData">
          <el-icon><Refresh /></el-icon>
          <span>刷新</span>
        </el-button>
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>
          <span>部署组件</span>
        </el-button>
      </div>
    </div>

    <div class="search-bar">
      <el-select v-model="query.componentType" placeholder="组件类型" clearable style="width:150px">
        <el-option v-for="t in types" :key="t" :label="t" :value="t" />
      </el-select>
      <el-select v-model="query.status" placeholder="状态" clearable style="width:140px">
        <el-option label="运行中" value="RUNNING" />
        <el-option label="已停止" value="STOPPED" />
        <el-option label="异常" value="FAILED" />
      </el-select>
      <el-button type="primary" @click="query.page = 1; loadData()">查询</el-button>
      <el-button @click="batchStatus('RUNNING')">批量启动</el-button>
      <el-button @click="batchStatus('STOPPED')">批量停止</el-button>
    </div>

    <el-table
      :data="tableData"
      v-loading="loading"
      border
      row-key="id"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="48" />
      <el-table-column prop="componentType" label="组件类型" width="130" />
      <el-table-column prop="version" label="版本" width="120" />
      <el-table-column prop="hostId" label="部署主机" min-width="190">
        <template #default="{ row }">{{ hostName(row.hostId) }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="tagType(row.status)" size="small">{{ tagLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" width="180" />
      <el-table-column label="操作" width="250" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link :type="row.status === 'RUNNING' ? 'warning' : 'success'" @click="changeStatus(row)">
            {{ row.status === 'RUNNING' ? '停止' : '启动' }}
          </el-button>
          <el-button link type="success" @click="restart(row)">重启</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="query.page"
      v-model:page-size="query.size"
      :total="total"
      layout="total, sizes, prev, pager, next"
      style="margin-top:16px"
      @change="loadData"
    />

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑组件' : '部署组件'" width="480px">
      <el-form label-width="90px">
        <el-form-item label="组件类型">
          <el-select v-model="form.componentType" style="width:100%">
            <el-option v-for="t in types" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="主机">
          <el-select v-model="form.hostId" style="width:100%">
            <el-option v-for="h in hosts" :key="h.id" :label="`${h.hostname} (${h.ip || '-'})`" :value="h.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="版本"><el-input v-model="form.version" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width:100%">
            <el-option label="运行中" value="RUNNING" />
            <el-option label="已停止" value="STOPPED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
