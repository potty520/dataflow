<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>问题闭环</h2>
        <div class="sub">发现 → 派单 → 处理 → 复核 → 关闭 的闭环状态机，超期自动升级、复发自动重派</div>
      </div>
      <el-button size="small" @click="load">刷新</el-button>
    </div>

    <div class="grid grid-4" style="margin-bottom:12px">
      <div class="stat">
        <div class="label">闭环率</div>
        <div class="value">{{ metrics.closedRate ?? 0 }}<small>%</small></div>
        <div class="foot">目标 ≥ {{ metrics.target }}% · 已关闭 {{ metrics.closed }}/{{ metrics.total }}</div>
      </div>
      <div class="stat orange"><div class="label">待处理</div><div class="value">{{ metrics.open }}</div><div class="foot">含已派单与处理中</div></div>
      <div class="stat red"><div class="label">超期 / 复发</div><div class="value">{{ metrics.overdue }}<small>/ {{ metrics.recurred }}</small></div><div class="foot">超期率 {{ metrics.overdueRate }}% · 复发率 {{ metrics.recurRate }}%</div></div>
      <div class="stat green"><div class="label">平均处理时长</div><div class="value">{{ metrics.avgHandleHours }}<small>小时</small></div><div class="foot">按已关闭问题单统计</div></div>
    </div>

    <div class="grid grid-2" style="margin-bottom:12px">
      <div class="card">
        <div class="card-head"><h3>闭环趋势</h3></div>
        <ChartBox :option="trendOption" height="200px" />
      </div>
      <div class="card">
        <div class="card-head"><h3>责任人处理能力</h3></div>
        <el-table :data="assignees" size="small" max-height="200">
          <el-table-column prop="assignee" label="责任人" width="90" />
          <el-table-column prop="total" label="单量" width="70" align="right" />
          <el-table-column prop="closed" label="已关闭" width="80" align="right" />
          <el-table-column prop="overdue" label="超期" width="70" align="right" />
          <el-table-column prop="closedRate" label="闭环率" min-width="120">
            <template #default="{ row }">
              <el-progress :percentage="Number(row.closedRate || 0)" :stroke-width="8" :show-text="false" />
              <span style="font-size:11px">{{ row.closedRate }}%</span>
            </template>
          </el-table-column>
          <el-table-column prop="avgHours" label="平均时长(h)" width="100" align="right" />
        </el-table>
      </div>
    </div>

    <div class="card">
      <div class="toolbar">
        <el-select v-model="query.status" placeholder="状态" clearable size="small" style="width:130px" @change="search">
          <el-option v-for="(v, k) in statusCn" :key="k" :label="v" :value="k" />
        </el-select>
        <el-select v-model="query.severity" placeholder="级别" clearable size="small" style="width:120px" @change="search">
          <el-option label="CRITICAL" value="CRITICAL" /><el-option label="MAJOR" value="MAJOR" /><el-option label="MINOR" value="MINOR" />
        </el-select>
        <el-input v-model="query.tableName" placeholder="表名" clearable size="small" style="width:160px" @keyup.enter="search" @clear="search" />
        <span class="spacer"></span>
        <span class="muted" style="font-size:12px">共 {{ total }} 张问题单</span>
      </div>
      <el-table :data="issues" size="small" v-loading="loading">
        <el-table-column prop="code" label="问题单号" width="180"><template #default="{ row }"><span class="mono">{{ row.code }}</span></template></el-table-column>
        <el-table-column prop="ruleName" label="触发规则" min-width="180" show-overflow-tooltip />
        <el-table-column prop="tableName" label="表" width="140"><template #default="{ row }"><span class="mono">{{ row.tableName }}</span></template></el-table-column>
        <el-table-column prop="columnName" label="字段" width="110" />
        <el-table-column prop="dimension" label="维度" width="86"><template #default="{ row }">{{ dimCn[row.dimension] || row.dimension }}</template></el-table-column>
        <el-table-column prop="severity" label="级别" width="86">
          <template #default="{ row }"><el-tag size="small" :type="row.severity === 'CRITICAL' ? 'danger' : row.severity === 'MAJOR' ? 'warning' : 'info'" effect="plain">{{ row.severity }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }"><el-tag size="small" :type="statusType(row.status)" effect="plain">{{ statusCn[row.status] || row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="assignee" label="责任人" width="86" />
        <el-table-column prop="dueTime" label="处理时限" width="140"><template #default="{ row }">{{ fmt(row.dueTime) }}</template></el-table-column>
        <el-table-column prop="recurCnt" label="复发" width="60" align="right" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button v-if="['DISCOVERED','RECURRED'].includes(row.status)" link type="warning" size="small" @click="openDispatch(row)">派单</el-button>
            <el-button v-if="['DISPATCHED','PROCESSING','OVERDUE'].includes(row.status)" link type="primary" size="small" @click="openHandle(row)">处理</el-button>
            <el-button v-if="row.status === 'REVIEWING'" link type="success" size="small" @click="openReview(row)">复核</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="display:flex;justify-content:flex-end;margin-top:10px">
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total"
                       layout="total, prev, pager, next" @change="loadIssues" />
      </div>
    </div>

    <el-dialog v-model="dispatchVisible" title="问题派单" width="480px">
      <el-form label-width="90px" size="small">
        <el-form-item label="责任人">
          <el-select v-model="dispatchForm.assignee" filterable style="width:100%">
            <el-option v-for="a in assigneeOptions" :key="a" :label="a" :value="a" />
          </el-select>
          <div v-if="assigneeFallback" class="muted" style="font-size:11.5px;margin-top:4px">
            责任人清单加载失败，已回退为内置列表
          </div>
        </el-form-item>
        <el-form-item label="处理时限">
          <el-input-number v-model="dispatchForm.dueHours" :min="4" :max="720" :step="12" /> 小时
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="dispatchVisible = false">取消</el-button>
        <el-button size="small" type="primary" @click="doDispatch">确认派单</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="handleVisible" title="提交处理结果" width="520px">
      <el-input v-model="handleForm.handleResult" type="textarea" :rows="4" placeholder="请说明修复内容、影响范围与回刷情况" />
      <template #footer>
        <el-button size="small" @click="handleVisible = false">取消</el-button>
        <el-button size="small" type="primary" @click="doHandle">提交待复核</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="问题单详情" size="52%">
      <div v-if="detail.id">
        <div class="kv"><span class="k">单号</span><span class="v mono">{{ detail.code }}</span></div>
        <div class="kv"><span class="k">规则</span><span class="v">{{ detail.ruleName }}</span></div>
        <div class="kv"><span class="k">对象</span><span class="v mono">{{ detail.tableName }}.{{ detail.columnName }}</span></div>
        <div class="kv"><span class="k">状态</span><span class="v"><el-tag size="small">{{ statusCn[detail.status] }}</el-tag></span></div>
        <div class="kv"><span class="k">描述</span><span class="v">{{ detail.description }}</span></div>
        <div class="kv"><span class="k">异常样例</span><span class="v mono">{{ detail.sampleData }}</span></div>
        <div class="kv"><span class="k">处理结果</span><span class="v">{{ detail.handleResult || '-' }}</span></div>
        <div class="kv"><span class="k">复核意见</span><span class="v">{{ detail.reviewResult || '-' }}</span></div>
        <div class="section-title">流转记录</div>
        <el-timeline>
          <el-timeline-item v-for="f in detail.flows || []" :key="f.id" :timestamp="fmt(f.createTime)">
            {{ f.fromStatus }} → {{ f.toStatus }} · {{ f.operator }}
            <div class="muted" style="font-size:11.5px">{{ f.comment }}</div>
          </el-timeline-item>
        </el-timeline>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ChartBox from '@/components/ChartBox.vue'
import { qualityApi, authApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

const store = useAuthStore()
const issues = ref([])
const metrics = ref({})
const assignees = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ status: '', severity: '', tableName: '', pageNum: 1, pageSize: 10 })
const dispatchVisible = ref(false)
const dispatchForm = reactive({ assignee: '刘琴', dueHours: 72 })
const handleVisible = ref(false)
const handleForm = reactive({ handleResult: '' })
const detailVisible = ref(false)
const detail = ref({})
const assigneeOptions = ref([])
const assigneeFallback = ref(false)
let activeIssue = null

const statusCn = { DISCOVERED: '待派单', DISPATCHED: '已派单', PROCESSING: '处理中', REVIEWING: '待复核', CLOSED: '已关闭', OVERDUE: '已超期', IGNORED: '已忽略', RECURRED: '已复发' }
const dimCn = { COMPLETENESS: '完整性', UNIQUENESS: '唯一性', ACCURACY: '准确性', VALIDITY: '有效性', CONSISTENCY: '一致性', TIMELINESS: '时效性' }
const fmt = (v) => (v ? String(v).replace('T', ' ').slice(0, 16) : '-')
const statusType = (s) => ({ CLOSED: 'success', OVERDUE: 'danger', REVIEWING: 'warning', PROCESSING: 'warning', DISPATCHED: 'primary', DISCOVERED: 'info', RECURRED: 'danger' }[s] || 'info')

const trendOption = computed(() => {
  const rows = (metrics.value.trend || []).slice().reverse()
  return {
    grid: { left: 40, right: 18, top: 22, bottom: 26 },
    tooltip: { trigger: 'axis' },
    legend: { top: 0, itemWidth: 8, itemHeight: 8, textStyle: { fontSize: 10 } },
    xAxis: { type: 'category', data: rows.map((r) => String(r.statDate).slice(5)), axisLabel: { fontSize: 10, color: '#6b7280' }, axisLine: { lineStyle: { color: '#e5e7eb' } } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#f1f5f9' } }, axisLabel: { fontSize: 10, color: '#6b7280' } },
    series: [
      { name: '新增', type: 'bar', barWidth: 9, data: rows.map((r) => r.newCnt), itemStyle: { color: '#f59e0b', borderRadius: [2, 2, 0, 0] } },
      { name: '关闭', type: 'bar', barWidth: 9, data: rows.map((r) => r.closedCnt), itemStyle: { color: '#16a34a', borderRadius: [2, 2, 0, 0] } }
    ]
  }
})

const loadIssues = async () => {
  loading.value = true
  try {
    const d = await qualityApi.issues({ ...query })
    issues.value = d.records || []
    total.value = d.total || 0
  } catch (e) {
    issues.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 筛选条件变化统一回到第一页。 */
const search = () => {
  query.pageNum = 1
  loadIssues()
}

const load = async () => {
  // KPI 与责任人能力是旁路统计，取不到也不影响问题单列表
  const [m, a] = await Promise.allSettled([qualityApi.issueMetrics(), qualityApi.byAssignee()])
  metrics.value = m.status === 'fulfilled' ? m.value || {} : {}
  assignees.value = a.status === 'fulfilled' ? a.value.records || [] : []
  loadIssues()
}

/** 派单候选：来自登录即可访问的 /auth/users/options，后端不可用时才回退内置列表。 */
const FALLBACK_ASSIGNEES = ['刘琴', '张三', '王武', '陈顾问', '赵安全']
const fallbackAssignees = () => {
  const set = new Set(FALLBACK_ASSIGNEES)
  if (store.user?.realName) set.add(store.user.realName)
  return [...set]
}

const loadAssignees = async () => {
  try {
    const d = await authApi.userOptions()
    const names = (d.records || [])
      .map((u) => (u.realName || u.username || '').trim())
      .filter(Boolean)
    const uniq = [...new Set(names)]
    if (!uniq.length) throw new Error('empty')
    assigneeOptions.value = uniq
    assigneeFallback.value = false
  } catch (e) {
    assigneeOptions.value = fallbackAssignees()
    assigneeFallback.value = true
  }
  // 后端派单按"责任人显示名"存库，这里保持同样的取值语义
  if (!assigneeOptions.value.includes(dispatchForm.assignee)) {
    dispatchForm.assignee = assigneeOptions.value[0] || ''
  }
}

const openDispatch = (row) => {
  activeIssue = row
  dispatchForm.assignee = row.assignee || assigneeOptions.value[0] || '刘琴'
  dispatchVisible.value = true
}
const doDispatch = async () => {
  if (!dispatchForm.assignee) return ElMessage.warning('请选择责任人')
  try {
    await qualityApi.dispatch(activeIssue.id, { ...dispatchForm })
    ElMessage.success('已派单')
    dispatchVisible.value = false
    load()
  } catch (e) {
    /* http 层已提示 */
  }
}

const openHandle = (row) => {
  activeIssue = row
  handleForm.handleResult = ''
  handleVisible.value = true
}
const doHandle = async () => {
  if (!handleForm.handleResult) return ElMessage.warning('请填写处理结果')
  try {
    await qualityApi.handle(activeIssue.id, { ...handleForm })
    ElMessage.success('已提交复核')
    handleVisible.value = false
    load()
  } catch (e) {
    /* http 层已提示 */
  }
}

const openReview = async (row) => {
  try {
    await ElMessageBox.confirm('确认修复结果有效并关闭问题单？复核人不得与责任人相同', '复核')
  } catch (e) {
    return
  }
  try {
    await qualityApi.review(row.id, { result: 'PASS', comment: '复核通过' })
    ElMessage.success('问题单已关闭')
    load()
  } catch (e) {
    /* http 层已提示 */
  }
}

const openDetail = async (row) => {
  try {
    detail.value = await qualityApi.issue(row.id)
    detailVisible.value = true
  } catch (e) {
    /* http 层已提示 */
  }
}

onMounted(() => {
  load()
  loadAssignees()
})
</script>
