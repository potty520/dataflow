<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ackInfraAlert,
  createInfraAlert,
  deleteInfraAlert,
  infraAlertPage,
  recoverInfraAlert,
  updateInfraAlert
} from '../../../api'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const dialogVisible = ref(false)
const editingId = ref(null)
const query = reactive({ page: 1, size: 10, alertLevel: '', status: '', targetName: '' })
const form = ref({})

const levelMeta = {
  INFO: { label: '提示', type: 'info' },
  WARN: { label: '警告', type: 'warning' },
  CRITICAL: { label: '严重', type: 'danger' }
}

const statusMeta = {
  ACTIVE: { label: '待处理', type: 'danger' },
  ACKED: { label: '已确认', type: 'warning' },
  RECOVERED: { label: '已恢复', type: 'success' },
  PAUSED: { label: '已暂停', type: 'info' }
}

function resetForm() {
  form.value = {
    alertName: '',
    alertLevel: 'WARN',
    alertType: 'CAPACITY',
    targetType: 'CLUSTER',
    targetName: '',
    ruleExpr: '',
    message: '',
    handleSuggestion: '',
    status: 'ACTIVE'
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
    const res = await infraAlertPage(query)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

async function save() {
  if (editingId.value) {
    await updateInfraAlert(editingId.value, form.value)
  } else {
    await createInfraAlert(form.value)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadData()
}

async function ack(row) {
  await ackInfraAlert(row.id)
  ElMessage.success('已确认')
  loadData()
}

async function recover(row) {
  await recoverInfraAlert(row.id)
  ElMessage.success('已恢复')
  loadData()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除告警「${row.alertName}」？`, '删除确认', { type: 'warning' })
  await deleteInfraAlert(row.id)
  ElMessage.success('删除成功')
  loadData()
}

function levelType(value) {
  return levelMeta[value]?.type || 'info'
}

function levelLabel(value) {
  return levelMeta[value]?.label || value || '未知'
}

function statusType(value) {
  return statusMeta[value]?.type || 'info'
}

function statusLabel(value) {
  return statusMeta[value]?.label || value || '未知'
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
        <p class="page-kicker">基础支撑 / 告警治理</p>
        <h1 class="page-title">告警中心</h1>
        <p class="page-subtitle">管理资源、组件和容量告警，支持确认和恢复处理。</p>
      </div>
      <div class="page-actions">
        <el-button :loading="loading" @click="loadData">
          <el-icon><Refresh /></el-icon>
          <span>刷新</span>
        </el-button>
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>
          <span>新增告警</span>
        </el-button>
      </div>
    </div>

    <div class="search-bar">
      <el-input v-model="query.targetName" placeholder="目标名称" clearable style="width:200px" />
      <el-select v-model="query.alertLevel" placeholder="级别" clearable style="width:140px">
        <el-option label="提示" value="INFO" />
        <el-option label="警告" value="WARN" />
        <el-option label="严重" value="CRITICAL" />
      </el-select>
      <el-select v-model="query.status" placeholder="状态" clearable style="width:140px">
        <el-option label="待处理" value="ACTIVE" />
        <el-option label="已确认" value="ACKED" />
        <el-option label="已恢复" value="RECOVERED" />
      </el-select>
      <el-button type="primary" @click="query.page = 1; loadData()">查询</el-button>
    </div>

    <el-table :data="tableData" v-loading="loading" border>
      <el-table-column prop="alertName" label="告警名称" min-width="170" />
      <el-table-column prop="alertLevel" label="级别" width="90">
        <template #default="{ row }">
          <el-tag :type="levelType(row.alertLevel)" size="small">{{ levelLabel(row.alertLevel) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="alertType" label="类型" width="130" />
      <el-table-column prop="targetName" label="目标对象" min-width="150" />
      <el-table-column prop="message" label="告警内容" min-width="240" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="95">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastTriggerTime" label="触发时间" width="180" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="warning" @click="ack(row)">确认</el-button>
          <el-button link type="success" @click="recover(row)">恢复</el-button>
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

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑告警' : '新增告警'" width="620px">
      <el-form label-width="100px">
        <el-form-item label="告警名称"><el-input v-model="form.alertName" /></el-form-item>
        <el-form-item label="级别">
          <el-select v-model="form.alertLevel" style="width:100%">
            <el-option label="提示" value="INFO" />
            <el-option label="警告" value="WARN" />
            <el-option label="严重" value="CRITICAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型"><el-input v-model="form.alertType" /></el-form-item>
        <el-form-item label="目标类型"><el-input v-model="form.targetType" /></el-form-item>
        <el-form-item label="目标名称"><el-input v-model="form.targetName" /></el-form-item>
        <el-form-item label="规则表达式"><el-input v-model="form.ruleExpr" /></el-form-item>
        <el-form-item label="告警内容"><el-input v-model="form.message" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="处理建议"><el-input v-model="form.handleSuggestion" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width:100%">
            <el-option label="待处理" value="ACTIVE" />
            <el-option label="已确认" value="ACKED" />
            <el-option label="已恢复" value="RECOVERED" />
            <el-option label="已暂停" value="PAUSED" />
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
