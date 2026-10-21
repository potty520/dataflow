<template>
  <div class="layout">
    <aside class="side" :class="{ collapsed }">
      <div class="logo">
        <div class="mark">DGP</div>
        <div v-show="!collapsed" class="logo-text">
          <b>数据治理平台</b>
          <span>Data Governance Platform</span>
        </div>
      </div>
      <el-scrollbar class="nav-scroll">
        <div v-for="g in groups" :key="g.domain" class="nav-group">
          <div v-show="!collapsed && !(g.items.length === 1 && g.items[0].name === g.name)" class="nav-group-title">{{ g.name }}</div>
          <router-link v-for="m in g.items" :key="m.code" :to="m.path" class="nav-item"
                       :class="{ active: isActive(m.path) }">
            <el-icon class="nav-icon"><component :is="m.icon" /></el-icon>
            <span v-show="!collapsed">{{ m.name }}</span>
          </router-link>
        </div>
      </el-scrollbar>
      <div class="side-foot" @click="collapsed = !collapsed">
        <el-icon><component :is="collapsed ? 'Expand' : 'Fold'" /></el-icon>
        <span v-show="!collapsed">收起菜单</span>
      </div>
    </aside>

    <div class="main">
      <header class="topbar">
        <div class="crumbs">
          <span class="cur">{{ route.meta.title }}</span>
          <span class="path">/ {{ groupName }}</span>
        </div>
        <div class="right">
          <el-tooltip content="平台健康检查（免权限探针）">
            <el-tag :type="healthType" effect="plain" size="small" style="cursor:pointer" @click="loadHealth">
              <span class="tag-dot" :style="{ background: healthColor }"></span>
              平台 {{ healthText }}
            </el-tag>
          </el-tooltip>
          <el-tag effect="plain" size="small" type="info">{{ store.tenantName }}</el-tag>
          <el-tag effect="plain" size="small">{{ store.roleName }}</el-tag>
          <el-dropdown trigger="click" @command="onCommand">
            <span class="user">
              <el-avatar :size="24" style="background:#2563eb;font-size:11px">{{ (store.user?.realName || 'U').slice(0, 1) }}</el-avatar>
              <span class="uname">{{ store.user?.realName }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="me">账号信息</el-dropdown-item>
                <el-dropdown-item command="apis">接口清单</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <el-scrollbar class="content">
        <router-view v-slot="{ Component }">
          <component :is="Component" />
        </router-view>
      </el-scrollbar>
    </div>

    <el-dialog v-model="meVisible" title="账号与数据权限" width="520px">
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="用户名">{{ store.user?.username }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ store.user?.realName }}</el-descriptions-item>
        <el-descriptions-item label="角色">{{ store.roleName }}（{{ store.user?.roleCode }}）</el-descriptions-item>
        <el-descriptions-item label="租户">{{ store.tenantName }}（{{ store.user?.tenantPath }}）</el-descriptions-item>
        <el-descriptions-item label="权限编码">
          <div class="perm-box">
            <el-tag v-for="p in store.perms.slice(0, 30)" :key="p" size="small" effect="plain" style="margin:2px">{{ p }}</el-tag>
          </div>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <el-drawer v-model="apiVisible" title="平台接口清单（实时从 MVC 注册表导出）" size="60%">
      <el-input v-model="apiKeyword" placeholder="按路径 / 权限过滤" clearable style="margin-bottom:10px" />
      <el-table :data="filteredApis" size="small" height="calc(100vh - 200px)">
        <el-table-column prop="method" label="方法" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.method === 'GET' ? 'success' : row.method === 'POST' ? 'warning' : 'info'">{{ row.method }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="路径" min-width="260">
          <template #default="{ row }"><span class="mono">{{ row.path }}</span></template>
        </el-table-column>
        <el-table-column prop="perm" label="权限编码" width="220" />
        <el-table-column prop="handler" label="实现" min-width="220" />
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { adminApi } from '@/api'

const route = useRoute()
const router = useRouter()
const store = useAuthStore()
const collapsed = ref(false)
// UNKNOWN 表示"探测失败/尚未探测"，与"服务异常"区分开，避免非管理员看到永久红点
const health = ref('UNKNOWN')
const meVisible = ref(false)
const apiVisible = ref(false)
const apiKeyword = ref('')
const apis = ref([])

const healthText = computed(() => ({ UP: '正常', DEGRADED: '降级', UNKNOWN: '未知' }[health.value] || '未知'))
const healthType = computed(() => ({ UP: 'success', DEGRADED: 'warning', UNKNOWN: 'info' }[health.value] || 'info'))
const healthColor = computed(() => ({ UP: '#16a34a', DEGRADED: '#d97706', UNKNOWN: '#94a3b8' }[health.value] || '#94a3b8'))

const GROUP_NAME = {
  portal: '治理概览', metadata: '元数据与血缘', model: '数据建模', standard: '数据标准',
  integration: '数据集成', workflow: '调度与工作流', quality: '数据质量', security: '数据安全',
  asset: '数据资产', service: '数据服务', industry: '行业适配', admin: '系统管理'
}

const groups = computed(() => {
  const order = ['portal', 'metadata', 'model', 'standard', 'integration', 'workflow', 'quality', 'security', 'asset', 'service', 'industry', 'admin']
  const map = new Map()
  for (const m of store.menus) {
    const d = m.domain || 'portal'
    if (!map.has(d)) map.set(d, [])
    map.get(d).push(m)
  }
  return order.filter((d) => map.has(d)).map((d) => ({ domain: d, name: GROUP_NAME[d] || d, items: map.get(d) }))
})

const groupName = computed(() => {
  const m = store.menus.find((x) => x.path === route.path)
  return GROUP_NAME[m?.domain] || ''
})

const isActive = (path) => route.path === path

const loadHealth = async () => {
  try {
    // 免权限探针：任何登录角色都可调用，失败只表示"未知"，不弹提示
    const h = await adminApi.ping()
    health.value = h && h.status ? h.status : 'UNKNOWN'
  } catch (e) {
    health.value = 'UNKNOWN'
  }
}

const filteredApis = computed(() => {
  const k = apiKeyword.value.trim()
  if (!k) return apis.value
  return apis.value.filter((a) => a.path.includes(k) || a.perm.includes(k) || a.handler.includes(k))
})

const onCommand = async (cmd) => {
  if (cmd === 'logout') {
    await store.logout()
    router.push('/login')
  } else if (cmd === 'me') {
    meVisible.value = true
  } else if (cmd === 'apis') {
    apiVisible.value = true
    if (!apis.value.length) {
      try {
        const d = await adminApi.apis()
        apis.value = d.records || []
      } catch (e) {
        // 非管理员无接口清单权限，抽屉留空即可（http 层已提示）
        apis.value = []
      }
    }
  }
}

onMounted(async () => {
  if (!store.menus.length) {
    try {
      await store.fetchMenus()
    } catch (e) {
      /* 菜单失败不影响页面主体 */
    }
  }
  loadHealth()
})
</script>

<style scoped>
.layout { height: 100%; display: flex; background: var(--dgp-bg); }
.side {
  width: 216px; flex: none; background: linear-gradient(180deg, var(--dgp-sidebar), #0a1428);
  display: flex; flex-direction: column; transition: width .18s ease; color: #cbd5e1;
}
.side.collapsed { width: 62px; }
.logo { height: 56px; display: flex; align-items: center; gap: 10px; padding: 0 14px; border-bottom: 1px solid rgba(255,255,255,.06); }
.mark {
  width: 34px; height: 34px; border-radius: 8px; flex: none; display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #2563eb, #38bdf8); color: #fff; font-weight: 800; font-size: 12px; letter-spacing: .5px;
}
.logo-text { display: flex; flex-direction: column; line-height: 1.3; overflow: hidden; white-space: nowrap; }
.logo-text b { color: #fff; font-size: 14px; }
.logo-text span { font-size: 9.5px; color: #64748b; letter-spacing: .3px; }
.nav-scroll { flex: 1; padding: 6px 0 12px; }
.nav-group { margin-top: 8px; }
.nav-group-title { font-size: 10.5px; color: #4b6187; padding: 6px 16px; letter-spacing: 1px; }
.nav-item {
  display: flex; align-items: center; gap: 9px; height: 36px; padding: 0 16px; color: #b6c6de; font-size: 13px;
  border-left: 3px solid transparent; transition: all .15s ease; white-space: nowrap;
}
.nav-item:hover { background: rgba(37, 99, 235, .12); color: #fff; }
.nav-item.active { background: linear-gradient(90deg, rgba(37,99,235,.32), rgba(37,99,235,.06)); color: #fff; border-left-color: #3b82f6; }
.nav-icon { font-size: 15px; }
.side.collapsed .nav-item { justify-content: center; padding: 0; }
.side-foot {
  height: 40px; display: flex; align-items: center; gap: 8px; justify-content: center; cursor: pointer;
  border-top: 1px solid rgba(255,255,255,.06); color: #64748b; font-size: 12px;
}
.main { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.topbar {
  height: 52px; flex: none; background: #fff; border-bottom: 1px solid var(--dgp-border);
  display: flex; align-items: center; justify-content: space-between; padding: 0 18px;
}
.crumbs { display: flex; align-items: baseline; gap: 8px; }
.crumbs .cur { font-size: 14px; font-weight: 600; }
.crumbs .path { font-size: 12px; color: var(--dgp-text-2); }
.right { display: flex; align-items: center; gap: 10px; }
.user { display: flex; align-items: center; gap: 6px; cursor: pointer; font-size: 12.5px; }
.uname { max-width: 90px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.content { flex: 1; min-height: 0; }
.perm-box { max-height: 180px; overflow: auto; }
</style>
