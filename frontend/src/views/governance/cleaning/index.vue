<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Delete, EditPen, Plus, RefreshRight, Search, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createGovCleaning,
  deleteGovCleaning,
  executeGovCleaning,
  govCleaningOverview,
  govCleaningPage,
  govCleaningPreview,
  govCleaningTemplates,
  updateGovCleaning
} from '../../../api'

const layers = ['ODS', 'STD', 'DWD', 'DWS', 'ADS']
const overview = ref({})
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const templates = ref([])
const query = reactive({ page: 1, size: 10, keyword: '', layerCode: '', ruleState: '' })
const dialogVisible = ref(false)
const previewVisible = ref(false)
const previewLoading = ref(false)
const isEdit = ref(false)
const form = ref(defaultForm())
const previewData = ref(null)

function defaultForm() {
  return {
    layerCode: 'ODS',
    tableEn: '',
    tableCn: '',
    datasourceId: null,
    cleanRules: '',
    remark: ''
  }
}

const configuredRate = computed(() => {
  const totalCount = Number(overview.value.modelCount || 0)
  const configured = Number(overview.value.configuredCount || 0)
  if (!totalCount) return 0
  return Math.round((configured / totalCount) * 100)
})

async function loadOverview() {
  const res = await govCleaningOverview()
  overview.value = res.data || {}
}

async function loadTemplates() {
  const res = await govCleaningTemplates()
  templates.value = res.data || []
}

async function loadData() {
  loading.value = true
  try {
    const res = await govCleaningPage(query)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

function openCreate() {
  isEdit.value = false
  form.value = defaultForm()
  dialogVisible.value = true
}

function useTemplate(item) {
  form.value = {
    ...form.value,
    cleanRules: item.rules || ''
  }
  if (!dialogVisible.value) dialogVisible.value = true
  ElMessage.success('已应用清洗模板')
}

function openEdit(row) {
  isEdit.value = true
  form.value = {
    ...defaultForm(),
    ...row
  }
  dialogVisible.value = true
}

async function save() {
  const payload = { ...form.value }
  if (isEdit.value) {
    await updateGovCleaning(payload.id, payload)
  } else {
    await createGovCleaning(payload)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  await Promise.all([loadOverview(), loadData()])
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除清洗模型「${row.tableEn}」？`, '提示', { type: 'warning' })
  await deleteGovCleaning(row.id)
  ElMessage.success('删除成功')
  await Promise.all([loadOverview(), loadData()])
}

async function openPreview(row) {
  previewVisible.value = true
  previewLoading.value = true
  previewData.value = null
  try {
    const res = await govCleaningPreview(row.id)
    previewData.value = res.data || null
  } finally {
    previewLoading.value = false
  }
}

async function executeCleaning(row) {
  previewVisible.value = true
  previewLoading.value = true
  previewData.value = null
  try {
    const res = await executeGovCleaning(row.id)
    previewData.value = res.data || null
    ElMessage.success('清洗执行完成')
    await Promise.all([loadOverview(), loadData()])
  } finally {
    previewLoading.value = false
  }
}

function formatRuleCount(row) {
  return row.ruleCount ?? String(row.cleanRules || '')
    .split(/[,，;；\n\r]+/)
    .map(s => s.trim())
    .filter(Boolean).length
}

function ruleState(row) {
  return row.cleanRules ? '已配置' : '待补全'
}

function ruleType(row) {
  return row.cleanRules ? 'success' : 'warning'
}

function buildLayerName(code) {
  return code || 'OTHER'
}

onMounted(async () => {
  await Promise.all([loadOverview(), loadTemplates(), loadData()])
})
</script>

<template>
  <div class="page-shell">
    <section class="page-top">
      <div>
        <p class="page-kicker">GOVERNANCE / CLEANING</p>
        <h1 class="page-title">数据清洗中心</h1>
        <p class="page-subtitle">围绕建模表统一维护清洗规则，集中管理空值、重复值、格式化和脱敏策略。</p>
      </div>
      <div class="page-actions">
        <el-button type="primary" :icon="Plus" @click="openCreate">新增模型</el-button>
        <el-button :icon="RefreshRight" @click="loadData">刷新列表</el-button>
      </div>
    </section>

    <div class="metric-grid">
      <div class="metric-card">
        <div class="metric-label">清洗模型</div>
        <div class="metric-value">{{ overview.modelCount || 0 }}</div>
        <div class="metric-note">覆盖数仓建模表</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">已配置规则</div>
        <div class="metric-value">{{ overview.configuredCount || 0 }}</div>
        <div class="metric-note">配置率 {{ configuredRate }}%</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">待补全规则</div>
        <div class="metric-value">{{ overview.pendingCount || 0 }}</div>
        <div class="metric-note">建议优先处理 ODS / STD</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">规则片段</div>
        <div class="metric-value">{{ overview.ruleCount || 0 }}</div>
        <div class="metric-note">按分隔符拆分统计</div>
      </div>
    </div>

    <el-card class="surface-card">
      <template #header>清洗模板</template>
      <div class="template-grid">
        <div v-for="item in templates" :key="item.name" class="template-item" @click="useTemplate(item)">
          <div class="template-title">{{ item.name }}</div>
          <div class="template-desc">{{ item.description }}</div>
          <div class="template-rules">{{ item.rules }}</div>
        </div>
      </div>
    </el-card>

    <el-card class="surface-card">
      <div class="search-bar">
        <el-input v-model="query.keyword" placeholder="表名关键词" clearable style="width: 180px" />
        <el-select v-model="query.layerCode" placeholder="分层" clearable style="width: 140px">
          <el-option v-for="layer in layers" :key="layer" :label="layer" :value="layer" />
        </el-select>
        <el-select v-model="query.ruleState" placeholder="规则状态" clearable style="width: 140px">
          <el-option label="已配置" value="CONFIGURED" />
          <el-option label="待补全" value="EMPTY" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="loadData">查询</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="layerCode" label="分层" width="90">
          <template #default="{ row }">
            <el-tag effect="light">{{ buildLayerName(row.layerCode) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="tableEn" label="英文表名" min-width="180" />
        <el-table-column prop="tableCn" label="中文表名" min-width="180" />
        <el-table-column prop="cleanRules" label="清洗规则" min-width="260" show-overflow-tooltip />
        <el-table-column label="规则数" width="90">
          <template #default="{ row }">{{ formatRuleCount(row) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="ruleType(row)" effect="light">{{ ruleState(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="180" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="View" @click="openPreview(row)">预览</el-button>
            <el-button link type="success" :icon="EditPen" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" :icon="Delete" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        style="margin-top: 16px"
        @size-change="loadData"
        @current-change="loadData"
      />
    </el-card>

    <el-card class="surface-card">
      <template #header>最近更新</template>
      <div class="recent-grid">
        <div v-for="item in (overview.recentModels || [])" :key="item.id" class="recent-item">
          <div class="recent-title">{{ item.tableEn }}</div>
          <div class="recent-sub">{{ item.tableCn }}</div>
          <div class="recent-meta">
            <el-tag size="small" effect="light">{{ item.layerCode }}</el-tag>
            <span>{{ item.ruleCount || 0 }} 条规则</span>
          </div>
        </div>
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑清洗模型' : '新增清洗模型'" width="580px">
      <el-form label-width="96px">
        <el-form-item label="分层">
          <el-select v-model="form.layerCode" style="width: 100%">
            <el-option v-for="layer in layers" :key="layer" :label="layer" :value="layer" />
          </el-select>
        </el-form-item>
        <el-form-item label="英文表名"><el-input v-model="form.tableEn" /></el-form-item>
        <el-form-item label="中文表名"><el-input v-model="form.tableCn" /></el-form-item>
        <el-form-item label="数据源ID"><el-input-number v-model="form.datasourceId" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="清洗规则">
          <el-input
            v-model="form.cleanRules"
            type="textarea"
            :rows="4"
            placeholder="空值过滤,重复值去重,手机号脱敏"
          />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="previewVisible" title="清洗预览" size="520px">
      <el-skeleton v-if="previewLoading" :rows="8" animated />
      <template v-else-if="previewData">
        <div class="drawer-actions">
          <el-button type="warning" @click="executeCleaning(previewData.model)">执行清洗</el-button>
        </div>
        <div class="preview-summary">
          <div class="metric-card">
            <div class="metric-label">预估清洗前</div>
            <div class="metric-value">{{ previewData.summary?.beforeRows || 0 }}</div>
          </div>
          <div class="metric-card">
            <div class="metric-label">预估清洗后</div>
            <div class="metric-value">{{ previewData.summary?.afterRows || 0 }}</div>
          </div>
          <div class="metric-card">
            <div class="metric-label">风险等级</div>
            <div class="metric-value">{{ previewData.summary?.riskLevel || '-' }}</div>
          </div>
        </div>

        <el-descriptions :column="1" border style="margin-bottom: 16px">
          <el-descriptions-item label="表名">{{ previewData.model?.tableEn }}</el-descriptions-item>
          <el-descriptions-item label="规则数">{{ previewData.summary?.ruleCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="预估剔除">{{ previewData.summary?.removedRows || 0 }}</el-descriptions-item>
        </el-descriptions>

        <div class="section-label">清洗步骤</div>
        <el-timeline>
          <el-timeline-item
            v-for="step in (previewData.steps || [])"
            :key="step.order + step.title"
            :timestamp="step.order"
          >
            <strong>{{ step.title }}</strong>
            <div class="subtle">{{ step.detail }}</div>
            <div class="subtle">影响：{{ step.impact }}</div>
          </el-timeline-item>
        </el-timeline>

        <div class="section-label" style="margin-top: 16px">建议</div>
        <el-space wrap>
          <el-tag v-for="item in (previewData.suggestions || [])" :key="item" effect="light">{{ item }}</el-tag>
        </el-space>
      </template>
      <el-empty v-else description="暂无预览数据" />
    </el-drawer>
  </div>
</template>

<style scoped>
.recent-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px;
}

.template-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.template-item {
  border: 1px solid var(--app-border);
  border-radius: 8px;
  padding: 14px;
  background: var(--app-surface-soft);
  cursor: pointer;
}

.template-item:hover {
  border-color: var(--app-primary);
  background: #fff;
}

.template-title {
  font-weight: 600;
  color: var(--app-text);
}

.template-desc {
  margin-top: 6px;
  color: var(--app-muted);
  font-size: 12px;
}

.template-rules {
  margin-top: 10px;
  color: var(--app-primary);
  font-size: 12px;
  line-height: 1.6;
}

.drawer-actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.recent-item {
  border: 1px solid var(--app-border);
  border-radius: 8px;
  padding: 14px;
  background: #fff;
}

.recent-title {
  font-weight: 600;
  color: var(--app-text);
}

.recent-sub {
  margin-top: 4px;
  color: var(--app-muted);
  font-size: 13px;
}

.recent-meta {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--app-muted);
  font-size: 12px;
}

.preview-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.section-label {
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 600;
  color: var(--app-text);
}

.subtle {
  margin-top: 4px;
  color: var(--app-muted);
  font-size: 12px;
  line-height: 1.5;
}
</style>
