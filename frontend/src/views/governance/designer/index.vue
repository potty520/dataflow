<script setup>
import { onMounted, reactive, ref } from 'vue'
import { govLayerList, datasourceList, createGovModel, generateDdl } from '../../../api'
import { ElMessage } from 'element-plus'

const layers = ref([])
const datasources = ref([])
const dataTypes = ['STRING', 'INT', 'BIGINT', 'DOUBLE', 'DECIMAL', 'DATE', 'TIMESTAMP', 'BOOLEAN']
const form = reactive({
  tableEn: '', tableCn: '', layerCode: '', domainId: null,
  datasourceId: null, description: '', lifecycleDays: 365, lifecyclePolicy: 'ARCHIVE'
})
const fields = ref([])
const ddlDialogVisible = ref(false)
const ddlEngine = ref('hive')
const ddlContent = ref('')

let fieldId = 0

async function loadOptions() {
  const [lRes, dRes] = await Promise.all([govLayerList(), datasourceList()])
  layers.value = lRes.data
  datasources.value = dRes.data
}

function addField() {
  fields.value.push({
    id: ++fieldId, fieldEn: '', fieldCn: '', dataType: 'STRING',
    length: null, precision: null, nullable: true, isPk: false,
    isPartition: false, defaultVal: '', comment: ''
  })
}

function removeField(index) {
  fields.value.splice(index, 1)
}

async function previewDdl() {
  if (!form.tableEn) { ElMessage.warning('请先输入英文表名'); return }
  try {
    // We build a quick preview without saving first
    const res = await generateDdl(0, { engine: ddlEngine.value })
    ddlContent.value = generateLocalDdl()
    ddlDialogVisible.value = true
  } catch {
    ddlContent.value = generateLocalDdl()
    ddlDialogVisible.value = true
  }
}

function generateLocalDdl() {
  if (fields.value.length === 0) return '-- 暂无可预览的字段\n-- 请添加字段后重试'
  let sb = ''
  sb += '-- DDL for ' + form.tableEn + '\n'
  sb += 'CREATE TABLE IF NOT EXISTS ' + form.tableEn + ' (\n'
  const cols = fields.value.map(f => {
    let def = '  ' + f.fieldEn + ' ' + f.dataType
    if (f.length && (f.dataType === 'STRING' || f.dataType === 'VARCHAR')) def += '(' + f.length + ')'
    if (f.precision && f.dataType === 'DECIMAL') def += '(18,' + f.precision + ')'
    if (!f.nullable) def += ' NOT NULL'
    if (f.defaultVal) def += " DEFAULT '" + f.defaultVal + "'"
    if (f.comment) def += " COMMENT '" + f.comment + "'"
    return def
  })
  sb += cols.join(',\n')
  if (fields.value.some(f => f.isPk)) {
    const pkFields = fields.value.filter(f => f.isPk).map(f => f.fieldEn)
    sb += ',\n  PRIMARY KEY (' + pkFields.join(', ') + ')'
  }
  sb += '\n)'
  if (form.tableCn) sb += " COMMENT '" + form.tableCn + "'"
  const pkField = fields.value.find(f => f.isPartition)
  if (pkField) sb += '\nPARTITIONED BY (' + pkField.fieldEn + ' STRING)'
  sb += '\nSTORED AS PARQUET'
  sb += ';\n'
  return sb
}

async function save() {
  if (!form.tableEn) { ElMessage.warning('请输入英文表名'); return }
  try {
    await createGovModel({
      ...form,
      fields: fields.value.map(f => ({
        fieldEn: f.fieldEn, fieldCn: f.fieldCn, fieldType: f.dataType,
        description: f.comment, isSensitive: 0
      }))
    })
    // Also create asset_table + asset_field for it
    ElMessage.success('保存成功')
    // Reset
    form.tableEn = ''; form.tableCn = ''; form.description = ''
    fields.value = []; fieldId = 0
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || '未知错误'))
  }
}

function exportDdl() {
  const ddl = generateLocalDdl()
  const blob = new Blob([ddl], { type: 'text/plain' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = (form.tableEn || 'table') + '.sql'
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('DDL已导出')
}

onMounted(loadOptions)
</script>

<template>
  <div class="page-container">
    <div class="page-header">可视化表设计器</div>
    <el-form label-width="100px" style="max-width:900px">
      <el-row :gutter="16">
        <el-col :span="8">
          <el-form-item label="英文表名"><el-input v-model="form.tableEn" placeholder="如 dwd_user_info" /></el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="中文表名"><el-input v-model="form.tableCn" placeholder="如 用户明细表" /></el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="数仓分层">
            <el-select v-model="form.layerCode" style="width:100%">
              <el-option v-for="l in layers" :key="l.id" :label="l.layerCode + ' - ' + l.layerName" :value="l.layerCode" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="8">
          <el-form-item label="数据源">
            <el-select v-model="form.datasourceId" style="width:100%" clearable>
              <el-option v-for="d in datasources" :key="d.id" :label="d.name" :value="d.id" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="16">
          <el-form-item label="描述"><el-input v-model="form.description" /></el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="8">
          <el-form-item label="生命周期(天)"><el-input-number v-model="form.lifecycleDays" :min="1" :max="3650" /></el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="过期策略">
            <el-select v-model="form.lifecyclePolicy" style="width:100%">
              <el-option label="归档" value="ARCHIVE" />
              <el-option label="删除" value="DELETE" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <div style="margin:16px 0">
      <el-button type="primary" @click="addField">+ 添加字段</el-button>
      <span style="color:#999;margin-left:8px">共 {{ fields.length }} 个字段</span>
    </div>

    <el-table :data="fields" border>
      <el-table-column label="序号" width="60">
        <template #default="{ $index }">{{ $index + 1 }}</template>
      </el-table-column>
      <el-table-column label="字段英文名" width="150">
        <template #default="{ row }"><el-input v-model="row.fieldEn" size="small" /></template>
      </el-table-column>
      <el-table-column label="字段中文名" width="150">
        <template #default="{ row }"><el-input v-model="row.fieldCn" size="small" /></template>
      </el-table-column>
      <el-table-column label="数据类型" width="130">
        <template #default="{ row }">
          <el-select v-model="row.dataType" size="small">
            <el-option v-for="t in dataTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="长度" width="80">
        <template #default="{ row }"><el-input-number v-model="row.length" size="small" :min="0" controls-position="right" /></template>
      </el-table-column>
      <el-table-column label="精度" width="80">
        <template #default="{ row }"><el-input-number v-model="row.precision" size="small" :min="0" controls-position="right" /></template>
      </el-table-column>
      <el-table-column label="可空" width="70" align="center">
        <template #default="{ row }"><el-checkbox v-model="row.nullable" /></template>
      </el-table-column>
      <el-table-column label="主键" width="70" align="center">
        <template #default="{ row }"><el-checkbox v-model="row.isPk" /></template>
      </el-table-column>
      <el-table-column label="分区" width="70" align="center">
        <template #default="{ row }"><el-checkbox v-model="row.isPartition" /></template>
      </el-table-column>
      <el-table-column label="默认值" width="130">
        <template #default="{ row }"><el-input v-model="row.defaultVal" size="small" /></template>
      </el-table-column>
      <el-table-column label="注释" min-width="150">
        <template #default="{ row }"><el-input v-model="row.comment" size="small" /></template>
      </el-table-column>
      <el-table-column label="操作" width="80">
        <template #default="{ $index }">
          <el-button link type="danger" @click="removeField($index)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div style="margin-top:24px;display:flex;gap:12px">
      <el-button type="warning" @click="previewDdl">预览DDL</el-button>
      <el-button type="primary" @click="save">保存</el-button>
      <el-button type="success" @click="exportDdl">导出DDL</el-button>
    </div>

    <el-dialog v-model="ddlDialogVisible" title="DDL预览" width="700px">
      <el-select v-model="ddlEngine" style="margin-bottom:12px;width:120px">
        <el-option label="Hive" value="hive" />
        <el-option label="MySQL" value="mysql" />
        <el-option label="Spark" value="spark" />
      </el-select>
      <pre style="background:#f5f5f5;padding:16px;border-radius:4px;overflow-x:auto;max-height:500px;font-family:monospace;font-size:13px">{{ ddlContent }}</pre>
      <template #footer>
        <el-button @click="ddlDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="exportDdl(); ddlDialogVisible = false">导出SQL文件</el-button>
      </template>
    </el-dialog>
  </div>
</template>
