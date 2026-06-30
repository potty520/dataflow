<script setup>
import { onMounted, reactive, ref } from 'vue'
import { dataflowPage, createDataflow, updateDataflow, deleteDataflow, executeDataflow, datasourceList } from '../../../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const statusMap = { NOT_STARTED: '未开始', RUNNING: '运行中', SUCCESS: '成功', FAILED: '失败', WAITING: '等待执行' }
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const sources = ref([])
const query = reactive({ page: 1, size: 10, name: '', status: '' })
const dialogVisible = ref(false)
const form = ref({})
const isEdit = ref(false)

async function loadData() {
  loading.value = true
  try {
    const res = await dataflowPage(query)
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
  form.value = { name: '', sourceId: null, targetId: null, writeMode: 'insert', faultTolerance: 0, preSql: '', fieldMapping: '{}' }
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

async function save() {
  if (isEdit.value) await updateDataflow(form.value.id, form.value)
  else await createDataflow(form.value)
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadData()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除数据流「${row.name}」？`, '提示', { type: 'warning' })
  await deleteDataflow(row.id)
  ElMessage.success('删除成功')
  loadData()
}

async function execute(row) {
  await executeDataflow(row.id)
  ElMessage.success('执行成功')
  loadData()
}

onMounted(() => { loadSources(); loadData() })
</script>

<template>
  <div class="page-container">
    <div class="page-header">数据流管理</div>
    <div class="search-bar">
      <el-input v-model="query.name" placeholder="数据流名称" clearable style="width:180px" />
      <el-select v-model="query.status" placeholder="运行状态" clearable style="width:140px">
        <el-option v-for="(label, key) in statusMap" :key="key" :label="label" :value="key" />
      </el-select>
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button type="success" @click="openCreate">新建数据流</el-button>
    </div>
    <el-table :data="tableData" v-loading="loading" border>
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="sourceName" label="数据源" />
      <el-table-column prop="targetName" label="目标源" />
      <el-table-column prop="status" label="状态">
        <template #default="{ row }">{{ statusMap[row.status] || row.status }}</template>
      </el-table-column>
      <el-table-column prop="readCount" label="读取条数" width="100" />
      <el-table-column prop="writeCount" label="写入条数" width="100" />
      <el-table-column prop="lastRunTime" label="最近运行" width="180" />
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button link type="primary" @click="execute(row)">执行</el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" layout="total, prev, pager, next" style="margin-top:16px" @change="loadData" />

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑数据流' : '新建数据流'" width="560px">
      <el-form label-width="90px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="数据源">
          <el-select v-model="form.sourceId" style="width:100%"><el-option v-for="s in sources" :key="s.id" :label="s.name" :value="s.id" /></el-select>
        </el-form-item>
        <el-form-item label="目标源">
          <el-select v-model="form.targetId" style="width:100%"><el-option v-for="s in sources" :key="s.id" :label="s.name" :value="s.id" /></el-select>
        </el-form-item>
        <el-form-item label="写入模式"><el-input v-model="form.writeMode" /></el-form-item>
        <el-form-item label="容错"><el-input-number v-model="form.faultTolerance" :min="0" /></el-form-item>
        <el-form-item label="前置SQL"><el-input v-model="form.preSql" type="textarea" /></el-form-item>
        <el-form-item label="字段映射"><el-input v-model="form.fieldMapping" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
