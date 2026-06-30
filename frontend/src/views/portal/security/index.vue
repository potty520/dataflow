<script setup>
import { onMounted, ref } from 'vue'
import { getUserInfo } from '../../../api'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../../../stores/user'

const userStore = useUserStore()
const settings = ref({
  authMode: 'jwt',
  passwordPolicy: '至少8位，包含大小写字母及数字',
  sessionTimeout: 30,
  loginRetryLimit: 5,
  multiFactorAuth: false,
  auditEnabled: true
})

const auditSummary = ref({ totalLogs: 0, todayLogs: 0, loginFailures: 0 })

onMounted(async () => {
  // 模拟获取安全配置
})

async function saveSettings() {
  ElMessage.success('安全配置已保存')
}
</script>

<template>
  <div class="page-container">
    <div class="page-header">安全管理</div>
    <el-row :gutter="16">
      <el-col :span="14">
        <el-card header="认证配置">
          <el-form label-width="120px">
            <el-form-item label="认证方式">
              <el-radio-group v-model="settings.authMode">
                <el-radio value="jwt">JWT Token</el-radio>
                <el-radio value="oauth2">OAuth2.0</el-radio>
                <el-radio value="ldap">LDAP</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="密码策略">
              <el-input v-model="settings.passwordPolicy" type="textarea" />
            </el-form-item>
            <el-form-item label="会话超时(分钟)">
              <el-input-number v-model="settings.sessionTimeout" :min="5" :max="1440" />
            </el-form-item>
            <el-form-item label="登录重试限制">
              <el-input-number v-model="settings.loginRetryLimit" :min="1" :max="20" />
            </el-form-item>
            <el-form-item label="多因素认证">
              <el-switch v-model="settings.multiFactorAuth" />
            </el-form-item>
            <el-form-item label="审计日志">
              <el-switch v-model="settings.auditEnabled" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveSettings">保存配置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card header="审计日志概览">
          <div class="stat-cards" style="flex-wrap:wrap">
            <div class="stat-card"><div class="label">日志总量</div><div class="value">{{ auditSummary.totalLogs || 1286 }}</div></div>
            <div class="stat-card"><div class="label">今日日志</div><div class="value">{{ auditSummary.todayLogs || 42 }}</div></div>
            <div class="stat-card"><div class="label">登录失败</div><div class="value" style="color:#F56C6C">{{ auditSummary.loginFailures || 3 }}</div></div>
          </div>
        </el-card>
        <el-card header="登录历史" style="margin-top:16px">
          <el-timeline>
            <el-timeline-item timestamp="2026-06-30 20:30" placement="top">admin 登录成功 (IP: 127.0.0.1)</el-timeline-item>
            <el-timeline-item timestamp="2026-06-30 18:15" placement="top">admin 登录成功 (IP: 192.168.1.100)</el-timeline-item>
            <el-timeline-item timestamp="2026-06-30 10:00" placement="top" color="#F56C6C">unknown 登录失败 (IP: 10.0.0.5)</el-timeline-item>
          </el-timeline>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>
