<script setup>
import { onMounted, reactive, ref } from 'vue'
import { resourceMonitor, hostPage, resourceGroupPage, createResourceGroup, updateResourceGroup, deleteResourceGroup,
         storageQuotaPage, createStorageQuota, updateStorageQuota, deleteStorageQuota, infraDashboard } from '../../../api'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'

const tabActive = ref('overview')
const monitorData = ref({})
const hosts = ref([])
const groups = ref([])
const quotas = ref([])
const total = reactive({ groups: 0, quotas: 0 })
const query = reactive({ page: 1, size: 10 })
const groupDialogVisible = ref(false)
const groupForm = ref({})
const groupIsEdit = ref(false)
const quotaDialogVisible = ref(false)
const quotaForm = ref({})
const quotaIsEdit = ref(false)
const cpuChartRef = ref(null)
const memChartRef = ref(null)
const diskChartRef = ref(null)

async function loadMonitor() {
  const res = await resourceMonitor()
  monitorData.value = res.data
  hosts.value = res.data.hosts || []
  setTimeout(() => {
    initGauges()
  }, 100)
}

function initGauges() {
  if (cpuChartRef.value) {
    const chart = echarts.init(cpuChartRef.value)
    chart.setOption({
      series: [{ type: 'gauge', min: 0, max: 100,
        detail: { formatter: '{value}%' },
        data: [{ value: monitorData.value.cpuUsagePercent || 0, name: 'CPU' }] }]
    })
  }
  if (memChartRef.value) {
    const chart = echarts.init(memChartRef.value)
    chart.setOption({
      series: [{ type: 'gauge', min: 0, max: 100,
        detail: { formatter: '{value}%' },
        data: [{ value: monitorData.value.memoryUsagePercent || 0, name: '内存' }] }]
    })
  }
  if (diskChartRef.value) {
    const chart = echarts.init(diskChartRef.value)
    chart.setOption({
      series: [{ type: 'gauge', min: 0, max: 100,
        detail: { formatter: '{value}%' },
        data: [{ value: monitorData.value.diskUsagePercent || 0, name: '磁盘' }] }]
    })
  }
}

async function loadGroups() {
  const res = await resourceGroupPage(query)
  groups.value = res.data.records
  total.groups = res.data.total
}

async function loadQuotas() {
  const res = await storageQuotaPage(query)
  quotas.value = res.data.records
  total.quotas = res.data.total
}

function openGroupCreate() {
  groupIsEdit.value = false
  groupForm.value = { groupName: '', resourceType: 'YARN_QUEUE', resourceKey: '', description: '', maxCores: 0, maxMemoryGb: 0, status: 'ENABLED' }
  groupDialogVisible.value = true
}

function openGroupEdit(row) {
  groupIsEdit.value = true
  groupForm.value = { ...row }
  groupDialogVisible.value = true
}

async function saveGroup() {
  if (groupIsEdit.value) await updateResourceGroup(groupForm.value.id, groupForm.value)
  else await createResourceGroup(groupForm.value)
  ElMessage.success('保存成功')
  groupDialogVisible.value = false
  loadGroups()
}

async function removeGroup(row) {
  await ElMessageBox.confirm(`确认删除资源组「${row.groupName}」？`, '提示', { type: 'warning' })
  await deleteResourceGroup(row.id)
  ElMessage.success('删除成功')
  loadGroups()
}

function openQuotaCreate() {
  quotaIsEdit.value = false
  quotaForm.value = { databaseName: '', maxSizeGb: 100, currentUsageGb: 0, alertThresholdPct: 80, status: 'NORMAL' }
  quotaDialogVisible.value = true
}

function openQuotaEdit(row) {
  quotaIsEdit.value = true
  quotaForm.value = { ...row }
  quotaDialogVisible.value = true
}

async function saveQuota() {
  if (quotaIsEdit.value) await updateStorageQuota(quotaForm.value.id, quotaForm.value)
  else await createStorageQuota(quotaForm.value)
  ElMessage.success('保存成功')
  quotaDialogVisible.value = false
  loadQuotas()
}

async function removeQuota(row) {
  await ElMessageBox.confirm(`确认删除存储配额「${row.databaseName}」？`, '提示', { type: 'warning' })
  await deleteStorageQuota(row.id)
  ElMessage.success('删除成功')
  loadQuotas()
}

function onTabChange(tab) {
  if (tab === 'groups') loadGroups()
  if (tab === 'quotas') loadQuotas()
}

onMounted(loadMonitor)
</script>

<template>
  <div class="page-container">
    <div class="page-header">资源监控</div>

    <el-tabs v-model="tabActive" @tab-change="onTabChange">
      <el-tab-pane label="总览" name="overview">
        <!-- Gauges -->
        <el-row :gutter="16" style="margin-bottom:24px">
          <el-col :span="8">
            <el-card><div ref="cpuChartRef" style="height:200px"></div></el-card>
          </el-col>
          <el-col :span="8">
            <el-card><div ref="memChartRef" style="height:200px"></div></el-card>
          </el-col>
          <el-col :span="8">
            <el-card><div ref="diskChartRef" style="height:200px"></div></el-card>
          </el-col>
        </el-row>

        <!-- Stats cards -->
        <el-row :gutter="16">
          <el-col :span="6">
            <el-card><div style="text-align:center">
              <div style="color:#999;font-size:13px">CPU总核数</div>
              <div style="font-size:28px;font-weight:bold;color:#1890ff">{{ monitorData.cpuTotalCores || 0 }}</div>
              <div style="color:#999">已用 {{ monitorData.cpuUsedCores || 0 }} 核</div>
            </div></el-card>
          </el-col>
          <el-col :span="6">
            <el-card><div style="text-align:center">
              <div style="color:#999;font-size:13px">内存总量</div>
              <div style="font-size:28px;font-weight:bold;color:#52c41a">{{ monitorData.memoryTotalGb || 0 }} GB</div>
              <div style="color:#999">已用 {{ monitorData.memoryUsedGb || 0 }} GB</div>
            </div></el-card>
          </el-col>
          <el-col :span="6">
            <el-card><div style="text-align:center">
              <div style="color:#999;font-size:13px">磁盘总量</div>
              <div style="font-size:28px;font-weight:bold;color:#fa8c16">{{ monitorData.diskTotalGb || 0 }} GB</div>
              <div style="color:#999">已用 {{ monitorData.diskUsedGb || 0 }} GB</div>
            </div></el-card>
          </el-col>
          <el-col :span="6">
            <el-card><div style="text-align:center">
              <div style="color:#999;font-size:13px">存储总量</div>
              <div style="font-size:28px;font-weight:bold;color:#722ed1">{{ monitorData.storageTotalGb || 0 }} GB</div>
              <div style="color:#999">已用 {{ monitorData.storageUsedGb || 0 }} GB</div>
            </div></el-card>
          </el-col>
        </el-row>

        <!-- Per-host disk bars -->
        <el-card style="margin-top:24px">
          <template #header>主机磁盘使用</template>
          <div v-for="h in hosts" :key="h.hostname" style="margin-bottom:16px">
            <div style="display:flex;justify-content:space-between;margin-bottom:4px">
              <span>{{ h.hostname }} ({{ h.ip }})</span>
              <span>{{ h.diskUsed || 0 }} / {{ h.diskGb || 0 }} GB</span>
            </div>
            <el-progress :percentage="h.diskGb ? Math.round(h.diskUsed / h.diskGb * 100) : 0" :status="(h.diskGb && h.diskUsed / h.diskGb > 0.8) ? 'exception' : ''" />
          </div>
        </el-card>
      </el-tab-pane>

      <!-- 资源组管理 -->
      <el-tab-pane label="资源组" name="groups">
        <div style="margin-bottom:12px">
          <el-button type="primary" @click="openGroupCreate">新增资源组</el-button>
        </div>
        <el-table :data="groups" border>
          <el-table-column prop="groupName" label="组名称" />
          <el-table-column prop="resourceType" label="类型" width="130" />
          <el-table-column prop="resourceKey" label="资源Key" width="130" />
          <el-table-column prop="maxCores" label="最大核数" width="100" />
          <el-table-column prop="maxMemoryGb" label="最大内存(GB)" width="120" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button link type="primary" @click="openGroupEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="removeGroup(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 存储配额 -->
      <el-tab-pane label="存储配额" name="quotas">
        <div style="margin-bottom:12px">
          <el-button type="primary" @click="openQuotaCreate">新增配额</el-button>
        </div>
        <el-table :data="quotas" border>
          <el-table-column prop="databaseName" label="数据库" />
          <el-table-column prop="maxSizeGb" label="配额上限(GB)" width="130" />
          <el-table-column prop="currentUsageGb" label="当前使用(GB)" width="130" />
          <el-table-column label="使用率" width="200">
            <template #default="{ row }">
              <el-progress :percentage="row.maxSizeGb ? Math.round(row.currentUsageGb / row.maxSizeGb * 100) : 0"
                           :status="(row.maxSizeGb && row.currentUsageGb / row.maxSizeGb * 100 > (row.alertThresholdPct || 80)) ? 'exception' : ''" />
            </template>
          </el-table-column>
          <el-table-column prop="alertThresholdPct" label="告警阈值(%)" width="120" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'WARNING' ? 'danger' : 'success'">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button link type="primary" @click="openQuotaEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="removeQuota(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 资源组弹窗 -->
    <el-dialog v-model="groupDialogVisible" :title="groupIsEdit ? '编辑资源组' : '新增资源组'" width="500px">
      <el-form label-width="100px">
        <el-form-item label="组名称"><el-input v-model="groupForm.groupName" /></el-form-item>
        <el-form-item label="资源类型">
          <el-select v-model="groupForm.resourceType" style="width:100%">
            <el-option label="YARN队列" value="YARN_QUEUE" />
            <el-option label="K8S命名空间" value="K8S_NAMESPACE" />
          </el-select>
        </el-form-item>
        <el-form-item label="资源Key"><el-input v-model="groupForm.resourceKey" /></el-form-item>
        <el-form-item label="最大核数"><el-input-number v-model="groupForm.maxCores" :min="0" /></el-form-item>
        <el-form-item label="最大内存(GB)"><el-input-number v-model="groupForm.maxMemoryGb" :min="0" :precision="1" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="groupForm.status" style="width:100%">
            <el-option label="启用" value="ENABLED" />
            <el-option label="禁用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="groupForm.description" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="groupDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveGroup">保存</el-button>
      </template>
    </el-dialog>

    <!-- 存储配额弹窗 -->
    <el-dialog v-model="quotaDialogVisible" :title="quotaIsEdit ? '编辑存储配额' : '新增存储配额'" width="500px">
      <el-form label-width="110px">
        <el-form-item label="数据库名"><el-input v-model="quotaForm.databaseName" /></el-form-item>
        <el-form-item label="配额上限(GB)"><el-input-number v-model="quotaForm.maxSizeGb" :min="1" /></el-form-item>
        <el-form-item label="当前使用(GB)"><el-input-number v-model="quotaForm.currentUsageGb" :min="0" :precision="1" /></el-form-item>
        <el-form-item label="告警阈值(%)"><el-input-number v-model="quotaForm.alertThresholdPct" :min="1" :max="100" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="quotaForm.status" style="width:100%">
            <el-option label="正常" value="NORMAL" />
            <el-option label="告警" value="WARNING" />
            <el-option label="超限" value="EXCEEDED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="quotaDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveQuota">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
