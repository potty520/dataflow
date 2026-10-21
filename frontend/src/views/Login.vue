<template>
  <div class="login-wrap">
    <div class="login-card">
      <div class="login-left">
        <div>
          <h1>DGP 数据治理平台</h1>
          <p>
            端到端一体化的企业级数据治理底座：元数据采集与血缘追溯、统一建模与标准落地、
            批流一体加工调度、六维度质量稽核与问题闭环、分级分类与动态脱敏、标准化数据服务。
          </p>
          <div class="badges">
            <span>元数据血缘</span><span>统一建模</span><span>数据标准</span>
            <span>图形化 ETL</span><span>质量稽核</span><span>动态脱敏</span>
            <span>数据服务</span><span>行业可配置</span>
          </div>
        </div>
        <div style="font-size: 11.5px; color: #7f96bd">v1.0.0 · 私有化 / 信创适配 · 支持 5 行业配置包</div>
      </div>
      <div class="login-right">
        <h2>账号登录</h2>
        <div class="tip">登录后将按角色自动加载菜单与数据权限</div>
        <el-form :model="form" label-position="top" @keyup.enter="submit">
          <el-form-item label="用户名">
            <el-input v-model="form.username" size="large" placeholder="请输入用户名" :prefix-icon="User" />
          </el-form-item>
          <el-form-item label="口令">
            <el-input v-model="form.password" type="password" size="large" show-password
                      placeholder="请输入口令" :prefix-icon="Lock" />
          </el-form-item>
          <el-button type="primary" size="large" style="width: 100%; margin-top: 6px" :loading="loading" @click="submit">
            登 录
          </el-button>
        </el-form>
        <div v-if="demoEnabled" class="accounts">
          <div><b>演示账号</b>（口令均为 Dgp@2026）</div>
          <div v-for="a in accounts" :key="a.u">
            <a href="javascript:void 0" @click="fill(a)">{{ a.u }}</a> · {{ a.n }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const store = useAuthStore()
const loading = ref(false)
// 生产构建（.env.production 设 VITE_DEMO_LOGIN=false）不预填演示口令、不渲染演示账号清单
const demoEnabled = import.meta.env.VITE_DEMO_LOGIN !== 'false'
const form = reactive({
  username: demoEnabled ? 'admin' : '',
  password: demoEnabled ? 'Dgp@2026' : ''
})
const accounts = demoEnabled
  ? [
      { u: 'admin', n: '数据治理管理员' },
      { u: 'zhangsan', n: '数据开发工程师' },
      { u: 'liuqin', n: '数据质量专员' },
      { u: 'wangwu', n: '分析师/业务用户' },
      { u: 'secadmin', n: '安全管理员' },
      { u: 'consultant', n: '行业实施顾问' }
    ]
  : []

const fill = (a) => {
  form.username = a.u
  form.password = 'Dgp@2026'
}

const submit = async () => {
  if (!form.username || !form.password) return ElMessage.warning('请输入用户名与口令')
  loading.value = true
  try {
    await store.login({ ...form })
    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    const target = redirect.startsWith('/') && redirect !== '/login' ? redirect : '/overview'
    router.replace(target)
  } catch (e) {
    // http 层已提示具体失败原因
  } finally {
    loading.value = false
  }
}
</script>
