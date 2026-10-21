<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>行业适配</h2>
        <div class="sub">以配置包方式适配通信/金融/工业/政务/零售，禁止为单行业复制代码</div>
      </div>
      <div class="toolbar" style="margin:0">
        <el-tag effect="plain" size="small">配置包 {{ stats.packageCount }}</el-tag>
        <el-tag effect="plain" size="small" type="success">已实例化 {{ stats.appliedCount }}</el-tag>
        <el-tag effect="plain" size="small">术语 {{ stats.termCount }}</el-tag>
      </div>
    </div>

    <div class="grid grid-3">
      <div v-for="p in packages" :key="p.id" class="card" style="margin:0">
        <div class="card-head">
          <h3>{{ p.packageName }}</h3>
          <el-tag size="small" :type="p.status === 'ENABLED' ? 'success' : 'info'" effect="plain">{{ p.status }}</el-tag>
        </div>
        <div class="kv"><span class="k">包编码</span><span class="v mono">{{ p.packageCode }}</span></div>
        <div class="kv"><span class="k">行业</span><span class="v">{{ p.industryName }}（{{ p.industryCode }}）</span></div>
        <div class="kv"><span class="k">版本</span><span class="v">{{ p.version }} ｜ 平台 ≥ {{ p.minPlatformVersion }}</span></div>
        <div class="kv"><span class="k">内容</span><span class="v">{{ p.itemCnt }} 项 ｜ 已实例化 {{ p.applied }} 次</span></div>
        <div style="margin:6px 0 10px">
          <el-tag v-for="t in p.itemTypes || []" :key="t.itemType" size="small" effect="plain" style="margin:0 4px 4px 0">
            {{ typeCn(t.itemType) }} × {{ t.cnt }}
          </el-tag>
        </div>
        <div class="muted" style="font-size:11.5px;line-height:1.7;min-height:52px">{{ p.description }}</div>
        <div class="toolbar" style="margin-top:8px;margin-bottom:0">
          <el-button size="small" type="primary" :loading="installing === p.id" @click="instantiate(p)">一键实例化</el-button>
          <el-button size="small" @click="showManifest(p)">查看 manifest</el-button>
          <el-button v-if="p.status !== 'ENABLED'" size="small" @click="enable(p)">启用</el-button>
          <el-button v-else size="small" @click="disable(p)">停用</el-button>
        </div>
      </div>
    </div>

    <div class="grid grid-2" style="margin-top:12px">
      <div class="card">
        <div class="card-head"><h3>实例化差异</h3><span class="hint">模板项 vs 已生成模型</span></div>
        <el-table :data="diff" size="small">
          <el-table-column prop="packageCode" label="包" width="150"><template #default="{ row }"><span class="mono">{{ row.packageCode }}</span></template></el-table-column>
          <el-table-column prop="templateItems" label="模板项" width="86" align="right" />
          <el-table-column prop="instantiatedModels" label="已生成模型" width="110" align="right" />
          <el-table-column prop="drift" label="状态" width="100">
            <template #default="{ row }"><el-tag size="small" :type="row.drift === '已实例化' ? 'success' : 'warning'" effect="plain">{{ row.drift }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="applied" label="应用次数" width="90" align="right" />
        </el-table>
      </div>
      <div class="card">
        <div class="card-head"><h3>行业血缘规则</h3></div>
        <el-table :data="lineageRules" size="small" max-height="260">
          <el-table-column prop="industry" label="行业" width="100" />
          <el-table-column prop="packageCode" label="包" width="160"><template #default="{ row }"><span class="mono">{{ row.packageCode }}</span></template></el-table-column>
          <el-table-column label="规则摘要" min-width="240">
            <template #default="{ row }">
              <span v-for="(r, i) in row.content?.rules || []" :key="i" style="margin-right:10px">{{ r.name }}（{{ r.strategy }}）</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <div class="card" style="margin-top:12px">
      <div class="card-head">
        <h3>行业术语表</h3>
        <el-select v-model="industry" size="small" clearable placeholder="按行业过滤" style="width:150px" @change="loadTerms">
          <el-option v-for="i in ['GOV','TELECOM','FINANCE','INDUSTRY','RETAIL']" :key="i" :label="i" :value="i" />
        </el-select>
      </div>
      <el-table :data="terms" size="small" max-height="330">
        <el-table-column prop="industry" label="行业" width="100" />
        <el-table-column prop="termCode" label="术语编码" width="150"><template #default="{ row }"><span class="mono">{{ row.termCode }}</span></template></el-table-column>
        <el-table-column prop="termName" label="术语" width="140" />
        <el-table-column prop="definition" label="定义" min-width="240" show-overflow-tooltip />
        <el-table-column prop="columnPattern" label="字段匹配" min-width="170"><template #default="{ row }"><span class="mono">{{ row.columnPattern }}</span></template></el-table-column>
        <el-table-column prop="stdElementCode" label="数据元" width="130" />
        <el-table-column prop="synonyms" label="同义词" width="140" />
        <el-table-column prop="securityLevel" label="级别" width="70" />
      </el-table>
    </div>

    <el-dialog v-model="manifestVisible" title="行业包 manifest.json" width="720px">
      <pre class="manifest">{{ JSON.stringify(manifestData, null, 2) }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { industryApi } from '@/api'

const packages = ref([])
const diff = ref([])
const terms = ref([])
const lineageRules = ref([])
const stats = ref({})
const industry = ref('')
const installing = ref(null)
const manifestVisible = ref(false)
const manifestData = ref({})

const typeCn = (t) => ({
  MODEL_TEMPLATE: '模型模板', RULE_TEMPLATE: '规则库', TERMINOLOGY: '术语表',
  LINEAGE_RULE: '血缘规则', REPORT_TEMPLATE: '报表模板', SENSITIVE_RULE: '敏感规则', DATA_ELEMENT: '数据元'
}[t] || t)

const load = async () => {
  // 各区块独立容错：任一接口异常只影响对应面板
  try {
    packages.value = (await industryApi.packages({})).records || []
  } catch (e) {
    packages.value = []
  }
  try {
    stats.value = (await industryApi.stats()) || {}
  } catch (e) {
    stats.value = {}
  }
  try {
    diff.value = (await industryApi.diff()).records || []
  } catch (e) {
    diff.value = []
  }
  try {
    lineageRules.value = (await industryApi.lineageRules()).records || []
  } catch (e) {
    lineageRules.value = []
  }
  loadTerms()
}

const loadTerms = async () => {
  try {
    terms.value = (await industryApi.terminology({ industry: industry.value || undefined })).records || []
  } catch (e) {
    terms.value = []
  }
}

const instantiate = async (p) => {
  installing.value = p.id
  try {
    const r = await industryApi.instantiate(p.packageCode)
    ElMessage.success(`实例化完成：模型 ${r.models} 个，规则模板 ${r.ruleTemplates} 条，术语 ${r.terms} 条`)
    load()
  } catch (e) {
    /* http 层已提示 */
  } finally {
    installing.value = null
  }
}

const enable = async (p) => { await industryApi.enable(p.packageCode); ElMessage.success('已启用'); load() }
const disable = async (p) => { await industryApi.disable(p.packageCode); ElMessage.warning('已停用'); load() }

const showManifest = async (p) => {
  try {
    const r = await industryApi.exportPackage(p.packageCode)
    manifestData.value = r.manifest
    manifestVisible.value = true
  } catch (e) {
    /* http 层已提示 */
  }
}

onMounted(load)
</script>

<style scoped>
.manifest { background: #0f172a; color: #c7d2fe; border-radius: 6px; padding: 12px; max-height: 520px; overflow: auto; font-size: 11.5px; font-family: Consolas, monospace; }
</style>
