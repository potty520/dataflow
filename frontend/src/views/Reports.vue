<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>质量报告</h2>
        <div class="sub">自动生成结构化质量报告，支持 HTML / CSV / Markdown 导出</div>
      </div>
      <el-button type="primary" size="small" :icon="DocumentAdd" :loading="generating" @click="generate">生成报告</el-button>
    </div>

    <div class="grid grid-3">
      <div class="stat">
        <div class="label">报告模板</div>
        <div class="value">{{ templates.length }}</div>
        <div class="foot">质量/资产/监管报送</div>
      </div>
      <div class="stat green">
        <div class="label">已生成报告</div>
        <div class="value">{{ reports.length }}</div>
        <div class="foot">最近：{{ reports[0]?.title || '暂无' }}</div>
      </div>
      <div class="stat orange">
        <div class="label">导出文件</div>
        <div class="value">{{ files.length }}</div>
        <div class="foot">HTML / CSV / Markdown</div>
      </div>
    </div>

    <div class="card" style="margin-top:12px">
      <div class="card-head"><h3>报告清单</h3></div>
      <el-table :data="reports" size="small">
        <el-table-column prop="code" label="报告编号" width="180"><template #default="{ row }"><span class="mono">{{ row.code }}</span></template></el-table-column>
        <el-table-column prop="title" label="标题" min-width="240" />
        <el-table-column prop="scope" label="范围" width="140" />
        <el-table-column prop="period" label="周期" width="90" />
        <el-table-column prop="totalScore" label="综合分" width="90" align="right" />
        <el-table-column prop="scoreLevel" label="等级" width="80" />
        <el-table-column prop="creator" label="编制人" width="86" />
        <el-table-column prop="createTime" label="生成时间" width="150"><template #default="{ row }">{{ fmt(row.createTime) }}</template></el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="preview(row)">预览</el-button>
            <el-button link type="primary" size="small" @click="exportAs(row, 'html')">HTML</el-button>
            <el-button link type="primary" size="small" @click="exportAs(row, 'csv')">CSV</el-button>
            <el-button link type="primary" size="small" @click="exportAs(row, 'md')">MD</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="grid grid-2" style="margin-top:12px">
      <div class="card">
        <div class="card-head"><h3>报告模板</h3></div>
        <el-table :data="templates" size="small">
          <el-table-column prop="code" label="模板编码" width="170"><template #default="{ row }"><span class="mono">{{ row.code }}</span></template></el-table-column>
          <el-table-column prop="name" label="名称" min-width="160" />
          <el-table-column prop="reportType" label="类型" width="90" />
          <el-table-column prop="industry" label="行业" width="80" />
          <el-table-column prop="engine" label="引擎" width="80" />
        </el-table>
      </div>
      <div class="card">
        <div class="card-head"><h3>导出记录</h3></div>
        <el-table :data="files" size="small" max-height="240">
          <el-table-column prop="fileName" label="文件" min-width="200" />
          <el-table-column prop="fmt" label="格式" width="80" />
          <el-table-column prop="sizeKb" label="大小(KB)" width="90" align="right" />
          <el-table-column prop="createTime" label="时间" width="150"><template #default="{ row }">{{ fmt(row.createTime) }}</template></el-table-column>
        </el-table>
      </div>
    </div>

    <el-drawer v-model="previewVisible" :title="current.title" size="56%">
      <div v-if="context">
        <div class="grid grid-4" style="margin-bottom:12px">
          <div class="stat" style="padding:10px"><div class="label">综合分</div><div class="value" style="font-size:20px">{{ context.avgScore }}</div></div>
          <div class="stat" style="padding:10px"><div class="label">等级</div><div class="value" style="font-size:20px">{{ context.level }}</div></div>
          <div class="stat" style="padding:10px"><div class="label">纳入表数</div><div class="value" style="font-size:20px">{{ context.tableCount }}</div></div>
          <div class="stat" style="padding:10px"><div class="label">统计周期</div><div class="value" style="font-size:16px">{{ context.period }}</div></div>
        </div>
        <div class="section-title">维度得分</div>
        <el-table :data="context.dimensions || []" size="small">
          <el-table-column prop="dimension" label="维度" width="140" />
          <el-table-column prop="rules" label="规则数" width="90" align="right" />
          <el-table-column prop="passRate" label="通过率(%)" width="110" align="right" />
        </el-table>
        <div class="section-title">表评分（后十名）</div>
        <el-table :data="(context.tables || []).slice(0, 10)" size="small" max-height="300">
          <el-table-column prop="name" label="表" width="150"><template #default="{ row }"><span class="mono">{{ row.name }}</span></template></el-table-column>
          <el-table-column prop="cnName" label="中文名" width="140" />
          <el-table-column prop="qualityScore" label="评分" width="90" align="right" />
          <el-table-column prop="owner" label="责任人" width="90" />
        </el-table>
        <div style="margin-top:12px">
          <el-button size="small" type="primary" @click="exportAs(current, 'html')">导出 HTML</el-button>
          <el-button size="small" @click="exportAs(current, 'csv')">导出 CSV</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { DocumentAdd } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { qualityApi, reportApi } from '@/api'

const reports = ref([])
const templates = ref([])
const files = ref([])
const generating = ref(false)
const previewVisible = ref(false)
const current = ref({})
const context = ref(null)

const fmt = (v) => (v ? String(v).replace('T', ' ').slice(0, 16) : '-')

const load = async () => {
  // 报告清单是主数据；模板与导出记录是旁路，任一失败都不影响清单展示
  let list = []
  try {
    const d = await qualityApi.reports({ pageSize: 50 })
    reports.value = d.records || []
    list = reports.value
  } catch (e) {
    reports.value = []
  }
  try {
    const t = await reportApi.templates()
    templates.value = t.records || []
  } catch (e) {
    templates.value = []
  }
  files.value = []
  const details = await Promise.allSettled(list.slice(0, 5).map((r) => qualityApi.report(r.id)))
  for (const d of details) {
    if (d.status === 'fulfilled' && d.value) files.value.push(...(d.value.files || []))
  }
}

const generate = async () => {
  generating.value = true
  try {
    const r = await qualityApi.generateReport({ scope: '全域核心表', period: new Date().toISOString().slice(0, 7) })
    ElMessage.success(`报告已生成：${r.code}，综合分 ${r.score}`)
    await load()
  } catch (e) {
    /* http 层已提示 */
  } finally {
    generating.value = false
  }
}

const preview = async (row) => {
  try {
    current.value = await qualityApi.report(row.id)
    context.value = current.value.context
    previewVisible.value = true
  } catch (e) {
    /* http 层已提示 */
  }
}

const exportAs = async (row, format) => {
  try {
    const r = await qualityApi.exportReport(row.id, format)
    ElMessage.success(`已导出 ${r.fileName}（${r.sizeKb} KB）`)
    load()
  } catch (e) {
    /* http 层已提示 */
  }
}

onMounted(load)
</script>
