<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../../stores/user'
import { ElMessage } from 'element-plus'

const router = useRouter()
const store = useUserStore()
const loading = ref(false)
const form = reactive({ username: 'admin', password: 'admin123' })

async function handleLogin() {
  loading.value = true
  try {
    await store.login(form)
    await store.fetchInfo()
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <h1>大数据基座平台</h1>
      <el-form @submit.prevent="handleLogin">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" prefix-icon="User" size="large" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" prefix-icon="Lock" size="large" show-password />
        </el-form-item>
        <el-button type="primary" size="large" style="width:100%" :loading="loading" @click="handleLogin">登录</el-button>
      </el-form>
      <p class="tip">默认账号：admin / admin123</p>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0b1d3a 0%, #1677ff 100%);
}
.login-card {
  width: 420px;
  background: #fff;
  border-radius: 12px;
  padding: 40px;
  box-shadow: 0 8px 32px rgba(0,0,0,.2);
}
h1 { font-size: 22px; margin: 0 0 24px; text-align: center; }
.tip { text-align: center; color: #999; font-size: 12px; margin-top: 16px; }
</style>
