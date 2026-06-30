<script setup>
import { onMounted, reactive, ref } from 'vue'
import { devQualityRulePage, devQualityTemplates, createDevQualityRule, deleteDevQualityRule, devQualityTaskPage, createDevQualityTask, executeDevQualityTask, devQualityOverview } from '../../../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const tab = ref('rule')
const overview = ref({})
const loading = ref(false)
const data = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10 })
const templates = ref([])
const ruleDialog = ref(false)
const taskDialog = ref(false)
const ruleForm = ref({ name: '', dimension: '', ruleLevel: 'FIELD', ruleExpr: '', templateType: '' })
const taskForm = ref({ ruleId: null, targetTable: '' })

async function loadOverview() {
  const res = await devQualityOverview()
  overview.value = res.data || {}
}

async function loadData() {
  loading.value = true
  try {
    const res = tab.value === 'rule' ? await devQualityRulePage(query) : await devQualityTaskPage(query)
    data.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

async function loadTemplates() {
  const res = await devQualityTemplates()
  templates.value = res.data || []
}

function useTemplate(t) {
  ruleForm.value = { name: t.dimension + '规则', dimension: t.dimension, ruleLevel: 'FIELD', ruleExpr: '', templateType: t.templateType }
  ruleDialog.value = true
}

async function saveRule() {
  await createDevQualityRule(ruleForm.value)
  ElMessage.success('创建成功')
  ruleDialog.value = false
  loadData()
}

async function saveTask() {
  await createDevQualityTask(taskForm.value)
  ElMessage.success('创建成功')
  taskDialog.value = false
  loadData()
}

async function removeRule(row) {
  await ElMessageBox.confirm('确认删除？', '提示', { type: 'warning' })
  await deleteDevQualityRule(row.id)
  loadData()
}

onMounted(() => { loadOverview(); loadTemplates(); loadData() })
</script>

<template>
  <div class="page-container">
    <div class="page-header">数据质量</div>
    <div class="stat-cards">
      <div class="stat-card"><div class="label">质量规则</div><div class="value">{{ overview.ruleCount || 0 }}</div></div>
      <div class="stat-card"><div class="label">质量任务</div><div class="value">{{ overview.taskCount || 0 }}</div></div>
      <div class="stat-card"><div class="label">执行成功</div><div class="value">{{ overview.successCount || 0 }}</div></div>
    </div>
    <el-tabs v-model="tab" @tab-change="loadData">
      <el-tab-pane label="质量规则" name="rule">
        <div class="search-bar">
          <el-button type="success" @click="ruleDialog = true">新增规则</el-button>
        </div>
        <el-card header="预置规则模板" style="margin-bottom:16px">
          <el-tag v-for="t in templates" :key="t.templateType" style="margin:4px;cursor:pointer" @click="useTemplate(t)">{{ t.dimension }}</el-tag>
        </el-card>
        <el-table :data="data" v-loading="loading" border>
          <el-table-column prop="name" label="规则名称" />
          <el-table-column prop="dimension" label="维度" width="100" />
          <el-table-column prop="ruleLevel" label="级别" width="90" />
          <el-table-column prop="ruleExpr" label="规则表达式" show-overflow-tooltip />
          <el-table-column label="操作" width="100"><template #default="{ row }"><el-button link type="danger" @click="removeRule(row)">删除</el-button></template></el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="质量任务" name="task">
        <div class="search-bar"><el-button type="success" @click="taskDialog = true">新建任务</el-button></div>
        <el-table :data="data" v-loading="loading" border>
          <el-table-column prop="targetTable" label="目标表" />
          <el-table-column prop="status" label="状态" width="90" />
          <el-table-column prop="score" label="评分" width="80" />
          <el-table-column prop="issueCount" label="问题数" width="80" />
          <el-table-column prop="lastRunTime" label="最近执行" width="180" />
          <el-table-column label="操作" width="100"><template #default="{ row }"><el-button link type="success" @click="executeDevQualityTask(row.id).then(loadData)">执行</el-button></template></el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
    <el-dialog v-model="ruleDialog" title="新增质量规则" width="480px">
      <el-form label-width="90px">
        <el-form-item label="名称"><el-input v-model="ruleForm.name" /></el-form-item>
        <el-form-item label="维度"><el-input v-model="ruleForm.dimension" /></el-form-item>
        <el-form-item label="表达式"><el-input v-model="ruleForm.ruleExpr" type="textarea" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="ruleDialog = false">取消</el-button><el-button type="primary" @click="saveRule">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="taskDialog" title="新建质量任务" width="420px">
      <el-form label-width="90px">
        <el-form-item label="规则ID"><el-input-number v-model="taskForm.ruleId" :min="1" /></el-form-item>
        <el-form-item label="目标表"><el-input v-model="taskForm.targetTable" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="taskDialog = false">取消</el-button><el-button type="primary" @click="saveTask">保存</el-button></template>
    </el-dialog>
  </div>
</template>
