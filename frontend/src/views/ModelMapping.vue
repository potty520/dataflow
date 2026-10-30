<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>类型与字段映射</h2>
        <div class="sub">数据库字段类型映射配置 + 库间字段映射，供建模 DDL 与数据迁移复用</div>
      </div>
    </div>

    <el-card shadow="never">
      <div class="card-head">
        <h3>类型映射（源类型 → 目标方言）</h3>
        <div class="toolbar" style="margin:0">
          <el-select v-model="typeDialect" size="small" style="width:130px" @change="loadTypes">
            <el-option label="MYSQL" value="MYSQL" /><el-option label="POSTGRESQL" value="POSTGRESQL" />
            <el-option label="XINGYUN" value="XINGYUN" />
          </el-select>
          <el-button size="small" @click="loadTypes">查询</el-button>
          <el-button size="small" type="primary" @click="openType()">新建映射</el-button>
        </div>
      </div>
      <el-table :data="types" size="small" v-loading="loading">
        <el-table-column prop="sourceType" label="源类型" width="160"><template #default="{ row }"><span class="mono">{{ row.sourceType }}</span></template></el-table-column>
        <el-table-column prop="targetDialect" label="目标方言" width="130" />
        <el-table-column prop="targetType" label="目标类型" width="180"><template #default="{ row }"><span class="mono">{{ row.targetType }}</span></template></el-table-column>
        <el-table-column prop="status" label="状态" width="90"><template #default="{ row }"><el-tag size="small" :type="row.status === 'ENABLED' ? 'success' : 'info'">{{ row.status === 'ENABLED' ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column prop="remark" label="说明" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openType(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="removeType(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="hint" style="margin-top:6px">解析优先级：精确源类型 → 类型族（STRING/INT/BIGINT/DECIMAL/DATE/TIMESTAMP/BOOL）→ 代码内置默认。</div>
    </el-card>

    <el-card shadow="never" style="margin-top:12px">
      <div class="card-head">
        <h3>库间字段映射</h3>
        <div class="toolbar" style="margin:0">
          <el-select v-model="colModelId" size="small" placeholder="全部模型" clearable style="width:200px" @change="loadColumns">
            <el-option v-for="m in models" :key="m.id" :label="m.code + ' · ' + m.name" :value="m.id" />
          </el-select>
          <el-input v-model="colKeyword" size="small" placeholder="表/字段关键字" clearable style="width:180px" @keyup.enter="loadColumns" />
          <el-button size="small" @click="loadColumns">查询</el-button>
          <el-button size="small" type="primary" @click="openColumn()">新建字段映射</el-button>
        </div>
      </div>
      <el-table :data="columns" size="small" v-loading="loading">
        <el-table-column prop="modelId" label="模型 ID" width="90" align="right" />
        <el-table-column label="源表.字段" min-width="190"><template #default="{ row }"><span class="mono">{{ row.sourceTable }}.{{ row.sourceColumn }}</span></template></el-table-column>
        <el-table-column label="目标表.字段" min-width="190"><template #default="{ row }"><span class="mono">{{ row.targetTable }}.{{ row.targetColumn }}</span></template></el-table-column>
        <el-table-column prop="sourceType" label="源类型" width="110" />
        <el-table-column prop="targetType" label="目标类型" width="110" />
        <el-table-column prop="expression" label="转换表达式" min-width="150" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="80" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openColumn(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="removeColumn(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="typeDialog" :title="typeForm.id ? '编辑类型映射' : '新建类型映射'" width="480px">
      <el-form label-width="100px" size="small">
        <el-form-item label="源类型"><el-input v-model="typeForm.sourceType" :disabled="!!typeForm.id" placeholder="如 VARCHAR / STRING / INT" /></el-form-item>
        <el-form-item label="目标方言">
          <el-select v-model="typeForm.targetDialect" :disabled="!!typeForm.id" style="width:100%">
            <el-option label="MYSQL" value="MYSQL" /><el-option label="POSTGRESQL" value="POSTGRESQL" /><el-option label="XINGYUN" value="XINGYUN" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标类型"><el-input v-model="typeForm.targetType" placeholder="如 VARCHAR(255) / NUMERIC(18,2)" /></el-form-item>
        <el-form-item label="状态"><el-radio-group v-model="typeForm.status"><el-radio label="ENABLED">启用</el-radio><el-radio label="DISABLED">停用</el-radio></el-radio-group></el-form-item>
        <el-form-item label="说明"><el-input v-model="typeForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="typeDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="saveType">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="columnDialog" :title="columnForm.id ? '编辑字段映射' : '新建字段映射'" width="560px">
      <el-form label-width="100px" size="small">
        <el-form-item label="模型">
          <el-select v-model="columnForm.modelId" style="width:100%" placeholder="可选">
            <el-option v-for="m in models" :key="m.id" :label="m.code + ' · ' + m.name" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="源表"><el-input v-model="columnForm.sourceTable" /></el-form-item>
        <el-form-item label="源字段"><el-input v-model="columnForm.sourceColumn" /></el-form-item>
        <el-form-item label="目标表"><el-input v-model="columnForm.targetTable" /></el-form-item>
        <el-form-item label="目标字段"><el-input v-model="columnForm.targetColumn" /></el-form-item>
        <el-form-item label="源/目标类型">
          <el-input v-model="columnForm.sourceType" placeholder="源类型" style="width:45%" />
          <el-input v-model="columnForm.targetType" placeholder="目标类型" style="width:45%;margin-left:10px" />
        </el-form-item>
        <el-form-item label="转换表达式"><el-input v-model="columnForm.expression" placeholder="可选，如 ROUND(amount*1.13,2)" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="columnForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="columnDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="saveColumn">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { modelApi } from '@/api'

const loading = ref(false)
const types = ref([])
const columns = ref([])
const models = ref([])
const typeDialect = ref('MYSQL')
const colModelId = ref(null)
const colKeyword = ref('')

const typeDialog = ref(false)
const typeForm = reactive({ id: null, sourceType: '', targetDialect: 'MYSQL', targetType: '', status: 'ENABLED', remark: '' })
const columnDialog = ref(false)
const columnForm = reactive({ id: null, modelId: null, sourceTable: '', sourceColumn: '', targetTable: '', targetColumn: '', sourceType: '', targetType: '', expression: '', remark: '' })

const loadTypes = async () => {
  loading.value = true
  try { types.value = (await modelApi.typeMappings({ dialect: typeDialect.value })).records || [] }
  catch (e) { types.value = [] } finally { loading.value = false }
}
const loadColumns = async () => {
  loading.value = true
  try { columns.value = (await modelApi.columnMappings({ modelId: colModelId.value || undefined, keyword: colKeyword.value })).records || [] }
  catch (e) { columns.value = [] } finally { loading.value = false }
}
const loadModels = async () => { try { models.value = (await modelApi.list({ pageSize: 100 })).records || [] } catch (e) { models.value = [] } }

const openType = (row) => {
  Object.assign(typeForm, { id: null, sourceType: '', targetDialect: typeDialect.value, targetType: '', status: 'ENABLED', remark: '' })
  if (row) Object.assign(typeForm, { id: row.id, sourceType: row.sourceType, targetDialect: row.targetDialect, targetType: row.targetType, status: row.status, remark: row.remark })
  typeDialog.value = true
}
const saveType = async () => {
  try {
    if (typeForm.id) await modelApi.updateTypeMapping(typeForm.id, { targetType: typeForm.targetType, status: typeForm.status, remark: typeForm.remark })
    else await modelApi.createTypeMapping({ sourceType: typeForm.sourceType, targetDialect: typeForm.targetDialect, targetType: typeForm.targetType, status: typeForm.status, remark: typeForm.remark })
    ElMessage.success('类型映射已保存'); typeDialog.value = false; loadTypes()
  } catch (e) { /* http 层已提示 */ }
}
const removeType = async (row) => {
  try { await ElMessageBox.confirm('删除类型映射 ' + row.sourceType + ' → ' + row.targetType + '？', '提示', { type: 'warning' }); await modelApi.deleteTypeMapping(row.id); loadTypes() }
  catch (e) { /* 取消 */ }
}
const openColumn = (row) => {
  Object.assign(columnForm, { id: null, modelId: colModelId.value || null, sourceTable: '', sourceColumn: '', targetTable: '', targetColumn: '', sourceType: '', targetType: '', expression: '', remark: '' })
  if (row) Object.assign(columnForm, { id: row.id, modelId: row.modelId, sourceTable: row.sourceTable, sourceColumn: row.sourceColumn, targetTable: row.targetTable, targetColumn: row.targetColumn, sourceType: row.sourceType, targetType: row.targetType, expression: row.expression, remark: row.remark })
  columnDialog.value = true
}
const saveColumn = async () => {
  try {
    if (columnForm.id) await modelApi.updateColumnMapping(columnForm.id, { ...columnForm })
    else await modelApi.createColumnMapping({ ...columnForm })
    ElMessage.success('字段映射已保存'); columnDialog.value = false; loadColumns()
  } catch (e) { /* http 层已提示 */ }
}
const removeColumn = async (row) => {
  try { await ElMessageBox.confirm('删除该字段映射？', '提示', { type: 'warning' }); await modelApi.deleteColumnMapping(row.id); loadColumns() }
  catch (e) { /* 取消 */ }
}

onMounted(() => { loadTypes(); loadColumns(); loadModels() })
</script>

<style scoped>
.page { padding: 4px; }
.card-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.hint { color: #909399; font-size: 12px; }
</style>
