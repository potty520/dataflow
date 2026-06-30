<script setup>
import { onMounted, reactive, ref } from 'vue'
import { userPage, createUser, updateUser, deleteUser, lockUser, roleList, deptTree } from '../../../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const roles = ref([])
const depts = ref([])
const query = reactive({ page: 1, size: 10, username: '' })
const dialogVisible = ref(false)
const form = ref({})
const isEdit = ref(false)

async function loadData() {
  loading.value = true
  try {
    const res = await userPage(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function loadMeta() {
  const [r, d] = await Promise.all([roleList(), deptTree()])
  roles.value = r.data
  depts.value = d.data
}

function openCreate() {
  isEdit.value = false
  form.value = { username: '', password: '', deptId: null, phone: '', status: 1, roleIds: [] }
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  form.value = { ...row, password: '' }
  dialogVisible.value = true
}

async function save() {
  if (isEdit.value) {
    await updateUser(form.value.id, form.value)
  } else {
    await createUser(form.value)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadData()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除用户「${row.username}」？`, '提示', { type: 'warning' })
  await deleteUser(row.id)
  ElMessage.success('删除成功')
  loadData()
}

async function toggleLock(row) {
  const status = row.status === 1 ? 0 : 1
  await lockUser(row.id, status)
  ElMessage.success(status === 0 ? '已锁定' : '已解锁')
  loadData()
}

onMounted(() => { loadMeta(); loadData() })
</script>

<template>
  <div class="page-container">
    <div class="page-header">用户管理</div>
    <div class="search-bar">
      <el-input v-model="query.username" placeholder="用户名" clearable style="width:200px" />
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button type="success" @click="openCreate">创建用户</el-button>
    </div>
    <el-table :data="tableData" v-loading="loading" border>
      <el-table-column prop="username" label="用户名" />
      <el-table-column prop="nickname" label="昵称" />
      <el-table-column prop="deptName" label="部门" />
      <el-table-column prop="phone" label="手机号" />
      <el-table-column prop="roleNames" label="角色">
        <template #default="{ row }">{{ (row.roleNames || []).join(', ') }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态">
        <template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '正常' : '锁定' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link @click="toggleLock(row)">{{ row.status === 1 ? '锁定' : '解锁' }}</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" layout="total, prev, pager, next" style="margin-top:16px" @change="loadData" />

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑用户' : '创建用户'" width="520px">
      <el-form label-width="90px">
        <el-form-item label="用户名"><el-input v-model="form.username" :disabled="isEdit" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" type="password" :placeholder="isEdit ? '留空则不修改' : ''" show-password /></el-form-item>
        <el-form-item label="部门">
          <el-select v-model="form.deptId" style="width:100%">
            <el-option v-for="d in depts" :key="d.id" :label="d.deptName" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleIds" multiple style="width:100%">
            <el-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
