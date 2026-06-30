<script setup>
import { onMounted, reactive, ref } from 'vue'
import { incrementPage, createIncrement, updateIncrement, deleteIncrement } from '../../../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, tableName: '' })
const dialogVisible = ref(false)
const form = ref({})
const isEdit = ref(false)

async function loadData() {
  loading.value = true
  try {
    const res = await incrementPage(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function openCreate() {
  isEdit.value = false
  form.value = { tableName: '', fieldName: '', fieldType: 'VARCHAR', fieldComment: '', incrementValue: '0' }
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

async function save() {
  if (isEdit.value) await updateIncrement(form.value.id, form.value)
  else await createIncrement(form.value)
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadData()
}

async function remove(row) {
  await ElMessageBox.confirm('确认删除该增量字段？', '提示', { type: 'warning' })
  await deleteIncrement(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">增量字段管理</div>
    <div class="search-bar">
      <el-input v-model="query.tableName" placeholder="表名称" clearable style="width:200px" />
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button type="success" @click="openCreate">添加增量字段</el-button>
    </div>
    <el-table :data="tableData" v-loading="loading" border>
      <el-table-column prop="tableName" label="表名称" />
      <el-table-column prop="fieldName" label="字段名称" />
      <el-table-column prop="fieldType" label="字段类型" />
      <el-table-column prop="fieldComment" label="注释" />
      <el-table-column prop="incrementValue" label="当前增量值" />
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" layout="total, prev, pager, next" style="margin-top:16px" @change="loadData" />

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑增量字段' : '添加增量字段'" width="480px">
      <el-form label-width="90px">
        <el-form-item label="表名称"><el-input v-model="form.tableName" /></el-form-item>
        <el-form-item label="字段名称"><el-input v-model="form.fieldName" /></el-form-item>
        <el-form-item label="字段类型"><el-input v-model="form.fieldType" /></el-form-item>
        <el-form-item label="注释"><el-input v-model="form.fieldComment" /></el-form-item>
        <el-form-item label="增量值"><el-input v-model="form.incrementValue" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
