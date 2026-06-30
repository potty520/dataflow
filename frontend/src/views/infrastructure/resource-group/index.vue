<script setup>
import { onMounted, reactive, ref } from 'vue'
import { resourceGroupPage, createResourceGroup, updateResourceGroup, deleteResourceGroup } from '../../../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const data = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10 })
const dialogVisible = ref(false)
const form = ref({})
const isEdit = ref(false)

async function loadData() {
  const res = await resourceGroupPage(query)
  data.value = res.data.records
  total.value = res.data.total
}

function openCreate() {
  isEdit.value = false
  form.value = { groupName: '', resourceType: 'YARN_QUEUE', resourceKey: '', description: '', maxCores: 0, maxMemoryGb: 0, status: 'ENABLED' }
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

async function save() {
  if (isEdit.value) await updateResourceGroup(form.value.id, form.value)
  else await createResourceGroup(form.value)
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadData()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除资源组「${row.groupName}」？`, '提示', { type: 'warning' })
  await deleteResourceGroup(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">计算资源组</div>
    <div style="margin-bottom:12px">
      <el-button type="primary" @click="openCreate">新增资源组</el-button>
    </div>
    <el-table :data="data" border>
      <el-table-column prop="groupName" label="组名称" />
      <el-table-column prop="resourceType" label="类型" width="130">
        <template #default="{ row }">
          <el-tag>{{ row.resourceType === 'YARN_QUEUE' ? 'YARN队列' : 'K8S命名空间' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="resourceKey" label="资源Key" width="150" />
      <el-table-column prop="maxCores" label="最大核数" width="100" />
      <el-table-column prop="maxMemoryGb" label="最大内存(GB)" width="120" />
      <el-table-column prop="description" label="描述" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">{{ row.status === 'ENABLED' ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total"
                   layout="total, prev, pager, next" style="margin-top:16px" @change="loadData" />

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑资源组' : '新增资源组'" width="500px">
      <el-form label-width="100px">
        <el-form-item label="组名称"><el-input v-model="form.groupName" /></el-form-item>
        <el-form-item label="资源类型">
          <el-select v-model="form.resourceType" style="width:100%">
            <el-option label="YARN队列" value="YARN_QUEUE" />
            <el-option label="K8S命名空间" value="K8S_NAMESPACE" />
          </el-select>
        </el-form-item>
        <el-form-item label="资源Key"><el-input v-model="form.resourceKey" /></el-form-item>
        <el-form-item label="最大核数"><el-input-number v-model="form.maxCores" :min="0" /></el-form-item>
        <el-form-item label="最大内存(GB)"><el-input-number v-model="form.maxMemoryGb" :min="0" :precision="1" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width:100%">
            <el-option label="启用" value="ENABLED" />
            <el-option label="禁用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
