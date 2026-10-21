<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>标准映射与落地检查</h2>
        <div class="sub">源字段与数据元映射、自动推荐、未遵循标准的字段识别（建表/开发流程卡点）</div>
      </div>
      <el-button size="small" :icon="Back" @click="$router.push('/standard')">返回标准列表</el-button>
    </div>

    <div class="grid grid-3" style="margin-bottom:12px">
      <div class="stat">
        <div class="label">整体落地率</div>
        <div class="value">{{ summary.overallRate ?? 0 }}<small>%</small></div>
        <div class="foot">目标 ≥ {{ summary.target }}% · 已映射 {{ summary.mappedColumns }}/{{ summary.totalColumns }} 字段</div>
      </div>
      <div class="stat orange">
        <div class="label">待映射字段</div>
        <div class="value">{{ (summary.totalColumns || 0) - (summary.mappedColumns || 0) }}</div>
        <div class="foot">可通过自动推荐批量确认</div>
      </div>
      <div class="stat green">
        <div class="label">最近检查</div>
        <div class="value" style="font-size:18px">{{ fmt(summary.history?.[0]?.checkTime) }}</div>
        <div class="foot">{{ summary.history?.[0]?.scope || '尚未执行' }} · 通过率 {{ summary.history?.[0]?.compliantRate ?? '-' }}%</div>
      </div>
    </div>

    <div class="card">
      <div class="card-head">
        <h3>自动映射推荐</h3>
        <span class="hint">按字段名精确/包含 + 中文名相似度 + 类型一致性打分</span>
      </div>
      <div class="toolbar">
        <el-select v-model="tableId" placeholder="全部未映射字段" clearable filterable style="width:240px" @change="recommend">
          <el-option v-for="t in tables" :key="t.id" :label="`${t.name} · ${t.cnName || ''}`" :value="t.id" />
        </el-select>
        <el-button size="small" type="primary" :icon="MagicStick" @click="recommend">生成推荐</el-button>
        <span class="spacer"></span>
        <span class="muted" style="font-size:12px">共 {{ recommendations.length }} 个字段可推荐</span>
        <el-button size="small" type="success" :disabled="!selection.length" @click="confirmBatch">确认选中（{{ selection.length }}）</el-button>
      </div>
      <el-table :data="recommendations" size="small" v-loading="recLoading" @selection-change="(v) => (selection = v)" max-height="330">
        <el-table-column type="selection" width="42" />
        <el-table-column prop="tableName" label="表" width="140"><template #default="{ row }"><span class="mono">{{ row.tableName }}</span></template></el-table-column>
        <el-table-column prop="columnName" label="字段" width="140"><template #default="{ row }"><span class="mono">{{ row.columnName }}</span></template></el-table-column>
        <el-table-column prop="columnCnName" label="中文名" width="120" />
        <el-table-column prop="dataType" label="类型" width="100" />
        <el-table-column label="推荐数据元" min-width="300">
          <template #default="{ row }">
            <el-select v-model="row.selected" size="small" style="width:100%" placeholder="选择数据元">
              <el-option v-for="c in row.recommend" :key="c.stdCode" :label="`${c.stdCode} · ${c.elementCnName}（${c.score}分）`" :value="c.stdCode" />
            </el-select>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="grid grid-2" style="margin-top:12px">
      <div class="card">
        <div class="card-head">
          <h3>标准落地检查</h3>
          <el-button size="small" type="primary" :loading="checking" @click="runCheck">执行检查</el-button>
        </div>
        <div v-if="check">
          <el-progress :percentage="Number(check.compliantRate)" :stroke-width="12" :color="check.compliantRate >= 80 ? '#16a34a' : '#d97706'" />
          <div class="kv" style="margin-top:10px"><span class="k">检查范围</span><span class="v">{{ check.scope }}</span></div>
          <div class="kv"><span class="k">合规字段</span><span class="v">{{ check.compliantColumns }} / {{ check.totalColumns }}</span></div>
          <div class="kv"><span class="k">结论</span><span class="v">{{ check.pass ? '达到 80% 目标' : '未达标，需推进映射' }}</span></div>
          <el-table :data="check.violations.slice(0, 12)" size="small" style="margin-top:8px" max-height="240">
            <el-table-column prop="tableName" label="表" width="130" />
            <el-table-column prop="columnName" label="字段" width="130" />
            <el-table-column prop="reason" label="原因" min-width="180" />
          </el-table>
        </div>
        <div v-else class="empty">点击「执行检查」识别未遵循标准的字段</div>
      </div>
      <div class="card">
        <div class="card-head"><h3>各表落地率</h3></div>
        <el-table :data="summary.byTable || []" size="small" max-height="300">
          <el-table-column prop="tableName" label="表" min-width="150"><template #default="{ row }"><span class="mono">{{ row.tableName }}</span></template></el-table-column>
          <el-table-column prop="totalCols" label="字段" width="70" align="right" />
          <el-table-column prop="mappedCols" label="已映射" width="80" align="right" />
          <el-table-column prop="rate" label="落地率" width="100">
            <template #default="{ row }">
              <el-progress :percentage="Number(row.rate || 0)" :stroke-width="8" :show-text="false" :color="row.rate >= 80 ? '#16a34a' : '#d97706'" />
              <span style="font-size:11px">{{ row.rate }}%</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <div class="card" style="margin-top:12px">
      <div class="card-head"><h3>治理建议</h3></div>
      <div v-for="(r, i) in summary.recommendations || []" :key="i" class="kv">
        <span class="k" style="width:22px">{{ i + 1 }}</span><span class="v">{{ r }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Back, MagicStick } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { standardApi, metadataApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

const store = useAuthStore()

const tables = ref([])
const tableId = ref(null)
const recommendations = ref([])
const selection = ref([])
const recLoading = ref(false)
const summary = ref({})
const check = ref(null)
const checking = ref(false)

const fmt = (v) => (v ? String(v).replace('T', ' ').slice(0, 16) : '-')

const recommend = async () => {
  if (!store.has('standard:mapping:recommend')) {
    recommendations.value = []
    return
  }
  recLoading.value = true
  try {
    const d = await standardApi.recommend({ tableId: tableId.value || undefined })
    recommendations.value = (d.records || []).map((r) => ({ ...r, selected: r.recommend?.[0]?.stdCode }))
  } catch (e) {
    recommendations.value = []
  } finally {
    recLoading.value = false
  }
}

const confirmBatch = async () => {
  const items = selection.value
    .map((r) => ({
      columnId: r.columnId,
      stdCode: r.selected,
      matchType: 'AUTO_CONFIRM',
      // 取"被选中的那个候选"的分数，而不是永远取推荐第一位
      score: r.recommend?.find((c) => c.stdCode === r.selected)?.score ?? 0
    }))
    .filter((i) => i.stdCode)
  if (!items.length) return ElMessage.warning('请先为选中字段选择数据元')
  try {
    const r = await standardApi.batchMapping({ items })
    ElMessage.success(`已确认 ${r.mapped} 个字段映射`)
    recommend(); loadSummary()
  } catch (e) {
    /* http 层已提示 */
  }
}

const runCheck = async () => {
  checking.value = true
  try {
    check.value = await standardApi.compliance(tableId.value ? { tableName: tables.value.find((t) => t.id === tableId.value)?.name } : {})
    ElMessage[check.value.pass ? 'success' : 'warning'](`落地率 ${check.value.compliantRate}%`)
    loadSummary()
  } catch (e) {
    /* http 层已提示 */
  } finally {
    checking.value = false
  }
}

const loadSummary = async () => {
  if (!store.has('standard:compliance:view')) {
    summary.value = {}
    return
  }
  try {
    summary.value = (await standardApi.complianceSummary()) || {}
  } catch (e) {
    summary.value = {}
  }
}

onMounted(() => {
  recommend()
  loadSummary()
  // 表下拉为旁路数据，加载失败不影响推荐与检查
  metadataApi
    .tables({ pageSize: 200 })
    .then((t) => {
      tables.value = t.records || []
    })
    .catch(() => {
      tables.value = []
    })
})
</script>
