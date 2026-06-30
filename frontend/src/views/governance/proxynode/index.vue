<script setup>
import { onMounted, reactive, ref } from 'vue'
import { govProxyPage, createGovProxy, startGovProxy, stopGovProxy, deleteGovProxy } from '../../../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, name: '' })
const dialogVisible = ref(false)
const form = ref({ name: '', host: '', port: 8080, remark: '' })

async function loadData() {
  loading.value = true
  try {
    const res = await govProxyPage(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

function openCreate() {
  form.value = { name: '', host: '', port: 8080, remark: '' }
  dialogVisible.value = true
}

async function save() {
  await createGovProxy(form.value)
  ElMessage.success('创建成功')
  dialogVisible.value = false
  loadData()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除代理节点「${row.name}」？`, '提示', { type: 'warning' })
  await deleteGovProxy(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">代理节点管理</div>
    <div class="search-bar">
      <el-input v-model="query.name" placeholder="节点名称" clearable style="width:200px" />
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button type="success" @click="openCreate">创建节点</el-button>
    </div>
    <el-table :data="tableData" v-loading="loading" border>
      <el-table-column prop="name" label="节点名称" />
      <el-table-column prop="host" label="IP地址" />
      <el-table-column prop="port" label="端口" width="80" />
      <el-table-column prop="remark" label="描述" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'RUNNING' ? 'success' : 'info'" size="small">{{ row.status === 'RUNNING' ? '运行中' : '已停止' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" width="180" />
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button link type="success" v-if="row.status !== 'RUNNING'" @click="startGovProxy(row.id).then(loadData)">启动</el-button>
          <el-button link type="warning" v-else @click="stopGovProxy(row.id).then(loadData)">停止</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" layout="total, prev, pager, next" style="margin-top:16px" @change="loadData" />

    <el-dialog v-model="dialogVisible" title="创建代理节点" width="460px">
      <el-form label-width="90px">
        <el-form-item label="节点名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="IP地址"><el-input v-model="form.host" /></el-form-item>
        <el-form-item label="端口"><el-input-number v-model="form.port" :min="1" :max="65535" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>
