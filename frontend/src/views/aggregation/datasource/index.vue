<script setup>
import { onMounted, reactive, ref } from 'vue'
import { datasourcePage, createDatasource, updateDatasource, deleteDatasource } from '../../../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const dbTypes = ['MySQL', 'Oracle', 'PostgreSQL', 'SQLServer', 'Kafka', 'HDFS', 'MongoDB']
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, name: '', dbType: '' })
const dialogVisible = ref(false)
const form = ref({})
const isEdit = ref(false)

async function loadData() {
  loading.value = true
  try {
    const res = await datasourcePage(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function openCreate() {
  isEdit.value = false
  form.value = { name: '', dbType: 'MySQL', host: '127.0.0.1', port: 3306, databaseName: '', username: '', password: '' }
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

async function save() {
  if (isEdit.value) await updateDatasource(form.value.id, form.value)
  else await createDatasource(form.value)
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadData()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除数据源「${row.name}」？`, '提示', { type: 'warning' })
  await deleteDatasource(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">数据源管理</div>
    <div class="search-bar">
      <el-input v-model="query.name" placeholder="数据源名称" clearable style="width:180px" />
      <el-select v-model="query.dbType" placeholder="数据库类型" clearable style="width:140px">
        <el-option v-for="t in dbTypes" :key="t" :label="t" :value="t" />
      </el-select>
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button type="success" @click="openCreate">新增数据源</el-button>
    </div>
    <el-table :data="tableData" v-loading="loading" border>
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="dbType" label="类型" width="120" />
      <el-table-column prop="host" label="主机" />
      <el-table-column prop="port" label="端口" width="80" />
      <el-table-column prop="databaseName" label="数据库" />
      <el-table-column prop="locked" label="状态" width="90">
        <template #default="{ row }"><el-tag :type="row.locked ? 'warning' : 'success'" size="small">{{ row.locked ? '已锁定' : '可用' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button link type="primary" :disabled="row.locked === 1" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" :disabled="row.locked === 1" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" layout="total, prev, pager, next" style="margin-top:16px" @change="loadData" />

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑数据源' : '新增数据源'" width="520px">
      <el-form label-width="90px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="类型"><el-select v-model="form.dbType" style="width:100%"><el-option v-for="t in dbTypes" :key="t" :label="t" :value="t" /></el-select></el-form-item>
        <el-form-item label="主机"><el-input v-model="form.host" /></el-form-item>
        <el-form-item label="端口"><el-input-number v-model="form.port" :min="1" /></el-form-item>
        <el-form-item label="数据库"><el-input v-model="form.databaseName" /></el-form-item>
        <el-form-item label="用户名"><el-input v-model="form.username" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" type="password" show-password /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
