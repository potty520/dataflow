<script setup>
import { onMounted, reactive, ref } from 'vue'
import { infraClusterPage, createInfraCluster, deleteInfraCluster, infraDashboard, infraHostPage, createInfraHost, deleteInfraHost } from '../../../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const stats = ref({})
const clusters = ref([])
const clusterTotal = ref(0)
const hosts = ref([])
const hostTotal = ref(0)
const clusterQuery = reactive({ page: 1, size: 10 })
const hostQuery = reactive({ page: 1, size: 10 })
const clusterDialog = ref(false)
const hostDialog = ref(false)
const clusterForm = ref({ name: '', description: '', hostCount: 0 })
const hostForm = ref({ hostname: '', ip: '', cpuCores: 8, memoryGb: 32, diskGb: 500 })

async function loadAll() {
  const [d, c, h] = await Promise.all([infraDashboard(), infraClusterPage(clusterQuery), infraHostPage(hostQuery)])
  stats.value = d.data || {}
  clusters.value = c.data.records
  clusterTotal.value = c.data.total
  hosts.value = h.data.records
  hostTotal.value = h.data.total
}

async function saveCluster() {
  await createInfraCluster(clusterForm.value)
  ElMessage.success('创建成功')
  clusterDialog.value = false
  loadAll()
}

async function saveHost() {
  await createInfraHost(hostForm.value)
  ElMessage.success('添加成功')
  hostDialog.value = false
  loadAll()
}

async function removeCluster(row) {
  await ElMessageBox.confirm('确认删除？', '提示', { type: 'warning' })
  await deleteInfraCluster(row.id)
  loadAll()
}

async function removeHost(row) {
  await ElMessageBox.confirm('确认删除？', '提示', { type: 'warning' })
  await deleteInfraHost(row.id)
  loadAll()
}

onMounted(loadAll)
</script>

<template>
  <div class="page-container">
    <div class="page-header">集群管理</div>
    <div class="stat-cards">
      <div class="stat-card"><div class="label">主机总数</div><div class="value">{{ stats.hostCount || 0 }}</div></div>
      <div class="stat-card"><div class="label">在线主机</div><div class="value">{{ stats.onlineHosts || 0 }}</div></div>
      <div class="stat-card"><div class="label">运行组件</div><div class="value">{{ stats.runningComponents || 0 }}</div></div>
      <div class="stat-card"><div class="label">集群数</div><div class="value">{{ stats.clusterCount || 0 }}</div></div>
    </div>
    <el-row :gutter="16">
      <el-col :span="12">
        <el-card header="集群列表">
          <el-button type="success" size="small" @click="clusterDialog = true" style="margin-bottom:12px">新建集群</el-button>
          <el-table :data="clusters" border size="small">
            <el-table-column prop="name" label="名称" />
            <el-table-column prop="hostCount" label="节点数" width="80" />
            <el-table-column prop="status" label="状态" width="90" />
            <el-table-column label="操作" width="80"><template #default="{ row }"><el-button link type="danger" @click="removeCluster(row)">删除</el-button></template></el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card header="主机管理">
          <el-button type="success" size="small" @click="hostDialog = true" style="margin-bottom:12px">添加主机</el-button>
          <el-table :data="hosts" border size="small">
            <el-table-column prop="hostname" label="主机名" />
            <el-table-column prop="ip" label="IP" />
            <el-table-column prop="status" label="状态" width="80" />
            <el-table-column label="操作" width="80"><template #default="{ row }"><el-button link type="danger" @click="removeHost(row)">删除</el-button></template></el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
    <el-dialog v-model="clusterDialog" title="新建集群" width="420px">
      <el-form label-width="80px">
        <el-form-item label="名称"><el-input v-model="clusterForm.name" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="clusterForm.description" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="clusterDialog = false">取消</el-button><el-button type="primary" @click="saveCluster">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="hostDialog" title="添加主机" width="420px">
      <el-form label-width="80px">
        <el-form-item label="主机名"><el-input v-model="hostForm.hostname" /></el-form-item>
        <el-form-item label="IP"><el-input v-model="hostForm.ip" /></el-form-item>
        <el-form-item label="CPU核"><el-input-number v-model="hostForm.cpuCores" :min="1" /></el-form-item>
        <el-form-item label="内存GB"><el-input-number v-model="hostForm.memoryGb" :min="1" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="hostDialog = false">取消</el-button><el-button type="primary" @click="saveHost">保存</el-button></template>
    </el-dialog>
  </div>
</template>
