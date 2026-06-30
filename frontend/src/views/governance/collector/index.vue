<script setup>
import { onMounted, reactive, ref } from 'vue'
import {
  metaCollectorPage, createMetaCollector, updateMetaCollector,
  deleteMetaCollector, runMetaCollector, metaCollectorLogs, datasourceList
} from '../../../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const collectTypes = [
  { value: 'FULL', label: '全量采集' },
  { value: 'SCHEMA', label: '结构采集' },
  { value: 'REGEX', label: '正则采集' }
]

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, taskName: '' })
const dialogVisible = ref(false)
const logVisible = ref(false)
const logs = ref([])
const form = ref({})
const isEdit = ref(false)
const datasources = ref([])

async function loadData() {
  loading.value = true
  try {
    const res = await metaCollectorPage(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

async function loadDs() {
  const res = await datasourceList()
  datasources.value = res.data || []
}

function openCreate() {
  isEdit.value = false
  form.value = { taskName: '', datasourceId: null, collectType: 'FULL', targetPattern: '', cronExpr: '', triggerOnChange: 0 }
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

async function save() {
  if (isEdit.value) await updateMetaCollector(form.value.id, form.value)
  else await createMetaCollector(form.value)
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadData()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除采集任务「${row.taskName}」？`, '提示', { type: 'warning' })
  await deleteMetaCollector(row.id)
  ElMessage.success('删除成功')
  loadData()
}

async function doRun(row) {
  ElMessage.info('正在执行元数据采集...')
  const res = await runMetaCollector(row.id)
  if (res.data && res.data.status === 'SUCCESS') {
    ElMessage.success(`采集完成: 表 ${res.data.tableCount}, 字段 ${res.data.fieldCount}`)
  } else {
    ElMessage.error('采集失败: ' + (res.data?.errorLog || '未知错误'))
  }
  loadData()
}

async function showLogs(row) {
  const res = await metaCollectorLogs(row.id)
  logs.value = res.data || []
  logVisible.value = true
}

onMounted(() => { loadData(); loadDs() })
</script>

<template>
  <div class="page-container">
    <div class="page-header">元数据采集</div>
    <div class="search-bar">
      <el-input v-model="query.taskName" placeholder="任务名称" clearable style="width:200px" />
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button type="success" @click="openCreate">创建采集任务</el-button>
    </div>
    <el-table :data="tableData" v-loading="loading" border>
      <el-table-column prop="taskName" label="任务名称" />
      <el-table-column prop="datasourceId" label="数据源" width="120">
        <template #default="{ row }">
          {{ datasources.find(d => d.id === row.datasourceId)?.name || row.datasourceId }}
        </template>
      </el-table-column>
      <el-table-column prop="collectType" label="采集类型" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.collectType === 'FULL' ? 'primary' : row.collectType === 'SCHEMA' ? 'warning' : 'info'">
            {{ collectTypes.find(c => c.value === row.collectType)?.label || row.collectType }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="targetPattern" label="目标模式" width="140" />
      <el-table-column prop="cronExpr" label="调度表达式" width="120" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 'RUNNING' ? 'warning' : 'info'">{{ row.status === 'RUNNING' ? '运行中' : '空闲' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastRunTime" label="上次运行" width="160" />
      <el-table-column label="操作" width="280">
        <template #default="{ row }">
          <el-button link type="success" @click="doRun(row)">立即采集</el-button>
          <el-button link @click="showLogs(row)">执行日志</el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total"
      layout="total, prev, pager, next" style="margin-top:16px" @change="loadData" />

    <!-- Create/Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑采集任务' : '创建采集任务'" width="560px">
      <el-form label-width="110px">
        <el-form-item label="任务名称"><el-input v-model="form.taskName" /></el-form-item>
        <el-form-item label="数据源">
          <el-select v-model="form.datasourceId" placeholder="选择数据源" style="width:100%">
            <el-option v-for="ds in datasources" :key="ds.id" :label="ds.name" :value="ds.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="采集类型">
          <el-select v-model="form.collectType">
            <el-option v-for="ct in collectTypes" :key="ct.value" :label="ct.label" :value="ct.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标模式" v-if="form.collectType === 'REGEX'">
          <el-input v-model="form.targetPattern" placeholder="正则表达式，如 ^user_" />
        </el-form-item>
        <el-form-item label="Cron表达式">
          <el-input v-model="form.cronExpr" placeholder="如 0 0 2 * * ?" />
        </el-form-item>
        <el-form-item label="变更自动触发">
          <el-switch v-model="form.triggerOnChange" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- Logs Dialog -->
    <el-dialog v-model="logVisible" title="执行日志" width="600px">
      <el-table :data="logs" border size="small">
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'SUCCESS' ? 'success' : 'danger'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="tableCount" label="表数" width="60" />
        <el-table-column prop="fieldCount" label="字段数" width="70" />
        <el-table-column prop="durationMs" label="耗时(ms)" width="100" />
        <el-table-column prop="errorLog" label="错误信息" />
        <el-table-column prop="createTime" label="时间" width="160" />
      </el-table>
      <el-empty v-if="!logs.length" description="暂无执行日志" />
    </el-dialog>
  </div>
</template>
