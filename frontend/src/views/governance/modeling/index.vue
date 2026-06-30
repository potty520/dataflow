<script setup>
import { onMounted, reactive, ref } from 'vue'
import { govModelPage, createGovModel, updateGovModel, deleteGovModel } from '../../../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const layers = ['ODS', 'STD', 'DWD', 'DWS', 'ADS']
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, layerCode: '', keyword: '' })
const dialogVisible = ref(false)
const form = ref({})
const isEdit = ref(false)

async function loadData() {
  loading.value = true
  try {
    const res = await govModelPage(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

function openCreate() {
  isEdit.value = false
  form.value = { layerCode: 'ODS', tableEn: '', tableCn: '', cleanRules: '', remark: '' }
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

async function save() {
  if (isEdit.value) await updateGovModel(form.value.id, form.value)
  else await createGovModel(form.value)
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadData()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除表「${row.tableEn}」？`, '提示', { type: 'warning' })
  await deleteGovModel(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">数据建模</div>
    <div class="search-bar">
      <el-select v-model="query.layerCode" placeholder="数仓分层" clearable style="width:120px">
        <el-option v-for="l in layers" :key="l" :label="l" :value="l" />
      </el-select>
      <el-input v-model="query.keyword" placeholder="表名" clearable style="width:180px" />
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button type="success" @click="openCreate">新增模型表</el-button>
    </div>
    <el-table :data="tableData" v-loading="loading" border>
      <el-table-column prop="layerCode" label="分层" width="80" />
      <el-table-column prop="tableEn" label="英文表名" />
      <el-table-column prop="tableCn" label="中文表名" />
      <el-table-column prop="cleanRules" label="清洗规则" show-overflow-tooltip />
      <el-table-column prop="createBy" label="创建人" width="100" />
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" layout="total, prev, pager, next" style="margin-top:16px" @change="loadData" />
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑模型' : '新增模型'" width="520px">
      <el-form label-width="90px">
        <el-form-item label="分层"><el-select v-model="form.layerCode" style="width:100%"><el-option v-for="l in layers" :key="l" :value="l" /></el-select></el-form-item>
        <el-form-item label="英文表名"><el-input v-model="form.tableEn" /></el-form-item>
        <el-form-item label="中文表名"><el-input v-model="form.tableCn" /></el-form-item>
        <el-form-item label="清洗规则"><el-input v-model="form.cleanRules" type="textarea" placeholder="值域校验,空值过滤等" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>
