<script setup>
import { onMounted, reactive, ref } from 'vue'
import { svcUnitPage, createSvcUnit, publishSvcUnit, offlineSvcUnit, deleteSvcUnit } from '../../../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const statusMap = { 0: '草稿', 1: '已发布', 2: '已下线' }
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, unitName: '' })
const dialogVisible = ref(false)
const form = ref({})

async function loadData() {
  loading.value = true
  try {
    const res = await svcUnitPage(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

function openCreate() {
  form.value = { unitName: '', description: '' }
  dialogVisible.value = true
}

async function save() {
  await createSvcUnit(form.value)
  ElMessage.success('创建成功')
  dialogVisible.value = false
  loadData()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除服务单元「${row.unitName}」？`, '提示', { type: 'warning' })
  await deleteSvcUnit(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">服务单元管理</div>
    <div class="search-bar">
      <el-input v-model="query.unitName" placeholder="服务单元名称" clearable style="width:200px" />
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button type="success" @click="openCreate">创建单元</el-button>
    </div>
    <el-table :data="tableData" v-loading="loading" border>
      <el-table-column prop="unitName" label="单元名称" />
      <el-table-column prop="description" label="描述" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'info' : 'warning'" size="small">{{ statusMap[row.status] }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column prop="updateTime" label="更新时间" width="180" />
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button link type="success" v-if="row.status !== 1" @click="publishSvcUnit(row.id).then(loadData)">发布</el-button>
          <el-button link type="warning" v-if="row.status === 1" @click="offlineSvcUnit(row.id).then(loadData)">下线</el-button>
          <el-button link type="danger" :disabled="row.status === 1" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" layout="total, prev, pager, next" style="margin-top:16px" @change="loadData" />

    <el-dialog v-model="dialogVisible" title="创建服务单元" width="480px">
      <el-form label-width="100px">
        <el-form-item label="单元名称"><el-input v-model="form.unitName" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>
