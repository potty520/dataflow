<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>质量配置</h2>
        <div class="sub">工单流转规则、稽核任务模板、质量评估报告模板</div>
      </div>
    </div>

    <el-tabs v-model="tab" type="border-card">
      <el-tab-pane label="工单规则" name="issue">
        <div class="toolbar">
          <span class="hint">控制是否自动建单、超时/升级小时数与默认责任人；保存后对新产生的问题单生效</span>
          <span class="spacer"></span>
          <el-button size="small" type="primary" @click="openIssueRule()">新建规则</el-button>
        </div>
        <el-table :data="issueRules" size="small" v-loading="loading">
          <el-table-column prop="code" label="编码" width="170"><template #default="{ row }"><span class="mono">{{ row.code }}</span></template></el-table-column>
          <el-table-column prop="name" label="名称" min-width="150" />
          <el-table-column prop="autoCreate" label="自动建单" width="90"><template #default="{ row }">{{ row.autoCreate ? '是' : '否' }}</template></el-table-column>
          <el-table-column prop="overdueHours" label="超时(小时)" width="100" align="right" />
          <el-table-column prop="escalateHours" label="升级(小时)" width="100" align="right" />
          <el-table-column prop="defaultAssignee" label="默认责任人" width="120" />
          <el-table-column prop="status" label="状态" width="90" />
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openIssueRule(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="removeIssueRule(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="稽核模板" name="check">
        <div class="toolbar">
          <span class="hint">把常用规则组合保存为模板，一键生成稽核任务</span>
          <span class="spacer"></span>
          <el-button size="small" type="primary" @click="openCheckTemplate()">新建模板</el-button>
        </div>
        <el-table :data="checkTemplates" size="small" v-loading="loading">
          <el-table-column prop="code" label="编码" width="170"><template #default="{ row }"><span class="mono">{{ row.code }}</span></template></el-table-column>
          <el-table-column prop="name" label="名称" min-width="150" />
          <el-table-column prop="checkMode" label="模式" width="90" />
          <el-table-column prop="scheduleType" label="调度" width="100" />
          <el-table-column prop="batchSize" label="批次" width="90" align="right" />
          <el-table-column label="规则" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ (row.ruleCodeList || []).join('、') }}</template>
          </el-table-column>
          <el-table-column label="操作" width="190">
            <template #default="{ row }">
              <el-button link type="success" size="small" @click="applyTemplate(row)">生成任务</el-button>
              <el-button link type="primary" size="small" @click="openCheckTemplate(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="removeCheckTemplate(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="报告模板" name="report">
        <div class="toolbar">
          <span class="hint">配置质量评估报告章节（JSON）；报告生成时可选择模板</span>
          <span class="spacer"></span>
          <el-button size="small" type="primary" @click="openReportTemplate()">新建模板</el-button>
        </div>
        <el-table :data="reportTemplates" size="small" v-loading="loading">
          <el-table-column prop="code" label="编码" width="170"><template #default="{ row }"><span class="mono">{{ row.code }}</span></template></el-table-column>
          <el-table-column prop="name" label="名称" min-width="180" />
          <el-table-column prop="reportType" label="类型" width="120" />
          <el-table-column prop="engine" label="引擎" width="90" />
          <el-table-column prop="status" label="状态" width="90" />
          <el-table-column label="操作" width="170">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openReportTemplate(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="removeReportTemplate(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="issueRuleDialog" :title="issueRuleForm.id ? '编辑工单规则' : '新建工单规则'" width="520px">
      <el-form label-width="110px" size="small">
        <el-form-item label="编码"><el-input v-model="issueRuleForm.code" :disabled="!!issueRuleForm.id" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="issueRuleForm.name" /></el-form-item>
        <el-form-item label="自动建单"><el-switch v-model="issueRuleForm.autoCreate" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="超时小时"><el-input-number v-model="issueRuleForm.overdueHours" :min="1" :max="720" /></el-form-item>
        <el-form-item label="升级小时"><el-input-number v-model="issueRuleForm.escalateHours" :min="1" :max="1440" /></el-form-item>
        <el-form-item label="默认责任人"><el-input v-model="issueRuleForm.defaultAssignee" /></el-form-item>
        <el-form-item label="状态"><el-radio-group v-model="issueRuleForm.status"><el-radio label="ENABLED">启用</el-radio><el-radio label="DISABLED">停用</el-radio></el-radio-group></el-form-item>
        <el-form-item label="说明"><el-input v-model="issueRuleForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="issueRuleDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="saveIssueRule">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="checkTemplateDialog" :title="checkTemplateForm.id ? '编辑稽核模板' : '新建稽核模板'" width="560px">
      <el-form label-width="100px" size="small">
        <el-form-item label="编码"><el-input v-model="checkTemplateForm.code" :disabled="!!checkTemplateForm.id" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="checkTemplateForm.name" /></el-form-item>
        <el-form-item label="模式"><el-radio-group v-model="checkTemplateForm.checkMode"><el-radio label="FULL">全量</el-radio><el-radio label="INCREMENTAL">增量</el-radio></el-radio-group></el-form-item>
        <el-form-item label="调度"><el-select v-model="checkTemplateForm.scheduleType" style="width:100%"><el-option label="手动" value="MANUAL" /><el-option label="CRON" value="CRON" /></el-select></el-form-item>
        <el-form-item label="CRON"><el-input v-model="checkTemplateForm.cron" placeholder="如 0 2 * * *" /></el-form-item>
        <el-form-item label="批次大小"><el-input-number v-model="checkTemplateForm.batchSize" :min="100" :max="1000000" /></el-form-item>
        <el-form-item label="规则编码"><el-input v-model="checkTemplateForm.ruleCodesText" type="textarea" :rows="3" placeholder="多个规则编码用逗号分隔" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="checkTemplateForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="checkTemplateDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="saveCheckTemplate">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reportTemplateDialog" :title="reportTemplateForm.id ? '编辑报告模板' : '新建报告模板'" width="560px">
      <el-form label-width="100px" size="small">
        <el-form-item label="编码"><el-input v-model="reportTemplateForm.code" :disabled="!!reportTemplateForm.id" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="reportTemplateForm.name" /></el-form-item>
        <el-form-item label="章节(JSON)"><el-input v-model="reportTemplateForm.sectionsText" type="textarea" :rows="6" class="mono" placeholder='["总体评分","维度得分","Top问题"]' /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="reportTemplateDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="saveReportTemplate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { qualityApi, reportApi } from '@/api'

const tab = ref('issue')
const loading = ref(false)
const issueRules = ref([])
const checkTemplates = ref([])
const reportTemplates = ref([])

const issueRuleDialog = ref(false)
const issueRuleForm = reactive({ id: null, code: '', name: '', autoCreate: 1, overdueHours: 72, escalateHours: 96, defaultAssignee: '刘琴', status: 'ENABLED', remark: '' })
const checkTemplateDialog = ref(false)
const checkTemplateForm = reactive({ id: null, code: '', name: '', checkMode: 'FULL', scheduleType: 'MANUAL', cron: '', batchSize: 10000, ruleCodesText: '', remark: '' })
const reportTemplateDialog = ref(false)
const reportTemplateForm = reactive({ id: null, code: '', name: '', sectionsText: '["总体评分","维度得分","Top问题"]' })

const load = async () => {
  loading.value = true
  const [i, c, r] = await Promise.allSettled([qualityApi.issueRules(), qualityApi.checkTemplates(), reportApi.templates()])
  issueRules.value = i.status === 'fulfilled' ? (i.value.records || []) : []
  checkTemplates.value = c.status === 'fulfilled' ? (c.value.records || []) : []
  reportTemplates.value = r.status === 'fulfilled' ? (r.value.records || []) : []
  loading.value = false
}

const openIssueRule = (row) => {
  Object.assign(issueRuleForm, { id: null, code: '', name: '', autoCreate: 1, overdueHours: 72, escalateHours: 96, defaultAssignee: '刘琴', status: 'ENABLED', remark: '' })
  if (row) Object.assign(issueRuleForm, { id: row.id, code: row.code, name: row.name, autoCreate: row.autoCreate, overdueHours: row.overdueHours, escalateHours: row.escalateHours, defaultAssignee: row.defaultAssignee, status: row.status, remark: row.remark })
  issueRuleDialog.value = true
}
const saveIssueRule = async () => {
  try {
    if (issueRuleForm.id) await qualityApi.updateIssueRule(issueRuleForm.id, { ...issueRuleForm })
    else await qualityApi.createIssueRule({ ...issueRuleForm })
    ElMessage.success('规则已保存'); issueRuleDialog.value = false; load()
  } catch (e) { /* http 层已提示 */ }
}
const removeIssueRule = async (row) => {
  try { await ElMessageBox.confirm('删除工单规则 ' + row.code + '？', '提示', { type: 'warning' }); await qualityApi.deleteIssueRule(row.id); load() }
  catch (e) { /* 取消 */ }
}

const openCheckTemplate = (row) => {
  Object.assign(checkTemplateForm, { id: null, code: '', name: '', checkMode: 'FULL', scheduleType: 'MANUAL', cron: '', batchSize: 10000, ruleCodesText: '', remark: '' })
  if (row) Object.assign(checkTemplateForm, { id: row.id, code: row.code, name: row.name, checkMode: row.checkMode, scheduleType: row.scheduleType, cron: row.cron || '', batchSize: row.batchSize, ruleCodesText: (row.ruleCodeList || []).join(','), remark: row.remark })
  checkTemplateDialog.value = true
}
const saveCheckTemplate = async () => {
  const body = { ...checkTemplateForm, ruleCodes: checkTemplateForm.ruleCodesText.split(',').map((s) => s.trim()).filter(Boolean) }
  delete body.ruleCodesText
  try {
    if (checkTemplateForm.id) await qualityApi.updateCheckTemplate(checkTemplateForm.id, body)
    else await qualityApi.createCheckTemplate(body)
    ElMessage.success('模板已保存'); checkTemplateDialog.value = false; load()
  } catch (e) { /* http 层已提示 */ }
}
const removeCheckTemplate = async (row) => {
  try { await ElMessageBox.confirm('删除模板 ' + row.code + '？', '提示', { type: 'warning' }); await qualityApi.deleteCheckTemplate(row.id); load() }
  catch (e) { /* 取消 */ }
}
const applyTemplate = async (row) => {
  try {
    const d = await qualityApi.applyCheckTemplate(row.id)
    ElMessage.success('已生成稽核任务 ' + d.code + '（绑定 ' + d.rules + ' 条规则）')
  } catch (e) { /* http 层已提示 */ }
}

const openReportTemplate = (row) => {
  Object.assign(reportTemplateForm, { id: null, code: '', name: '', sectionsText: '["总体评分","维度得分","Top问题"]' })
  if (row) {
    Object.assign(reportTemplateForm, { id: row.id, code: row.code, name: row.name, sectionsText: row.content || '[]' })
  }
  reportTemplateDialog.value = true
}
const saveReportTemplate = async () => {
  let sections
  try { sections = JSON.parse(reportTemplateForm.sectionsText || '[]') } catch (e) { ElMessage.warning('章节 JSON 格式不正确'); return }
  try {
    if (reportTemplateForm.id) await reportApi.updateTemplate(reportTemplateForm.id, { name: reportTemplateForm.name, sections })
    else await reportApi.createTemplate({ code: reportTemplateForm.code, name: reportTemplateForm.name, sections })
    ElMessage.success('报告模板已保存'); reportTemplateDialog.value = false; load()
  } catch (e) { /* http 层已提示 */ }
}
const removeReportTemplate = async (row) => {
  try { await ElMessageBox.confirm('删除报告模板 ' + row.code + '？', '提示', { type: 'warning' }); await reportApi.deleteTemplate(row.id); load() }
  catch (e) { /* 取消 */ }
}

onMounted(load)
</script>

<style scoped>
.page { padding: 4px; }
.hint { color: #909399; font-size: 12px; }
</style>
