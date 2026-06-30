<script setup>
import { onMounted, reactive, ref } from 'vue'
import { svcRateLimitPage, createSvcRateLimit, deleteSvcRateLimit } from '../../../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10 })
const dialogVisible = ref(false)
const form = ref({ apiName: '', apiId: null, timeWindowSec: 60, maxRequests: 100, enabled: 1 })

async function loadData() {
  loading.value = true
  try {
    const res = await svcRateLimitPage(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

function openCreate() {
  form.value = { apiName: '', apiId: null, timeWindowSec: 60, maxRequests: 100, enabled: 1 }
  dialogVisible.value = true
}

async function save() {
  await createSvcRateLimit(form.value)
  ElMessage.success('创建成功')
  dialogVisible.value = false
  loadData()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除流控策略？`, '提示', { type: 'warning' })
  await deleteSvcRateLimit(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">流控策略</div>
    <div class="search-bar">
      <el-button type="primary" @click="loadData">刷新</el-button>
      <el-button type="success" @click="openCreate">创建策略</el-button>
    </div>
    <el-table :data="tableData" v-loading="loading" border>
      <el-table-column prop="apiName" label="API名称" />
      <el-table-column prop="timeWindowSec" label="时间窗口(秒)" width="120" />
      <el-table-column prop="maxRequests" label="最大请求数" width="120" />
      <el-table-column prop="enabled" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">{{ row.enabled === 1 ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" layout="total, prev, pager, next" style="margin-top:16px" @change="loadData" />

    <el-dialog v-model="dialogVisible" title="创建流控策略" width="480px">
      <el-form label-width="110px">
        <el-form-item label="API名称"><el-input v-model="form.apiName" /></el-form-item>
        <el-form-item label="时间窗口(秒)"><el-input-number v-model="form.timeWindowSec" :min="1" :max="86400" /></el-form-item>
        <el-form-item label="最大请求数"><el-input-number v-model="form.maxRequests" :min="1" :max="100000" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>
