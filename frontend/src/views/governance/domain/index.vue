<script setup>
import { onMounted, reactive, ref } from 'vue'
import { govLayerList, govDomainList, createGovDomain, updateGovDomain, deleteGovDomain, sortGovDomain } from '../../../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const layers = ref([])
const selectedLayer = ref(null)
const domainData = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const form = ref({})
const isEdit = ref(false)

async function loadLayers() {
  const res = await govLayerList()
  layers.value = res.data
}

async function loadDomains() {
  if (!selectedLayer.value) { domainData.value = []; return }
  const res = await govDomainList({ layerId: selectedLayer.value })
  domainData.value = res.data
}

function onLayerSelect(layerId) {
  selectedLayer.value = layerId
  loadDomains()
}

function openCreate() {
  isEdit.value = false
  form.value = { layerId: selectedLayer.value, domainName: '', domainCode: '', description: '', parentId: 0, sortOrder: domainData.value.length + 1 }
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

async function save() {
  if (isEdit.value) await updateGovDomain(form.value.id, form.value)
  else await createGovDomain(form.value)
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadDomains()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除域「${row.domainName}」？`, '提示', { type: 'warning' })
  await deleteGovDomain(row.id)
  ElMessage.success('删除成功')
  loadDomains()
}

function moveUp(index) {
  if (index <= 0) return
  const row = domainData.value[index]
  domainData.value.splice(index, 1)
  domainData.value.splice(index - 1, 0, row)
  updateSortOrder()
}

function moveDown(index) {
  if (index >= domainData.value.length - 1) return
  const row = domainData.value[index]
  domainData.value.splice(index, 1)
  domainData.value.splice(index + 1, 0, row)
  updateSortOrder()
}

async function updateSortOrder() {
  const updates = domainData.value.map((item, idx) => ({ id: item.id, sortOrder: idx + 1 }))
  await sortGovDomain(updates)
}

onMounted(loadLayers)
</script>

<template>
  <div class="page-container">
    <div class="page-header">业务域管理</div>
    <div style="display:flex;gap:20px">
      <div style="width:200px;border:1px solid #dcdfe6;border-radius:4px;padding:12px">
        <div style="font-weight:bold;margin-bottom:8px">数仓分层</div>
        <div v-for="layer in layers" :key="layer.id"
             :style="{ padding: '8px 12px', cursor: 'pointer', borderRadius: '4px',
                       background: selectedLayer === layer.id ? '#e6f7ff' : 'transparent',
                       borderLeft: selectedLayer === layer.id ? '3px solid #1890ff' : '3px solid transparent' }"
             @click="onLayerSelect(layer.id)">
          {{ layer.layerCode }} - {{ layer.layerName }}
        </div>
      </div>
      <div style="flex:1">
        <div style="margin-bottom:12px">
          <el-button type="success" @click="openCreate" :disabled="!selectedLayer">新增业务域</el-button>
          <span v-if="!selectedLayer" style="color:#999;margin-left:8px">请先选择左侧分层</span>
        </div>
        <el-table :data="domainData" border>
          <el-table-column prop="domainName" label="域名称" />
          <el-table-column prop="domainCode" label="域代码" width="150" />
          <el-table-column prop="description" label="描述" show-overflow-tooltip />
          <el-table-column prop="sortOrder" label="排序" width="80" />
          <el-table-column label="操作" width="240">
            <template #default="{ row, $index }">
              <el-button link type="primary" @click="moveUp($index)">↑</el-button>
              <el-button link type="primary" @click="moveDown($index)">↓</el-button>
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="remove(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑业务域' : '新增业务域'" width="500px">
      <el-form label-width="90px">
        <el-form-item label="域名称"><el-input v-model="form.domainName" /></el-form-item>
        <el-form-item label="域代码"><el-input v-model="form.domainCode" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
