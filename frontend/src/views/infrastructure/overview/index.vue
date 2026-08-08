<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { infraSupportOverview } from '../../../api'

const router = useRouter()
const loading = ref(false)
const summary = ref({
  componentCoverage: [],
  configFiles: [],
  alerts: [],
  componentStatus: {},
  hostStatus: {}
})

const statusMeta = {
  RUNNING: { label: '运行中', type: 'success' },
  ATTENTION: { label: '需关注', type: 'warning' },
  MISSING: { label: '未部署', type: 'danger' },
  ACTIVE: { label: '待处理', type: 'danger' },
  ACKED: { label: '已确认', type: 'warning' },
  RECOVERED: { label: '已恢复', type: 'success' },
  INFO: { label: '提示', type: 'info' },
  WARN: { label: '警告', type: 'warning' },
  CRITICAL: { label: '严重', type: 'danger' }
}

const metricItems = [
  { key: 'hostCount', label: '主机总数', note: '纳管节点' },
  { key: 'onlineHosts', label: '在线主机', note: '当前可用' },
  { key: 'componentCount', label: '组件实例', note: '已登记服务' },
  { key: 'runningComponents', label: '运行组件', note: '服务状态正常' },
  { key: 'clusterCount', label: '集群数量', note: '计算与存储集群' },
  { key: 'resourceGroupCount', label: '资源组', note: 'YARN / K8S 资源池' },
  { key: 'repositoryCount', label: '部署包', note: '组件仓库' },
  { key: 'activeAlertCount', label: '活动告警', note: '需要关注的事件' }
]

async function loadData() {
  loading.value = true
  try {
    const res = await infraSupportOverview()
    summary.value = res.data || summary.value
  } finally {
    loading.value = false
  }
}

function go(path) {
  router.push(path)
}

function tagType(value) {
  return statusMeta[value]?.type || 'info'
}

function tagLabel(value) {
  return statusMeta[value]?.label || value || '未知'
}

function coveragePercent(row) {
  if (!row.instances) return 0
  return Math.round((row.running / row.instances) * 100)
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '-'
}

onMounted(loadData)
</script>

<template>
  <div class="page-container infrastructure-overview">
    <div class="page-top">
      <div>
        <p class="page-kicker">基础支撑</p>
        <h1 class="page-title">基础支撑总览</h1>
        <p class="page-subtitle">统一查看主机、集群、服务组件、配置文件和运行告警。</p>
      </div>
      <div class="page-actions">
        <el-button :loading="loading" @click="loadData">
          <el-icon><Refresh /></el-icon>
          <span>刷新状态</span>
        </el-button>
        <el-button type="primary" @click="go('/infrastructure/host')">
          <el-icon><Platform /></el-icon>
          <span>主机管理</span>
        </el-button>
        <el-button @click="go('/infrastructure/alert')">
          <el-icon><Warning /></el-icon>
          <span>告警中心</span>
        </el-button>
      </div>
    </div>

    <div class="metric-grid">
      <div v-for="item in metricItems" :key="item.key" class="metric-card">
        <div class="metric-label">{{ item.label }}</div>
        <div class="metric-value">{{ summary[item.key] ?? 0 }}</div>
        <div class="metric-note">{{ item.note }}</div>
      </div>
    </div>

    <div class="overview-grid">
      <el-card class="surface-card" shadow="never">
        <template #header>
          <div class="section-heading">
            <span>平台组件覆盖</span>
            <el-button link type="primary" @click="go('/infrastructure/component')">组件管理</el-button>
          </div>
        </template>
        <el-table :data="summary.componentCoverage" v-loading="loading" size="small">
          <el-table-column prop="name" label="组件" width="110" />
          <el-table-column label="实例" width="80">
            <template #default="{ row }">{{ row.running }} / {{ row.instances }}</template>
          </el-table-column>
          <el-table-column label="覆盖率" min-width="150">
            <template #default="{ row }">
              <el-progress :percentage="coveragePercent(row)" :status="row.status === 'MISSING' ? 'exception' : ''" />
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="tagType(row.status)" size="small">{{ tagLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card class="surface-card" shadow="never">
        <template #header>
          <div class="section-heading">
            <span>配置文件同步</span>
            <el-button link type="primary" @click="go('/infrastructure/config')">服务配置</el-button>
          </div>
        </template>
        <el-table :data="summary.configFiles" v-loading="loading" size="small">
          <el-table-column prop="fileName" label="文件" min-width="150" />
          <el-table-column prop="component" label="组件" width="80" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.configured ? 'success' : 'warning'" size="small">
                {{ row.configured ? '已配置' : '待配置' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>

    <el-card class="surface-card" shadow="never">
      <template #header>
        <div class="section-heading">
          <span>近期告警</span>
          <el-button link type="primary" @click="go('/infrastructure/alert')">查看全部</el-button>
        </div>
      </template>
      <el-table :data="summary.alerts" v-loading="loading" size="small">
        <el-table-column prop="alertName" label="告警名称" min-width="180" />
        <el-table-column prop="targetName" label="目标" width="180" />
        <el-table-column prop="message" label="事件描述" show-overflow-tooltip />
        <el-table-column prop="alertLevel" label="级别" width="90">
          <template #default="{ row }">
            <el-tag :type="tagType(row.alertLevel)" size="small">{{ tagLabel(row.alertLevel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="处理状态" width="95">
          <template #default="{ row }">
            <el-tag :type="tagType(row.status)" size="small">{{ tagLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastTriggerTime" label="触发时间" width="170">
          <template #default="{ row }">{{ formatTime(row.lastTriggerTime) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.overview-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr);
  gap: 16px;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-weight: 600;
}

@media (max-width: 1050px) {
  .overview-grid {
    grid-template-columns: 1fr;
  }
}
</style>
