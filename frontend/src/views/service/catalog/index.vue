<script setup>
import { onMounted, ref } from 'vue'
import { svcCatalogProjectList, svcCatalogTree, createSvcCatalog, deleteSvcCatalog } from '../../../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const projects = ref([])
const catalogTree = ref([])
const selectedProject = ref(null)
const dialogVisible = ref(false)
const form = ref({ name: '', parentId: 0 })

async function loadProjects() {
  const res = await svcCatalogProjectList()
  projects.value = res.data
  if (projects.value.length && !selectedProject.value) {
    selectedProject.value = projects.value[0].id
    loadCatalog()
  }
}

async function loadCatalog() {
  if (!selectedProject.value) return
  const res = await svcCatalogTree({ projectId: selectedProject.value })
  catalogTree.value = res.data
}

function openCreate(parentId = 0) {
  form.value = { name: '', parentId, projectId: selectedProject.value }
  dialogVisible.value = true
}

async function save() {
  await createSvcCatalog(form.value)
  ElMessage.success('创建成功')
  dialogVisible.value = false
  loadCatalog()
}

async function remove(row) {
  await ElMessageBox.confirm('确认删除该目录？', '提示', { type: 'warning' })
  await deleteSvcCatalog(row.id)
  loadCatalog()
}

onMounted(loadProjects)
</script>

<template>
  <div class="page-container">
    <div class="page-header">服务目录管理</div>
    <div class="search-bar">
      <el-select v-model="selectedProject" placeholder="目录项目" style="width:200px" @change="loadCatalog">
        <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
      </el-select>
      <el-button type="success" :disabled="!selectedProject" @click="openCreate(0)">创建根目录</el-button>
    </div>
    <el-table :data="catalogTree" border row-key="id" default-expand-all>
      <el-table-column prop="name" label="目录名称" />
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button link type="primary" @click="openCreate(row.id)">新增子目录</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="dialogVisible" title="创建服务目录" width="400px">
      <el-form label-width="80px"><el-form-item label="名称"><el-input v-model="form.name" /></el-form-item></el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>
