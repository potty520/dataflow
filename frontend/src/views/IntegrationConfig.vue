<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>集成配置</h2>
        <div class="sub">函数库、增量字段、批次与并发配置，作为数据集成作业的复用参数</div>
      </div>
    </div>

    <el-tabs v-model="tab" type="border-card">
      <el-tab-pane label="函数库" name="functions">
        <div class="toolbar">
          <el-input v-model="functionKeyword" size="small" placeholder="编码/名称" clearable style="width:200px" @keyup.enter="loadFunctions" />
          <el-button size="small" @click="loadFunctions">查询</el-button>
          <span class="spacer"></span>
          <el-button size="small" type="primary" @click="openFunction()">新建函数</el-button>
        </div>
        <el-table :data="functions" size="small" v-loading="loading">
          <el-table-column prop="code" label="编码" width="170"><template #default="{ row }"><span class="mono">{{ row.code }}</span></template></el-table-column>
          <el-table-column prop="name" label="名称" width="160" />
          <el-table-column prop="expression" label="表达式" min-width="260" show-overflow-tooltip><template #default="{ row }"><span class="mono">{{ row.expression }}</span></template></el-table-column>
          <el-table-column prop="category" label="分类" width="120" />
          <el-table-column prop="status" label="状态" width="90" />
          <el-table-column label="操作" width="200">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="testFunction(row)">试算</el-button>
              <el-button link type="primary" size="small" @click="openFunction(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="removeFunction(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="增量字段" name="incr">
        <div class="toolbar">
          <span class="hint">为作业配置增量水位字段（时间戳/自增 ID），运行时可读取 last_value</span>
          <span class="spacer"></span>
          <el-button size="small" type="primary" @click="openIncr()">配置增量</el-button>
        </div>
        <el-table :data="incrList" size="small" v-loading="loading">
          <el-table-column prop="jobId" label="作业 ID" width="90" align="right" />
          <el-table-column prop="fieldName" label="增量字段" width="180"><template #default="{ row }"><span class="mono">{{ row.fieldName }}</span></template></el-table-column>
          <el-table-column prop="fieldType" label="类型" width="110" />
          <el-table-column prop="startValue" label="起始值" width="180" />
          <el-table-column prop="lastValue" label="当前水位" width="180" />
          <el-table-column prop="status" label="状态" width="90" />
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openIncr(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="removeIncr(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="批次配置" name="batch">
        <div class="toolbar">
          <span class="hint">按作业配置批次大小、并发度与失败重试次数</span>
          <span class="spacer"></span>
          <el-button size="small" type="primary" @click="openBatch()">配置批次</el-button>
        </div>
        <el-table :data="batchList" size="small" v-loading="loading">
          <el-table-column prop="jobId" label="作业 ID" width="90" align="right" />
          <el-table-column prop="batchSize" label="批次大小" width="110" align="right" />
          <el-table-column prop="concurrency" label="并发度" width="90" align="right" />
          <el-table-column prop="retryCount" label="重试次数" width="100" align="right" />
          <el-table-column prop="status" label="状态" width="90" />
          <el-table-column prop="remark" label="说明" min-width="200" show-overflow-tooltip />
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openBatch(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="removeBatch(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="functionDialog" :title="functionForm.id ? '编辑函数' : '新建函数'" width="560px">
      <el-form label-width="90px" size="small">
        <el-form-item label="编码"><el-input v-model="functionForm.code" :disabled="!!functionForm.id" placeholder="FN_XXX" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="functionForm.name" /></el-form-item>
        <el-form-item label="表达式"><el-input v-model="functionForm.expression" type="textarea" :rows="3" class="mono" placeholder="如 ROUND(amount*1.13,2)" /></el-form-item>
        <el-form-item label="分类"><el-input v-model="functionForm.category" placeholder="数值/字符串/日期" /></el-form-item>
        <el-form-item label="状态"><el-radio-group v-model="functionForm.status"><el-radio label="ENABLED">启用</el-radio><el-radio label="DISABLED">停用</el-radio></el-radio-group></el-form-item>
        <el-form-item label="说明"><el-input v-model="functionForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="functionDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="saveFunction">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="incrDialog" title="增量字段配置" width="520px">
      <el-form label-width="100px" size="small">
        <el-form-item label="作业"><el-select v-model="incrForm.jobId" filterable style="width:100%"><el-option v-for="j in jobs" :key="j.id" :label="j.code + ' · ' + (j.name || '')" :value="j.id" /></el-select></el-form-item>
        <el-form-item label="增量字段"><el-input v-model="incrForm.fieldName" placeholder="如 update_time / id" /></el-form-item>
        <el-form-item label="字段类型"><el-select v-model="incrForm.fieldType" style="width:100%"><el-option label="时间戳" value="TIMESTAMP" /><el-option label="自增 ID" value="ID" /><el-option label="数值" value="NUMBER" /></el-select></el-form-item>
        <el-form-item label="起始值"><el-input v-model="incrForm.startValue" placeholder="可选" /></el-form-item>
        <el-form-item label="当前水位"><el-input v-model="incrForm.lastValue" placeholder="可选" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="incrForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="incrDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="saveIncr">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="batchDialog" title="批次配置" width="500px">
      <el-form label-width="100px" size="small">
        <el-form-item label="作业"><el-select v-model="batchForm.jobId" filterable style="width:100%"><el-option v-for="j in jobs" :key="j.id" :label="j.code + ' · ' + (j.name || '')" :value="j.id" /></el-select></el-form-item>
        <el-form-item label="批次大小"><el-input-number v-model="batchForm.batchSize" :min="100" :max="1000000" /></el-form-item>
        <el-form-item label="并发度"><el-input-number v-model="batchForm.concurrency" :min="1" :max="32" /></el-form-item>
        <el-form-item label="重试次数"><el-input-number v-model="batchForm.retryCount" :min="0" :max="10" /></el-form-item>
        <el-form-item label="状态"><el-radio-group v-model="batchForm.status"><el-radio label="ENABLED">启用</el-radio><el-radio label="DISABLED">停用</el-radio></el-radio-group></el-form-item>
        <el-form-item label="说明"><el-input v-model="batchForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="batchDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="saveBatch">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { integrationApi, integrationConfigApi } from '@/api'

const tab = ref('functions')
const loading = ref(false)
const functions = ref([])
const incrList = ref([])
const batchList = ref([])
const jobs = ref([])
const functionKeyword = ref('')

const functionDialog = ref(false)
const functionForm = reactive({ id: null, code: '', name: '', expression: '', category: '数值', status: 'ENABLED', remark: '' })
const incrDialog = ref(false)
const incrForm = reactive({ id: null, jobId: null, fieldName: '', fieldType: 'TIMESTAMP', startValue: '', lastValue: '', status: 'ENABLED', remark: '' })
const batchDialog = ref(false)
const batchForm = reactive({ id: null, jobId: null, batchSize: 10000, concurrency: 2, retryCount: 3, status: 'ENABLED', remark: '' })

const loadFunctions = async () => {
  loading.value = true
  try { functions.value = (await integrationConfigApi.functions({ keyword: functionKeyword.value })).records || [] }
  catch (e) { functions.value = [] } finally { loading.value = false }
}
const loadIncr = async () => { try { incrList.value = (await integrationConfigApi.incr({})).records || [] } catch (e) { incrList.value = [] } }
const loadBatch = async () => { try { batchList.value = (await integrationConfigApi.batch({})).records || [] } catch (e) { batchList.value = [] } }
const loadJobs = async () => { try { jobs.value = (await integrationApi.jobs({ pageSize: 100 })).records || [] } catch (e) { jobs.value = [] } }

const openFunction = (row) => {
  Object.assign(functionForm, { id: null, code: '', name: '', expression: '', category: '数值', status: 'ENABLED', remark: '' })
  if (row) Object.assign(functionForm, { id: row.id, code: row.code, name: row.name, expression: row.expression, category: row.category, status: row.status, remark: row.remark })
  functionDialog.value = true
}
const saveFunction = async () => {
  try {
    if (functionForm.id) await integrationConfigApi.updateFunction(functionForm.id, { ...functionForm })
    else await integrationConfigApi.createFunction({ ...functionForm })
    ElMessage.success('函数已保存'); functionDialog.value = false; loadFunctions()
  } catch (e) { /* http 层已提示 */ }
}
const removeFunction = async (row) => {
  try { await ElMessageBox.confirm('删除函数 ' + row.code + '？', '提示', { type: 'warning' }); await integrationConfigApi.deleteFunction(row.id); loadFunctions() }
  catch (e) { /* 取消 */ }
}
const testFunction = async (row) => {
  try {
    const d = await integrationConfigApi.testFunction(row.expression)
    ElMessage.success('试算结果：' + d.value)
  } catch (e) { /* http 层已提示 */ }
}

const openIncr = (row) => {
  Object.assign(incrForm, { id: null, jobId: null, fieldName: '', fieldType: 'TIMESTAMP', startValue: '', lastValue: '', status: 'ENABLED', remark: '' })
  if (row) Object.assign(incrForm, { id: row.id, jobId: row.jobId, fieldName: row.fieldName, fieldType: row.fieldType, startValue: row.startValue, lastValue: row.lastValue, status: row.status, remark: row.remark })
  incrDialog.value = true
}
const saveIncr = async () => {
  try { await integrationConfigApi.saveIncr({ ...incrForm }); ElMessage.success('增量配置已保存'); incrDialog.value = false; loadIncr() }
  catch (e) { /* http 层已提示 */ }
}
const removeIncr = async (row) => {
  try { await ElMessageBox.confirm('删除该增量配置？', '提示', { type: 'warning' }); await integrationConfigApi.deleteIncr(row.id); loadIncr() }
  catch (e) { /* 取消 */ }
}
const openBatch = (row) => {
  Object.assign(batchForm, { id: null, jobId: null, batchSize: 10000, concurrency: 2, retryCount: 3, status: 'ENABLED', remark: '' })
  if (row) Object.assign(batchForm, { id: row.id, jobId: row.jobId, batchSize: row.batchSize, concurrency: row.concurrency, retryCount: row.retryCount, status: row.status, remark: row.remark })
  batchDialog.value = true
}
const saveBatch = async () => {
  try { await integrationConfigApi.saveBatch({ ...batchForm }); ElMessage.success('批次配置已保存'); batchDialog.value = false; loadBatch() }
  catch (e) { /* http 层已提示 */ }
}
const removeBatch = async (row) => {
  try { await ElMessageBox.confirm('删除该批次配置？', '提示', { type: 'warning' }); await integrationConfigApi.deleteBatch(row.id); loadBatch() }
  catch (e) { /* 取消 */ }
}

onMounted(() => { loadFunctions(); loadIncr(); loadBatch(); loadJobs() })
</script>

<style scoped>
.page { padding: 4px; }
.hint { color: #909399; font-size: 12px; }
</style>
