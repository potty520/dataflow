<script setup>
import { nextTick, onMounted, reactive, ref } from 'vue'
import { devWorkflowPage, createDevWorkflow, updateDevWorkflow, runDevWorkflow, publishDevWorkflow, deleteDevWorkflow } from '../../../api'
import { ElMessage, ElMessageBox } from 'element-plus'
import WorkflowEditor from '../../../components/WorkflowEditor.vue'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, name: '' })
const designerVisible = ref(false)
const form = ref({})
const isEdit = ref(false)
const dagJson = ref('{"nodes":[],"edges":[]}')

async function loadData() {
  loading.value = true
  try {
    const res = await devWorkflowPage(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

function openCreate() {
  isEdit.value = false
  form.value = { name: '', scheduleCron: '' }
  dagJson.value = '{"nodes":[],"edges":[]}'
  designerVisible.value = true
}

function openDesigner(row) {
  isEdit.value = true
  form.value = { ...row }
  dagJson.value = row.dagJson || '{"nodes":[],"edges":[]}'
  designerVisible.value = true
}

async function save() {
  const payload = { ...form.value, dagJson: dagJson.value }
  if (isEdit.value) await updateDevWorkflow(form.value.id, payload)
  else await createDevWorkflow(payload)
  ElMessage.success('保存成功')
  designerVisible.value = false
  loadData()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除工作流「${row.name}」？`, '提示', { type: 'warning' })
  await deleteDevWorkflow(row.id)
  loadData()
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">工作流开发</div>
    <div class="search-bar">
      <el-input v-model="query.name" placeholder="工作流名称" clearable style="width:200px" />
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button type="success" @click="openCreate">可视化创建工作流</el-button>
    </div>
    <el-table :data="tableData" v-loading="loading" border>
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column prop="scheduleCron" label="调度Cron" />
      <el-table-column prop="createBy" label="创建人" width="100" />
      <el-table-column label="操作" width="320">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDesigner(row)">可视化编排</el-button>
          <el-button link type="success" @click="runDevWorkflow(row.id).then(loadData)">运行</el-button>
          <el-button link type="warning" @click="publishDevWorkflow(row.id).then(loadData)">发布</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" layout="total, prev, pager, next" style="margin-top:16px" @change="loadData" />

    <el-drawer v-model="designerVisible" :title="isEdit ? '工作流可视化编排' : '新建工作流'" size="92%" destroy-on-close>
      <el-form :inline="true" style="margin-bottom:12px">
        <el-form-item label="工作流名称"><el-input v-model="form.name" style="width:220px" /></el-form-item>
        <el-form-item label="调度Cron"><el-input v-model="form.scheduleCron" placeholder="0 0 2 * * ?" style="width:180px" /></el-form-item>
      </el-form>
      <WorkflowEditor v-model="dagJson" />
      <div style="margin-top:16px;text-align:right">
        <el-button @click="designerVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存工作流</el-button>
      </div>
    </el-drawer>
  </div>
</template>
