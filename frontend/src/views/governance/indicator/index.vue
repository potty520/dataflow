<script setup>
import { onMounted, reactive, ref } from 'vue'
import {
  govPeriodPage, createGovPeriod, updateGovPeriod, publishGovPeriod, deleteGovPeriod,
  govAtomicPage, createGovAtomic, updateGovAtomic, deleteGovAtomic,
  govLimitPage, createGovLimit, updateGovLimit, deleteGovLimit,
  govDerivedPage, createGovDerived, updateGovDerived, deleteGovDerived
} from '../../../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const tab = ref('period')
const loading = ref(false)
const data = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, name: '' })
const dialogVisible = ref(false)
const form = ref({})
const isEdit = ref(false)

const loaders = {
  period: () => govPeriodPage(query),
  atomic: () => govAtomicPage(query),
  limit: () => govLimitPage(query),
  derived: () => govDerivedPage(query)
}

async function loadData() {
  loading.value = true
  try {
    const res = await loaders[tab.value]()
    data.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

function onTabChange() {
  query.page = 1
  query.name = ''
  loadData()
}

function openCreate() {
  isEdit.value = false
  const defaults = {
    period: { name: '', cronExpr: '', status: 0 },
    atomic: { name: '', calcLogic: '', sourceField: '', status: 0 },
    limit: { name: '', sourceField: '', calcLogic: '' },
    derived: { name: '', granularity: '', dataTheme: '', status: 0 }
  }
  form.value = defaults[tab.value]
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

async function save() {
  const apis = {
    period: [createGovPeriod, updateGovPeriod],
    atomic: [createGovAtomic, updateGovAtomic],
    limit: [createGovLimit, updateGovLimit],
    derived: [createGovDerived, updateGovDerived]
  }
  const [create, update] = apis[tab.value]
  if (isEdit.value) await update(form.value.id, form.value)
  else await create(form.value)
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadData()
}

async function remove(row) {
  await ElMessageBox.confirm('确认删除？', '提示', { type: 'warning' })
  const dels = { period: deleteGovPeriod, atomic: deleteGovAtomic, limit: deleteGovLimit, derived: deleteGovDerived }
  await dels[tab.value](row.id)
  ElMessage.success('删除成功')
  loadData()
}

async function publish(row) {
  await publishGovPeriod(row.id)
  ElMessage.success('已发布')
  loadData()
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">指标管理</div>
    <el-tabs v-model="tab" @tab-change="onTabChange">
      <el-tab-pane label="统计周期" name="period" />
      <el-tab-pane label="原子指标" name="atomic" />
      <el-tab-pane label="业务限定" name="limit" />
      <el-tab-pane label="派生指标" name="derived" />
    </el-tabs>
    <div class="search-bar">
      <el-input v-model="query.name" placeholder="名称" clearable style="width:180px" />
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button type="success" @click="openCreate">新增</el-button>
    </div>
    <el-table :data="data" v-loading="loading" border>
      <el-table-column prop="name" label="名称" />
      <el-table-column v-if="tab === 'period'" prop="cronExpr" label="Cron表达式" />
      <el-table-column v-if="tab === 'atomic'" prop="calcLogic" label="计算逻辑" show-overflow-tooltip />
      <el-table-column v-if="tab === 'atomic'" prop="sourceField" label="来源字段" />
      <el-table-column v-if="tab === 'limit'" prop="sourceField" label="来源字段" />
      <el-table-column v-if="tab === 'derived'" prop="granularity" label="统计粒度" />
      <el-table-column v-if="tab === 'derived'" prop="dataTheme" label="数据主题" />
      <el-table-column v-if="tab !== 'limit'" prop="status" label="状态" width="80">
        <template #default="{ row }">{{ {0:'草稿',1:'已发布',2:'已下线'}[row.status] || row.status }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button v-if="tab === 'period' && row.status !== 1" link type="success" @click="publish(row)">发布</el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" layout="total, prev, pager, next" style="margin-top:16px" @change="loadData" />
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑' : '新增'" width="480px">
      <el-form label-width="90px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item v-if="tab === 'period'" label="Cron"><el-input v-model="form.cronExpr" /></el-form-item>
        <el-form-item v-if="tab === 'atomic'" label="计算逻辑"><el-input v-model="form.calcLogic" type="textarea" /></el-form-item>
        <el-form-item v-if="tab === 'atomic' || tab === 'limit'" label="来源字段"><el-input v-model="form.sourceField" /></el-form-item>
        <el-form-item v-if="tab === 'limit'" label="计算逻辑"><el-input v-model="form.calcLogic" type="textarea" /></el-form-item>
        <el-form-item v-if="tab === 'derived'" label="统计粒度"><el-input v-model="form.granularity" /></el-form-item>
        <el-form-item v-if="tab === 'derived'" label="数据主题"><el-input v-model="form.dataTheme" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>
