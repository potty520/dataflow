<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>数据标准</h2>
        <div class="sub">数据元定义、分类发布与标准体系维护，作为建表与开发流程的强制引用基线</div>
      </div>
      <el-button type="primary" size="small" :icon="Plus" @click="dialog = true">新建数据元</el-button>
    </div>

    <div class="grid grid-4" style="margin-bottom:12px">
      <div class="stat"><div class="label">数据元</div><div class="value">{{ stats.elementCount }}</div><div class="foot">已发布 {{ stats.publishedCount }} 个</div></div>
      <div class="stat green"><div class="label">数据标准</div><div class="value">{{ stats.standardCount }}</div><div class="foot">客户/交易/产品/设备/政务</div></div>
      <div class="stat orange"><div class="label">字段映射</div><div class="value">{{ stats.mappingCount }}</div><div class="foot">已确认落地的字段级映射</div></div>
      <div class="stat"><div class="label">行业来源</div><div class="value">{{ (stats.bySource || []).length }}</div><div class="foot">国标 / 行标 / 企业标准</div></div>
    </div>

    <el-tabs v-model="tab" type="border-card" style="border-radius:8px">
      <el-tab-pane label="数据元" name="element">
        <div class="toolbar">
          <el-input v-model="query.keyword" placeholder="编码 / 名称" clearable style="width:200px" @keyup.enter="load" @clear="load" />
          <el-select v-model="query.category" placeholder="分类" clearable style="width:130px" @change="load">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
          <el-button size="small" type="primary" @click="load">查询</el-button>
          <span class="spacer"></span>
          <router-link to="/standard/mapping"><el-button size="small" type="primary" plain>进入映射与落地检查</el-button></router-link>
        </div>
        <el-table :data="elements" size="small" v-loading="loading">
          <el-table-column prop="code" label="数据元编码" width="150"><template #default="{ row }"><span class="mono">{{ row.code }}</span></template></el-table-column>
          <el-table-column prop="cnName" label="中文名" width="130" />
          <el-table-column prop="name" label="英文名" width="140"><template #default="{ row }"><span class="mono">{{ row.name }}</span></template></el-table-column>
          <el-table-column prop="category" label="分类" width="90" />
          <el-table-column prop="dataType" label="类型" width="100" />
          <el-table-column prop="dataLen" label="长度" width="80" />
          <el-table-column prop="fmt" label="格式/取值" min-width="150" show-overflow-tooltip />
          <el-table-column prop="ruleDesc" label="质量规则" min-width="180" show-overflow-tooltip />
          <el-table-column prop="source" label="来源" width="130" />
          <el-table-column prop="mappedColumns" label="已映射字段" width="100" align="right" />
          <el-table-column prop="status" label="状态" width="90">
            <template #default="{ row }"><el-tag size="small" :type="row.status === 'PUBLISHED' ? 'success' : 'info'" effect="plain">{{ row.status }}</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status !== 'PUBLISHED'" link type="primary" size="small" @click="publish(row)">发布</el-button>
              <span v-else class="muted" style="font-size:12px">已生效</span>
            </template>
          </el-table-column>
        </el-table>
        <div style="display:flex;justify-content:flex-end;margin-top:10px">
          <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total"
                         layout="total, prev, pager, next" @change="load" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="数据标准" name="standard">
        <el-table :data="standards" size="small">
          <el-table-column prop="code" label="标准编码" width="170"><template #default="{ row }"><span class="mono">{{ row.code }}</span></template></el-table-column>
          <el-table-column prop="cnName" label="标准名称" min-width="180" />
          <el-table-column prop="category" label="主题" width="100" />
          <el-table-column prop="industry" label="行业" width="100" />
          <el-table-column prop="source" label="依据" width="130" />
          <el-table-column prop="elementCount" label="数据元数" width="90" align="right" />
          <el-table-column prop="version" label="版本" width="80" />
          <el-table-column prop="status" label="状态" width="90"><template #default="{ row }"><el-tag size="small" type="success" effect="plain">{{ row.status }}</el-tag></template></el-table-column>
          <el-table-column prop="description" label="说明" min-width="240" show-overflow-tooltip />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="基础标准" name="base">
        <div class="toolbar">
          <el-select v-model="baseType" style="width:150px" @change="loadBaseGroups">
            <el-option label="数据字典" value="DICT" />
            <el-option label="代码集" value="CODESET" />
            <el-option label="常用词汇" value="GLOSSARY" />
            <el-option label="模型标准" value="MODEL_STD" />
          </el-select>
          <el-input v-model="baseKeyword" placeholder="编码 / 名称" clearable style="width:200px" @keyup.enter="loadBaseGroups" @clear="loadBaseGroups" />
          <el-button size="small" type="primary" @click="loadBaseGroups">查询</el-button>
          <el-button size="small" type="primary" @click="openGroup()">新建分组</el-button>
          <span class="spacer"></span>
          <span class="hint">基础标准库：数据字典 / 代码集 / 常用词汇 / 模型标准，可被数据元与映射引用</span>
        </div>
        <el-table :data="baseGroups" size="small" v-loading="baseLoading">
          <el-table-column prop="code" label="分组编码" width="180"><template #default="{ row }"><span class="mono">{{ row.code }}</span></template></el-table-column>
          <el-table-column prop="name" label="名称" min-width="180" />
          <el-table-column label="类型" width="110"><template #default="{ row }">{{ baseTypeLabel(row.type) }}</template></el-table-column>
          <el-table-column prop="itemCount" label="条目数" width="90" align="right" />
          <el-table-column prop="status" label="状态" width="90"><template #default="{ row }"><el-tag size="small" :type="row.status === 'ENABLED' ? 'success' : 'info'" effect="plain">{{ row.status === 'ENABLED' ? '启用' : '停用' }}</el-tag></template></el-table-column>
          <el-table-column prop="description" label="说明" min-width="240" show-overflow-tooltip />
          <el-table-column label="操作" width="190" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openItems(row)">条目</el-button>
              <el-button link type="primary" size="small" @click="openGroup(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="removeGroup(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="dialog" title="新建数据元" width="560px">
      <el-form :model="form" label-width="90px" size="small">
        <el-form-item label="编码"><el-input v-model="form.code" placeholder="DE_XXX" /></el-form-item>
        <el-form-item label="中文名"><el-input v-model="form.cnName" /></el-form-item>
        <el-form-item label="英文名"><el-input v-model="form.name" placeholder="customer_id" /></el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" style="width:100%">
            <el-option v-for="c in ['客户','交易','产品','设备','公共']" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据类型">
          <el-select v-model="form.dataType" style="width:100%">
            <el-option v-for="t in ['STRING','INT','DECIMAL','DATE','TIMESTAMP']" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="长度/精度"><el-input v-model="form.dataLen" placeholder="32 或 12,2" /></el-form-item>
        <el-form-item label="格式"><el-input v-model="form.fmt" placeholder="如 ^1[3-9]\\d{9}$" /></el-form-item>
        <el-form-item label="业务含义"><el-input v-model="form.businessDesc" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="质量规则"><el-input v-model="form.ruleDesc" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="dialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="create">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="groupDialog" :title="groupForm.id ? '编辑标准分组' : '新建标准分组'" width="520px">
      <el-form label-width="90px" size="small">
        <el-form-item label="类型">
          <el-select v-model="groupForm.type" :disabled="!!groupForm.id" style="width:100%">
            <el-option label="数据字典" value="DICT" /><el-option label="代码集" value="CODESET" />
            <el-option label="常用词汇" value="GLOSSARY" /><el-option label="模型标准" value="MODEL_STD" />
          </el-select>
        </el-form-item>
        <el-form-item label="编码"><el-input v-model="groupForm.code" :disabled="!!groupForm.id" placeholder="如 DICT_GENDER" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="groupForm.name" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="groupForm.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="groupDialog = false">取消</el-button>
        <el-button size="small" type="primary" :loading="saving" @click="saveGroup">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="itemDialog" :title="itemForm.id ? '编辑条目' : '新建条目'" width="520px">
      <el-form label-width="90px" size="small">
        <el-form-item label="编码"><el-input v-model="itemForm.code" :disabled="!!itemForm.id" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="itemForm.name" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="itemForm.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="itemForm.sortNo" :min="0" /></el-form-item>
        <el-form-item label="状态"><el-radio-group v-model="itemForm.status"><el-radio label="ENABLED">启用</el-radio><el-radio label="DISABLED">停用</el-radio></el-radio-group></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="itemDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="saveItem">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="importDialog" title="批量导入条目" width="560px">
      <el-alert :closable="false" type="info" show-icon title="每行一条：编码,名称,说明（逗号或 Tab 分隔，可含表头）" />
      <el-input v-model="importText" type="textarea" :rows="10" class="mono" placeholder="M,男&#10;F,女&#10;U,未知" style="margin-top:10px" />
      <template #footer>
        <el-button size="small" @click="importDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="doImport">导入</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="itemsVisible" :title="'条目 · ' + (currentGroup.name || '')" size="62%">
      <div class="toolbar">
        <el-input v-model="itemKeyword" placeholder="编码 / 名称" clearable style="width:200px" @keyup.enter="loadItems" />
        <el-button size="small" @click="loadItems">查询</el-button>
        <el-button size="small" type="primary" @click="openItem()">新建条目</el-button>
        <el-button size="small" @click="importDialog = true">批量导入</el-button>
        <span class="spacer"></span>
        <span class="hint">{{ baseTypeLabel(currentGroup.type) }} · {{ currentGroup.code }}</span>
      </div>
      <el-table :data="baseItems" size="small" max-height="calc(100vh - 240px)">
        <el-table-column prop="code" label="编码" width="160"><template #default="{ row }"><span class="mono">{{ row.code }}</span></template></el-table-column>
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
        <el-table-column prop="sortNo" label="排序" width="70" align="right" />
        <el-table-column prop="status" label="状态" width="80" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openItem(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="removeItem(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { standardApi, standardBaseApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

const store = useAuthStore()

const tab = ref('element')
const elements = ref([])
const standards = ref([])
const stats = ref({})
const total = ref(0)
const loading = ref(false)
const dialog = ref(false)
const categories = ['客户', '交易', '产品', '设备', '公共']
const query = reactive({ keyword: '', category: '', pageNum: 1, pageSize: 15 })
const form = reactive({ code: '', cnName: '', name: '', category: '公共', dataType: 'STRING', dataLen: '32', fmt: '', businessDesc: '', ruleDesc: '' })

const load = async () => {
  loading.value = true
  try {
    const d = await standardApi.elements({ ...query })
    elements.value = d.records || []
    total.value = d.total || 0
  } catch (e) {
    elements.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const create = async () => {
  if (!form.code || !form.cnName) return ElMessage.warning('编码与中文名必填')
  try {
    await standardApi.createElement({ ...form, industry: 'GOV', source: '企业标准' })
    ElMessage.success('数据元已创建（DRAFT，需发布后生效）')
    dialog.value = false
    load(); loadStats()
  } catch (e) {
    /* http 层已提示 */
  }
}

const publish = async (row) => {
  try {
    await standardApi.publishElement(row.id)
    ElMessage.success('已发布，可用于字段映射与建表引用')
    load(); loadStats()
  } catch (e) {
    /* http 层已提示 */
  }
}

const loadStats = async () => {
  if (!store.has('standard:overview:view')) {
    stats.value = {}
    return
  }
  try {
    stats.value = (await standardApi.stats()) || {}
  } catch (e) {
    stats.value = {}
  }
}


const baseType = ref('DICT')
const baseKeyword = ref('')
const baseLoading = ref(false)
const baseGroups = ref([])
const groupDialog = ref(false)
const groupForm = reactive({ id: null, type: 'DICT', code: '', name: '', description: '' })
const itemsVisible = ref(false)
const currentGroup = ref({})
const baseItems = ref([])
const itemKeyword = ref('')
const itemDialog = ref(false)
const itemForm = reactive({ id: null, code: '', name: '', description: '', sortNo: 0, status: 'ENABLED' })
const importDialog = ref(false)
const importText = ref('')
const saving = ref(false)
const BASE_TYPES = { DICT: '数据字典', CODESET: '代码集', GLOSSARY: '常用词汇', MODEL_STD: '模型标准' }
const baseTypeLabel = (t) => BASE_TYPES[t] || t

const loadBaseGroups = async () => {
  baseLoading.value = true
  try {
    const d = await standardBaseApi.groups({ type: baseType.value, keyword: baseKeyword.value })
    baseGroups.value = d.records || []
  } catch (e) { baseGroups.value = [] } finally { baseLoading.value = false }
}
const openGroup = (row) => {
  Object.assign(groupForm, { id: null, type: baseType.value, code: '', name: '', description: '' })
  if (row) Object.assign(groupForm, { id: row.id, type: row.type, code: row.code, name: row.name, description: row.description })
  groupDialog.value = true
}
const saveGroup = async () => {
  saving.value = true
  try {
    if (groupForm.id) await standardBaseApi.updateGroup(groupForm.id, { name: groupForm.name, description: groupForm.description })
    else await standardBaseApi.createGroup({ type: groupForm.type, code: groupForm.code, name: groupForm.name, description: groupForm.description })
    ElMessage.success(groupForm.id ? '分组已更新' : '分组已创建')
    groupDialog.value = false
    loadBaseGroups()
  } catch (e) { /* http 层已提示 */ } finally { saving.value = false }
}
const removeGroup = async (row) => {
  try {
    await ElMessageBox.confirm('删除分组 ' + row.name + '？其下条目会一并删除', '提示', { type: 'warning' })
    await standardBaseApi.deleteGroup(row.id)
    ElMessage.success('已删除')
    loadBaseGroups()
  } catch (e) { /* 取消 */ }
}
const openItems = async (row) => {
  currentGroup.value = row
  itemKeyword.value = ''
  itemsVisible.value = true
  await loadItems()
}
const loadItems = async () => {
  if (!currentGroup.value.id) return
  const d = await standardBaseApi.items(currentGroup.value.id, { keyword: itemKeyword.value })
  baseItems.value = d.records || []
}
const openItem = (row) => {
  Object.assign(itemForm, { id: null, code: '', name: '', description: '', sortNo: 0, status: 'ENABLED' })
  if (row) Object.assign(itemForm, { id: row.id, code: row.code, name: row.name, description: row.description, sortNo: row.sortNo, status: row.status })
  itemDialog.value = true
}
const saveItem = async () => {
  try {
    if (itemForm.id) await standardBaseApi.updateItem(itemForm.id, { name: itemForm.name, description: itemForm.description, sortNo: itemForm.sortNo, status: itemForm.status })
    else await standardBaseApi.createItem(currentGroup.value.id, { code: itemForm.code, name: itemForm.name, description: itemForm.description, sortNo: itemForm.sortNo })
    ElMessage.success('条目已保存')
    itemDialog.value = false
    loadItems(); loadBaseGroups()
  } catch (e) { /* http 层已提示 */ }
}
const removeItem = async (row) => {
  try {
    await ElMessageBox.confirm('删除条目 ' + row.code + '？', '提示', { type: 'warning' })
    await standardBaseApi.deleteItem(row.id)
    loadItems(); loadBaseGroups()
  } catch (e) { /* 取消 */ }
}
const doImport = async () => {
  const rows = importText.value.split(/\r?\n/).map((s) => s.trim()).filter(Boolean)
  const items = []
  for (const line of rows) {
    const parts = line.split(/[,\t]/).map((s) => s.trim())
    if (parts.length < 2) continue
    if (parts[0].toLowerCase() === 'code' || parts[0] === '编码') continue
    items.push({ code: parts[0], name: parts[1], description: parts[2] || '' })
  }
  if (!items.length) { ElMessage.warning('没有可导入的行'); return }
  try {
    const d = await standardBaseApi.batchItems(currentGroup.value.id, items)
    ElMessage.success('导入完成：新增 ' + d.inserted + '，跳过 ' + d.skipped)
    importDialog.value = false
    importText.value = ''
    loadItems(); loadBaseGroups()
  } catch (e) { /* http 层已提示 */ }
}

onMounted(() => {
  // 列表为主，统计与标准清单旁路加载，互不阻塞
  load()
  loadStats()
  if (!store.has('standard:standard:view')) {
    standards.value = []
    return
  }
  standardApi
    .standards()
    .then((d) => {
      standards.value = d.records || []
    })
    .catch(() => {
      standards.value = []
    })
})
</script>
