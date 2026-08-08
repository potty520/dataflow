<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { configList, configUpdateValue, infraConfigFiles, updateInfraConfigFile } from '../../../api'

const activeTab = ref('files')
const loading = ref(false)
const files = ref([])
const configs = ref([])
const fileDialogVisible = ref(false)
const configDialogVisible = ref(false)
const fileForm = ref({})
const configForm = ref({})

async function loadData() {
  loading.value = true
  try {
    const [fileRes, configRes] = await Promise.all([infraConfigFiles(), configList()])
    files.value = fileRes.data || []
    configs.value = configRes.data || []
  } finally {
    loading.value = false
  }
}

function openFile(row) {
  fileForm.value = { ...row, env: 'DEV' }
  fileDialogVisible.value = true
}

function openConfig(row) {
  configForm.value = { ...row }
  configDialogVisible.value = true
}

async function saveFile() {
  await updateInfraConfigFile(fileForm.value.key, {
    configValue: fileForm.value.configValue,
    description: fileForm.value.description,
    env: fileForm.value.env || 'DEV'
  })
  ElMessage.success('配置文件已保存')
  fileDialogVisible.value = false
  loadData()
}

async function saveConfig() {
  await configUpdateValue(configForm.value.configKey, {
    configValue: configForm.value.configValue,
    description: configForm.value.description,
    env: configForm.value.env
  })
  ElMessage.success('系统参数已保存')
  configDialogVisible.value = false
  loadData()
}

function envTag(env) {
  return { DEV: '', TEST: 'warning', PROD: 'danger' }[env] || 'info'
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <div class="page-top">
      <div>
        <p class="page-kicker">基础支撑 / 配置管理</p>
        <h1 class="page-title">服务配置</h1>
        <p class="page-subtitle">集中维护 Hadoop、YARN、Hive、Flink、Hudi 等运行配置与平台参数。</p>
      </div>
      <div class="page-actions">
        <el-button :loading="loading" @click="loadData">
          <el-icon><Refresh /></el-icon>
          <span>刷新</span>
        </el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="配置文件" name="files">
        <el-table :data="files" v-loading="loading" border>
          <el-table-column prop="fileName" label="文件名" min-width="170" />
          <el-table-column prop="component" label="组件" width="100" />
          <el-table-column prop="description" label="用途" min-width="260" show-overflow-tooltip />
          <el-table-column prop="required" label="必需" width="90">
            <template #default="{ row }">
              <el-tag :type="row.required === '1' ? 'danger' : 'info'" size="small">
                {{ row.required === '1' ? '必需' : '可选' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="配置状态" width="110">
            <template #default="{ row }">
              <el-tag :type="row.configured ? 'success' : 'warning'" size="small">
                {{ row.configured ? '已保存' : '待保存' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="updateTime" label="更新时间" width="180" />
          <el-table-column label="操作" width="110">
            <template #default="{ row }">
              <el-button link type="primary" @click="openFile(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="系统参数" name="params">
        <el-table :data="configs" v-loading="loading" border>
          <el-table-column prop="configKey" label="配置键" min-width="240" />
          <el-table-column prop="configValue" label="配置值" min-width="260" show-overflow-tooltip />
          <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
          <el-table-column prop="env" label="环境" width="90">
            <template #default="{ row }">
              <el-tag :type="envTag(row.env)" size="small">{{ row.env || 'DEV' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="updateTime" label="更新时间" width="180" />
          <el-table-column label="操作" width="110">
            <template #default="{ row }">
              <el-button link type="primary" @click="openConfig(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="fileDialogVisible" :title="`编辑 ${fileForm.fileName || ''}`" width="720px">
      <el-form label-width="92px">
        <el-form-item label="配置键"><el-input v-model="fileForm.key" disabled /></el-form-item>
        <el-form-item label="组件"><el-input v-model="fileForm.component" disabled /></el-form-item>
        <el-form-item label="环境">
          <el-select v-model="fileForm.env" style="width:100%">
            <el-option label="开发" value="DEV" />
            <el-option label="测试" value="TEST" />
            <el-option label="生产" value="PROD" />
          </el-select>
        </el-form-item>
        <el-form-item label="配置内容">
          <el-input v-model="fileForm.configValue" type="textarea" :rows="14" resize="vertical" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="fileDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveFile">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="configDialogVisible" title="编辑系统参数" width="560px">
      <el-form label-width="92px">
        <el-form-item label="配置键"><el-input v-model="configForm.configKey" disabled /></el-form-item>
        <el-form-item label="配置值"><el-input v-model="configForm.configValue" type="textarea" :rows="5" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="configForm.description" /></el-form-item>
        <el-form-item label="环境">
          <el-select v-model="configForm.env" style="width:100%">
            <el-option label="开发" value="DEV" />
            <el-option label="测试" value="TEST" />
            <el-option label="生产" value="PROD" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="configDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveConfig">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
