<script setup>
import { onMounted, reactive, ref } from 'vue'
import { getUserInfo, changePassword, updateProfile } from '../../../api'
import { useUserStore } from '../../../stores/user'
import { ElMessage } from 'element-plus'

const userStore = useUserStore()
const profile = ref({})
const profileForm = reactive({ nickname: '', phone: '', avatar: '' })
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const profileLoading = ref(false)
const pwdLoading = ref(false)

onMounted(async () => {
  const res = await getUserInfo()
  const user = res.data?.user || userStore.user || {}
  profile.value = user
  profileForm.nickname = user.nickname || ''
  profileForm.phone = user.phone || ''
  profileForm.avatar = user.avatar || ''
})

async function saveProfile() {
  profileLoading.value = true
  try {
    await updateProfile(profileForm)
    ElMessage.success('个人信息已更新')
    await userStore.fetchInfo()
  } finally { profileLoading.value = false }
}

async function savePassword() {
  if (!pwdForm.oldPassword) { ElMessage.warning('请输入原密码'); return }
  if (!pwdForm.newPassword) { ElMessage.warning('请输入新密码'); return }
  if (pwdForm.newPassword !== pwdForm.confirmPassword) { ElMessage.warning('两次新密码不一致'); return }
  pwdLoading.value = true
  try {
    await changePassword(pwdForm)
    ElMessage.success('密码修改成功')
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirmPassword = ''
  } finally { pwdLoading.value = false }
}
</script>

<template>
  <div class="page-container">
    <div class="page-header">个人中心</div>
    <el-row :gutter="16">
      <el-col :span="10">
        <el-card>
          <template #header><div style="display:flex;align-items:center"><el-avatar :size="60" :src="profile.avatar" /><span style="margin-left:12px;font-size:18px;font-weight:600">{{ profile.nickname || profile.username }}</span></div></template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="用户名">{{ profile.username }}</el-descriptions-item>
            <el-descriptions-item label="昵称">{{ profile.nickname }}</el-descriptions-item>
            <el-descriptions-item label="手机号">{{ profile.phone || '未设置' }}</el-descriptions-item>
            <el-descriptions-item label="租户">{{ profile.tenantName || '张掖市大数据平台' }}</el-descriptions-item>
            <el-descriptions-item label="角色">{{ (profile.roles || []).join(', ') || '管理员' }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="profile.status === 1 ? 'success' : 'danger'" size="small">{{ profile.status === 1 ? '正常' : '锁定' }}</el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :span="14">
        <el-card header="编辑资料">
          <el-form label-width="100px">
            <el-form-item label="昵称"><el-input v-model="profileForm.nickname" /></el-form-item>
            <el-form-item label="手机号"><el-input v-model="profileForm.phone" /></el-form-item>
            <el-form-item label="头像URL"><el-input v-model="profileForm.avatar" placeholder="https://..." /></el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="profileLoading" @click="saveProfile">保存修改</el-button>
            </el-form-item>
          </el-form>
        </el-card>
        <el-card header="修改密码" style="margin-top:16px">
          <el-form label-width="100px">
            <el-form-item label="原密码"><el-input v-model="pwdForm.oldPassword" type="password" show-password /></el-form-item>
            <el-form-item label="新密码"><el-input v-model="pwdForm.newPassword" type="password" show-password /></el-form-item>
            <el-form-item label="确认密码"><el-input v-model="pwdForm.confirmPassword" type="password" show-password /></el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="pwdLoading" @click="savePassword">修改密码</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>
