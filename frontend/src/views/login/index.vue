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
  <div class="login-shell">
    <section class="login-copy">
      <h1>大数据基座平台</h1>
    </section>

    <el-card class="login-card" shadow="never">
      <div class="login-card-mark">DF</div>
      <div class="login-title">账号登录</div>
      <el-form @submit.prevent="handleLogin">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" prefix-icon="User" size="large" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" prefix-icon="Lock" size="large" show-password />
        </el-form-item>
        <el-button type="primary" size="large" class="login-button" :loading="loading" @click="handleLogin">登录</el-button>
      </el-form>
      <p class="tip">默认账号：admin / admin123</p>
    </el-card>
  </div>
</template>

<style scoped>
.login-shell {
  min-height: 100vh;
  width: 100%;
  display: grid;
  grid-template-columns: 1.1fr 420px;
  gap: 28px;
  padding: clamp(28px, 5vw, 72px);
  align-items: center;
  background:
    linear-gradient(90deg, rgba(5, 23, 31, 0.84) 0%, rgba(5, 23, 31, 0.62) 44%, rgba(5, 23, 31, 0.3) 100%),
    url('../../assets/login-data-bg.svg') center / cover no-repeat;
  color: #ecfdf5;
  position: relative;
  overflow: hidden;
}

.login-shell::after {
  content: '';
  position: absolute;
  inset: 34px;
  border: 1px solid rgba(221, 248, 234, 0.14);
  pointer-events: none;
}

.login-copy {
  position: relative;
  z-index: 1;
  max-width: 520px;
  padding: 24px 0 24px clamp(0px, 5vw, 80px);
}

.login-copy h1 {
  margin: 0;
  font-size: clamp(34px, 4vw, 56px);
  line-height: 1.2;
  color: #f7fff9;
  text-shadow: 0 18px 44px rgba(0, 0, 0, 0.32);
}

.login-card {
  position: relative;
  z-index: 1;
  width: 420px;
  border: 1px solid rgba(221, 248, 234, 0.22);
  border-radius: 8px;
  padding: 32px;
  background: rgba(247, 255, 249, 0.9);
  box-shadow: 0 28px 70px rgba(0, 0, 0, 0.34);
  backdrop-filter: blur(18px);
}

.login-card-mark {
  width: 42px;
  height: 42px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  margin-bottom: 20px;
  background: #0f3a42;
  color: #f3c969;
  font-weight: 800;
}

.login-title {
  font-size: 20px;
  font-weight: 700;
  margin-bottom: 22px;
  color: #102a2f;
}

.login-card :deep(.el-input__wrapper) {
  min-height: 42px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(15, 58, 66, 0.14);
  box-shadow: none;
}

.login-card :deep(.el-input__wrapper.is-focus) {
  border-color: #179b7f;
  box-shadow: 0 0 0 3px rgba(54, 211, 153, 0.16);
}

.login-button {
  width: 100%;
  border: none;
  background: linear-gradient(135deg, #0f3a42 0%, #179b7f 100%);
  color: #f7fff9;
  font-weight: 700;
}

.login-button:hover,
.login-button:focus {
  background: linear-gradient(135deg, #164d55 0%, #21b991 100%);
  color: #fff;
}

.tip {
  text-align: center;
  color: rgba(16, 42, 47, 0.6);
  font-size: 12px;
  margin-top: 16px;
}

@media (max-width: 920px) {
  .login-shell {
    grid-template-columns: 1fr;
    padding: 24px;
  }

  .login-copy {
    padding-left: 0;
  }

  .login-card {
    width: 100%;
  }
}
</style>
