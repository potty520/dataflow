<script setup>
import { onMounted, reactive, ref } from 'vue'
import { apiPage, createApi, updateApi, publishApi, offlineApi, deleteApi } from '../../../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const statusMap = { 0: '草稿', 1: '已发布', 2: '已下线' }
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, apiName: '' })
const dialogVisible = ref(false)
const form = ref({})
const isEdit = ref(false)

async function loadData() {
  loading.value = true
  try {
    const res = await apiPage(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function openCreate() {
  isEdit.value = false
  form.value = { apiName: '', requestMethod: 'GET', updateFrequency: 'REALTIME', description: '', createType: 'WIZARD', sqlContent: '' }
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

async function save() {
  if (isEdit.value) await updateApi(form.value.id, form.value)
  else await createApi(form.value)
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadData()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除API「${row.apiName}」？`, '提示', { type: 'warning' })
  await deleteApi(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">API管理</div>
    <div class="search-bar">
      <el-input v-model="query.apiName" placeholder="API名称" clearable style="width:200px" />
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button type="success" @click="openCreate">创建API</el-button>
    </div>
    <el-table :data="tableData" v-loading="loading" border>
      <el-table-column prop="apiName" label="API名称" />
      <el-table-column prop="requestMethod" label="请求方法" width="90" />
      <el-table-column prop="createType" label="创建方式" width="100" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }"><el-tag size="small">{{ statusMap[row.status] }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" width="180" />
      <el-table-column label="操作" width="280">
        <template #default="{ row }">
          <el-button link type="success" v-if="row.status !== 1" @click="publishApi(row.id).then(loadData)">发布</el-button>
          <el-button link type="warning" v-if="row.status === 1" @click="offlineApi(row.id).then(loadData)">下线</el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" :disabled="row.status === 1" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" layout="total, prev, pager, next" style="margin-top:16px" @change="loadData" />

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑API' : '创建API'" width="560px">
      <el-form label-width="90px">
        <el-form-item label="API名称"><el-input v-model="form.apiName" /></el-form-item>
        <el-form-item label="请求方法"><el-select v-model="form.requestMethod"><el-option value="GET" /><el-option value="POST" /></el-select></el-form-item>
        <el-form-item label="创建方式"><el-select v-model="form.createType"><el-option value="WIZARD" label="向导" /><el-option value="SQL" label="脚本" /><el-option value="REST" label="反向代理" /></el-select></el-form-item>
        <el-form-item label="更新频率"><el-input v-model="form.updateFrequency" /></el-form-item>
        <el-form-item label="SQL/配置"><el-input v-model="form.sqlContent" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
