<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { changePassword, updateProfile } from '../api'
import { useUserStore } from '../stores/user'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const collapsed = ref(false)
const profileVisible = ref(false)
const pwdVisible = ref(false)
const profileForm = ref({ nickname: '', phone: '' })
const pwdForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })
const hiddenSidebarCodes = new Set(['portal_profile', 'log', 'config'])

const activeMenu = computed(() => route.path)
const navigationMenus = computed(() => normalizeMenus(store.menus || []))
const defaultOpenedMenus = computed(() => {
  const current = route.path
  const matched = navigationMenus.value.find((menu) =>
    menu.children?.some((child) => child.path === current)
  )
  return matched?.path ? [matched.path] : []
})

function normalizeMenus(menus) {
  return menus
    .filter((menu) => isMenuVisible(menu))
    .filter((menu) => (menu.parentId ?? 0) === 0)
    .filter((menu) => menu.menuType === 1 || menu.path === '/dashboard')
    .map((menu) => ({
      ...menu,
      children: normalizeChildren(menu.children || [])
    }))
    .filter((menu) => menu.menuType !== 1 || menu.children.length > 0)
    .sort(compareMenus)
}

function normalizeChildren(children) {
  return children
    .filter((menu) => isMenuVisible(menu))
    .filter((menu) => menu.menuType === 2)
    .sort(compareMenus)
}

function isMenuVisible(menu) {
  return menu.visible !== 0 && !hiddenSidebarCodes.has(menu.menuCode)
}

function compareMenus(a, b) {
  return (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || (a.id ?? 0) - (b.id ?? 0)
}

function resolveIcon(icon) {
  return icon || 'Menu'
}

function openProfile() {
  profileForm.value = {
    nickname: store.user?.nickname || '',
    phone: store.user?.phone || ''
  }
  profileVisible.value = true
}

async function saveProfile() {
  await updateProfile(profileForm.value)
  ElMessage.success('保存成功')
  profileVisible.value = false
  await store.fetchInfo()
}

async function savePassword() {
  await changePassword(pwdForm.value)
  ElMessage.success('密码修改成功')
  pwdVisible.value = false
}

function logout() {
  store.logout()
  router.push('/login')
}

function refreshPage() {
  router.go(0)
}

function goDashboard() {
  router.push('/dashboard')
}
</script>

<template>
  <el-container class="layout-shell">
    <el-aside class="layout-aside" :width="collapsed ? '76px' : '252px'">
      <div class="brand-bar">
        <div class="brand-mark">DF</div>
        <div v-if="!collapsed" class="brand-copy">
          <div class="brand-name">数据基座</div>
          <div class="brand-sub">治理 · 清洗 · 开发</div>
        </div>
      </div>
      <el-scrollbar class="menu-scroll">
        <el-menu
          :key="activeMenu"
          :default-active="activeMenu"
          :default-openeds="defaultOpenedMenus"
          :collapse="collapsed"
          :collapse-transition="false"
          router
          unique-opened
          background-color="transparent"
          text-color="#c8d5ec"
          active-text-color="#ffffff"
        >
          <template v-for="menu in navigationMenus" :key="menu.id">
            <el-sub-menu v-if="menu.menuType === 1 && menu.children?.length" :index="menu.path || String(menu.id)">
              <template #title>
                <el-icon><component :is="resolveIcon(menu.icon)" /></el-icon>
                <span>{{ menu.menuName }}</span>
              </template>
              <el-menu-item v-for="child in menu.children" :key="child.id" :index="child.path">
                <el-icon v-if="child.icon"><component :is="resolveIcon(child.icon)" /></el-icon>
                <span v-else class="menu-dot" />
                <span>{{ child.menuName }}</span>
              </el-menu-item>
            </el-sub-menu>
            <el-menu-item v-else-if="menu.menuType === 2" :index="menu.path">
              <el-icon><component :is="resolveIcon(menu.icon)" /></el-icon>
              <span>{{ menu.menuName }}</span>
            </el-menu-item>
          </template>
        </el-menu>
      </el-scrollbar>
    </el-aside>

    <el-container class="layout-body">
      <el-header class="layout-header">
        <div class="header-left">
          <el-button class="collapse-btn" circle text @click="collapsed = !collapsed">
            <el-icon><Fold v-if="!collapsed" /><Expand v-else /></el-icon>
          </el-button>
          <div>
            <div class="header-title">{{ route.meta.title || '首页' }}</div>
          </div>
        </div>
        <div class="header-right">
          <el-button text class="header-action" @click="goDashboard">
            <el-icon><House /></el-icon>
            <span>首页</span>
          </el-button>
          <el-button text class="header-action" @click="refreshPage">
            <el-icon><RefreshRight /></el-icon>
            <span>刷新</span>
          </el-button>
          <el-dropdown>
            <button type="button" class="user-chip">
              <el-icon><UserFilled /></el-icon>
              <span>{{ store.user?.nickname || store.user?.username || '用户' }}</span>
              <el-icon><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="openProfile">个人信息</el-dropdown-item>
                <el-dropdown-item @click="pwdVisible = true">修改密码</el-dropdown-item>
                <el-dropdown-item divided @click="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>

    <el-dialog v-model="profileVisible" title="个人信息" width="420px">
      <el-form label-width="80px">
        <el-form-item label="昵称"><el-input v-model="profileForm.nickname" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="profileForm.phone" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="profileVisible = false">取消</el-button>
        <el-button type="primary" @click="saveProfile">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="pwdVisible" title="修改密码" width="420px">
      <el-form label-width="90px">
        <el-form-item label="原密码"><el-input v-model="pwdForm.oldPassword" type="password" show-password /></el-form-item>
        <el-form-item label="新密码"><el-input v-model="pwdForm.newPassword" type="password" show-password /></el-form-item>
        <el-form-item label="确认密码"><el-input v-model="pwdForm.confirmPassword" type="password" show-password /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" @click="savePassword">确定</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<style scoped>
.layout-shell {
  height: 100vh;
  background: var(--app-bg);
}

.layout-aside {
  background: linear-gradient(180deg, #142033 0%, #192844 100%);
  border-right: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  flex-direction: column;
  transition: width 0.18s ease;
  overflow: hidden;
}

.brand-bar {
  min-height: 72px;
  padding: 16px 14px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.brand-mark {
  width: 38px;
  height: 38px;
  min-width: 38px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
  font-weight: 700;
  letter-spacing: 0;
}

.brand-name {
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  line-height: 1.2;
}

.brand-sub {
  color: rgba(255, 255, 255, 0.65);
  font-size: 12px;
  margin-top: 2px;
  white-space: normal;
  line-height: 1.4;
  max-width: 160px;
}

.brand-copy {
  min-width: 0;
  overflow: hidden;
}

.menu-scroll {
  flex: 1;
}

.layout-header {
  height: 72px;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--app-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
}

.header-left,
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--app-text);
  line-height: 1.2;
}

.header-action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--app-text);
}

.collapse-btn {
  color: var(--app-text);
}

.user-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--app-border);
  background: #fff;
  color: var(--app-text);
  border-radius: 8px;
  padding: 8px 12px;
  cursor: pointer;
}

.layout-main {
  padding: 28px 34px;
  overflow: auto;
  background: linear-gradient(180deg, #f5f8fc 0%, #eef3f9 100%);
}

:deep(.el-menu) {
  border-right: none;
  width: 100%;
  padding: 10px 0 16px;
}

:deep(.el-menu-item),
:deep(.el-sub-menu__title) {
  border-radius: 8px;
  margin: 3px 10px;
  height: 44px;
  line-height: 44px;
  color: #c8d5ec;
}

:deep(.el-menu-item:hover),
:deep(.el-sub-menu__title:hover) {
  background: rgba(255, 255, 255, 0.08);
}

:deep(.el-sub-menu__title) {
  font-weight: 600;
}

:deep(.el-sub-menu .el-menu-item) {
  height: 38px;
  line-height: 38px;
  min-width: 0;
  margin: 2px 10px 2px 42px;
  padding-left: 14px !important;
  color: #aebed7;
  font-size: 13px;
}

:deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, rgba(37, 99, 235, 0.72), rgba(14, 165, 233, 0.36));
  box-shadow: inset 3px 0 0 rgba(255, 255, 255, 0.85);
}

:deep(.el-sub-menu.is-active > .el-sub-menu__title) {
  color: #fff;
}

:deep(.el-menu--collapse .el-menu-item),
:deep(.el-menu--collapse .el-sub-menu__title) {
  justify-content: center;
  margin: 4px 10px;
  padding: 0 !important;
}

:deep(.el-menu--collapse .el-icon) {
  margin-right: 0;
}

.menu-dot {
  width: 5px;
  height: 5px;
  min-width: 5px;
  border-radius: 50%;
  margin-right: 8px;
  background: currentColor;
  opacity: 0.65;
}
</style>
