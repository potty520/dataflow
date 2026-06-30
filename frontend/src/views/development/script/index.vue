<script setup>
import { onMounted, reactive, ref } from 'vue'
import { devScriptPage, createDevScript, updateDevScript, runDevScript, deleteDevScript } from '../../../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const types = ['SQL', 'Hive', 'Shell', 'Python', 'Spark']
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, name: '', scriptType: '' })
const dialogVisible = ref(false)
const form = ref({})
const isEdit = ref(false)

async function loadData() {
  loading.value = true
  try {
    const res = await devScriptPage(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

function openCreate() {
  isEdit.value = false
  form.value = { name: '', scriptType: 'SQL', content: '' }
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

async function save() {
  if (isEdit.value) await updateDevScript(form.value.id, form.value)
  else await createDevScript(form.value)
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadData()
}

async function remove(row) {
  await ElMessageBox.confirm('确认删除？', '提示', { type: 'warning' })
  await deleteDevScript(row.id)
  loadData()
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">脚本开发</div>
    <div class="search-bar">
      <el-input v-model="query.name" placeholder="脚本名称" clearable style="width:180px" />
      <el-select v-model="query.scriptType" placeholder="类型" clearable style="width:120px"><el-option v-for="t in types" :key="t" :value="t" /></el-select>
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button type="success" @click="openCreate">新建脚本</el-button>
    </div>
    <el-table :data="tableData" v-loading="loading" border>
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="scriptType" label="类型" width="90" />
      <el-table-column prop="status" label="状态" width="90" />
      <el-table-column prop="lastRunTime" label="最近运行" width="180" />
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button link type="success" @click="runDevScript(row.id).then(loadData)">运行</el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" layout="total, prev, pager, next" style="margin-top:16px" @change="loadData" />
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑脚本' : '新建脚本'" width="640px">
      <el-form label-width="80px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="类型"><el-select v-model="form.scriptType" style="width:100%"><el-option v-for="t in types" :key="t" :value="t" /></el-select></el-form-item>
        <el-form-item label="内容"><el-input v-model="form.content" type="textarea" :rows="12" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>
