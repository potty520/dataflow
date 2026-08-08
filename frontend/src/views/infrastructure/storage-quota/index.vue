<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createStorageQuota, deleteStorageQuota, storageQuotaPage, updateStorageQuota } from '../../../api'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const dialogVisible = ref(false)
const editingId = ref(null)
const query = reactive({ page: 1, size: 10, databaseName: '', status: '' })
const form = ref({})

function resetForm() {
  form.value = {
    datasourceId: null,
    databaseName: '',
    maxSizeGb: 100,
    currentUsageGb: 0,
    alertThresholdPct: 80,
    status: 'NORMAL'
  }
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
    const res = await storageQuotaPage(query)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

async function save() {
  if (editingId.value) {
    await updateStorageQuota(editingId.value, form.value)
  } else {
    await createStorageQuota(form.value)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadData()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除数据库配额「${row.databaseName}」？`, '删除确认', { type: 'warning' })
  await deleteStorageQuota(row.id)
  ElMessage.success('删除成功')
  loadData()
}

function usagePercent(row) {
  if (!row.maxSizeGb) return 0
  return Math.round((row.currentUsageGb / row.maxSizeGb) * 100)
}

function statusType(status) {
  return status === 'WARNING' ? 'warning' : status === 'EXCEEDED' ? 'danger' : 'success'
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
        <p class="page-kicker">基础支撑 / 容量治理</p>
        <h1 class="page-title">存储配额</h1>
        <p class="page-subtitle">跟踪数据库容量、使用率和告警阈值，提前暴露超限风险。</p>
      </div>
      <div class="page-actions">
        <el-button :loading="loading" @click="loadData">
          <el-icon><Refresh /></el-icon>
          <span>刷新</span>
        </el-button>
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>
          <span>新增配额</span>
        </el-button>
      </div>
    </div>

    <div class="search-bar">
      <el-input v-model="query.databaseName" placeholder="数据库名称" clearable style="width:200px" />
      <el-select v-model="query.status" placeholder="状态" clearable style="width:140px">
        <el-option label="正常" value="NORMAL" />
        <el-option label="预警" value="WARNING" />
        <el-option label="超限" value="EXCEEDED" />
      </el-select>
      <el-button type="primary" @click="query.page = 1; loadData()">查询</el-button>
    </div>

    <el-table :data="tableData" v-loading="loading" border>
      <el-table-column prop="databaseName" label="数据库" min-width="160" />
      <el-table-column prop="maxSizeGb" label="配额上限(GB)" width="130" />
      <el-table-column prop="currentUsageGb" label="当前使用(GB)" width="130" />
      <el-table-column label="使用率" min-width="220">
        <template #default="{ row }">
          <el-progress :percentage="usagePercent(row)" :status="usagePercent(row) >= (row.alertThresholdPct || 80) ? 'exception' : ''" />
        </template>
      </el-table-column>
      <el-table-column prop="alertThresholdPct" label="阈值(%)" width="100" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ row.status || 'NORMAL' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
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

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑存储配额' : '新增存储配额'" width="520px">
      <el-form label-width="110px">
        <el-form-item label="数据库名称"><el-input v-model="form.databaseName" /></el-form-item>
        <el-form-item label="数据源ID"><el-input-number v-model="form.datasourceId" :min="0" :controls="false" /></el-form-item>
        <el-form-item label="配额上限(GB)"><el-input-number v-model="form.maxSizeGb" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="当前使用(GB)"><el-input-number v-model="form.currentUsageGb" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="告警阈值(%)"><el-input-number v-model="form.alertThresholdPct" :min="1" :max="100" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width:100%">
            <el-option label="正常" value="NORMAL" />
            <el-option label="预警" value="WARNING" />
            <el-option label="超限" value="EXCEEDED" />
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
