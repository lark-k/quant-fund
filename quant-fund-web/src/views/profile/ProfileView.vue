<script setup lang="ts">
import { reactive, ref, watchEffect } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const savingProfile = ref(false)
const savingPassword = ref(false)

const profileForm = reactive({
  username: '',
  nickname: '',
  phone: '',
  email: '',
  role: 'USER',
  riskLevel: 'MEDIUM'
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: ''
})

watchEffect(() => {
  if (!auth.user) return
  profileForm.username = auth.user.username
  profileForm.nickname = auth.user.nickname
  profileForm.phone = auth.user.phone || ''
  profileForm.email = auth.user.email || ''
  profileForm.role = auth.user.role
  profileForm.riskLevel = auth.user.riskLevel
})

async function saveProfile() {
  savingProfile.value = true
  try {
    await auth.updateProfile({
      nickname: profileForm.nickname,
      phone: profileForm.phone || undefined,
      email: profileForm.email || undefined
    })
    ElMessage.success('个人资料已保存')
  } finally {
    savingProfile.value = false
  }
}

async function updatePassword() {
  if (!passwordForm.oldPassword || !passwordForm.newPassword) {
    ElMessage.warning('请填写旧密码和新密码')
    return
  }
  savingPassword.value = true
  try {
    await auth.updatePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    ElMessage.success('密码已更新，请妥善保管新密码')
  } finally {
    savingPassword.value = false
  }
}
</script>

<template>
  <div class="screen-grid">
    <section class="panel">
      <div class="panel-header">
        <h2 class="panel-title">个人资料</h2>
        <button class="primary-button" :disabled="savingProfile" @click="saveProfile">
          {{ savingProfile ? '保存中' : '保存资料' }}
        </button>
      </div>
      <div class="panel-body config-grid">
        <label>用户名<input v-model="profileForm.username" class="form-control" disabled /></label>
        <label>昵称<input v-model="profileForm.nickname" class="form-control" maxlength="64" /></label>
        <label>手机号<input v-model="profileForm.phone" class="form-control" maxlength="32" /></label>
        <label>邮箱<input v-model="profileForm.email" class="form-control" maxlength="128" /></label>
        <label>角色<input v-model="profileForm.role" class="form-control" disabled /></label>
        <label>风险等级<input v-model="profileForm.riskLevel" class="form-control" disabled /></label>
      </div>
    </section>

    <section class="panel">
      <div class="panel-header">
        <h2 class="panel-title">修改密码</h2>
      </div>
      <div class="panel-body config-grid">
        <input v-model="passwordForm.oldPassword" class="form-control" type="password" placeholder="旧密码" autocomplete="current-password" />
        <input v-model="passwordForm.newPassword" class="form-control" type="password" placeholder="新密码，至少 8 位且包含字母和数字" autocomplete="new-password" />
        <button class="primary-button" :disabled="savingPassword" @click="updatePassword">
          {{ savingPassword ? '更新中' : '更新密码' }}
        </button>
      </div>
    </section>
  </div>
</template>
