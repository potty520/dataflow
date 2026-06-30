<script setup>
import { onMounted, ref } from 'vue'
import { apiPage, apiParams, apiCall, apiDoc } from '../../../api'
import { ElMessage } from 'element-plus'

const apis = ref([])
const selectedApi = ref(null)
const params = ref([])
const paramValues = ref({})
const response = ref('')
const curlCommand = ref('')
const loading = ref(false)
const docText = ref('')

async function loadApis() {
  const res = await apiPage({ page: 1, size: 100, apiName: '' })
  apis.value = (res.data?.records || []).filter(a => a.status === 1)
}

async function selectApi(api) {
  selectedApi.value = api
  response.value = ''
  paramValues.value = {}
  try {
    const [paramsRes, docRes] = await Promise.all([
      apiParams(api.id),
      apiDoc(api.id)
    ])
    params.value = paramsRes.data || []
    docText.value = docRes.data || ''
    // Generate curl
    const method = api.requestMethod || 'GET'
    const url = `/api/service/api/${api.id}/call`
    let curl = `curl -X ${method} 'http://localhost:8080${url}'`
    curl += ` \\\n  -H 'Content-Type: application/json'`
    curl += ` \\\n  -H 'X-App-Key: YOUR_APP_KEY'`
    curl += ` \\\n  -H 'X-App-Secret: YOUR_APP_SECRET'`
    if (params.value.length > 0 && method !== 'GET') {
      const body = {}
      params.value.forEach(p => { body[p.name] = p.defaultValue || '' })
      curl += ` \\\n  -d '${JSON.stringify(body)}'`
    }
    curlCommand.value = curl
  } catch (e) {
    ElMessage.error('获取API参数失败')
  }
}

async function tryCall() {
  loading.value = true
  try {
    const res = await apiCall(selectedApi.value.id, paramValues.value)
    response.value = JSON.stringify(res.data, null, 2)
    ElMessage.success('调用成功')
  } catch (e) {
    response.value = JSON.stringify({ error: e.message || '调用失败' }, null, 2)
    ElMessage.error('调用失败')
  } finally {
    loading.value = false
  }
}

function copyCurl() {
  navigator.clipboard.writeText(curlCommand.value)
  ElMessage.success('已复制curl命令')
}

onMounted(loadApis)
</script>

<template>
  <div class="page-container debug-page">
    <div class="page-header">API调试工具</div>
    <el-row :gutter="16">
      <!-- API List -->
      <el-col :span="6">
        <el-card header="已发布API" shadow="never">
          <el-input v-model="searchText" placeholder="搜索API" size="small" clearable style="margin-bottom:10px" />
          <div class="api-list">
            <div v-for="api in apis" :key="api.id"
              :class="['api-item', { active: selectedApi?.id === api.id }]"
              @click="selectApi(api)">
              <span class="api-method">{{ api.requestMethod }}</span>
              <span class="api-name">{{ api.apiName }}</span>
            </div>
          </div>
          <el-empty v-if="!apis.length" description="无已发布API" />
        </el-card>
      </el-col>

      <!-- Debug Panel -->
      <el-col :span="18">
        <el-card v-if="selectedApi" shadow="never">
          <template #header>
            <div class="debug-header">
              <el-tag :type="selectedApi.requestMethod === 'POST' ? 'primary' : 'success'" size="small">
                {{ selectedApi.requestMethod }}
              </el-tag>
              <span class="debug-path">/api/service/api/{{ selectedApi.id }}/call</span>
              <el-tag v-if="selectedApi.apiVersion" size="small" type="info" style="margin-left:8px">v{{ selectedApi.apiVersion }}</el-tag>
              <el-button size="small" style="margin-left:auto" @click="copyCurl">复制curl</el-button>
            </div>
          </template>

          <!-- Parameters Form -->
          <div v-if="params.length > 0" class="params-section">
            <h4>参数</h4>
            <el-form label-width="100px" size="small">
              <el-form-item v-for="p in params" :key="p.name" :label="p.name">
                <el-input v-model="paramValues[p.name]" :placeholder="p.type + (p.description ? ' - ' + p.description : '')" />
              </el-form-item>
            </el-form>
          </div>
          <el-empty v-else description="此API无需参数" :image-size="40" />

          <div style="margin-top:16px; display:flex; gap:8px">
            <el-button type="primary" @click="tryCall" :loading="loading">▶ 发送请求</el-button>
          </div>

          <!-- Response -->
          <div v-if="response" style="margin-top:16px">
            <h4>响应结果</h4>
            <pre class="response-json"><code>{{ response }}</code></pre>
          </div>

          <!-- curl preview -->
          <div v-if="curlCommand" style="margin-top:16px">
            <el-collapse>
              <el-collapse-item title="curl命令预览">
                <pre class="curl-preview"><code>{{ curlCommand }}</code></pre>
              </el-collapse-item>
              <el-collapse-item title="API文档">
                <pre class="doc-preview"><code>{{ docText }}</code></pre>
              </el-collapse-item>
            </el-collapse>
          </div>
        </el-card>
        <el-card v-else shadow="never">
          <el-empty description="请从左侧选择一个已发布API进行调试" :image-size="80" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.debug-page { }
.api-list { max-height: 500px; overflow-y: auto; }
.api-item {
  padding: 8px 12px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
}
.api-item:hover, .api-item.active { background: #ecf5ff; color: #409eff; }
.api-method { font-weight: bold; font-size: 12px; width: 42px; }
.api-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.debug-header { display: flex; align-items: center; gap: 10px; }
.debug-path { font-family: monospace; color: #666; }
.params-section h4 { margin: 0 0 10px 0; color: #333; }
.response-json {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 16px;
  border-radius: 6px;
  overflow-x: auto;
  font-size: 13px;
  max-height: 400px;
  overflow-y: auto;
}
.curl-preview, .doc-preview {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  font-size: 12px;
  overflow-x: auto;
  white-space: pre-wrap;
}
</style>
