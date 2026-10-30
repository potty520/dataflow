<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>菜单与门户角色</h2>
        <div class="sub">菜单树维护、角色-菜单授权、门户-角色映射；保存后菜单即时生效</div>
      </div>
    </div>

    <el-tabs v-model="tab" type="border-card">
      <el-tab-pane label="菜单管理" name="menus">
        <div class="toolbar">
          <el-input v-model="keyword" size="small" placeholder="编码/标题" clearable style="width:200px" />
          <span class="spacer"></span>
          <el-button size="small" type="primary" @click="openMenu()">新建菜单</el-button>
        </div>
        <el-table :data="filteredMenus" size="small" v-loading="loading">
          <el-table-column prop="code" label="编码" width="180"><template #default="{ row }"><span class="mono">{{ row.code }}</span></template></el-table-column>
          <el-table-column prop="title" label="标题" width="150" />
          <el-table-column prop="path" label="路径" min-width="180"><template #default="{ row }"><span class="mono">{{ row.path }}</span></template></el-table-column>
          <el-table-column prop="perm" label="权限" min-width="180"><template #default="{ row }"><span class="mono">{{ row.perm }}</span></template></el-table-column>
          <el-table-column prop="parentCode" label="父级" width="110" />
          <el-table-column prop="sortNo" label="排序" width="70" align="right" />
          <el-table-column prop="status" label="状态" width="90" />
          <el-table-column label="操作" width="130">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openMenu(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="removeMenu(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="角色菜单" name="roles">
        <div class="toolbar">
          <el-select v-model="roleCode" size="small" placeholder="选择角色" style="width:220px" @change="loadRoleMenus">
            <el-option v-for="r in roles" :key="r.code" :label="r.code + ' · ' + r.name" :value="r.code" />
          </el-select>
          <el-button size="small" @click="loadRoleMenus">刷新</el-button>
          <span class="spacer"></span>
          <el-button size="small" type="primary" :disabled="!roleCode" @click="saveRoleMenus">保存授权</el-button>
        </div>
        <el-alert :closable="false" type="info" show-icon title="不勾选任何菜单时，该角色沿用系统默认菜单；勾选后仅下发所选菜单。" style="margin-bottom:8px" />
        <el-checkbox-group v-model="checkedMenus">
          <div v-for="m in menus" :key="m.code" style="display:inline-block;width:260px;margin:4px 0">
            <el-checkbox :label="m.code">{{ m.title }}</el-checkbox>
          </div>
        </el-checkbox-group>
      </el-tab-pane>

      <el-tab-pane label="门户角色" name="portals">
        <el-card shadow="never" style="margin-bottom:12px">
          <div class="card-head"><h3>CAS 接入</h3><span class="hint">启用后登录页显示统一门户登录入口</span></div>
          <el-form :model="casForm" label-width="110px" size="small" style="max-width:720px">
            <el-form-item label="启用 CAS"><el-switch v-model="casForm.enabled" :active-value="1" :inactive-value="0" /></el-form-item>
            <el-form-item label="CAS 服务地址"><el-input v-model="casForm.serverUrl" placeholder="https://cas.example.com/cas" /></el-form-item>
            <el-form-item label="平台访问地址"><el-input v-model="casForm.serviceUrl" placeholder="http://192.168.1.41:8092" /></el-form-item>
            <el-form-item label="门户编码"><el-input v-model="casForm.portalCode" placeholder="GOV_PORTAL" /></el-form-item>
            <el-form-item label="自动开通账号"><el-switch v-model="casForm.autoCreate" :active-value="1" :inactive-value="0" /></el-form-item>
            <el-form-item label="默认角色"><el-input v-model="casForm.defaultRole" placeholder="ROLE_ANALYST" /></el-form-item>
            <el-form-item><el-button type="primary" size="small" @click="saveCas">保存 CAS 配置</el-button></el-form-item>
          </el-form>
        </el-card>
        <el-card shadow="never" style="margin-bottom:12px">
          <div class="card-head">
            <h3>门户列表</h3>
            <el-button size="small" type="primary" @click="portalSysDialog = true">新增门户</el-button>
          </div>
          <el-table :data="portalsSys" size="small">
            <el-table-column prop="code" label="编码" width="180"><template #default="{ row }"><span class="mono">{{ row.code }}</span></template></el-table-column>
            <el-table-column prop="name" label="名称" width="160" />
            <el-table-column prop="domain" label="域名" min-width="200" />
            <el-table-column prop="status" label="状态" width="90" />
            <el-table-column label="操作" width="90"><template #default="{ row }"><el-button link type="danger" size="small" @click="removeSysPortal(row)">删除</el-button></template></el-table-column>
          </el-table>
        </el-card>
        <div class="toolbar">
          <span class="hint">门户编码 → 角色编码映射（供 CAS 门户按门户展示角色）</span>
          <span class="spacer"></span>
          <el-button size="small" type="primary" @click="portalDialog = true">新增映射</el-button>
        </div>
        <el-table :data="portals" size="small">
          <el-table-column prop="portalCode" label="门户编码" width="200"><template #default="{ row }"><span class="mono">{{ row.portalCode }}</span></template></el-table-column>
          <el-table-column prop="roleCode" label="角色编码" width="220"><template #default="{ row }"><span class="mono">{{ row.roleCode }}</span></template></el-table-column>
          <el-table-column prop="status" label="状态" width="100" />
          <el-table-column prop="remark" label="说明" min-width="200" />
          <el-table-column label="操作" width="90">
            <template #default="{ row }"><el-button link type="danger" size="small" @click="removePortal(row)">删除</el-button></template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="menuDialog" :title="menuForm.id ? '编辑菜单' : '新建菜单'" width="540px">
      <el-form label-width="90px" size="small">
        <el-form-item label="编码"><el-input v-model="menuForm.code" :disabled="!!menuForm.id" /></el-form-item>
        <el-form-item label="标题"><el-input v-model="menuForm.title" /></el-form-item>
        <el-form-item label="路径"><el-input v-model="menuForm.path" placeholder="/xxx" /></el-form-item>
        <el-form-item label="图标"><el-input v-model="menuForm.icon" placeholder="Element Plus 图标名" /></el-form-item>
        <el-form-item label="权限"><el-input v-model="menuForm.perm" placeholder="如 quality:config:view" /></el-form-item>
        <el-form-item label="父级">
          <el-select v-model="menuForm.parentCode" clearable style="width:100%">
            <el-option v-for="m in menus" :key="m.code" :label="m.code + ' · ' + m.title" :value="m.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="menuForm.sortNo" :min="0" /></el-form-item>
        <el-form-item label="状态"><el-radio-group v-model="menuForm.status"><el-radio label="ENABLED">启用</el-radio><el-radio label="DISABLED">停用</el-radio></el-radio-group></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="menuDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="saveMenu">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="portalSysDialog" title="新增门户" width="460px">
      <el-form label-width="90px" size="small">
        <el-form-item label="编码"><el-input v-model="portalSysForm.code" placeholder="GOV_PORTAL" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="portalSysForm.name" /></el-form-item>
        <el-form-item label="域名"><el-input v-model="portalSysForm.domain" placeholder="https://portal.example.com" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="portalSysForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="portalSysDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="saveSysPortal">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="portalDialog" title="新增门户角色映射" width="460px">
      <el-form label-width="90px" size="small">
        <el-form-item label="门户编码"><el-input v-model="portalForm.portalCode" placeholder="如 GOV_PORTAL" /></el-form-item>
        <el-form-item label="角色"><el-select v-model="portalForm.roleCode" style="width:100%"><el-option v-for="r in roles" :key="r.code" :label="r.code + ' · ' + r.name" :value="r.code" /></el-select></el-form-item>
        <el-form-item label="说明"><el-input v-model="portalForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="portalDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="savePortal">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi, authApi } from '@/api'

const tab = ref('menus')
const loading = ref(false)
const menus = ref([])
const roles = ref([])
const portals = ref([])
const keyword = ref('')
const roleCode = ref('')
const checkedMenus = ref([])
const menuDialog = ref(false)
const portalDialog = ref(false)
const menuForm = reactive({ id: null, code: '', title: '', path: '', icon: 'Menu', perm: '', parentCode: null, sortNo: 0, status: 'ENABLED' })
const portalForm = reactive({ portalCode: '', roleCode: '', remark: '' })
const casForm = reactive({ enabled: 0, serverUrl: '', serviceUrl: '', portalCode: 'GOV_PORTAL', autoCreate: 1, defaultRole: 'ROLE_ANALYST' })
const portalsSys = ref([])
const portalSysDialog = ref(false)
const portalSysForm = reactive({ code: '', name: '', domain: '', remark: '' })
const loadCas = async () => { try { Object.assign(casForm, await adminApi.casConfig()) } catch (e) { /* ignore */ } }
const saveCas = async () => { try { await adminApi.saveCasConfig({ ...casForm }); ElMessage.success('CAS 配置已保存') } catch (e) { /* http 层已提示 */ } }
const loadSysPortals = async () => { try { portalsSys.value = (await adminApi.portalsList()).records || [] } catch (e) { portalsSys.value = [] } }
const saveSysPortal = async () => { try { await adminApi.createPortal({ ...portalSysForm }); ElMessage.success('门户已创建'); portalSysDialog.value = false; loadSysPortals() } catch (e) { /* http 层已提示 */ } }
const removeSysPortal = async (row) => { try { await ElMessageBox.confirm('删除门户 ' + row.code + '？', '提示', { type: 'warning' }); await adminApi.deletePortal(row.id); loadSysPortals() } catch (e) { /* 取消 */ } }

const filteredMenus = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return menus.value
  return menus.value.filter((m) => String(m.code).toLowerCase().includes(k) || String(m.title).toLowerCase().includes(k))
})

const loadMenus = async () => {
  loading.value = true
  try { menus.value = (await adminApi.menus()).records || [] } catch (e) { menus.value = [] } finally { loading.value = false }
}
const loadRoles = async () => { try { roles.value = await authApi.roles() || [] } catch (e) { roles.value = [] } }
const loadPortals = async () => { try { portals.value = (await adminApi.portals()).records || [] } catch (e) { portals.value = [] } }
const loadRoleMenus = async () => {
  if (!roleCode.value) return
  try { checkedMenus.value = (await adminApi.roleMenus(roleCode.value)).menuCodes || [] } catch (e) { checkedMenus.value = [] }
}
const openMenu = (row) => {
  Object.assign(menuForm, { id: null, code: '', title: '', path: '', icon: 'Menu', perm: '', parentCode: null, sortNo: 0, status: 'ENABLED' })
  if (row) Object.assign(menuForm, { id: row.id, code: row.code, title: row.title, path: row.path, icon: row.icon, perm: row.perm, parentCode: row.parentCode, sortNo: row.sortNo, status: row.status })
  menuDialog.value = true
}
const saveMenu = async () => {
  try {
    if (menuForm.id) await adminApi.updateMenu(menuForm.id, { ...menuForm })
    else await adminApi.createMenu({ ...menuForm })
    ElMessage.success('菜单已保存，刷新后生效'); menuDialog.value = false; loadMenus()
  } catch (e) { /* http 层已提示 */ }
}
const removeMenu = async (row) => {
  try { await ElMessageBox.confirm('删除菜单 ' + row.title + '？关联角色授权会一并清理', '提示', { type: 'warning' }); await adminApi.deleteMenu(row.id); loadMenus() }
  catch (e) { /* 取消 */ }
}
const saveRoleMenus = async () => {
  try { await adminApi.saveRoleMenus(roleCode.value, checkedMenus.value); ElMessage.success('角色菜单已保存，重新登录/刷新后生效') }
  catch (e) { /* http 层已提示 */ }
}
const savePortal = async () => {
  try { await adminApi.addPortalRole({ ...portalForm }); ElMessage.success('已保存'); portalDialog.value = false; loadPortals() }
  catch (e) { /* http 层已提示 */ }
}
const removePortal = async (row) => {
  try { await adminApi.removePortalRole(row.portalCode, row.roleCode); loadPortals() } catch (e) { /* 取消 */ }
}

onMounted(() => { loadMenus(); loadRoles(); loadPortals(); loadCas(); loadSysPortals() })
</script>

<style scoped>
.page { padding: 4px; }
.hint { color: #909399; font-size: 12px; }
</style>
