<script setup>
import { onMounted, reactive, ref } from 'vue'
import { devSchedulePage, createDevSchedule, updateDevSchedule, startDevSchedule, stopDevSchedule, deleteDevSchedule } from '../../../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, name: '' })
const dialogVisible = ref(false)
const form = ref({})
const isEdit = ref(false)

async function loadData() {
  loading.value = true
  try {
    const res = await devSchedulePage(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

function openCreate() {
  isEdit.value = false
  form.value = { name: '', triggerType: 'CRON', cronExpr: '0 0 2 * * ?', workflowId: null }
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

async function save() {
  if (isEdit.value) await updateDevSchedule(form.value.id, form.value)
  else await createDevSchedule(form.value)
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadData()
}

async function remove(row) {
  await ElMessageBox.confirm('确认删除？', '提示', { type: 'warning' })
  await deleteDevSchedule(row.id)
  loadData()
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">数据调度</div>
    <div class="search-bar">
      <el-input v-model="query.name" placeholder="任务名称" clearable style="width:200px" />
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button type="success" @click="openCreate">新建调度</el-button>
    </div>
    <el-table :data="tableData" v-loading="loading" border>
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="triggerType" label="触发方式" width="100" />
      <el-table-column prop="cronExpr" label="Cron" />
      <el-table-column prop="status" label="状态" width="90" />
      <el-table-column prop="lastRunTime" label="最近运行" width="180" />
      <el-table-column label="操作" width="260">
        <template #default="{ row }">
          <el-button link type="success" v-if="row.status !== 'RUNNING'" @click="startDevSchedule(row.id).then(loadData)">启动</el-button>
          <el-button link type="warning" v-else @click="stopDevSchedule(row.id).then(loadData)">停止</el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" layout="total, prev, pager, next" style="margin-top:16px" @change="loadData" />
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑调度' : '新建调度'" width="480px">
      <el-form label-width="90px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="触发方式"><el-select v-model="form.triggerType"><el-option value="CRON" /><el-option value="IMMEDIATE" label="立即" /></el-select></el-form-item>
        <el-form-item label="Cron"><el-input v-model="form.cronExpr" /></el-form-item>
        <el-form-item label="工作流ID"><el-input-number v-model="form.workflowId" :min="1" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>
