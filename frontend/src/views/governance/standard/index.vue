<script setup>
import { onMounted, reactive, ref } from 'vue'
import { govCodeSetPage, createGovCodeSet, updateGovCodeSet, deleteGovCodeSet, govWordPage, createGovWord, updateGovWord, deleteGovWord } from '../../../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const tab = ref('codeset')
const loading = ref(false)
const codeData = ref([])
const wordData = ref([])
const codeTotal = ref(0)
const wordTotal = ref(0)
const codeQuery = reactive({ page: 1, size: 10, name: '' })
const wordQuery = reactive({ page: 1, size: 10, keyword: '' })
const dialogVisible = ref(false)
const form = ref({})
const isEdit = ref(false)
const formType = ref('codeset')

async function loadCodeSet() {
  loading.value = true
  try {
    const res = await govCodeSetPage(codeQuery)
    codeData.value = res.data.records
    codeTotal.value = res.data.total
  } finally { loading.value = false }
}

async function loadWord() {
  loading.value = true
  try {
    const res = await govWordPage(wordQuery)
    wordData.value = res.data.records
    wordTotal.value = res.data.total
  } finally { loading.value = false }
}

function openCreate(type) {
  formType.value = type
  isEdit.value = false
  form.value = type === 'codeset' ? { code: '', name: '', codeValue: '', version: '1.0' } : { wordEn: '', wordCn: '', definition: '', version: '1.0' }
  dialogVisible.value = true
}

function openEdit(type, row) {
  formType.value = type
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

async function save() {
  if (formType.value === 'codeset') {
    if (isEdit.value) await updateGovCodeSet(form.value.id, form.value)
    else await createGovCodeSet(form.value)
    loadCodeSet()
  } else {
    if (isEdit.value) await updateGovWord(form.value.id, form.value)
    else await createGovWord(form.value)
    loadWord()
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
}

async function remove(type, row) {
  await ElMessageBox.confirm('确认删除？', '提示', { type: 'warning' })
  if (type === 'codeset') { await deleteGovCodeSet(row.id); loadCodeSet() }
  else { await deleteGovWord(row.id); loadWord() }
  ElMessage.success('删除成功')
}

onMounted(() => { loadCodeSet(); loadWord() })
</script>

<template>
  <div class="page-container">
    <div class="page-header">数据标准</div>
    <el-tabs v-model="tab">
      <el-tab-pane label="代码集管理" name="codeset">
        <div class="search-bar">
          <el-input v-model="codeQuery.name" placeholder="名称" clearable style="width:180px" />
          <el-button type="primary" @click="loadCodeSet">查询</el-button>
          <el-button type="success" @click="openCreate('codeset')">新增代码集</el-button>
        </div>
        <el-table :data="codeData" v-loading="loading" border>
          <el-table-column prop="code" label="编码" />
          <el-table-column prop="name" label="名称" />
          <el-table-column prop="codeValue" label="码值" />
          <el-table-column prop="version" label="版本" width="80" />
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit('codeset', row)">编辑</el-button>
              <el-button link type="danger" @click="remove('codeset', row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination v-model:current-page="codeQuery.page" v-model:page-size="codeQuery.size" :total="codeTotal" layout="total, prev, pager, next" style="margin-top:16px" @change="loadCodeSet" />
      </el-tab-pane>
      <el-tab-pane label="单词管理" name="word">
        <div class="search-bar">
          <el-input v-model="wordQuery.keyword" placeholder="中英文关键词" clearable style="width:180px" />
          <el-button type="primary" @click="loadWord">查询</el-button>
          <el-button type="success" @click="openCreate('word')">新增单词</el-button>
        </div>
        <el-table :data="wordData" v-loading="loading" border>
          <el-table-column prop="wordEn" label="英文" />
          <el-table-column prop="wordCn" label="中文" />
          <el-table-column prop="definition" label="定义" />
          <el-table-column prop="version" label="版本" width="80" />
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit('word', row)">编辑</el-button>
              <el-button link type="danger" @click="remove('word', row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination v-model:current-page="wordQuery.page" v-model:page-size="wordQuery.size" :total="wordTotal" layout="total, prev, pager, next" style="margin-top:16px" @change="loadWord" />
      </el-tab-pane>
    </el-tabs>
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑' : '新增'" width="480px">
      <el-form v-if="formType === 'codeset'" label-width="80px">
        <el-form-item label="编码"><el-input v-model="form.code" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="码值"><el-input v-model="form.codeValue" /></el-form-item>
        <el-form-item label="版本"><el-input v-model="form.version" /></el-form-item>
      </el-form>
      <el-form v-else label-width="80px">
        <el-form-item label="英文"><el-input v-model="form.wordEn" /></el-form-item>
        <el-form-item label="中文"><el-input v-model="form.wordCn" /></el-form-item>
        <el-form-item label="定义"><el-input v-model="form.definition" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
