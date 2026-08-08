<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  batchDeleteHost,
  batchHostStatus,
  createInfraHost,
  deleteInfraHost,
  infraHostPage,
  infraSupportOverview,
  offlineInfraHost,
  onlineInfraHost,
  updateInfraHost
} from '../../../api'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const selectedRows = ref([])
const stats = ref({})
const dialogVisible = ref(false)
const editingId = ref(null)
const query = reactive({ page: 1, size: 10, hostname: '', status: '' })
const form = ref({})

const statusMeta = {
  ONLINE: { label: '在线', type: 'success' },
  OFFLINE: { label: '离线', type: 'info' },
  WARNING: { label: '告警', type: 'warning' },
  FAILED: { label: '异常', type: 'danger' }
}

function resetForm() {
  form.value = { hostname: '', ip: '', cpuCores: 8, memoryGb: 32, diskGb: 500, status: 'ONLINE' }
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
    const [pageRes, overviewRes] = await Promise.all([
      infraHostPage(query),
      infraSupportOverview()
    ])
    tableData.value = pageRes.data.records || []
    total.value = pageRes.data.total || 0
    stats.value = overviewRes.data || {}
  } finally {
    loading.value = false
  }
}

async function save() {
  if (editingId.value) {
    await updateInfraHost(editingId.value, form.value)
  } else {
    await createInfraHost(form.value)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadData()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认移除主机「${row.hostname}」？`, '移除确认', { type: 'warning' })
  await deleteInfraHost(row.id)
  ElMessage.success('主机已移除')
  loadData()
}

async function changeStatus(row) {
  if (row.status === 'ONLINE') {
    await offlineInfraHost(row.id)
    ElMessage.success('主机已下线')
  } else {
    await onlineInfraHost(row.id)
    ElMessage.success('主机已上线')
  }
  loadData()
}

function handleSelectionChange(rows) {
  selectedRows.value = rows
}

async function batchStatus(status) {
  if (!selectedRows.value.length) {
    ElMessage.warning('请先选择主机')
    return
  }
  await batchHostStatus({ ids: selectedRows.value.map(row => row.id), status })
  ElMessage.success(status === 'ONLINE' ? '批量上线成功' : '批量下线成功')
  loadData()
}

async function batchRemove() {
  if (!selectedRows.value.length) {
    ElMessage.warning('请先选择主机')
    return
  }
  await ElMessageBox.confirm(`确认移除选中的 ${selectedRows.value.length} 台主机？`, '批量移除', { type: 'warning' })
  await batchDeleteHost({ ids: selectedRows.value.map(row => row.id) })
  ElMessage.success('批量移除成功')
  loadData()
}

function tagType(status) {
  return statusMeta[status]?.type || 'info'
}

function tagLabel(status) {
  return statusMeta[status]?.label || status || '未知'
}

onMounted(() => {
  resetForm()
  loadData()
})
</script>

<template>
  <div class="page-container">
    <div class="page-top">
      <div>
        <p class="page-kicker">基础支撑 / 资源纳管</p>
        <h1 class="page-title">主机管理</h1>
        <p class="page-subtitle">维护集群节点信息，执行主机上线、下线和移除操作。</p>
      </div>
      <div class="page-actions">
        <el-button @click="loadData" :loading="loading">
          <el-icon><Refresh /></el-icon>
          <span>刷新</span>
        </el-button>
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>
          <span>添加主机</span>
        </el-button>
      </div>
    </div>

    <div class="metric-grid">
      <div class="metric-card">
        <div class="metric-label">主机总数</div>
        <div class="metric-value">{{ stats.hostCount || 0 }}</div>
        <div class="metric-note">已纳入平台管理</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">在线主机</div>
        <div class="metric-value">{{ stats.onlineHosts || 0 }}</div>
        <div class="metric-note">可承载集群服务</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">CPU 总核数</div>
        <div class="metric-value">{{ stats.resourceSummary?.cpuTotalCores || 0 }}</div>
        <div class="metric-note">当前纳管资源</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">内存总量</div>
        <div class="metric-value">{{ stats.resourceSummary?.memoryTotalGb || 0 }} GB</div>
        <div class="metric-note">主机资源汇总</div>
      </div>
    </div>

    <div class="search-bar">
      <el-input v-model="query.hostname" placeholder="主机名或关键词" clearable style="width:220px" />
      <el-select v-model="query.status" placeholder="状态" clearable style="width:140px">
        <el-option label="在线" value="ONLINE" />
        <el-option label="离线" value="OFFLINE" />
        <el-option label="异常" value="FAILED" />
      </el-select>
      <el-button type="primary" @click="query.page = 1; loadData()">查询</el-button>
      <el-button @click="batchStatus('ONLINE')">批量上线</el-button>
      <el-button @click="batchStatus('OFFLINE')">批量下线</el-button>
      <el-button type="danger" plain @click="batchRemove">批量移除</el-button>
    </div>

    <el-table
      :data="tableData"
      v-loading="loading"
      border
      row-key="id"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="48" />
      <el-table-column prop="hostname" label="主机名" min-width="160" />
      <el-table-column prop="ip" label="IP 地址" min-width="150" />
      <el-table-column prop="cpuCores" label="CPU(核)" width="100" />
      <el-table-column prop="memoryGb" label="内存(GB)" width="110" />
      <el-table-column prop="diskGb" label="磁盘(GB)" width="110" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="tagType(row.status)" size="small">{{ tagLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="纳管时间" width="180" />
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link :type="row.status === 'ONLINE' ? 'warning' : 'success'" @click="changeStatus(row)">
            {{ row.status === 'ONLINE' ? '下线' : '上线' }}
          </el-button>
          <el-button link type="danger" @click="remove(row)">移除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑主机' : '添加主机'" width="520px">
      <el-form label-width="90px">
        <el-form-item label="主机名"><el-input v-model="form.hostname" /></el-form-item>
        <el-form-item label="IP 地址"><el-input v-model="form.ip" /></el-form-item>
        <el-form-item label="CPU 核数"><el-input-number v-model="form.cpuCores" :min="1" /></el-form-item>
        <el-form-item label="内存 GB"><el-input-number v-model="form.memoryGb" :min="1" /></el-form-item>
        <el-form-item label="磁盘 GB"><el-input-number v-model="form.diskGb" :min="1" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width:100%">
            <el-option label="在线" value="ONLINE" />
            <el-option label="离线" value="OFFLINE" />
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
