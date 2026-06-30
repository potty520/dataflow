<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { changePassword, updateProfile } from '../api'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const profileVisible = ref(false)
const pwdVisible = ref(false)
const profileForm = ref({ nickname: '', phone: '' })
const pwdForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })

const activeMenu = computed(() => route.path)

const menuTree = computed(() => {
  const menus = store.menus || []
  return menus.filter(m => m.menuType === 1 || m.parentId === 0)
})

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

function resolveIcon(icon) {
  return icon || 'Menu'
}
</script>

<template>
  <el-container class="layout">
    <el-aside width="240px" class="aside">
      <div class="logo">大数据基座平台</div>
      <el-menu :default-active="activeMenu" router background-color="#001529" text-color="#fff" active-text-color="#409eff">
        <template v-for="menu in store.menus" :key="menu.id">
          <el-sub-menu v-if="menu.menuType === 1 && menu.children?.length" :index="menu.path || String(menu.id)">
            <template #title>
              <el-icon><component :is="resolveIcon(menu.icon)" /></el-icon>
              <span>{{ menu.menuName }}</span>
            </template>
            <el-menu-item v-for="child in menu.children" :key="child.id" :index="child.path">
              {{ child.menuName }}
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else-if="menu.menuType === 2" :index="menu.path">
            <el-icon><component :is="resolveIcon(menu.icon)" /></el-icon>
            <span>{{ menu.menuName }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div>{{ route.meta.title || '首页' }}</div>
        <div class="header-right">
          <el-dropdown>
            <span class="user-info">{{ store.user?.nickname || store.user?.username }}</span>
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
      <el-main class="main">
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
.layout { height: 100vh; }
.aside { background: #001529; }
.logo {
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  padding: 20px 16px;
  border-bottom: 1px solid rgba(255,255,255,.1);
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  border-bottom: 1px solid #eee;
}
.main { background: #f5f7fa; }
.user-info { cursor: pointer; color: #333; }
</style>
