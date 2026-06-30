<script setup>
import { onMounted, ref } from 'vue'
import { menuTree, createMenu, updateMenu, deleteMenu } from '../../../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const tableData = ref([])
const dialogVisible = ref(false)
const form = ref({})
const isEdit = ref(false)

async function loadData() {
  loading.value = true
  try {
    const res = await menuTree()
    tableData.value = res.data
  } finally {
    loading.value = false
  }
}

function openCreate(parentId = 0) {
  isEdit.value = false
  form.value = { parentId, menuCode: '', menuName: '', path: '', component: '', menuType: 2, serviceName: 'portal', sortOrder: 0 }
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

async function save() {
  if (isEdit.value) await updateMenu(form.value.id, form.value)
  else await createMenu(form.value)
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadData()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除菜单「${row.menuName}」？`, '提示', { type: 'warning' })
  await deleteMenu(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">菜单管理</div>
    <div class="search-bar">
      <el-button type="success" @click="openCreate(0)">创建菜单</el-button>
    </div>
    <el-table :data="tableData" v-loading="loading" row-key="id" border default-expand-all :tree-props="{ children: 'children' }">
      <el-table-column prop="menuName" label="菜单名称" />
      <el-table-column prop="menuCode" label="编码" />
      <el-table-column prop="path" label="路径" />
      <el-table-column prop="menuType" label="类型">
        <template #default="{ row }">{{ {1:'目录',2:'菜单',3:'按钮'}[row.menuType] }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑菜单' : '创建菜单'" width="520px">
      <el-form label-width="90px">
        <el-form-item label="菜单编码"><el-input v-model="form.menuCode" /></el-form-item>
        <el-form-item label="菜单名称"><el-input v-model="form.menuName" /></el-form-item>
        <el-form-item label="路径"><el-input v-model="form.path" /></el-form-item>
        <el-form-item label="组件"><el-input v-model="form.component" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.menuType"><el-option :value="1" label="目录" /><el-option :value="2" label="菜单" /><el-option :value="3" label="按钮" /></el-select>
        </el-form-item>
        <el-form-item label="所属服务"><el-input v-model="form.serviceName" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
