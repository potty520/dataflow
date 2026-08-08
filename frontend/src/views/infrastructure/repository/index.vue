<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createInfraRepository,
  deleteInfraRepository,
  distributeInfraRepository,
  infraRepositoryPage,
  updateInfraRepository
} from '../../../api'

const componentTypes = ['HDFS', 'YARN', 'ZooKeeper', 'Kafka', 'Flink', 'Hive', 'Kerberos', 'Ranger', 'Hudi']
const statusMeta = {
  READY: { label: '待分发', type: 'warning' },
  DISTRIBUTED: { label: '已分发', type: 'success' },
  DEPLOYED: { label: '已部署', type: 'success' },
  ARCHIVED: { label: '已归档', type: 'info' },
  FAILED: { label: '失败', type: 'danger' }
}

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const dialogVisible = ref(false)
const editingId = ref(null)
const query = reactive({ page: 1, size: 10, componentType: '', packageName: '', status: '' })
const form = ref({})

function resetForm() {
  form.value = {
    componentType: 'HDFS',
    packageName: '',
    version: '',
    filePath: '',
    packageSizeMb: 0,
    checksum: '',
    compatibleOs: 'CentOS 7.6',
    status: 'READY',
    remark: ''
  }
}

function openCreate() {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  form.value = { ...row }
  dialogVisible.value = true
}

async function loadData() {
  loading.value = true
  try {
    const res = await infraRepositoryPage(query)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

async function save() {
  if (editingId.value) {
    await updateInfraRepository(editingId.value, form.value)
  } else {
    await createInfraRepository(form.value)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadData()
}

async function distribute(row) {
  await distributeInfraRepository(row.id)
  ElMessage.success('分发任务已完成')
  loadData()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除部署包「${row.packageName}」？`, '删除确认', { type: 'warning' })
  await deleteInfraRepository(row.id)
  ElMessage.success('删除成功')
  loadData()
}

function tagType(status) {
  return statusMeta[status]?.type || 'info'
}

function tagLabel(status) {
  return statusMeta[status]?.label || status || '未知'
}

onMounted(() => {
  resetForm()
  loadData()
})
</script>

<template>
  <div class="page-container">
    <div class="page-top">
      <div>
        <p class="page-kicker">基础支撑 / 软件分发</p>
        <h1 class="page-title">组件仓库</h1>
        <p class="page-subtitle">管理 Hadoop、Flink、Kafka、Hive、Hudi 等组件部署包与分发状态。</p>
      </div>
      <div class="page-actions">
        <el-button :loading="loading" @click="loadData">
          <el-icon><Refresh /></el-icon>
          <span>刷新</span>
        </el-button>
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>
          <span>登记部署包</span>
        </el-button>
      </div>
    </div>

    <div class="search-bar">
      <el-select v-model="query.componentType" placeholder="组件类型" clearable style="width:150px">
        <el-option v-for="item in componentTypes" :key="item" :label="item" :value="item" />
      </el-select>
      <el-input v-model="query.packageName" placeholder="部署包名称" clearable style="width:220px" />
      <el-select v-model="query.status" placeholder="状态" clearable style="width:140px">
        <el-option label="待分发" value="READY" />
        <el-option label="已分发" value="DISTRIBUTED" />
        <el-option label="已部署" value="DEPLOYED" />
        <el-option label="已归档" value="ARCHIVED" />
      </el-select>
      <el-button type="primary" @click="query.page = 1; loadData()">查询</el-button>
    </div>

    <el-table :data="tableData" v-loading="loading" border>
      <el-table-column prop="componentType" label="组件" width="110" />
      <el-table-column prop="packageName" label="部署包" min-width="210" />
      <el-table-column prop="version" label="版本" width="110" />
      <el-table-column prop="filePath" label="存储路径" min-width="260" show-overflow-tooltip />
      <el-table-column prop="packageSizeMb" label="大小(MB)" width="110" />
      <el-table-column prop="compatibleOs" label="适配系统" width="130" />
      <el-table-column prop="status" label="状态" width="95">
        <template #default="{ row }">
          <el-tag :type="tagType(row.status)" size="small">{{ tagLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="210" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="success" @click="distribute(row)">分发</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="query.page"
      v-model:page-size="query.size"
      :total="total"
      layout="total, sizes, prev, pager, next"
      style="margin-top:16px"
      @change="loadData"
    />

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑部署包' : '登记部署包'" width="560px">
      <el-form label-width="96px">
        <el-form-item label="组件类型">
          <el-select v-model="form.componentType" style="width:100%">
            <el-option v-for="item in componentTypes" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="部署包名"><el-input v-model="form.packageName" /></el-form-item>
        <el-form-item label="版本"><el-input v-model="form.version" /></el-form-item>
        <el-form-item label="文件路径"><el-input v-model="form.filePath" /></el-form-item>
        <el-form-item label="大小(MB)"><el-input-number v-model="form.packageSizeMb" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="校验值"><el-input v-model="form.checksum" /></el-form-item>
        <el-form-item label="适配系统"><el-input v-model="form.compatibleOs" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width:100%">
            <el-option label="待分发" value="READY" />
            <el-option label="已分发" value="DISTRIBUTED" />
            <el-option label="已部署" value="DEPLOYED" />
            <el-option label="已归档" value="ARCHIVED" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
