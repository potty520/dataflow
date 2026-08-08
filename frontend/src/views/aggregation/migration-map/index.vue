<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  assetTableList,
  assetTableDetail,
  createMigrationMapping,
  generateDataflowFromMapping,
  migrationMappingValidate,
  updateMigrationMapping,
  migrationMappingDetail,
  migrationMappingPage
} from '../../../api'

const loading = ref(false)
const saving = ref(false)
const sourceTables = ref([])
const targetTables = ref([])
const sourceFields = ref([])
const targetFields = ref([])
const history = ref([])
const validation = ref(null)
const editingId = ref(null)
const query = reactive({
  keyword: ''
})
const form = reactive({
  name: '',
  sourceId: null,
  targetId: null,
  writeMode: 'insert',
  faultTolerance: 0,
  preSql: '',
  syncType: 'MIGRATION',
  mappingName: '',
  remarks: ''
})

const mappingRows = ref([])
const mappingStatusMeta = {
  DRAFT: { label: '草稿', type: 'info' },
  GENERATED: { label: '已生成', type: 'success' },
  INVALID: { label: '需处理', type: 'warning' }
}

const sourceTable = computed(() => sourceTables.value.find((item) => item.id === form.sourceId) || null)
const targetTable = computed(() => targetTables.value.find((item) => item.id === form.targetId) || null)

function buildDefaultName() {
  if (!sourceTable.value || !targetTable.value) return ''
  return `${sourceTable.value.tableEn || sourceTable.value.tableCn || 'source'}_to_${targetTable.value.tableEn || targetTable.value.tableCn || 'target'}`
}

function statusType(status) {
  return mappingStatusMeta[status]?.type || 'info'
}

function statusLabel(status) {
  return mappingStatusMeta[status]?.label || status || '未知'
}

async function loadTables() {
  loading.value = true
  try {
    const res = await assetTableList({ keyword: query.keyword || undefined })
    sourceTables.value = res.data || []
    targetTables.value = res.data || []
    if (!form.sourceId && sourceTables.value.length) form.sourceId = sourceTables.value[0].id
    if (!form.targetId && targetTables.value.length > 1) form.targetId = targetTables.value[1].id
    if (!form.targetId && targetTables.value.length) form.targetId = targetTables.value[0].id
  } finally {
    loading.value = false
  }
}

async function loadFields(tableId, target = false) {
  if (!tableId) return []
  const res = await assetTableDetail(tableId)
  const fields = res.data?.fields || []
  if (target) targetFields.value = fields
  else sourceFields.value = fields
  return fields
}

function syncMappingRows() {
  const current = new Map(mappingRows.value.map((row) => [row.sourceFieldId, row]))
  mappingRows.value = sourceFields.value.map((field, index) => {
    const existed = current.get(field.id)
    return existed || {
      id: `${field.id}-${index}`,
      sourceFieldId: field.id,
      sourceField: field.fieldEn,
      sourceName: field.fieldCn || field.fieldEn,
      targetFieldId: null,
      targetField: '',
      targetName: '',
      transformRule: '',
      required: 0
    }
  })
}

async function refreshSourceFields() {
  if (!form.sourceId) {
    sourceFields.value = []
    mappingRows.value = []
    return
  }
  await loadFields(form.sourceId, false)
  syncMappingRows()
}

async function refreshTargetFields() {
  if (!form.targetId) {
    targetFields.value = []
    return
  }
  await loadFields(form.targetId, true)
}

function onTargetChange(row) {
  const target = targetFields.value.find((item) => item.id === row.targetFieldId)
  row.targetField = target?.fieldEn || ''
  row.targetName = target?.fieldCn || target?.fieldEn || ''
}

function autoMatch() {
  const targetMap = new Map(targetFields.value.map((field) => [field.fieldEn, field]))
  mappingRows.value = mappingRows.value.map((row) => {
    const target = targetMap.get(row.sourceField) || targetFields.value.find((item) => item.fieldEn === row.sourceField.replace(/^src_/, ''))
    if (target) {
      row.targetFieldId = target.id
      row.targetField = target.fieldEn
      row.targetName = target.fieldCn || target.fieldEn
    }
    return row
  })
  ElMessage.success('已自动匹配')
}

function addCustomRow() {
  mappingRows.value.push({
    id: `custom-${Date.now()}`,
    sourceFieldId: null,
    sourceField: '',
    sourceName: '',
    targetFieldId: null,
    targetField: '',
    targetName: '',
    transformRule: '',
    required: 0
  })
}

function removeRow(index) {
  mappingRows.value.splice(index, 1)
}

async function loadMapping(row) {
  editingId.value = row.id
  form.name = row.name || ''
  form.sourceId = row.sourceTableId || null
  form.targetId = row.targetTableId || null
  form.writeMode = row.writeMode || 'insert'
  form.faultTolerance = row.faultTolerance || 0
  form.preSql = row.preSql || ''
  form.remarks = row.remark || ''
  await refreshSourceFields()
  await refreshTargetFields()
  let parsed = []
  try {
    parsed = row.fieldMapping ? JSON.parse(row.fieldMapping) : []
  } catch (e) {
    parsed = []
  }
  const sourceByName = new Map(sourceFields.value.map((item) => [item.fieldEn, item]))
  const targetByName = new Map(targetFields.value.map((item) => [item.fieldEn, item]))
  mappingRows.value = parsed.map((item, index) => {
    const source = sourceByName.get(item.sourceField)
    const target = targetByName.get(item.targetField)
    return {
      id: `${source?.id || 'custom'}-${index}`,
      sourceFieldId: source?.id || null,
      sourceField: item.sourceField || '',
      sourceName: item.sourceName || source?.fieldCn || '',
      targetFieldId: target?.id || null,
      targetField: item.targetField || '',
      targetName: item.targetName || target?.fieldCn || '',
      transformRule: item.transformRule || '',
      required: item.required || 0
    }
  })
  if (!mappingRows.value.length) syncMappingRows()
  await validateCurrentMapping(false)
  ElMessage.success('已载入映射')
}

async function reloadMapping(row) {
  const res = await migrationMappingDetail(row.id)
  await loadMapping(res.data.mapping)
}

function newMapping() {
  editingId.value = null
  validation.value = null
  form.name = buildDefaultName()
  form.writeMode = 'insert'
  form.faultTolerance = 0
  form.preSql = ''
  form.remarks = ''
  syncMappingRows()
}

const mappingJson = computed(() => JSON.stringify(mappingRows.value.map((row) => ({
  sourceField: row.sourceField,
  sourceName: row.sourceName,
  targetField: row.targetField,
  targetName: row.targetName,
  transformRule: row.transformRule,
  required: row.required
})), null, 2))

async function saveMapping() {
  if (!form.sourceId || !form.targetId) {
    ElMessage.warning('请选择源表和目标表')
    return
  }
  if (!form.name) {
    form.name = buildDefaultName()
  }
  saving.value = true
  try {
    const payload = {
      name: form.name,
      sourceTableId: form.sourceId,
      targetTableId: form.targetId,
      writeMode: form.writeMode,
      faultTolerance: form.faultTolerance,
      preSql: form.preSql,
      fieldMapping: mappingJson.value,
      remark: form.remarks || '数据迁移映射'
    }
    if (editingId.value) {
      await updateMigrationMapping(editingId.value, payload)
    } else {
      const res = await createMigrationMapping(payload)
      editingId.value = res.data?.id || editingId.value
    }
    ElMessage.success('迁移映射已保存')
    await loadHistory()
    await validateCurrentMapping(false)
  } finally {
    saving.value = false
  }
}

async function validateCurrentMapping(showMessage = true) {
  if (!editingId.value) {
    validation.value = buildLocalValidation()
    if (showMessage) ElMessage.info('当前方案尚未保存，已完成本地校验')
    return
  }
  const res = await migrationMappingValidate(editingId.value)
  validation.value = res.data || null
  if (showMessage) {
    const warnings = validation.value?.warnings || []
    if (warnings.length) ElMessage.warning(`发现 ${warnings.length} 个校验提示`)
    else ElMessage.success('映射校验通过')
  }
}

function buildLocalValidation() {
  const rows = mappingRows.value || []
  const matched = rows.filter(row => row.sourceField && row.targetField).length
  return {
    mappingFieldCount: rows.length,
    matchedCount: matched,
    sourceFieldCount: sourceFields.value.length,
    targetFieldCount: targetFields.value.length,
    warnings: rows.length ? [] : ['字段映射为空'],
    canGenerateDataflow: false
  }
}

async function generateDataflow() {
  if (!editingId.value) {
    ElMessage.warning('请先保存映射方案')
    return
  }
  await validateCurrentMapping(false)
  if (!validation.value?.canGenerateDataflow) {
    ElMessage.warning('当前映射未满足生成数据流条件')
    return
  }
  const res = await generateDataflowFromMapping(editingId.value)
  ElMessage.success(`已生成数据流：${res.data?.dataflowName || ''}`)
  await loadHistory()
  await validateCurrentMapping(false)
}

async function loadHistory() {
  const res = await migrationMappingPage({ page: 1, size: 8, name: form.name || '' })
  history.value = res.data?.records || []
}

watch(() => form.sourceId, refreshSourceFields)
watch(() => form.targetId, refreshTargetFields)
watch(() => [sourceTable.value, targetTable.value], () => {
  if (!form.name) form.name = buildDefaultName()
}, { deep: true })

onMounted(async () => {
  await loadTables()
  await refreshSourceFields()
  await refreshTargetFields()
  await loadHistory()
})
</script>

<template>
  <div class="page-container migration-page">
    <div class="page-top">
      <div>
        <p class="page-kicker">数据汇聚</p>
        <h1 class="page-title">数据迁移映射</h1>
        <p class="page-subtitle">把源表字段和目标表字段直接对齐，保存后可复用为数据流方案。</p>
      </div>
      <div class="page-actions">
        <el-button @click="validateCurrentMapping(true)">校验映射</el-button>
        <el-button type="warning" @click="generateDataflow">生成数据流</el-button>
        <el-input v-model="query.keyword" placeholder="搜索表名" clearable style="width: 220px" @change="loadTables" />
        <el-button @click="loadTables">刷新表</el-button>
        <el-button @click="newMapping">新建方案</el-button>
        <el-button type="primary" @click="autoMatch">自动匹配</el-button>
        <el-button type="success" :loading="saving" @click="saveMapping">保存映射</el-button>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :span="7">
        <el-card class="surface-card" shadow="never">
          <template #header>源表</template>
          <el-select v-model="form.sourceId" filterable placeholder="选择源表" style="width: 100%" :loading="loading">
            <el-option v-for="item in sourceTables" :key="item.id" :label="`${item.tableEn} / ${item.tableCn || ''}`" :value="item.id" />
          </el-select>
          <div class="table-meta">
            <div class="meta-line"><span>数据库</span><strong>{{ sourceTable?.databaseName || '-' }}</strong></div>
            <div class="meta-line"><span>分层</span><strong>{{ sourceTable?.layerCode || '-' }}</strong></div>
            <div class="meta-line"><span>行数</span><strong>{{ sourceTable?.rowCount ?? '-' }}</strong></div>
          </div>
          <el-table :data="sourceFields" size="small" border height="440">
            <el-table-column prop="fieldEn" label="字段" />
            <el-table-column prop="fieldCn" label="中文名" />
            <el-table-column prop="fieldType" label="类型" width="90" />
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="10">
        <el-card class="surface-card mapping-card" shadow="never">
          <template #header>
            <div class="card-head">
              <span>映射编辑</span>
              <el-input v-model="form.name" placeholder="方案名称" style="width: 260px" />
            </div>
          </template>
          <div class="mapping-toolbar">
            <el-select v-model="form.targetId" filterable placeholder="选择目标表" style="width: 100%" :loading="loading">
              <el-option v-for="item in targetTables" :key="item.id" :label="`${item.tableEn} / ${item.tableCn || ''}`" :value="item.id" />
            </el-select>
            <div class="inline-fields">
              <el-select v-model="form.writeMode" style="width: 150px">
                <el-option label="insert" value="insert" />
                <el-option label="upsert" value="upsert" />
                <el-option label="replace" value="replace" />
              </el-select>
              <el-input-number v-model="form.faultTolerance" :min="0" :max="999" controls-position="right" />
            </div>
          </div>

          <el-table :data="mappingRows" size="small" border height="420">
            <el-table-column label="源字段" min-width="180">
              <template #default="{ row }">
                <div class="field-line">
                  <strong>{{ row.sourceField || '-' }}</strong>
                  <span>{{ row.sourceName || '' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="目标字段" min-width="180">
              <template #default="{ row }">
                <el-select v-model="row.targetFieldId" filterable clearable placeholder="选择目标字段" style="width: 100%" @change="onTargetChange(row)">
                  <el-option v-for="item in targetFields" :key="item.id" :label="`${item.fieldEn}${item.fieldCn ? ` / ${item.fieldCn}` : ''}`" :value="item.id" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="转换规则" min-width="180">
              <template #default="{ row }">
                <el-input v-model="row.transformRule" placeholder="如：trim / cast / concat" />
              </template>
            </el-table-column>
            <el-table-column label="必填" width="70">
              <template #default="{ row }">
                <el-switch v-model="row.required" :active-value="1" :inactive-value="0" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80">
              <template #default="{ $index }">
                <el-button link type="danger" @click="removeRow($index)">删</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="mapping-footer">
            <div class="validation-panel">
              <div class="validation-metrics">
                <div>
                  <span>源字段</span>
                  <strong>{{ validation?.sourceFieldCount ?? sourceFields.length }}</strong>
                </div>
                <div>
                  <span>目标字段</span>
                  <strong>{{ validation?.targetFieldCount ?? targetFields.length }}</strong>
                </div>
                <div>
                  <span>已匹配</span>
                  <strong>{{ validation?.matchedCount ?? 0 }}</strong>
                </div>
              </div>
              <el-alert
                v-if="(validation?.warnings || []).length"
                type="warning"
                show-icon
                :closable="false"
                title="存在需要处理的映射提示"
              />
              <el-empty v-else-if="!validation" description="保存或点击校验后显示结果" :image-size="50" />
              <el-tag
                v-for="item in (validation?.warnings || [])"
                :key="item"
                type="warning"
                effect="light"
                class="warning-tag"
              >
                {{ item }}
              </el-tag>
            </div>
            <el-button @click="addCustomRow">添加一行</el-button>
            <el-input v-model="form.preSql" type="textarea" :rows="3" placeholder="前置 SQL" />
          </div>
        </el-card>
      </el-col>

      <el-col :span="7">
        <el-card class="surface-card" shadow="never">
          <template #header>目标表</template>
          <el-select v-model="form.targetId" filterable placeholder="选择目标表" style="width: 100%" :loading="loading">
            <el-option v-for="item in targetTables" :key="item.id" :label="`${item.tableEn} / ${item.tableCn || ''}`" :value="item.id" />
          </el-select>
          <div class="table-meta">
            <div class="meta-line"><span>数据库</span><strong>{{ targetTable?.databaseName || '-' }}</strong></div>
            <div class="meta-line"><span>分层</span><strong>{{ targetTable?.layerCode || '-' }}</strong></div>
            <div class="meta-line"><span>行数</span><strong>{{ targetTable?.rowCount ?? '-' }}</strong></div>
          </div>
          <el-table :data="targetFields" size="small" border height="440">
            <el-table-column prop="fieldEn" label="字段" />
            <el-table-column prop="fieldCn" label="中文名" />
            <el-table-column prop="fieldType" label="类型" width="90" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="14">
        <el-card class="surface-card" shadow="never">
          <template #header>映射预览</template>
          <pre class="json-preview">{{ mappingJson }}</pre>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card class="surface-card" shadow="never">
          <template #header>最近保存</template>
          <el-table :data="history" size="small" border height="260">
            <el-table-column prop="name" label="名称" />
            <el-table-column prop="sourceTableName" label="源表" show-overflow-tooltip />
            <el-table-column prop="targetTableName" label="目标表" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button link type="primary" @click="reloadMapping(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.migration-page {
  gap: 18px;
}

.table-meta {
  display: grid;
  gap: 8px;
  margin: 12px 0 14px;
}

.meta-line {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
  color: var(--app-muted);
}

.meta-line strong {
  color: var(--app-text);
  font-weight: 600;
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.mapping-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.mapping-toolbar {
  display: grid;
  gap: 10px;
}

.inline-fields {
  display: flex;
  gap: 10px;
}

.field-line {
  display: grid;
  gap: 4px;
}

.field-line span {
  color: var(--app-muted);
  font-size: 12px;
}

.mapping-footer {
  display: grid;
  gap: 10px;
}

.validation-panel {
  border: 1px solid var(--app-border);
  border-radius: 8px;
  padding: 12px;
  background: var(--app-surface-soft);
}

.validation-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.validation-metrics div {
  border: 1px solid var(--app-border);
  border-radius: 8px;
  padding: 10px;
  background: #fff;
}

.validation-metrics span {
  display: block;
  color: var(--app-muted);
  font-size: 12px;
}

.validation-metrics strong {
  display: block;
  margin-top: 6px;
  font-size: 20px;
}

.warning-tag {
  margin: 10px 8px 0 0;
}

.json-preview {
  margin: 0;
  min-height: 260px;
  max-height: 360px;
  overflow: auto;
  padding: 16px;
  background: #0f172a;
  color: #dbeafe;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.7;
}
</style>
