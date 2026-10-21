<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>数据建模</h2>
        <div class="sub">概念 / 逻辑 / 物理模型分层设计，支持发布审批、版本对比、影响分析与自助建表</div>
      </div>
      <el-button type="primary" size="small" :icon="Plus" @click="createDialog = true">新建模型</el-button>
    </div>

    <div class="grid grid-4" style="margin-bottom:12px">
      <div class="stat"><div class="label">模型总数</div><div class="value">{{ stats.modelCount }}</div><div class="foot">实体 {{ stats.entityCount }} · 属性 {{ stats.attributeCount }}</div></div>
      <div class="stat green"><div class="label">已发布</div><div class="value">{{ stats.publishedCount }}</div><div class="foot">可直接生成建表 DDL</div></div>
      <div class="stat orange"><div class="label">待审批</div><div class="value">{{ stats.pendingApproval }}</div><div class="foot">发布流程审核中</div></div>
      <div class="stat"><div class="label">行业模板</div><div class="value">{{ templates.length }}</div><div class="foot">来自行业配置包</div></div>
    </div>

    <div class="card" style="padding:0">
      <el-table :data="models" size="small" @row-click="openModel" style="cursor:pointer">
        <el-table-column prop="code" label="模型编码" width="170"><template #default="{ row }"><span class="mono">{{ row.code }}</span></template></el-table-column>
        <el-table-column prop="cnName" label="模型名称" min-width="160" />
        <el-table-column prop="modelType" label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ { CONCEPT: '概念', LOGICAL: '逻辑', PHYSICAL: '物理' }[row.modelType] || row.modelType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="layer" label="分层" width="70" />
        <el-table-column prop="industry" label="行业" width="90" />
        <el-table-column prop="entityCount" label="实体" width="70" align="right" />
        <el-table-column prop="attributeCount" label="属性" width="70" align="right" />
        <el-table-column prop="version" label="版本" width="80" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'PUBLISHED' ? 'success' : row.status === 'REVIEWING' ? 'warning' : 'info'">
              {{ { DRAFT: '草稿', REVIEWING: '审批中', PUBLISHED: '已发布' }[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="owner" label="责任人" width="86" />
        <el-table-column prop="updateTime" label="更新时间" width="150"><template #default="{ row }">{{ fmt(row.updateTime) }}</template></el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click.stop="openModel(row)">设计</el-button>
            <el-button link type="primary" size="small" @click.stop="showDdl(row)">DDL</el-button>
            <el-button v-if="row.status !== 'PUBLISHED'" link type="warning" size="small" @click.stop="publish(row)">提交发布</el-button>
            <el-button v-if="row.status === 'REVIEWING'" link type="success" size="small" @click.stop="approve(row)">审批</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-drawer v-model="drawer" :title="current.cnName || ''" size="66%">
      <div v-if="current.id">
        <div class="toolbar">
          <el-tag effect="plain" size="small">{{ current.code }}</el-tag>
          <el-tag size="small" type="info">{{ { CONCEPT: '概念模型', LOGICAL: '逻辑模型', PHYSICAL: '物理模型' }[current.modelType] }}</el-tag>
          <el-tag size="small">v{{ current.version }}</el-tag>
          <el-tag size="small" :type="current.status === 'PUBLISHED' ? 'success' : 'warning'">{{ current.status }}</el-tag>
          <span class="spacer"></span>
          <el-button size="small" @click="showImpact">影响分析</el-button>
        </div>
        <div class="section-title">实体与属性（{{ (current.entities || []).length }} 个实体）</div>
        <div class="grid grid-2">
          <div v-for="e in current.entities" :key="e.id" class="card" style="margin:0">
            <div class="card-head">
              <h3>{{ e.cnName }} <span class="muted mono" style="font-size:11px">{{ e.code }}</span></h3>
              <div>
                <el-tag size="small" effect="plain">{{ e.entityType }}</el-tag>
                <el-button link type="primary" size="small" style="margin-left:8px" @click="executeDdl(e)">执行建表</el-button>
              </div>
            </div>
            <el-table :data="e.attributes || []" size="small" max-height="240">
              <el-table-column prop="code" label="字段" width="120"><template #default="{ row }"><span class="mono">{{ row.code }}</span></template></el-table-column>
              <el-table-column prop="cnName" label="中文名" width="110" />
              <el-table-column prop="dataType" label="类型" width="90" />
              <el-table-column prop="dataLen" label="长度" width="60" />
              <el-table-column prop="isPk" label="PK" width="46" />
              <el-table-column prop="stdElementCode" label="标准" width="120"><template #default="{ row }"><span class="mono">{{ row.stdElementCode || '-' }}</span></template></el-table-column>
              <el-table-column prop="securityLevel" label="级别" width="60" />
            </el-table>
          </div>
        </div>
        <div class="section-title">实体关系</div>
        <el-table :data="current.relations || []" size="small">
          <el-table-column prop="sourceEntity" label="源实体" min-width="160" />
          <el-table-column prop="relationType" label="关系" width="80" />
          <el-table-column prop="targetEntity" label="目标实体" min-width="160" />
          <el-table-column prop="joinCondition" label="关联条件" min-width="240"><template #default="{ row }"><span class="mono">{{ row.joinCondition }}</span></template></el-table-column>
        </el-table>
        <div class="section-title">版本与发布记录</div>
        <el-table :data="current.versions || []" size="small">
          <el-table-column prop="version" label="版本" width="90" />
          <el-table-column prop="changeNote" label="变更说明" min-width="240" />
          <el-table-column prop="operator" label="操作人" width="90" />
          <el-table-column prop="createTime" label="时间" width="150"><template #default="{ row }">{{ fmt(row.createTime) }}</template></el-table-column>
        </el-table>
        <div class="section-title">审批单</div>
        <el-table :data="current.approvals || []" size="small">
          <el-table-column prop="version" label="版本" width="90" />
          <el-table-column prop="applicant" label="申请人" width="90" />
          <el-table-column prop="auditor" label="审批人" width="90" />
          <el-table-column prop="status" label="状态" width="100" />
          <el-table-column prop="comment" label="意见" min-width="180" />
          <el-table-column prop="createTime" label="提交时间" width="150"><template #default="{ row }">{{ fmt(row.createTime) }}</template></el-table-column>
        </el-table>
      </div>
    </el-drawer>

    <el-dialog v-model="createDialog" title="新建模型" width="520px">
      <el-form :model="form" label-width="90px" size="small">
        <el-form-item label="编码"><el-input v-model="form.code" placeholder="MDL_XXX" /></el-form-item>
        <el-form-item label="中文名"><el-input v-model="form.cnName" /></el-form-item>
        <el-form-item label="英文名"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="模型类型">
          <el-select v-model="form.modelType" style="width:100%">
            <el-option label="概念模型" value="CONCEPT" /><el-option label="逻辑模型" value="LOGICAL" /><el-option label="物理模型" value="PHYSICAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="分层">
          <el-select v-model="form.layer" style="width:100%">
            <el-option v-for="l in ['ODS','DWD','DWS','ADS']" :key="l" :label="l" :value="l" />
          </el-select>
        </el-form-item>
        <el-form-item label="行业">
          <el-select v-model="form.industry" style="width:100%">
            <el-option v-for="i in ['GOV','TELECOM','FINANCE','INDUSTRY','RETAIL']" :key="i" :label="i" :value="i" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="createDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="create">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="ddlDialog" title="生成建表 DDL（可执行）" width="720px">
      <div v-for="s in ddlResult" :key="s.entity" style="margin-bottom:12px">
        <div class="muted" style="font-size:12px;margin-bottom:4px">{{ s.entity }} · {{ s.cnName }}</div>
        <pre class="ddl">{{ s.ddl }}</pre>
      </div>
      <el-empty v-if="!ddlResult.length" description="该模型暂无实体" :image-size="60" />
    </el-dialog>

    <el-dialog v-model="impactDialog" title="变更影响分析" width="640px">
      <el-table :data="impactRows" size="small">
        <el-table-column prop="entity" label="实体" width="150" />
        <el-table-column prop="name" label="名称" width="140" />
        <el-table-column prop="downstreamTables" label="下游表" width="90" align="right" />
        <el-table-column prop="summary" label="影响摘要" min-width="260" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { modelApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

const store = useAuthStore()

const models = ref([])
const stats = ref({})
const templates = ref([])
const drawer = ref(false)
const current = ref({})
const createDialog = ref(false)
const ddlDialog = ref(false)
const ddlResult = ref([])
const impactDialog = ref(false)
const impactRows = ref([])
const form = reactive({ code: '', name: '', cnName: '', modelType: 'LOGICAL', layer: 'DWD', industry: 'GOV', description: '' })

const fmt = (v) => (v ? String(v).replace('T', ' ').slice(0, 16) : '-')

const load = async () => {
  try {
    const d = await modelApi.list({ pageSize: 50 })
    models.value = d.records || []
  } catch (e) {
    models.value = []
  }
  if (store.has('model:overview:view')) {
    try {
      stats.value = (await modelApi.stats()) || {}
    } catch (e) {
      stats.value = {}
    }
  } else {
    stats.value = {}
  }
  if (store.has('model:template:view')) {
    try {
      templates.value = (await modelApi.templates()).records || []
    } catch (e) {
      templates.value = []
    }
  } else {
    templates.value = []
  }
}

const refreshCurrent = async () => {
  if (!current.value?.id) return
  try {
    current.value = await modelApi.detail(current.value.id)
  } catch (e) {
    /* 详情刷新失败不影响已完成的建表结果提示 */
  }
}

const openModel = async (row) => {
  try {
    current.value = await modelApi.detail(row.id)
    drawer.value = true
  } catch (e) {
    /* http 层已提示 */
  }
}

const create = async () => {
  if (!form.code || !form.cnName) return ElMessage.warning('编码与中文名必填')
  try {
    await modelApi.create({ ...form, name: form.name || form.code.toLowerCase() })
    ElMessage.success('模型已创建')
    createDialog.value = false
    load()
  } catch (e) {
    /* http 层已提示 */
  }
}

const publish = async (row) => {
  let note = ''
  try {
    const { value } = await ElMessageBox.prompt('请输入发布说明', '提交发布审批', {
      inputValue: '模型结构变更，请评审',
      confirmButtonText: '提交',
      cancelButtonText: '取消'
    })
    note = value
  } catch (e) {
    return
  }
  try {
    await modelApi.publish(row.id, { comment: note })
    ElMessage.success('已提交审批')
    load()
  } catch (e) {
    /* http 层已提示 */
  }
}

const approve = async (row) => {
  let pass = true
  try {
    await ElMessageBox.confirm(
      `将审批模型「${row.cnName}」v${row.version}。通过后版本号自动 +1 并发布，驳回则退回草稿。`,
      '发布审批',
      { confirmButtonText: '通过', cancelButtonText: '驳回', distinguishCancelAndClose: true, type: 'warning' }
    )
  } catch (action) {
    if (action !== 'cancel') return
    try {
      await ElMessageBox.confirm(`确认驳回模型「${row.cnName}」的发布申请？`, '驳回确认', {
        confirmButtonText: '确认驳回',
        cancelButtonText: '取消',
        type: 'warning'
      })
    } catch (e) {
      return
    }
    pass = false
  }
  try {
    await modelApi.approve(row.id, { result: pass ? 'APPROVED' : 'REJECTED', comment: pass ? '评审通过' : '评审驳回' })
    ElMessage.success(pass ? '审批通过，模型已发布' : '已驳回，模型退回草稿')
    load()
  } catch (e) {
    /* http 层已提示 */
  }
}

const showDdl = async (row) => {
  try {
    const d = await modelApi.ddl(row.id)
    ddlResult.value = d.statements || []
    ddlDialog.value = true
  } catch (e) {
    /* http 层已提示 */
  }
}

/**
 * 执行建表：取消与执行失败必须区分。
 * 只有用户取消/关闭确认框时才静默返回；接口报错要显式暴露并刷新模型状态。
 */
const executeDdl = async (entity) => {
  try {
    await ElMessageBox.confirm(`将在数据湖中执行 CREATE TABLE ${entity.code}，是否继续？`, '自助统一建表', {
      confirmButtonText: '执行建表',
      cancelButtonText: '取消',
      distinguishCancelAndClose: true,
      type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    const r = await modelApi.executeDdl(entity.id)
    ElMessage.success(`建表成功：${r.table}`)
    await refreshCurrent()
    load()
  } catch (e) {
    if (!e || !e.__toasted) ElMessage.error((e && e.message) || '建表执行失败')
    await refreshCurrent()
  }
}

const showImpact = async () => {
  try {
    const d = await modelApi.impact(current.value.id)
    impactRows.value = d.impacts || []
    impactDialog.value = true
  } catch (e) {
    /* http 层已提示 */
  }
}

onMounted(load)
</script>

<style scoped>
.ddl { background: #0f172a; color: #c7d2fe; padding: 12px; border-radius: 6px; font-size: 12px; overflow: auto; font-family: Consolas, monospace; }
</style>
