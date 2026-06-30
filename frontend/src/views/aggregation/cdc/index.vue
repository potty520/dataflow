<script setup>
import { onMounted, reactive, ref } from 'vue'
import { cdcPage, createCdc, updateCdc, startCdc, stopCdc, deleteCdc, datasourceList } from '../../../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const sources = ref([])
const query = reactive({ page: 1, size: 10, name: '' })
const dialogVisible = ref(false)
const form = ref({})
const isEdit = ref(false)

async function loadData() {
  loading.value = true
  try {
    const res = await cdcPage(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function loadSources() {
  const res = await datasourceList()
  sources.value = res.data
}

function openCreate() {
  isEdit.value = false
  form.value = { name: '', sourceId: null, targetId: null, operationMode: 'INSERT', fieldMapping: '{}', remark: '' }
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

async function save() {
  if (isEdit.value) await updateCdc(form.value.id, form.value)
  else await createCdc(form.value)
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadData()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除CDC任务「${row.name}」？`, '提示', { type: 'warning' })
  await deleteCdc(row.id)
  ElMessage.success('删除成功')
  loadData()
}

async function start(row) {
  await ElMessageBox.confirm('确认启动该CDC任务？', '提示')
  await startCdc(row.id)
  ElMessage.success('已启动')
  loadData()
}

async function stop(row) {
  await ElMessageBox.confirm('确认停止该CDC任务？', '提示')
  await stopCdc(row.id)
  ElMessage.success('已停止')
  loadData()
}

onMounted(() => { loadSources(); loadData() })
</script>

<template>
  <div class="page-container">
    <div class="page-header">CDC实时同步</div>
    <div class="search-bar">
      <el-input v-model="query.name" placeholder="任务名称" clearable style="width:200px" />
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button type="success" @click="openCreate">新建CDC任务</el-button>
    </div>
    <el-table :data="tableData" v-loading="loading" border>
      <el-table-column prop="name" label="任务名称" />
      <el-table-column prop="operationMode" label="操作模式" width="100" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }"><el-tag :type="row.status === 'RUNNING' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="startTime" label="启动时间" width="180" />
      <el-table-column prop="updateTime" label="更新时间" width="180" />
      <el-table-column label="操作" width="260">
        <template #default="{ row }">
          <el-button link type="success" v-if="row.status !== 'RUNNING'" @click="start(row)">启动</el-button>
          <el-button link type="warning" v-else @click="stop(row)">停止</el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" layout="total, prev, pager, next" style="margin-top:16px" @change="loadData" />

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑CDC任务' : '新建CDC任务'" width="520px">
      <el-form label-width="90px">
        <el-form-item label="任务名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="数据源"><el-select v-model="form.sourceId" style="width:100%"><el-option v-for="s in sources" :key="s.id" :label="s.name" :value="s.id" /></el-select></el-form-item>
        <el-form-item label="目标源"><el-select v-model="form.targetId" style="width:100%"><el-option v-for="s in sources" :key="s.id" :label="s.name" :value="s.id" /></el-select></el-form-item>
        <el-form-item label="操作模式"><el-input v-model="form.operationMode" /></el-form-item>
        <el-form-item label="字段映射"><el-input v-model="form.fieldMapping" type="textarea" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
