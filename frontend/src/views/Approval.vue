<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>审批中心</h2>
        <div class="sub">流程定义、发起审批、待办处理与审批时间线（BPM 最小闭环）</div>
      </div>
      <el-button type="primary" size="small" @click="openStart">发起审批</el-button>
    </div>

    <el-tabs v-model="tab" type="border-card">
      <el-tab-pane label="我的待办" name="todo">
        <el-table :data="todoList" size="small" v-loading="loading">
          <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
          <el-table-column prop="defName" label="流程" width="160" />
          <el-table-column prop="nodeName" label="当前节点" width="140" />
          <el-table-column prop="starter" label="发起人" width="100" />
          <el-table-column prop="startTime" label="发起时间" width="160"><template #default="{ row }">{{ fmt(row.startTime) }}</template></el-table-column>
          <el-table-column label="操作" width="220">
            <template #default="{ row }">
              <el-button link type="success" size="small" @click="openAction(row, 'approve')">通过</el-button>
              <el-button link type="danger" size="small" @click="openAction(row, 'reject')">驳回</el-button>
              <el-button link type="primary" size="small" @click="openAction(row, 'transfer')">转办</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="我发起的" name="mine">
        <el-table :data="mineList" size="small" v-loading="loading">
          <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
          <el-table-column prop="defName" label="流程" width="160" />
          <el-table-column prop="status" label="状态" width="110"><template #default="{ row }"><el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
          <el-table-column prop="currentNode" label="当前节点" width="140" />
          <el-table-column prop="startTime" label="发起时间" width="170"><template #default="{ row }">{{ fmt(row.startTime) }}</template></el-table-column>
          <el-table-column label="操作" width="100"><template #default="{ row }"><el-button link type="primary" size="small" @click="showDetail(row)">详情</el-button></template></el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="流程定义" name="defs">
        <div class="toolbar">
          <span class="hint">节点顺序：start → approval… → end；审批人支持 USER（用户名）或 ROLE（角色编码）</span>
          <span class="spacer"></span>
          <el-button size="small" type="primary" @click="openDef()">新建流程</el-button>
        </div>
        <el-table :data="defs" size="small" v-loading="loading">
          <el-table-column prop="code" label="编码" width="180"><template #default="{ row }"><span class="mono">{{ row.code }}</span></template></el-table-column>
          <el-table-column prop="name" label="名称" min-width="160" />
          <el-table-column prop="version" label="版本" width="70" align="right" />
          <el-table-column prop="status" label="状态" width="100"><template #default="{ row }"><el-tag size="small" :type="row.status === 'PUBLISHED' ? 'success' : 'info'">{{ row.status }}</el-tag></template></el-table-column>
          <el-table-column label="操作" width="230">
            <template #default="{ row }">
              <el-button link type="primary" size="small" v-if="row.status !== 'PUBLISHED'" @click="openDef(row)">编辑</el-button>
              <el-button link type="success" size="small" v-if="row.status !== 'PUBLISHED'" @click="publish(row)">发布</el-button>
              <el-button link type="primary" size="small" v-if="row.status === 'PUBLISHED'" @click="openStart(row)">发起</el-button>
              <el-button link type="danger" size="small" @click="removeDef(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="actionDialog" :title="actionTitle" width="460px">
      <el-form label-width="90px" size="small">
        <el-form-item v-if="actionMode === 'transfer'" label="转办给"><el-input v-model="actionForm.assignee" placeholder="用户名，或下方角色" /></el-form-item>
        <el-form-item v-if="actionMode === 'transfer'" label="角色"><el-input v-model="actionForm.assigneeRole" placeholder="如 ROLE_QUALITY" /></el-form-item>
        <el-form-item label="意见"><el-input v-model="actionForm.comment" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="actionDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="doAction">确认</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="startDialog" title="发起审批" width="560px">
      <el-form label-width="90px" size="small">
        <el-form-item label="流程">
          <el-select v-model="startForm.defCode" style="width:100%">
            <el-option v-for="d in publishedDefs" :key="d.code" :label="d.code + ' · ' + d.name" :value="d.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题"><el-input v-model="startForm.title" /></el-form-item>
        <el-form-item label="业务类型"><el-input v-model="startForm.businessType" placeholder="如 STANDARD / MODEL / ACCESS" /></el-form-item>
        <el-form-item label="业务 ID"><el-input v-model="startForm.businessId" /></el-form-item>
        <el-form-item label="表单 JSON"><el-input v-model="startForm.formText" type="textarea" :rows="4" class="mono" placeholder='{"reason":"标准发布"}' /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="startDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="doStart">发起</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="defDialog" :title="defForm.id ? '编辑流程' : '新建流程'" width="640px">
      <el-form label-width="90px" size="small">
        <el-form-item label="编码"><el-input v-model="defForm.code" :disabled="!!defForm.id" placeholder="BPM_STANDARD_PUBLISH" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="defForm.name" /></el-form-item>
        <el-form-item label="节点 JSON"><el-input v-model="defForm.nodesText" type="textarea" :rows="8" class="mono" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="defForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="defDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="saveDef">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailDrawer" title="审批详情" size="55%">
      <div v-if="detail">
        <h3 style="margin-top:0">{{ detail.title }}</h3>
        <div class="hint">流程：{{ detail.defName }} · 状态：{{ statusLabel(detail.status) }} · 发起人：{{ detail.starter }}</div>
        <el-timeline style="margin-top:16px">
          <el-timeline-item v-for="t in detail.tasks || []" :key="t.id" :timestamp="fmt(t.finishTime || t.createTime)" :type="t.status === 'APPROVED' ? 'success' : t.status === 'REJECTED' ? 'danger' : 'primary'">
            <b>{{ t.nodeName }}</b>
            <div class="hint">{{ t.assignee || t.assigneeRole }} · {{ t.status }}<span v-if="t.comment"> · {{ t.comment }}</span></div>
          </el-timeline-item>
        </el-timeline>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { bpmApi } from '@/api'

const tab = ref('todo')
const loading = ref(false)
const todoList = ref([])
const mineList = ref([])
const defs = ref([])
const actionDialog = ref(false)
const actionMode = ref('approve')
const actionTask = ref(null)
const actionForm = reactive({ comment: '', assignee: '', assigneeRole: '' })
const startDialog = ref(false)
const startForm = reactive({ defCode: '', title: '', businessType: 'GENERAL', businessId: '', formText: '{}' })
const defDialog = ref(false)
const defForm = reactive({ id: null, code: '', name: '', nodesText: '', remark: '' })
const detailDrawer = ref(false)
const detail = ref(null)

const publishedDefs = computed(() => defs.value.filter((d) => d.status === 'PUBLISHED'))
const actionTitle = computed(() => ({ approve: '审批通过', reject: '审批驳回', transfer: '转办' }[actionMode.value]))
const statusLabel = (s) => ({ RUNNING: '审批中', APPROVED: '已通过', REJECTED: '已驳回', CANCELED: '已取消' }[s] || s)
const statusType = (s) => (s === 'APPROVED' ? 'success' : s === 'REJECTED' ? 'danger' : 'primary')
const fmt = (v) => (v ? String(v).replace('T', ' ').slice(0, 19) : '-')

const loadTodo = async () => { try { todoList.value = (await bpmApi.todo()).records || [] } catch (e) { todoList.value = [] } }
const loadMine = async () => { try { mineList.value = (await bpmApi.mine()).records || [] } catch (e) { mineList.value = [] } }
const loadDefs = async () => { try { defs.value = (await bpmApi.defs()).records || [] } catch (e) { defs.value = [] } }
const loadAll = async () => { loading.value = true; await Promise.allSettled([loadTodo(), loadMine(), loadDefs()]); loading.value = false }

const openAction = (row, mode) => {
  actionTask.value = row; actionMode.value = mode
  Object.assign(actionForm, { comment: '', assignee: '', assigneeRole: '' })
  actionDialog.value = true
}
const doAction = async () => {
  const id = actionTask.value.id
  try {
    if (actionMode.value === 'approve') await bpmApi.approve(id, actionForm.comment)
    else if (actionMode.value === 'reject') await bpmApi.reject(id, actionForm.comment)
    else await bpmApi.transfer(id, { ...actionForm })
    ElMessage.success('处理完成'); actionDialog.value = false; loadAll()
  } catch (e) { /* http 层已提示 */ }
}

const openStart = (def) => {
  Object.assign(startForm, { defCode: def ? def.code : '', title: def ? def.name + ' 审批' : '', businessType: 'GENERAL', businessId: '', formText: '{}' })
  startDialog.value = true
}
const doStart = async () => {
  let form
  try { form = JSON.parse(startForm.formText || '{}') } catch (e) { ElMessage.warning('表单 JSON 格式不正确'); return }
  try {
    const d = await bpmApi.start({ defCode: startForm.defCode, title: startForm.title, businessType: startForm.businessType, businessId: startForm.businessId, form })
    ElMessage.success('已发起，实例 #' + d.id); startDialog.value = false; loadAll()
  } catch (e) { /* http 层已提示 */ }
}

const defaultNodes = JSON.stringify({ nodes: [
  { id: 'start', type: 'start' },
  { id: 'approve1', type: 'approval', name: '部门审批', assigneeType: 'ROLE', assignee: 'ROLE_GOV_ADMIN' },
  { id: 'end', type: 'end' }
] }, null, 2)
const openDef = (row) => {
  Object.assign(defForm, { id: null, code: '', name: '', nodesText: defaultNodes, remark: '' })
  if (row) Object.assign(defForm, { id: row.id, code: row.code, name: row.name, nodesText: row.nodesJson || defaultNodes, remark: row.remark })
  defDialog.value = true
}
const saveDef = async () => {
  let nodes
  try { nodes = JSON.parse(defForm.nodesText) } catch (e) { ElMessage.warning('节点 JSON 格式不正确'); return }
  try {
    if (defForm.id) await bpmApi.updateDef(defForm.id, { name: defForm.name, nodes, remark: defForm.remark })
    else await bpmApi.createDef({ code: defForm.code, name: defForm.name, nodes, remark: defForm.remark })
    ElMessage.success('流程已保存（DRAFT，发布后可用）'); defDialog.value = false; loadDefs()
  } catch (e) { /* http 层已提示 */ }
}
const publish = async (row) => {
  try { await bpmApi.publishDef(row.id); ElMessage.success('已发布'); loadDefs() } catch (e) { /* http 层已提示 */ }
}
const removeDef = async (row) => {
  try { await ElMessageBox.confirm('删除流程 ' + row.code + '？', '提示', { type: 'warning' }); await bpmApi.deleteDef(row.id); loadDefs() }
  catch (e) { /* 取消 */ }
}
const showDetail = async (row) => {
  try { detail.value = await bpmApi.detail(row.id); detailDrawer.value = true } catch (e) { /* http 层已提示 */ }
}

onMounted(loadAll)
</script>

<style scoped>
.page { padding: 4px; }
.hint { color: #909399; font-size: 12px; }
</style>
