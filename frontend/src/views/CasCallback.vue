<template>
  <div class="cas-wrap">
    <div class="cas-card">
      <h2>正在完成统一门户登录…</h2>
      <p v-if="error" class="err">{{ error }}</p>
      <p v-else class="hint">CAS 票据已校验，正在换取平台会话</p>
      <el-button v-if="error" type="primary" @click="$router.replace('/login')">返回登录</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { authApi } from '@/api'

const route = useRoute()
const router = useRouter()
const store = useAuthStore()
const error = ref('')

onMounted(async () => {
  const code = typeof route.query.code === 'string' ? route.query.code : ''
  if (!code) { error.value = '缺少 CAS 登录票据'; return }
  try {
    const data = await authApi.casExchange(code)
    store.applyTokens(data)
    router.replace('/overview')
  } catch (e) {
    error.value = 'CAS 登录换取会话失败：' + (e.message || '请重试')
  }
})
</script>

<style scoped>
.cas-wrap { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: #f3f6fb; }
.cas-card { width: 420px; padding: 32px; background: #fff; border-radius: 10px; box-shadow: 0 6px 24px rgba(20, 40, 80, .08); text-align: center; }
.cas-card h2 { margin: 0 0 10px; color: #1f2d3d; font-size: 18px; }
.hint { color: #909399; font-size: 13px; }
.err { color: #f56c6c; font-size: 13px; }
</style>
