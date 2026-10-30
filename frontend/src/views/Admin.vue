<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>系统管理</h2>
        <div class="sub">平台健康、服务清单、运行参数、用户角色与接口清单</div>
      </div>
      <el-button size="small" @click="load">刷新</el-button>
    </div>

    <div class="grid grid-4" style="margin-bottom:12px">
      <div class="stat"><div class="label">平台状态</div><div class="value" :style="{ color: health.status === 'UP' ? '#16a34a' : '#dc2626' }">{{ health.status }}</div><div class="foot">版本 {{ health.version }}</div></div>
      <div class="stat"><div class="label">运行时长</div><div class="value">{{ Math.floor((health.uptimeSeconds || 0) / 60) }}<small>分钟</small></div><div class="foot">{{ health.components?.[0]?.detail || '' }}</div></div>
      <div class="stat orange"><div class="label">堆内存</div><div class="value">{{ health.jvm?.heapUsedMb }}<small>/ {{ health.jvm?.heapMaxMb }} MB</small></div><div class="foot">CPU {{ health.jvm?.processors }} 核</div></div>
      <div class="stat green"><div class="label">元数据对象</div><div class="value">{{ health.data?.tables }}</div><div class="foot">规则 {{ health.data?.qualityRules }} · API {{ health.data?.apiCount }}</div></div>
    </div>

    <el-tabs v-model="tab" @tab-change="onTabChange" type="border-card" style="border-radius:8px">
      <el-tab-pane label="平台健康" name="health">
        <el-table :data="health.components || []" size="small">
          <el-table-column prop="name" label="组件" width="170"><template #default="{ row }"><span class="mono">{{ row.name }}</span></template></el-table-column>
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'UP' ? 'success' : row.status === 'N/A' ? 'info' : 'danger'" effect="plain">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="detail" label="说明" min-width="380" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="服务清单" name="services">
        <div class="muted" style="font-size:12px;margin-bottom:8px">
          基线要求 14 个微服务；本交付将领域服务合并为单进程 dgp-boot，端口与领域边界保持一一对应，可平滑拆分。
        </div>
        <el-table :data="services" size="small">
          <el-table-column prop="service" label="服务名" width="180"><template #default="{ row }"><span class="mono">{{ row.service }}</span></template></el-table-column>
          <el-table-column prop="port" label="端口" width="90" align="right" />
          <el-table-column prop="duty" label="职责" min-width="240" />
          <el-table-column prop="module" label="实现模块" width="140"><template #default="{ row }"><span class="mono">com.dgp.{{ row.module }}</span></template></el-table-column>
          <el-table-column prop="status" label="状态" width="90"><template #default="{ row }"><el-tag size="small" type="success" effect="plain">{{ row.status }}</el-tag></template></el-table-column>
          <el-table-column prop="latencyMs" label="时延(ms)" width="100" align="right" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="运行参数" name="config">
        <el-table :data="configs" size="small">
          <el-table-column prop="configKey" label="参数键" width="260"><template #default="{ row }"><span class="mono">{{ row.configKey }}</span></template></el-table-column>
          <el-table-column prop="configValue" label="取值" min-width="320"><template #default="{ row }"><span class="mono">{{ row.configValue }}</span></template></el-table-column>
          <el-table-column prop="description" label="说明" min-width="240" />
          <el-table-column prop="updateTime" label="更新时间" width="150"><template #default="{ row }">{{ fmt(row.updateTime) }}</template></el-table-column>
          <el-table-column label="操作" width="80">
            <template #default="{ row }"><el-button link type="primary" size="small" @click="editConfig(row)">编辑</el-button></template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="用户与角色" name="user">
        <div class="toolbar" style="margin-bottom:8px">
          <el-button size="small" type="primary" @click="openUser()">新建用户</el-button>
          <el-button size="small" @click="openRole()">新建角色</el-button>
          <span class="hint" style="margin-left:8px">用户绑定角色；角色权限按接口权限点勾选，保存后立即生效</span>
        </div>
        <div class="grid grid-2">
          <div>
            <div class="section-title">用户（{{ users.length }}）</div>
            <el-table :data="users" size="small" max-height="330">
              <el-table-column prop="username" label="账号" width="110"><template #default="{ row }"><span class="mono">{{ row.username }}</span></template></el-table-column>
              <el-table-column prop="realName" label="姓名" width="90" />
              <el-table-column prop="roleName" label="角色" min-width="140" />
              <el-table-column prop="dept" label="部门" min-width="110" />
              <el-table-column prop="status" label="状态" width="80" />
              <el-table-column label="操作" width="190" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" size="small" @click="openUser(row)">编辑</el-button>
                  <el-button link type="warning" size="small" @click="resetUserPwd(row)">重置密码</el-button>
                  <el-button v-if="row.username !== 'admin'" link type="danger" size="small" @click="removeUser(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
          <div>
            <div class="section-title">角色（{{ roles.length }}）</div>
            <el-table :data="roles" size="small" max-height="330">
              <el-table-column prop="code" label="角色编码" width="190"><template #default="{ row }"><span class="mono">{{ row.code }}</span></template></el-table-column>
              <el-table-column prop="name" label="名称" width="140" />
              <el-table-column prop="userCount" label="用户数" width="80" align="right" />
              <el-table-column prop="description" label="职责" min-width="200" show-overflow-tooltip />
              <el-table-column label="操作" width="170" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" size="small" :disabled="isBuiltinRole(row.code)" @click="openRole(row)">编辑</el-button>
                  <el-button link type="primary" size="small" :disabled="isBuiltinRole(row.code)" @click="openPerms(row)">权限</el-button>
                  <el-button link type="danger" size="small" :disabled="isBuiltinRole(row.code) || row.userCount > 0"
                             :title="isBuiltinRole(row.code) ? '内置角色不可删除' : (row.userCount > 0 ? '该角色下还有用户，请先调整用户角色' : '')"
                             @click="removeRole(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="日志检索" name="logs">
        <div class="section-title">操作日志（治理操作审计）</div>
        <div class="toolbar" style="flex-wrap:wrap;gap:6px">
          <el-input v-model="opFilters.keyword" size="small" placeholder="关键字：用户/动作/模块/资源/IP" style="width:230px" clearable @keyup.enter="loadOpLogs" />
          <el-input v-model="opFilters.module" size="small" placeholder="模块" style="width:110px" clearable />
          <el-input v-model="opFilters.action" size="small" placeholder="动作" style="width:110px" clearable />
          <el-select v-model="opFilters.result" size="small" placeholder="结果" style="width:100px" clearable>
            <el-option label="SUCCESS" value="SUCCESS" /><el-option label="FAIL" value="FAIL" />
          </el-select>
          <el-input v-model="opFilters.ip" size="small" placeholder="IP" style="width:120px" clearable />
          <el-input v-model="opFilters.startTime" size="small" placeholder="开始 2026-10-01 00:00:00" style="width:190px" clearable />
          <el-input v-model="opFilters.endTime" size="small" placeholder="结束 2026-10-31 23:59:59" style="width:190px" clearable />
          <el-button size="small" type="primary" @click="loadOpLogs">查询</el-button>
          <el-button size="small" @click="resetOpLogs">重置</el-button>
          <el-button size="small" @click="exportLogs(opLogs.records, '操作日志.csv')">导出本页 CSV</el-button>
        </div>
        <el-table :data="opLogs.records" size="small" max-height="360" v-loading="logLoading">
          <el-table-column prop="logTime" label="时间" width="150"><template #default="{ row }">{{ fmt(row.logTime) }}</template></el-table-column>
          <el-table-column prop="username" label="用户" width="90" />
          <el-table-column prop="action" label="动作" width="110" />
          <el-table-column prop="module" label="模块" width="100" />
          <el-table-column prop="resource" label="资源" min-width="220" show-overflow-tooltip />
          <el-table-column prop="result" label="结果" width="80" />
          <el-table-column prop="ip" label="IP" width="120" />
          <el-table-column prop="traceId" label="链路 ID" width="150"><template #default="{ row }"><span class="mono">{{ (row.traceId || '').slice(0, 14) }}</span></template></el-table-column>
        </el-table>
        <el-pagination background layout="total, prev, pager, next, sizes" :total="opLogs.total" v-model:current-page="opLogs.pageNum" v-model:page-size="opLogs.pageSize" :page-sizes="[20,50,100]" style="margin-top:8px" @current-change="loadOpLogs" @size-change="loadOpLogs" />

        <div class="section-title" style="margin-top:18px">登录日志</div>
        <div class="toolbar" style="flex-wrap:wrap;gap:6px">
          <el-input v-model="loginFilters.username" size="small" placeholder="账号" style="width:140px" clearable />
          <el-select v-model="loginFilters.status" size="small" placeholder="状态" style="width:110px" clearable>
            <el-option label="SUCCESS" value="SUCCESS" /><el-option label="FAIL" value="FAIL" />
          </el-select>
          <el-input v-model="loginFilters.ip" size="small" placeholder="IP" style="width:130px" clearable />
          <el-input v-model="loginFilters.startTime" size="small" placeholder="开始 2026-10-01 00:00:00" style="width:190px" clearable />
          <el-input v-model="loginFilters.endTime" size="small" placeholder="结束 2026-10-31 23:59:59" style="width:190px" clearable />
          <el-button size="small" type="primary" @click="loadLoginLogs">查询</el-button>
          <el-button size="small" @click="resetLoginLogs">重置</el-button>
          <el-button size="small" @click="exportLogs(loginLogs.records, '登录日志.csv')">导出本页 CSV</el-button>
        </div>
        <el-table :data="loginLogs.records" size="small" max-height="300" v-loading="logLoading">
          <el-table-column prop="loginTime" label="时间" width="150"><template #default="{ row }">{{ fmt(row.loginTime) }}</template></el-table-column>
          <el-table-column prop="username" label="账号" width="110" />
          <el-table-column prop="status" label="状态" width="90" />
          <el-table-column prop="message" label="说明" min-width="200" show-overflow-tooltip />
          <el-table-column prop="ip" label="IP" width="130" />
          <el-table-column prop="userAgent" label="User-Agent" min-width="240" show-overflow-tooltip />
        </el-table>
        <el-pagination background layout="total, prev, pager, next, sizes" :total="loginLogs.total" v-model:current-page="loginLogs.pageNum" v-model:page-size="loginLogs.pageSize" :page-sizes="[20,50,100]" style="margin-top:8px" @current-change="loadLoginLogs" @size-change="loadLoginLogs" />
      </el-tab-pane>

      <el-tab-pane label="接口清单" name="api">
        <div class="toolbar">
          <el-input v-model="apiKeyword" placeholder="按路径 / 权限 / 实现类过滤" clearable style="width:280px" />
          <span class="spacer"></span>
          <el-tag effect="plain" size="small">共 {{ filteredApis.length }} 条</el-tag>
        </div>
        <el-table :data="filteredApis" size="small" max-height="420">
          <el-table-column prop="method" label="方法" width="86">
            <template #default="{ row }"><el-tag size="small" :type="row.method === 'GET' ? 'success' : row.method === 'POST' ? 'warning' : 'info'">{{ row.method }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="path" label="路径" min-width="280"><template #default="{ row }"><span class="mono">{{ row.path }}</span></template></el-table-column>
          <el-table-column prop="perm" label="权限编码" width="240" />
          <el-table-column prop="handler" label="实现" min-width="260" />
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="configVisible" :title="`编辑参数 · ${configForm.key}`" width="560px">
      <el-input v-model="configForm.value" type="textarea" :rows="4" class="mono" />
      <template #footer>
        <el-button size="small" @click="configVisible = false">取消</el-button>
        <el-button size="small" type="primary" @click="saveConfig">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="userDialog" :title="userForm.id ? '编辑用户' : '新建用户'" width="520px">
      <el-form label-width="90px" size="small">
        <el-form-item label="账号"><el-input v-model="userForm.username" :disabled="!!userForm.id" placeholder="字母/数字/下划线" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="userForm.realName" /></el-form-item>
        <el-form-item v-if="!userForm.id" label="初始口令"><el-input v-model="userForm.password" type="password" show-password placeholder="至少 8 位，含字母和数字" /></el-form-item>
        <el-form-item label="角色">
          <el-select v-model="userForm.roleCode" style="width:100%">
            <el-option v-for="r in roles" :key="r.code" :label="r.name + '（' + r.code + '）'" :value="r.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门"><el-input v-model="userForm.dept" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="userForm.email" /></el-form-item>
        <el-form-item v-if="userForm.id" label="状态">
          <el-radio-group v-model="userForm.status"><el-radio label="启用">启用</el-radio><el-radio label="停用">停用</el-radio></el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="userDialog = false">取消</el-button>
        <el-button size="small" type="primary" :loading="saving" @click="saveUser">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleDialog" :title="roleForm.id ? '编辑角色' : '新建角色'" width="480px">
      <el-form label-width="90px" size="small">
        <el-form-item label="角色编码"><el-input v-model="roleForm.code" :disabled="!!roleForm.id" placeholder="如 ROLE_DATA_STEWARD" /></el-form-item>
        <el-form-item label="角色名称"><el-input v-model="roleForm.name" /></el-form-item>
        <el-form-item label="职责说明"><el-input v-model="roleForm.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="roleDialog = false">取消</el-button>
        <el-button size="small" type="primary" :loading="saving" @click="saveRole">{{ roleForm.id ? '保存' : '创建' }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="permDialog" :title="'角色权限 · ' + permRole.name" width="640px">
      <el-alert :closable="false" type="info" show-icon title="勾选后保存立即生效；平台管理员角色（ROLE_GOV_ADMIN）不可修改" />
      <el-input v-model="permKeyword" size="small" placeholder="筛选权限编码" style="margin:8px 0" clearable />
      <div style="max-height:380px;overflow:auto;border:1px solid #e4e7ed;border-radius:4px;padding:8px">
        <el-checkbox-group v-model="permSelected">
          <div v-for="p in filteredPerms" :key="p" style="display:inline-block;width:48%;margin:2px 0">
            <el-checkbox :label="p"><span class="mono" style="font-size:12px">{{ p }}</span></el-checkbox>
          </div>
        </el-checkbox-group>
      </div>
      <div class="hint" style="margin-top:6px">已选 {{ permSelected.length }} 项 / 可选 {{ permOptions.length }} 项</div>
      <template #footer>
        <el-button size="small" @click="permDialog = false">取消</el-button>
        <el-button size="small" type="primary" :loading="saving" @click="savePerms">保存权限</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi, authApi } from '@/api'

const tab = ref('health')
const health = ref({})
const services = ref([])
const configs = ref([])
const users = ref([])
const roles = ref([])
const apis = ref([])
const apiKeyword = ref('')
const configVisible = ref(false)
const configForm = reactive({ key: '', value: '' })

const fmt = (v) => (v ? String(v).replace('T', ' ').slice(0, 16) : '-')

const filteredApis = computed(() => {
  const k = apiKeyword.value.trim()
  if (!k) return apis.value
  return apis.value.filter((a) => a.path.includes(k) || a.perm.includes(k) || a.handler.includes(k))
})

const load = async () => {
  // 各区块独立失败：任一接口异常只影响自己那块卡片/表格
  const [h, s, c, u, r, a] = await Promise.allSettled([
    adminApi.health(),
    adminApi.services(),
    adminApi.configs(),
    authApi.users({ pageSize: 50 }),
    authApi.roles(),
    adminApi.apis()
  ])
  health.value = h.status === 'fulfilled' ? h.value || {} : {}
  services.value = s.status === 'fulfilled' ? s.value.records || [] : []
  configs.value = c.status === 'fulfilled' ? c.value.records || [] : []
  users.value = u.status === 'fulfilled' ? u.value.records || [] : []
  roles.value = r.status === 'fulfilled' ? r.value.records || [] : []
  apis.value = a.status === 'fulfilled' ? a.value.records || [] : []
}

const editConfig = (row) => {
  configForm.key = row.configKey
  configForm.value = row.configValue
  configVisible.value = true
}

const saveConfig = async () => {
  try {
    await adminApi.saveConfig(configForm.key, configForm.value)
    ElMessage.success('参数已保存')
    configVisible.value = false
    load()
  } catch (e) {
    /* http 层已提示 */
  }
}


const userDialog = ref(false)
const roleDialog = ref(false)
const permDialog = ref(false)
const saving = ref(false)
const userForm = reactive({ id: null, username: '', realName: '', password: '', roleCode: '', dept: '', email: '', status: '启用' })
const roleForm = reactive({ id: null, code: '', name: '', description: '' })
const permRole = ref({})
const permSelected = ref([])
const permKeyword = ref('')
const permOptions = computed(() => {
  const set = new Set()
  apis.value.forEach((a) => { if (a.perm && a.perm !== '-') set.add(a.perm) })
  return Array.from(set).sort()
})
const filteredPerms = computed(() => {
  const k = permKeyword.value.trim().toLowerCase()
  return k ? permOptions.value.filter((p) => p.toLowerCase().includes(k)) : permOptions.value
})

const openUser = (row) => {
  Object.assign(userForm, { id: null, username: '', realName: '', password: '', roleCode: roles.value[0]?.code || '', dept: '', email: '', status: '启用' })
  if (row) {
    Object.assign(userForm, { id: row.id, username: row.username, realName: row.realName, roleCode: row.roleCode, dept: row.dept, email: row.email, status: row.status })
  }
  userDialog.value = true
}
const saveUser = async () => {
  saving.value = true
  try {
    if (userForm.id) {
      await authApi.updateUser(userForm.id, { realName: userForm.realName, roleCode: userForm.roleCode, dept: userForm.dept, email: userForm.email, status: userForm.status })
      ElMessage.success('用户已更新')
    } else {
      await authApi.createUser({ username: userForm.username, password: userForm.password, realName: userForm.realName, roleCode: userForm.roleCode, dept: userForm.dept, email: userForm.email })
      ElMessage.success('用户已创建')
    }
    userDialog.value = false
    load()
  } catch (e) { /* http 层已提示 */ } finally { saving.value = false }
}
const resetUserPwd = async (row) => {
  try {
    const { value } = await ElMessageBox.prompt('输入 ' + row.username + ' 的新口令（至少 8 位，含字母和数字）', '重置密码', { inputType: 'password', inputPattern: /^.{8,}$/, inputErrorMessage: '至少 8 位' })
    await authApi.resetPassword(row.id, { password: value })
    ElMessage.success('密码已重置')
  } catch (e) { /* 取消 */ }
}
const removeUser = async (row) => {
  try {
    await ElMessageBox.confirm('删除用户 ' + row.username + '？该操作不可恢复', '提示', { type: 'warning' })
    await authApi.deleteUser(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) { /* 取消 */ }
}
const BUILTIN_ROLES = ['ROLE_GOV_ADMIN', 'ROLE_DATA_DEV', 'ROLE_QUALITY', 'ROLE_ANALYST', 'ROLE_SEC_ADMIN', 'ROLE_INDUSTRY_CONSULTANT']
const isBuiltinRole = (code) => BUILTIN_ROLES.includes(code)
const removeRole = async (row) => {
  try {
    await ElMessageBox.confirm('删除角色 ' + row.name + '（' + row.code + '）？删除后不可恢复', '提示', { type: 'warning' })
    await authApi.deleteRole(row.id)
    ElMessage.success('角色已删除')
    load()
  } catch (e) { /* 取消或后端提示 */ }
}
const openRole = (row) => {
  Object.assign(roleForm, { id: null, code: '', name: '', description: '' })
  if (row) Object.assign(roleForm, { id: row.id, code: row.code, name: row.name, description: row.description })
  roleDialog.value = true
}
const saveRole = async () => {
  saving.value = true
  try {
    if (roleForm.id) {
      await authApi.updateRole(roleForm.id, { name: roleForm.name, description: roleForm.description })
      ElMessage.success('角色已更新')
    } else {
      await authApi.createRole({ code: roleForm.code, name: roleForm.name, description: roleForm.description })
      ElMessage.success('角色已创建，请继续分配权限')
    }
    roleDialog.value = false
    load()
  } catch (e) { /* http 层已提示 */ } finally { saving.value = false }
}
const openPerms = (row) => {
  permRole.value = row
  permSelected.value = String(row.perms || '').split(',').map((s) => s.trim()).filter(Boolean)
  permKeyword.value = ''
  permDialog.value = true
}
const savePerms = async () => {
  saving.value = true
  try {
    await authApi.grantRolePerms(permRole.value.id, permSelected.value)
    ElMessage.success('权限已保存')
    permDialog.value = false
    load()
  } catch (e) { /* http 层已提示 */ } finally { saving.value = false }
}


const logLoading = ref(false)
const opFilters = reactive({ keyword: '', module: '', action: '', result: '', ip: '', startTime: '', endTime: '' })
const opLogs = reactive({ records: [], total: 0, pageNum: 1, pageSize: 20 })
const loginFilters = reactive({ username: '', status: '', ip: '', startTime: '', endTime: '' })
const loginLogs = reactive({ records: [], total: 0, pageNum: 1, pageSize: 20 })

const onTabChange = (name) => { if (name === 'logs') loadLogs() }
const loadLogs = async () => { await Promise.allSettled([loadOpLogs(), loadLoginLogs()]) }
const loadOpLogs = async () => {
  logLoading.value = true
  try {
    const d = await adminApi.operationLogs({ ...opFilters, pageNum: opLogs.pageNum, pageSize: opLogs.pageSize })
    opLogs.records = d.records || []; opLogs.total = d.total || 0
  } catch (e) { opLogs.records = []; opLogs.total = 0 } finally { logLoading.value = false }
}
const loadLoginLogs = async () => {
  logLoading.value = true
  try {
    const d = await adminApi.loginLogs({ ...loginFilters, pageNum: loginLogs.pageNum, pageSize: loginLogs.pageSize })
    loginLogs.records = d.records || []; loginLogs.total = d.total || 0
  } catch (e) { loginLogs.records = []; loginLogs.total = 0 } finally { logLoading.value = false }
}
const resetOpLogs = () => { Object.assign(opFilters, { keyword: '', module: '', action: '', result: '', ip: '', startTime: '', endTime: '' }); opLogs.pageNum = 1; loadOpLogs() }
const resetLoginLogs = () => { Object.assign(loginFilters, { username: '', status: '', ip: '', startTime: '', endTime: '' }); loginLogs.pageNum = 1; loadLoginLogs() }
const exportLogs = (rows, filename) => {
  if (!rows || !rows.length) { ElMessage.warning('当前页没有数据'); return }
  const keys = Object.keys(rows[0])
  const esc = (v) => '"' + String(v == null ? '' : v).replace(/"/g, '""') + '"'
  const csv = '\uFEFF' + keys.join(',') + '\n' + rows.map((r) => keys.map((k) => esc(r[k])).join(',')).join('\n')
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
  const a = document.createElement('a'); a.href = url; a.download = filename; a.click(); URL.revokeObjectURL(url)
}

onMounted(load)
</script>
