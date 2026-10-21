<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>数据资产</h2>
        <div class="sub">面向业务方的资产目录：业务含义、覆盖范围、更新周期、责任人与价值评估</div>
      </div>
      <div class="toolbar" style="margin:0">
        <el-tag effect="plain" size="small">资产 {{ total }}</el-tag>
        <el-tag effect="plain" size="small" type="success">平均价值分 {{ stats.avgValueScore ?? '-' }}</el-tag>
      </div>
    </div>

    <div class="grid" style="grid-template-columns: 230px 1fr">
      <div class="card" style="padding:10px">
        <div class="card-head" style="margin-bottom:6px"><h3>主题分类</h3></div>
        <el-tree :data="categories" node-key="id" :props="{ label: 'name', children: 'children' }"
                 default-expand-all highlight-current @node-click="onCategory">
          <template #default="{ data }">
            <span style="font-size:12.5px">{{ data.name }}
              <span class="muted" style="font-size:11px">({{ data.assetCount ?? 0 }})</span>
            </span>
          </template>
        </el-tree>
      </div>

      <div>
        <div class="card">
          <div class="toolbar">
            <el-input v-model="query.keyword" placeholder="资产名称 / 表名 / 业务描述" clearable style="width:240px"
                      :prefix-icon="Search" @keyup.enter="search" @clear="search" />
            <el-select v-model="query.domain" placeholder="业务域" clearable style="width:140px" @change="search">
              <el-option v-for="d in ['客户域','交易域','产品域','设备域','政务域','经营分析域']" :key="d" :label="d" :value="d" />
            </el-select>
            <el-button size="small" type="primary" @click="search">检索</el-button>
          </div>
          <el-table :data="assets" size="small" v-loading="loading" @row-click="openDetail" style="cursor:pointer">
            <el-table-column prop="assetName" label="资产名称" min-width="150"><template #default="{ row }"><span class="mono">{{ row.assetName }}</span></template></el-table-column>
            <el-table-column prop="category" label="分类" width="110" />
            <el-table-column prop="domain" label="业务域" width="110" />
            <el-table-column prop="businessDesc" label="业务含义" min-width="220" show-overflow-tooltip />
            <el-table-column prop="updateCycle" label="更新周期" width="90" />
            <el-table-column prop="owner" label="责任人" width="86" />
            <el-table-column prop="valueScore" label="价值分" width="86" align="right">
              <template #default="{ row }"><span :style="{ color: '#2563eb', fontWeight: 600 }">{{ row.valueScore }}</span></template>
            </el-table-column>
            <el-table-column prop="viewCnt" label="访问" width="70" align="right" />
            <el-table-column prop="favoriteCnt" label="收藏" width="70" align="right" />
          </el-table>
          <div style="display:flex;justify-content:flex-end;margin-top:10px">
            <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total"
                           layout="total, prev, pager, next" @change="load" />
          </div>
        </div>

        <div class="card" style="margin-top:12px">
          <div class="card-head"><h3>资产价值评估依据</h3><span class="hint">{{ valueBasis.formula }}</span></div>
          <el-table :data="valueRows" size="small" max-height="280">
            <el-table-column prop="tableName" label="资产" min-width="150"><template #default="{ row }"><span class="mono">{{ row.tableName }}</span></template></el-table-column>
            <el-table-column prop="score" label="价值分" width="90" align="right" />
            <el-table-column prop="qualityScore" label="质量分" width="90" align="right" />
            <el-table-column prop="accessCount" label="热度" width="90" align="right" />
            <el-table-column prop="svcCalls" label="服务调用" width="100" align="right" />
            <el-table-column prop="cnName" label="中文名" min-width="140" />
          </el-table>
        </div>
      </div>
    </div>

    <el-drawer v-model="detailVisible" :title="detail.assetName || '资产详情'" size="54%">
      <div v-if="detail.id">
        <div class="kv"><span class="k">物理表</span><span class="v mono">{{ detail.tableName }}</span></div>
        <div class="kv"><span class="k">业务含义</span><span class="v">{{ detail.businessDesc }}</span></div>
        <div class="kv"><span class="k">覆盖范围</span><span class="v">{{ detail.coverScope }}</span></div>
        <div class="kv"><span class="k">更新周期</span><span class="v">{{ detail.updateCycle }}</span></div>
        <div class="kv"><span class="k">责任人</span><span class="v">{{ detail.owner }}</span></div>
        <div class="kv">
          <span class="k">价值分</span>
          <span class="v">{{ detail.valueScore?.score ?? '-' }}<template v-if="valueBasis.formula">（{{ valueBasis.formula }}）</template></span>
        </div>
        <div v-if="valueDims.length" class="kv">
          <span class="k">评分构成</span>
          <span class="v">
            <el-tag v-for="d in valueDims" :key="d.name" size="small" effect="plain" style="margin:0 6px 6px 0">
              {{ d.name }} {{ d.value }}
            </el-tag>
          </span>
        </div>
        <div class="section-title">字段信息</div>
        <el-table :data="detail.columns || []" size="small" max-height="240">
          <el-table-column prop="name" label="字段" width="140"><template #default="{ row }"><span class="mono">{{ row.name }}</span></template></el-table-column>
          <el-table-column prop="cnName" label="中文名" width="120" />
          <el-table-column prop="dataType" label="类型" width="100" />
          <el-table-column prop="sensitiveLevel" label="敏感级" width="80" />
          <el-table-column prop="stdCode" label="标准" width="130"><template #default="{ row }"><span class="mono">{{ row.stdCode || '-' }}</span></template></el-table-column>
        </el-table>
        <div class="section-title">质量与血缘</div>
        <div class="kv"><span class="k">上游</span><span class="v">{{ (detail.upstream || []).map((x) => x.srcTable).join('、') || '无' }}</span></div>
        <div class="kv"><span class="k">下游</span><span class="v">{{ (detail.downstream || []).map((x) => x.dstTable).join('、') || '无' }}</span></div>
        <el-button size="small" type="primary" style="margin-top:10px" @click="favorite">收藏该资产</el-button>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { assetApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

const store = useAuthStore()
const assets = ref([])
const categories = ref([])
const stats = ref({})
const valueBasis = ref({})
const valueRows = ref([])
const total = ref(0)
const loading = ref(false)
const detailVisible = ref(false)
const detail = ref({})
const query = reactive({ keyword: '', category: '', domain: '', pageNum: 1, pageSize: 10 })

const DIM_LABEL = { quality: '质量分', usage: '访问热度', service: '服务调用', coverage: '覆盖度' }
/** 详情里的 valueScore 是 ast_value_score 整行，评分构成在 dimJson 中。 */
const valueDims = computed(() => {
  const row = detail.value.valueScore
  if (!row) return []
  let dims = row.dimJson ?? row.dimensions ?? null
  if (typeof dims === 'string') {
    try {
      dims = JSON.parse(dims)
    } catch (e) {
      dims = null
    }
  }
  if (!dims || typeof dims !== 'object') return []
  return Object.keys(dims).map((k) => ({ name: DIM_LABEL[k] || k, value: dims[k] }))
})

const load = async () => {
  loading.value = true
  try {
    const d = await assetApi.search({ ...query })
    assets.value = d.records || []
    total.value = d.total || 0
  } catch (e) {
    assets.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 筛选条件变化统一回到第一页。 */
const search = () => {
  query.pageNum = 1
  load()
}

const onCategory = (data) => {
  query.category = data.name
  query.pageNum = 1
  load()
}

const openDetail = async (row) => {
  try {
    detail.value = await assetApi.detail(row.id)
    detailVisible.value = true
  } catch (e) {
    detail.value = {}
  }
}

const favorite = async () => {
  if (!detail.value.id) return
  try {
    await assetApi.favorite(detail.value.id)
    ElMessage.success('已收藏')
  } catch (e) {
    /* http 层已提示 */
  }
}

/** 分类树、概览统计、价值依据都是旁路数据，任一失败都不能影响资产列表。 */
const loadCategories = async () => {
  if (!store.has('asset:category:view')) return
  try {
    categories.value = (await assetApi.categories()).tree || []
  } catch (e) {
    categories.value = []
  }
}

const loadStats = async () => {
  if (!store.has('asset:overview:view')) return
  try {
    stats.value = (await assetApi.statsOverview()) || {}
  } catch (e) {
    stats.value = {}
  }
}

const loadValueBasis = async () => {
  if (!store.has('asset:value:view')) return
  try {
    const vb = (await assetApi.valueBasis()) || {}
    valueBasis.value = vb
    valueRows.value = (vb.records || []).slice(0, 12)
  } catch (e) {
    valueBasis.value = {}
    valueRows.value = []
  }
}

onMounted(() => {
  load()
  loadCategories()
  loadStats()
  loadValueBasis()
})
</script>
