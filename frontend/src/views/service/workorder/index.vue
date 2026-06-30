<script setup>
import { onMounted, reactive, ref } from 'vue'
import { svcWorkorderPage, createSvcWorkorder, approveSvcWorkorder } from '../../../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, status: null })
const dialogVisible = ref(false)
const form = ref({ apiName: '', appName: '', applyType: 'SUBSCRIBE', reason: '' })

async function loadData() {
  loading.value = true
  try {
    const res = await svcWorkorderPage(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

async function save() {
  await createSvcWorkorder(form.value)
  ElMessage.success('申请已提交')
  dialogVisible.value = false
  loadData()
}

async function approve(row, pass) {
  const { value } = await ElMessageBox.prompt(pass ? '审批通过备注' : '请输入拒绝原因', pass ? '通过' : '拒绝', { inputValue: '' })
  await approveSvcWorkorder(row.id, pass, value)
  ElMessage.success(pass ? '已通过' : '已拒绝')
  loadData()
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">工单管理</div>
    <div class="search-bar">
      <el-select v-model="query.status" placeholder="状态" clearable style="width:120px">
        <el-option :value="0" label="待审批" /><el-option :value="1" label="已通过" /><el-option :value="2" label="已拒绝" />
      </el-select>
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button type="success" @click="dialogVisible = true">新建申请</el-button>
    </div>
    <el-table :data="tableData" v-loading="loading" border>
      <el-table-column prop="apiName" label="API名称" />
      <el-table-column prop="appName" label="应用名称" />
      <el-table-column prop="applicant" label="申请人" width="100" />
      <el-table-column prop="applyType" label="申请类型" width="100" />
      <el-table-column prop="reason" label="申请原因" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'danger' : 'warning'" size="small">{{ {0:'待审',1:'通过',2:'拒绝'}[row.status] }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <template v-if="row.status === 0">
            <el-button link type="success" @click="approve(row, true)">通过</el-button>
            <el-button link type="danger" @click="approve(row, false)">拒绝</el-button>
          </template>
          <span v-else>{{ row.approveReason || '-' }}</span>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" layout="total, prev, pager, next" style="margin-top:16px" @change="loadData" />
    <el-dialog v-model="dialogVisible" title="API调用申请" width="480px">
      <el-form label-width="90px">
        <el-form-item label="API名称"><el-input v-model="form.apiName" /></el-form-item>
        <el-form-item label="应用名称"><el-input v-model="form.appName" /></el-form-item>
        <el-form-item label="申请类型"><el-select v-model="form.applyType" style="width:100%"><el-option value="SUBSCRIBE" label="订阅" /><el-option value="CALL" label="调用" /></el-select></el-form-item>
        <el-form-item label="申请原因"><el-input v-model="form.reason" type="textarea" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" @click="save">提交</el-button></template>
    </el-dialog>
  </div>
</template>
