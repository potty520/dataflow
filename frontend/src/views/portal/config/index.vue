<script setup>
import { onMounted, ref } from 'vue'
import { configList, configUpdateValue } from '../../../api'
import { ElMessage } from 'element-plus'

const configs = ref([])
const editDialogVisible = ref(false)
const editForm = ref({ configKey: '', configValue: '', description: '', env: 'DEV' })

async function loadConfigs() {
  const res = await configList()
  configs.value = res.data || []
}

function openEdit(row) {
  editForm.value = { ...row }
  editDialogVisible.value = true
}

async function saveConfig() {
  await configUpdateValue(editForm.value.configKey, {
    configValue: editForm.value.configValue,
    description: editForm.value.description,
    env: editForm.value.env
  })
  ElMessage.success('配置已更新')
  editDialogVisible.value = false
  loadConfigs()
}

function getEnvTagType(env) {
  const map = { DEV: '', TEST: 'warning', PROD: 'danger' }
  return map[env] || 'info'
}

onMounted(loadConfigs)
</script>

<template>
  <div class="page-container">
    <div class="page-header">配置中心</div>
    <el-table :data="configs" border>
      <el-table-column prop="configKey" label="配置键" width="250" />
      <el-table-column prop="configValue" label="配置值" show-overflow-tooltip />
      <el-table-column prop="description" label="说明" width="250" show-overflow-tooltip />
      <el-table-column prop="env" label="环境" width="100">
        <template #default="{ row }">
          <el-tag :type="getEnvTagType(row.env)" size="small">{{ row.env }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" width="180" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="editDialogVisible" title="编辑配置" width="500px">
      <el-form label-width="90px">
        <el-form-item label="配置键">
          <el-input v-model="editForm.configKey" disabled />
        </el-form-item>
        <el-form-item label="配置值">
          <el-input v-model="editForm.configValue" type="textarea" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="editForm.description" />
        </el-form-item>
        <el-form-item label="环境">
          <el-select v-model="editForm.env" style="width:100%">
            <el-option label="开发" value="DEV" />
            <el-option label="测试" value="TEST" />
            <el-option label="生产" value="PROD" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveConfig">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
