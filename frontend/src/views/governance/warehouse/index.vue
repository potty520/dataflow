<script setup>
import { onMounted, ref } from 'vue'
import { govLayerList, createGovLayer, updateGovLayer, deleteGovLayer, govWhCatalogTree, createGovWhCatalog, deleteGovWhCatalog } from '../../../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const layers = ref([])
const catalogTree = ref([])
const layerDialog = ref(false)
const catalogDialog = ref(false)
const layerForm = ref({})
const catalogForm = ref({})
const isEditLayer = ref(false)
const selectedLayerId = ref(null)

async function loadLayers() {
  const res = await govLayerList()
  layers.value = res.data
}

async function loadCatalog() {
  const res = await govWhCatalogTree({ layerId: selectedLayerId.value })
  catalogTree.value = res.data
}

function openLayerCreate() {
  isEditLayer.value = false
  layerForm.value = { layerCode: '', layerName: '', description: '', sortOrder: 0 }
  layerDialog.value = true
}

function openLayerEdit(row) {
  isEditLayer.value = true
  layerForm.value = { ...row }
  layerDialog.value = true
}

async function saveLayer() {
  if (isEditLayer.value) await updateGovLayer(layerForm.value.id, layerForm.value)
  else await createGovLayer(layerForm.value)
  ElMessage.success('保存成功')
  layerDialog.value = false
  loadLayers()
}

async function removeLayer(row) {
  await ElMessageBox.confirm('确认删除该分层？', '提示', { type: 'warning' })
  await deleteGovLayer(row.id)
  loadLayers()
}

function openCatalogCreate() {
  catalogForm.value = { layerId: selectedLayerId.value, parentId: 0, name: '' }
  catalogDialog.value = true
}

async function saveCatalog() {
  await createGovWhCatalog(catalogForm.value)
  ElMessage.success('创建成功')
  catalogDialog.value = false
  loadCatalog()
}

async function removeCatalog(row) {
  await ElMessageBox.confirm('确认删除？', '提示', { type: 'warning' })
  await deleteGovWhCatalog(row.id)
  loadCatalog()
}

function selectLayer(id) {
  selectedLayerId.value = id
  loadCatalog()
}

onMounted(loadLayers)
</script>

<template>
  <div class="page-container">
    <div class="page-header">仓库分层</div>
    <el-row :gutter="16">
      <el-col :span="14">
        <el-card header="数仓分层 (ODS/STD/DWD/DWS/ADS)">
          <el-button type="success" size="small" @click="openLayerCreate" style="margin-bottom:12px">新增分层</el-button>
          <el-table :data="layers" border @row-click="(row) => selectLayer(row.id)" highlight-current-row>
            <el-table-column prop="layerCode" label="编码" width="80" />
            <el-table-column prop="layerName" label="名称" />
            <el-table-column prop="description" label="描述" />
            <el-table-column prop="sortOrder" label="排序" width="70" />
            <el-table-column label="操作" width="140">
              <template #default="{ row }">
                <el-button link type="primary" @click.stop="openLayerEdit(row)">编辑</el-button>
                <el-button link type="danger" @click.stop="removeLayer(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card header="目录管理">
          <el-button type="success" size="small" :disabled="!selectedLayerId" @click="openCatalogCreate" style="margin-bottom:12px">新增目录</el-button>
          <el-tree :data="catalogTree" :props="{ label: 'name', children: 'children' }" default-expand-all>
            <template #default="{ data }">
              <span>{{ data.name }}</span>
              <el-button link type="danger" size="small" @click="removeCatalog(data)">删除</el-button>
            </template>
          </el-tree>
        </el-card>
      </el-col>
    </el-row>
    <el-dialog v-model="layerDialog" :title="isEditLayer ? '编辑分层' : '新增分层'" width="420px">
      <el-form label-width="80px">
        <el-form-item label="编码"><el-input v-model="layerForm.layerCode" placeholder="ODS/STD/DWD/DWS/ADS" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="layerForm.layerName" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="layerForm.description" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="layerForm.sortOrder" :min="0" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="layerDialog = false">取消</el-button><el-button type="primary" @click="saveLayer">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="catalogDialog" title="新增目录" width="400px">
      <el-form label-width="80px"><el-form-item label="名称"><el-input v-model="catalogForm.name" /></el-form-item></el-form>
      <template #footer><el-button @click="catalogDialog = false">取消</el-button><el-button type="primary" @click="saveCatalog">保存</el-button></template>
    </el-dialog>
  </div>
</template>
