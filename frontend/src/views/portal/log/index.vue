<script setup>
import { onMounted, reactive, ref } from 'vue'
import { logPage } from '../../../api'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, username: '', eventName: '', result: null })

async function loadData() {
  loading.value = true
  try {
    const res = await logPage(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">日志管理</div>
    <div class="search-bar">
      <el-input v-model="query.username" placeholder="用户名" clearable style="width:160px" />
      <el-input v-model="query.eventName" placeholder="事件名称" clearable style="width:160px" />
      <el-select v-model="query.result" placeholder="执行结果" clearable style="width:120px">
        <el-option :value="1" label="成功" /><el-option :value="0" label="失败" />
      </el-select>
      <el-button type="primary" @click="loadData">查询</el-button>
    </div>
    <el-table :data="tableData" v-loading="loading" border>
      <el-table-column prop="username" label="用户" width="100" />
      <el-table-column prop="eventName" label="事件" />
      <el-table-column prop="result" label="结果" width="80">
        <template #default="{ row }"><el-tag :type="row.result === 1 ? 'success' : 'danger'" size="small">{{ row.result === 1 ? '成功' : '失败' }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="method" label="请求方式" width="90" />
      <el-table-column prop="resourceType" label="资源类别" width="100" />
      <el-table-column prop="resourceName" label="资源名称" />
      <el-table-column prop="ip" label="IP" width="120" />
      <el-table-column prop="createTime" label="时间" width="180" />
    </el-table>
    <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" layout="total, prev, pager, next" style="margin-top:16px" @change="loadData" />
  </div>
</template>
