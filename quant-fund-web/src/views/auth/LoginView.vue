<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, RouterLink } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const username = ref('zhangming')
const password = ref('QuantFund2026')
const loading = ref(false)

async function submit() {
  loading.value = true
  try {
    await auth.login(username.value, password.value)
    ElMessage.success('欢迎回到 QuantFund')
    router.push('/dashboard')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="auth-screen">
    <section class="auth-panel">
      <div class="brand auth-brand">
        <div class="brand-mark">Q</div>
        <div class="brand-text"><strong>QuantFund</strong><span>AI Fund Quant Dashboard</span></div>
      </div>
      <h1>登录基金量化驾驶舱</h1>
      <p>盘中估值、策略信号和 AI 建议均为辅助参考。</p>
      <form class="auth-form" @submit.prevent="submit">
        <label>用户名<input v-model="username" class="form-control" autocomplete="username" /></label>
        <label>密码<input v-model="password" class="form-control" type="password" autocomplete="current-password" /></label>
        <button class="primary-button" :disabled="loading">{{ loading ? '登录中...' : '登录' }}</button>
      </form>
      <RouterLink to="/register" class="muted-link">没有账号？注册</RouterLink>
    </section>
  </main>
</template>
