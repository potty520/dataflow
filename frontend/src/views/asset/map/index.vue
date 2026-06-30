<script setup>
import { nextTick, onMounted, reactive, ref } from 'vue'
import { assetTablePage, assetTableDetail, markKeyAsset, assetLineage } from '../../../api'
import { ElMessage } from 'element-plus'
import LineageGraph from '../../../components/LineageGraph.vue'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, keyword: '', layerCode: '' })
const detailVisible = ref(false)
const detail = ref({ table: {}, fields: [] })
const lineage = ref({ nodes: [], edges: [] })
const highlightId = ref(null)
const graphRef = ref(null)

async function loadData() {
  loading.value = true
  try {
    const res = await assetTablePage(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

async function loadLineage() {
  const res = await assetLineage()
  lineage.value = res.data || { nodes: [], edges: [] }
  await nextTick()
  graphRef.value?.resize()
}

async function showDetail(row) {
  highlightId.value = row.id
  const res = await assetTableDetail(row.id)
  detail.value = res.data
  detailVisible.value = true
}

function onGraphNodeClick(node) {
  const table = tableData.value.find(t => t.tableEn === node.name) ||
    lineage.value.nodes?.find(n => String(n.id) === node.id)
  if (table?.id) highlightId.value = table.id
  else if (node.id) highlightId.value = node.id
}

function focusTable(row) {
  highlightId.value = row.id
}

async function toggleKey(row) {
  await markKeyAsset(row.id, row.isKeyAsset === 1 ? 0 : 1)
  ElMessage.success('已更新')
  loadData()
  loadLineage()
}

onMounted(() => { loadData(); loadLineage() })
</script>

<template>
  <div class="page-container map-page">
    <div class="page-header">数据地图</div>

    <el-card class="topology-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>血缘拓扑图</span>
          <div>
            <el-tag v-for="layer in ['ODS','STD','DWD','DWS','ADS']" :key="layer" size="small" style="margin-left:6px">{{ layer }}</el-tag>
            <el-button size="small" style="margin-left:12px" @click="loadLineage">刷新地图</el-button>
          </div>
        </div>
      </template>
      <LineageGraph ref="graphRef" :graph="lineage" :highlight-id="highlightId" @node-click="onGraphNodeClick" />
    </el-card>

    <el-row :gutter="16" style="margin-top:16px">
      <el-col :span="24">
        <el-card header="资产检索" shadow="never">
          <div class="search-bar">
            <el-input v-model="query.keyword" placeholder="表中文名/英文名/库名" clearable style="width:220px" />
            <el-select v-model="query.layerCode" placeholder="分层" clearable style="width:100px">
              <el-option v-for="l in ['ODS','STD','DWD','DWS','ADS']" :key="l" :value="l" />
            </el-select>
            <el-button type="primary" @click="loadData">搜索</el-button>
          </div>
          <el-table :data="tableData" v-loading="loading" border highlight-current-row @row-click="focusTable">
            <el-table-column prop="tableEn" label="英文表名" />
            <el-table-column prop="tableCn" label="中文表名" />
            <el-table-column prop="layerCode" label="分层" width="80" />
            <el-table-column prop="databaseName" label="数据库" width="120" />
            <el-table-column prop="rowCount" label="行数" width="90" />
            <el-table-column prop="isKeyAsset" label="重点" width="70">
              <template #default="{ row }"><el-tag v-if="row.isKeyAsset === 1" type="warning" size="small">重点</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" width="160">
              <template #default="{ row }">
                <el-button link type="primary" @click.stop="showDetail(row)">详情</el-button>
                <el-button link @click.stop="toggleKey(row)">{{ row.isKeyAsset === 1 ? '取消重点' : '标为重点' }}</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" layout="total, prev, pager, next" style="margin-top:16px" @change="loadData" />
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="detailVisible" title="资产详情" width="560px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="英文名">{{ detail.table?.tableEn }}</el-descriptions-item>
        <el-descriptions-item label="中文名">{{ detail.table?.tableCn }}</el-descriptions-item>
        <el-descriptions-item label="数据库">{{ detail.table?.databaseName }}</el-descriptions-item>
        <el-descriptions-item label="分层">{{ detail.table?.layerCode }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="detail.fields" border style="margin-top:12px" size="small">
        <el-table-column prop="fieldEn" label="字段" />
        <el-table-column prop="fieldType" label="类型" width="90" />
        <el-table-column prop="isSensitive" label="敏感" width="60">
          <template #default="{ row }">{{ row.isSensitive === 1 ? '是' : '否' }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<style scoped>
.topology-card { margin-bottom: 0; }
.topology-card :deep(.el-card__body) { height: 480px; padding: 0; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
