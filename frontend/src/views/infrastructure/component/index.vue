<script setup>
import { onMounted, reactive, ref } from 'vue'
import { infraComponentPage, createInfraComponent, startInfraComponent, stopInfraComponent, deleteInfraComponent, infraHostPage } from '../../../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const types = ['HDFS', 'YARN', 'Kafka', 'Zookeeper', 'Flink', 'Hive']
const loading = ref(false)
const tableData = ref([])
const hosts = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, componentType: '' })
const dialogVisible = ref(false)
const form = ref({ componentType: 'HDFS', hostId: null, version: '' })

async function loadData() {
  loading.value = true
  try {
    const res = await infraComponentPage(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

async function loadHosts() {
  const res = await infraHostPage({ page: 1, size: 100 })
  hosts.value = res.data.records
}

async function save() {
  await createInfraComponent(form.value)
  ElMessage.success('创建成功')
  dialogVisible.value = false
  loadData()
}

async function remove(row) {
  await ElMessageBox.confirm('确认删除？', '提示', { type: 'warning' })
  await deleteInfraComponent(row.id)
  loadData()
}

onMounted(() => { loadHosts(); loadData() })
</script>

<template>
  <div class="page-container">
    <div class="page-header">大数据组件管理</div>
    <div class="search-bar">
      <el-select v-model="query.componentType" placeholder="组件类型" clearable style="width:140px"><el-option v-for="t in types" :key="t" :value="t" /></el-select>
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button type="success" @click="dialogVisible = true">部署组件</el-button>
    </div>
    <el-table :data="tableData" v-loading="loading" border>
      <el-table-column prop="componentType" label="组件类型" />
      <el-table-column prop="version" label="版本" />
      <el-table-column prop="hostId" label="主机ID" width="90" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }"><el-tag :type="row.status === 'RUNNING' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button link type="success" v-if="row.status !== 'RUNNING'" @click="startInfraComponent(row.id).then(loadData)">启动</el-button>
          <el-button link type="warning" v-else @click="stopInfraComponent(row.id).then(loadData)">停止</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" layout="total, prev, pager, next" style="margin-top:16px" @change="loadData" />
    <el-dialog v-model="dialogVisible" title="部署组件" width="420px">
      <el-form label-width="90px">
        <el-form-item label="组件类型"><el-select v-model="form.componentType" style="width:100%"><el-option v-for="t in types" :key="t" :value="t" /></el-select></el-form-item>
        <el-form-item label="主机"><el-select v-model="form.hostId" style="width:100%"><el-option v-for="h in hosts" :key="h.id" :label="h.hostname" :value="h.id" /></el-select></el-form-item>
        <el-form-item label="版本"><el-input v-model="form.version" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" @click="save">部署</el-button></template>
    </el-dialog>
  </div>
</template>
