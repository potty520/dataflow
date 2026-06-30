<script setup>
import { onMounted, reactive, ref } from 'vue'
import { rolePage, createRole, updateRole, deleteRole } from '../../../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, roleName: '' })
const dialogVisible = ref(false)
const form = ref({})
const isEdit = ref(false)

const dataScopeOptions = [
  { value: 1, label: '全部' },
  { value: 2, label: '自定义' },
  { value: 3, label: '本级及子级' },
  { value: 4, label: '本级' }
]

async function loadData() {
  loading.value = true
  try {
    const res = await rolePage(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function openCreate() {
  isEdit.value = false
  form.value = { roleName: '', roleKey: '', description: '', dataScope: 1 }
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

async function save() {
  if (isEdit.value) await updateRole(form.value.id, form.value)
  else await createRole(form.value)
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadData()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除角色「${row.roleName}」？`, '提示', { type: 'warning' })
  await deleteRole(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">角色管理</div>
    <div class="search-bar">
      <el-input v-model="query.roleName" placeholder="角色名称" clearable style="width:200px" />
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button type="success" @click="openCreate">创建角色</el-button>
    </div>
    <el-table :data="tableData" v-loading="loading" border>
      <el-table-column prop="roleName" label="角色名称" />
      <el-table-column prop="roleKey" label="角色标识" />
      <el-table-column prop="description" label="描述" />
      <el-table-column prop="dataScope" label="数据权限">
        <template #default="{ row }">{{ dataScopeOptions.find(o => o.value === row.dataScope)?.label }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" layout="total, prev, pager, next" style="margin-top:16px" @change="loadData" />

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑角色' : '创建角色'" width="480px">
      <el-form label-width="90px">
        <el-form-item label="角色名称"><el-input v-model="form.roleName" /></el-form-item>
        <el-form-item label="角色标识"><el-input v-model="form.roleKey" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" /></el-form-item>
        <el-form-item label="数据权限">
          <el-select v-model="form.dataScope" style="width:100%">
            <el-option v-for="o in dataScopeOptions" :key="o.value" :label="o.label" :value="o.value" />
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
