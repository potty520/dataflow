<script setup>
import { onMounted, ref } from 'vue'
import { deptTree, createDept, updateDept, deleteDept } from '../../../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const tableData = ref([])
const dialogVisible = ref(false)
const form = ref({})
const isEdit = ref(false)

async function loadData() {
  loading.value = true
  try {
    const res = await deptTree()
    tableData.value = res.data
  } finally {
    loading.value = false
  }
}

function openCreate(parentId = 0) {
  isEdit.value = false
  form.value = { parentId, deptName: '', sortOrder: 0 }
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

async function save() {
  if (isEdit.value) await updateDept(form.value.id, form.value)
  else await createDept(form.value)
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadData()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除部门「${row.deptName}」？`, '提示', { type: 'warning' })
  await deleteDept(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">部门管理</div>
    <div class="search-bar">
      <el-button type="success" @click="openCreate(0)">创建根部门</el-button>
    </div>
    <el-table :data="tableData" v-loading="loading" row-key="id" border default-expand-all :tree-props="{ children: 'children' }">
      <el-table-column prop="deptName" label="部门名称" />
      <el-table-column prop="sortOrder" label="排序" width="100" />
      <el-table-column label="操作" width="240">
        <template #default="{ row }">
          <el-button link type="primary" @click="openCreate(row.id)">新增子部门</el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑部门' : '创建部门'" width="420px">
      <el-form label-width="90px">
        <el-form-item label="部门名称"><el-input v-model="form.deptName" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
