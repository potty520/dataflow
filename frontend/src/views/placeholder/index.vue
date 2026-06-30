<script setup>
import { useRoute } from 'vue-router'

const route = useRoute()
const moduleNames = {
  standard: '数据标准', warehouse: '仓库分层', modeling: '数据建模', indicator: '指标管理',
  workflow: '工作流开发', script: '脚本开发', quality: '数据质量', schedule: '数据调度',
  catalog: '服务目录', workorder: '工单管理',
  overview: '数据总览', map: '数据地图', monitor: '资产监控',
  component: '组件管理', cluster: '集群管理'
}
</script>

<template>
  <div class="page-container">
    <div class="page-header">{{ moduleNames[route.params.module] || route.meta.title }}</div>
    <el-empty description="该模块框架已就绪，可按需求规格说明书继续扩展">
      <p style="color:#666;max-width:480px;margin:0 auto">
        当前路径：{{ route.path }}。需求文档中该模块的详细功能（如数据建模血缘分析、Hive脚本开发、集群监控大屏等）可在后续迭代中接入。
      </p>
    </el-empty>
  </div>
</template>
