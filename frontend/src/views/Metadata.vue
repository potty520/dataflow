<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>{{ route.meta.title }}</h2>
        <div class="sub">自动采集 · 全文检索 · 变更版本 · 字段级敏感标识</div>
      </div>
      <div class="toolbar" style="margin:0">
        <el-tag effect="plain" size="small">表 {{ stats.tableCount }}</el-tag>
        <el-tag effect="plain" size="small">字段 {{ stats.columnCount }}</el-tag>
        <el-tag effect="plain" size="small" type="success">血缘边 {{ stats.lineageEdgeCount }}</el-tag>
      </div>
    </div>

    <el-tabs v-model="tab" type="border-card" style="border-radius:8px">
      <!-- ============ 元数据目录 ============ -->
      <el-tab-pane label="元数据目录" name="catalog">
        <div class="toolbar">
          <el-input v-model="query.keyword" placeholder="表名 / 中文名 / 字段 / 描述" clearable style="width:240px"
                    :prefix-icon="Search" @keyup.enter="search" @clear="search" />
          <el-select v-model="query.layer" placeholder="数据分层" clearable style="width:120px" @change="search">
            <el-option v-for="l in ['ODS','DWD','DWS','ADS']" :key="l" :label="l" :value="l" />
          </el-select>
          <el-select v-model="query.domain" placeholder="业务域" clearable style="width:140px" @change="search">
            <el-option v-for="d in domains" :key="d" :label="d" :value="d" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="search">检索</el-button>
          <span class="spacer"></span>
          <span class="muted" style="font-size:12px">检索耗时 {{ costMs }} ms · 共 {{ total }} 条</span>
        </div>
        <el-table :data="tables" size="small" v-loading="loading" @row-click="openTable" style="cursor:pointer">
          <el-table-column prop="name" label="表名" min-width="170">
            <template #default="{ row }"><span class="mono">{{ row.name }}</span></template>
          </el-table-column>
          <el-table-column prop="cnName" label="中文名" min-width="150" show-overflow-tooltip />
          <el-table-column prop="layer" label="分层" width="76">
            <template #default="{ row }"><el-tag size="small" effect="plain">{{ row.layer }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="domain" label="业务域" width="100" />
          <el-table-column prop="rowCount" label="行数" width="90" align="right">
            <template #default="{ row }">{{ Number(row.rowCount || 0).toLocaleString() }}</template>
          </el-table-column>
          <el-table-column prop="qualityScore" label="质量分" width="92" align="center">
            <template #default="{ row }">
              <span :style="{ color: scoreColor(row.qualityScore), fontWeight: 600 }">{{ row.qualityScore ?? '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="sensitiveLevel" label="敏感级" width="80" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.sensitiveLevel === 'L4' ? 'danger' : row.sensitiveLevel === 'L3' ? 'warning' : 'info'" effect="plain">
                {{ row.sensitiveLevel }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="owner" label="责任人" width="86" />
          <el-table-column prop="updateFreq" label="更新频率" width="92" />
          <el-table-column prop="dsName" label="数据源" min-width="130" show-overflow-tooltip />
        </el-table>
        <div style="display:flex;justify-content:flex-end;margin-top:10px">
          <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total"
                         layout="total, sizes, prev, pager, next" :page-sizes="[10,20,50]" @change="loadTables" />
        </div>
      </el-tab-pane>

      <!-- ============ 数据源 ============ -->
      <el-tab-pane label="数据源管理" name="datasource">
        <div class="toolbar">
          <span class="muted" style="font-size:12px">连接测试为真实探测：H2 走探针 SQL，其他类型做 TCP 连通性检测并记录时延</span>
          <span class="spacer"></span>
          <el-button size="small" type="primary" :icon="Plus" @click="dsDialog = true">登记数据源</el-button>
        </div>
        <el-table :data="datasources" size="small" v-loading="dsLoading">
          <el-table-column prop="code" label="编码" width="130"><template #default="{ row }"><span class="mono">{{ row.code }}</span></template></el-table-column>
          <el-table-column prop="name" label="名称" min-width="150" />
          <el-table-column prop="dtype" label="类型" width="100"><template #default="{ row }"><el-tag size="small" effect="plain">{{ row.dtype }}</el-tag></template></el-table-column>
          <el-table-column label="连接地址" min-width="180">
            <template #default="{ row }"><span class="mono">{{ row.host }}:{{ row.port }}</span></template>
          </el-table-column>
          <el-table-column prop="dbName" label="库/主题" width="110" />
          <el-table-column prop="connStatus" label="连接状态" width="96">
            <template #default="{ row }">
              <el-tag size="small" :type="row.connStatus === '已连接' ? 'success' : row.connStatus === '异常' ? 'danger' : 'info'">{{ row.connStatus }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="latencyMs" label="时延" width="70" align="right"><template #default="{ row }">{{ row.latencyMs ? row.latencyMs + 'ms' : '-' }}</template></el-table-column>
          <el-table-column prop="tableCount" label="已采集表" width="86" align="right" />
          <el-table-column prop="lastTestTime" label="最近检测" width="140"><template #default="{ row }">{{ fmt(row.lastTestTime) }}</template></el-table-column>
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }"><el-button link type="primary" size="small" @click="testDs(row)">连接测试</el-button></template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ============ 采集任务 ============ -->
      <el-tab-pane label="采集任务" name="collect">
        <el-table :data="collectTasks" size="small">
          <el-table-column prop="name" label="任务名称" min-width="180" />
          <el-table-column prop="dsName" label="目标数据源" min-width="150" />
          <el-table-column prop="mode" label="模式" width="80" />
          <el-table-column prop="cron" label="调度表达式" width="130"><template #default="{ row }"><span class="mono">{{ row.cron }}</span></template></el-table-column>
          <el-table-column prop="newTables" label="新增表" width="80" align="right" />
          <el-table-column prop="changedCols" label="变更列" width="80" align="right" />
          <el-table-column prop="lastRunTime" label="最近执行" width="150"><template #default="{ row }">{{ fmt(row.lastRunTime) }}</template></el-table-column>
          <el-table-column prop="status" label="状态" width="86"><template #default="{ row }"><el-tag size="small" type="success" effect="plain">{{ row.status }}</el-tag></template></el-table-column>
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" :loading="running === row.id" @click="runCollect(row)">立即采集</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="section-title">采集实例（真实扫描 information_schema）</div>
        <el-table :data="instances" size="small">
          <el-table-column prop="dsName" label="数据源" min-width="150" />
          <el-table-column prop="status" label="状态" width="90" />
          <el-table-column prop="newTables" label="新增表" width="80" align="right" />
          <el-table-column prop="changedCols" label="新增列" width="80" align="right" />
          <el-table-column prop="durationMs" label="耗时(ms)" width="90" align="right" />
          <el-table-column prop="message" label="结果" min-width="280" show-overflow-tooltip />
          <el-table-column prop="operator" label="操作人" width="90" />
          <el-table-column prop="endTime" label="完成时间" width="150"><template #default="{ row }">{{ fmt(row.endTime) }}</template></el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 表详情抽屉 -->
    <el-drawer v-model="drawer" :title="detail.name + ' · ' + (detail.cnName || '')" size="62%">
      <div v-if="detail.name">
        <div class="grid grid-4" style="margin-bottom:12px">
          <div class="stat" style="padding:10px 12px"><div class="label">行数</div><div class="value" style="font-size:19px">{{ Number(detail.rowCount || 0).toLocaleString() }}</div></div>
          <div class="stat" style="padding:10px 12px"><div class="label">质量分</div><div class="value" style="font-size:19px" :style="{color:scoreColor(detail.qualityScore)}">{{ detail.qualityScore }}</div></div>
          <div class="stat" style="padding:10px 12px"><div class="label">字段数</div><div class="value" style="font-size:19px">{{ detail.columnCount }}</div></div>
          <div class="stat" style="padding:10px 12px"><div class="label">敏感级</div><div class="value" style="font-size:19px">{{ detail.sensitiveLevel }}</div></div>
        </div>
        <el-tabs v-model="detailTab">
          <el-tab-pane label="字段" name="col">
            <el-table :data="detail.columns || []" size="small" height="360">
              <el-table-column prop="name" label="字段" width="150"><template #default="{ row }"><span class="mono">{{ row.name }}</span></template></el-table-column>
              <el-table-column prop="cnName" label="中文名" width="130" />
              <el-table-column prop="dataType" label="类型" width="110" />
              <el-table-column prop="dataLen" label="长度" width="70" />
              <el-table-column prop="isPk" label="主键" width="60"><template #default="{ row }">{{ row.isPk ? '是' : '' }}</template></el-table-column>
              <el-table-column prop="nullable" label="可空" width="60"><template #default="{ row }">{{ row.nullable ? '是' : '否' }}</template></el-table-column>
              <el-table-column prop="stdCode" label="标准数据元" width="140"><template #default="{ row }"><span class="mono">{{ row.stdCode || '-' }}</span></template></el-table-column>
              <el-table-column prop="sensitiveLevel" label="敏感级" width="80" />
              <el-table-column prop="comment" label="注释" min-width="140" show-overflow-tooltip />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="血缘" name="lin">
            <div class="section-title">上游（数据来源）</div>
            <el-tag v-for="u in detail.upstream || []" :key="u.tableName" style="margin:0 6px 6px 0" effect="plain">
              {{ u.tableName }} <span class="muted">· {{ u.jobName }}</span>
            </el-tag>
            <div v-if="!(detail.upstream || []).length" class="empty">无上游（源表）</div>
            <div class="section-title">下游（数据流向）</div>
            <el-tag v-for="d in detail.downstream || []" :key="d.tableName" type="success" style="margin:0 6px 6px 0" effect="plain">
              {{ d.tableName }} <span class="muted">· {{ d.jobName }}</span>
            </el-tag>
            <div v-if="!(detail.downstream || []).length" class="empty">无下游</div>
            <el-button size="small" style="margin-top:10px" @click="gotoLineage(detail.name)">打开血缘图谱</el-button>
          </el-tab-pane>
          <el-tab-pane label="质量规则" name="qa">
            <el-table :data="detail.rules || []" size="small">
              <el-table-column prop="name" label="规则" min-width="180" />
              <el-table-column prop="dimension" label="维度" width="110" />
              <el-table-column prop="severity" label="级别" width="90" />
              <el-table-column prop="lastPassRate" label="通过率" width="90"><template #default="{ row }">{{ row.lastPassRate ?? '-' }}%</template></el-table-column>
              <el-table-column prop="lastFailed" label="异常行" width="90" align="right" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="敏感识别" name="sec">
            <el-table :data="detail.sensitiveColumns || []" size="small">
              <el-table-column prop="columnName" label="字段" width="150" />
              <el-table-column prop="securityLevel" label="敏感级" width="90" />
              <el-table-column prop="matchType" label="识别方式" width="110" />
              <el-table-column prop="algorithm" label="脱敏算法" width="120" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="业务信息" name="biz">
            <div class="kv"><span class="k">业务含义</span><span class="v">{{ detail.bizDesc || detail.description }}</span></div>
            <div class="kv"><span class="k">责任人</span><span class="v">{{ detail.owner }}</span></div>
            <div class="kv"><span class="k">更新频率</span><span class="v">{{ detail.updateFreq }}</span></div>
            <div class="kv"><span class="k">标签</span><span class="v">{{ detail.tags }}</span></div>
            <div class="kv"><span class="k">资产编号</span><span class="v">{{ detail.catalog?.id || '未登记' }}</span></div>
            <div class="kv"><span class="k">样例查询</span><span class="v mono">{{ detail.sampleSql }}</span></div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>

    <el-dialog v-model="dsDialog" title="登记数据源" width="520px">
      <el-form :model="dsForm" label-width="90px" size="small">
        <el-form-item label="编码"><el-input v-model="dsForm.code" placeholder="DS-XXX" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="dsForm.name" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="dsForm.dtype" style="width:100%">
            <el-option v-for="t in ['MySQL','PostgreSQL','Hive','Kafka','API','OPC_UA','H2']" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="主机"><el-input v-model="dsForm.host" /></el-form-item>
        <el-form-item label="端口"><el-input v-model="dsForm.port" /></el-form-item>
        <el-form-item label="库名"><el-input v-model="dsForm.dbName" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="dsForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="dsDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="createDs">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search, Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { metadataApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const store = useAuthStore()
const tab = ref(route.meta.tab || 'catalog')
const stats = ref({})
const tables = ref([])
const total = ref(0)
const costMs = ref(0)
const loading = ref(false)
const query = reactive({ keyword: '', layer: '', domain: '', pageNum: 1, pageSize: 10 })
const domains = ['客户域', '交易域', '产品域', '设备域', '政务域', '经营分析域', '公共域']
const datasources = ref([])
const dsLoading = ref(false)
const collectTasks = ref([])
const instances = ref([])
const running = ref(null)
const drawer = ref(false)
const detail = ref({})
const detailTab = ref('col')
const dsDialog = ref(false)
const dsForm = reactive({ code: '', name: '', dtype: 'MySQL', host: '', port: '', dbName: '', remark: '' })

const fmt = (v) => (v ? String(v).replace('T', ' ').slice(0, 19) : '-')
const scoreColor = (s) => (!s ? '#94a3b8' : s >= 90 ? '#16a34a' : s >= 80 ? '#2563eb' : s >= 70 ? '#d97706' : '#dc2626')

const loadTables = async () => {
  loading.value = true
  try {
    const d = await metadataApi.search({ ...query })
    tables.value = d.records || []
    total.value = d.total || 0
    costMs.value = d.costMs || 0
  } catch (e) {
    // 检索失败时保留空列表，页面其余区域（数据源/采集）不受影响
    tables.value = []
    total.value = 0
    costMs.value = 0
  } finally {
    loading.value = false
  }
}

/** 筛选条件变化统一回到第一页，避免"第 3 页 + 新条件"得到空页。 */
const search = () => {
  query.pageNum = 1
  loadTables()
}

/** 概览统计是旁路信息，任何失败都不能阻塞目录列表。 */
const loadStats = async () => {
  if (!store.has('metadata:overview:view')) return
  try {
    stats.value = await metadataApi.stats()
  } catch (e) {
    stats.value = {}
  }
}

const loadDs = async () => {
  dsLoading.value = true
  try {
    const d = await metadataApi.datasources({})
    datasources.value = d.records || []
  } catch (e) {
    datasources.value = []
  } finally {
    dsLoading.value = false
  }
}

const loadCollect = async () => {
  try {
    const d = await metadataApi.collectTasks()
    collectTasks.value = d.records || []
  } catch (e) {
    collectTasks.value = []
  }
  try {
    const i = await metadataApi.collectInstances({ pageSize: 10 })
    instances.value = i.records || []
  } catch (e) {
    instances.value = []
  }
}

const openTable = async (row) => {
  detail.value = {}
  detailTab.value = 'col'
  drawer.value = true
  try {
    detail.value = await metadataApi.table(row.id)
  } catch (e) {
    drawer.value = false
  }
}

const gotoLineage = (name) => {
  router.push({ path: '/lineage', query: { object: name } })
}

const testDs = async (row) => {
  const r = await metadataApi.testDatasource(row.id)
  ElMessage[r.status === '已连接' ? 'success' : 'warning'](`${row.name}：${r.status} · ${r.message}`)
  loadDs()
}

const runCollect = async (row) => {
  running.value = row.id
  try {
    const r = await metadataApi.runCollect(row.id)
    ElMessage.success(`采集完成：扫描 ${r.tableCount} 张表，新增 ${r.newTables} 张，新增列 ${r.changedColumns} 个`)
    loadCollect()
    loadTables()
  } finally {
    running.value = null
  }
}

const createDs = async () => {
  await metadataApi.createDatasource({ ...dsForm })
  ElMessage.success('数据源已登记')
  dsDialog.value = false
  loadDs()
}

watch(() => route.meta.tab, (t) => { if (t) tab.value = t })
watch(tab, (t) => {
  if (t === 'datasource' && !datasources.value.length) loadDs()
  if (t === 'collect' && !collectTasks.value.length) loadCollect()
})

onMounted(() => {
  // 主列表先加载，统计是旁路；两者互不阻塞
  loadTables()
  loadStats()
  if (tab.value === 'datasource') loadDs()
  if (tab.value === 'collect') loadCollect()
})
</script>
