<script setup>
import { onMounted, reactive, ref } from 'vue'
import { devUdfPage, createDevUdf, updateDevUdf, deleteDevUdf } from '../../../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const types = ['HIVE', 'SPARK']
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, udfName: '', udfType: '' })
const dialogVisible = ref(false)
const form = ref({})
const isEdit = ref(false)

async function loadData() {
  loading.value = true
  try {
    const res = await devUdfPage(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

function openCreate() {
  isEdit.value = false
  form.value = { udfName: '', udfType: 'HIVE', owner: '', className: '', jarPath: '', createSql: '', description: '', status: 0 }
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

async function save() {
  if (isEdit.value) await updateDevUdf(form.value.id, form.value)
  else await createDevUdf(form.value)
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadData()
}

async function remove(row) {
  await ElMessageBox.confirm('确认删除？', '提示', { type: 'warning' })
  await deleteDevUdf(row.id)
  loadData()
}

function statusLabel(s) { return { 0: '草稿', 1: '启用', 2: '停用' }[s] || s }

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">资源库 / UDF管理</div>
    <div class="search-bar">
      <el-input v-model="query.udfName" placeholder="UDF名称" clearable style="width:180px" />
      <el-select v-model="query.udfType" placeholder="类型" clearable style="width:120px"><el-option v-for="t in types" :key="t" :label="t" :value="t" /></el-select>
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button type="success" @click="openCreate">注册UDF</el-button>
    </div>
    <el-table :data="tableData" v-loading="loading" border>
      <el-table-column prop="udfName" label="函数名" />
      <el-table-column prop="udfType" label="类型" width="80" />
      <el-table-column prop="owner" label="负责人" width="100" />
      <el-table-column prop="className" label="类名" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">{{ statusLabel(row.status) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" layout="total, prev, pager, next" style="margin-top:16px" @change="loadData" />

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑UDF' : '注册UDF'" width="580px">
      <el-form label-width="90px">
        <el-form-item label="函数名"><el-input v-model="form.udfName" /></el-form-item>
        <el-form-item label="类型"><el-select v-model="form.udfType" style="width:100%"><el-option v-for="t in types" :key="t" :label="t" :value="t" /></el-select></el-form-item>
        <el-form-item label="负责人"><el-input v-model="form.owner" /></el-form-item>
        <el-form-item label="类名"><el-input v-model="form.className" /></el-form-item>
        <el-form-item label="JAR路径"><el-input v-model="form.jarPath" placeholder="/user/jars/xxx.jar" /></el-form-item>
        <el-form-item label="建函数SQL"><el-input v-model="form.createSql" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-container { padding: 16px; }
.page-header { font-size: 18px; font-weight: 600; margin-bottom: 16px; }
.search-bar { display: flex; gap: 8px; align-items: center; margin-bottom: 16px; flex-wrap: wrap; }
</style>
