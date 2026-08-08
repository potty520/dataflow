<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { DataAnalysis, EditPen, MagicStick, Select } from '@element-plus/icons-vue'
import { dashboardStats, devQualityOverview, govCleaningOverview, govMonitorStats } from '../../api'

const router = useRouter()
const platformStats = ref({})
const governanceStats = ref({})
const qualityStats = ref({})
const cleaningStats = ref({})

const keyMetrics = computed(() => [
  { label: '数据源数量', value: platformStats.value.datasourceCount || 0, note: '汇聚接入入口' },
  { label: '数据流任务', value: platformStats.value.dataflowCount || 0, note: '调度与同步任务' },
  { label: 'API 服务', value: platformStats.value.apiCount || 0, note: '服务开放能力' },
  { label: '治理模型', value: governanceStats.value.totalModels || 0, note: '建模与标准化' },
  { label: '清洗规则', value: cleaningStats.value.ruleCount || 0, note: '清洗规则片段' },
  { label: '质量任务', value: qualityStats.value.taskCount || 0, note: '数据质量巡检' }
])

const quickLinks = [
  { label: '数据清洗', path: '/governance/cleaning', icon: 'MagicStick' },
  { label: '数据治理', path: '/governance/modeling', icon: 'DataAnalysis' },
  { label: '数据质量', path: '/development/quality', icon: 'Select' },
  { label: '数据汇聚', path: '/aggregation/dataflow', icon: 'Connection' }
]

async function load() {
  const [platform, governance, quality, cleaning] = await Promise.all([
    dashboardStats(),
    govMonitorStats(),
    devQualityOverview(),
    govCleaningOverview()
  ])
  platformStats.value = platform.data || {}
  governanceStats.value = governance.data || {}
  qualityStats.value = quality.data || {}
  cleaningStats.value = cleaning.data || {}
}

onMounted(load)
</script>

<template>
  <div class="page-shell">
    <section class="page-top">
      <div>
        <h1 class="page-title">数据基座平台工作台</h1>
        <p class="page-subtitle">把门户、治理、清洗、开发、服务、资产和基础支撑放在同一屏里，方便直接操作。</p>
      </div>
      <div class="page-actions">
        <el-button type="primary" :icon="MagicStick" @click="router.push('/governance/cleaning')">数据清洗</el-button>
        <el-button :icon="DataAnalysis" @click="router.push('/governance/modeling')">数据治理</el-button>
        <el-button :icon="EditPen" @click="router.push('/development/quality')">数据质量</el-button>
      </div>
    </section>

    <div class="metric-grid">
      <div v-for="item in keyMetrics" :key="item.label" class="metric-card">
        <div class="metric-label">{{ item.label }}</div>
        <div class="metric-value">{{ item.value }}</div>
        <div class="metric-note">{{ item.note }}</div>
      </div>
    </div>

    <section class="dashboard-grid">
      <el-card class="surface-card">
        <template #header>平台概览</template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="数据源">{{ platformStats.datasourceCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="数据流">{{ platformStats.dataflowCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="API 服务">{{ platformStats.apiCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="应用数量">{{ platformStats.appCount || 0 }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card class="surface-card">
        <template #header>最近变更</template>
        <el-timeline v-if="(governanceStats.recentChanges || []).length">
          <el-timeline-item
            v-for="(item, index) in governanceStats.recentChanges"
            :key="index"
            :timestamp="item.time"
          >
            {{ item.detail }}
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无变更记录" />
      </el-card>
    </section>

    <section class="dashboard-grid">
      <el-card class="surface-card">
        <template #header>治理与清洗</template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="治理模型">{{ governanceStats.totalModels || 0 }}</el-descriptions-item>
          <el-descriptions-item label="治理标准">{{ governanceStats.totalStandards || 0 }}</el-descriptions-item>
          <el-descriptions-item label="已配置清洗">{{ cleaningStats.configuredCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="待补全清洗">{{ cleaningStats.pendingCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="清洗规则片段">{{ cleaningStats.ruleCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="代理节点">{{ governanceStats.totalProxyNodes || 0 }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card class="surface-card">
        <template #header>快捷入口</template>
        <div class="shortcut-grid">
          <div
            v-for="item in quickLinks"
            :key="item.label"
            class="shortcut-item"
            role="button"
            tabindex="0"
            @click="router.push(item.path)"
            @keydown.enter="router.push(item.path)"
          >
            <el-icon class="shortcut-icon"><component :is="item.icon" /></el-icon>
            <div>
              <div class="shortcut-title">{{ item.label }}</div>
              <div class="shortcut-sub">进入对应工作台</div>
            </div>
          </div>
        </div>
      </el-card>
    </section>
  </div>
</template>

<style scoped>
.shortcut-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.shortcut-item {
  display: flex;
  align-items: center;
  gap: 12px;
  border: 1px solid var(--app-border);
  border-radius: 8px;
  padding: 14px;
  background: var(--app-surface-soft);
  cursor: pointer;
}

.shortcut-item:hover {
  border-color: var(--app-primary);
  background: #fff;
}

.shortcut-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: rgba(37, 99, 235, 0.08);
  color: var(--app-primary);
}

.shortcut-title {
  font-weight: 600;
}

.shortcut-sub {
  margin-top: 4px;
  color: var(--app-muted);
  font-size: 12px;
}

@media (max-width: 860px) {
  .shortcut-grid {
    grid-template-columns: 1fr;
  }
}
</style>
