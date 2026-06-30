<script setup>
import { onMounted, reactive, ref } from 'vue'
import { tenantPage, createTenant, updateTenant, deleteTenant } from '../../../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, tenantName: '' })
const dialogVisible = ref(false)
const form = ref({})
const isEdit = ref(false)

async function loadData() {
  loading.value = true
  try {
    const res = await tenantPage(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function openCreate() {
  isEdit.value = false
  form.value = { tenantCode: '', tenantName: '', status: 1 }
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

async function save() {
  if (isEdit.value) {
    await updateTenant(form.value.id, form.value)
  } else {
    await createTenant(form.value)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadData()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除租户「${row.tenantName}」？`, '提示', { type: 'warning' })
  await deleteTenant(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">租户管理</div>
    <div class="search-bar">
      <el-input v-model="query.tenantName" placeholder="租户名称" clearable style="width:200px" />
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button type="success" @click="openCreate">创建租户</el-button>
    </div>
    <el-table :data="tableData" v-loading="loading" border>
      <el-table-column prop="tenantCode" label="租户编号" />
      <el-table-column prop="tenantName" label="租户名称" />
      <el-table-column prop="status" label="状态">
        <template #default="{ row }">{{ row.status === 1 ? '正常' : '冻结' }}</template>
      </el-table-column>
      <el-table-column prop="createBy" label="创建人" />
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" layout="total, prev, pager, next" style="margin-top:16px" @change="loadData" />

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑租户' : '创建租户'" width="480px">
      <el-form label-width="90px">
        <el-form-item label="租户编号"><el-input v-model="form.tenantCode" /></el-form-item>
        <el-form-item label="租户名称"><el-input v-model="form.tenantName" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status"><el-option :value="1" label="正常" /><el-option :value="0" label="冻结" /></el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
