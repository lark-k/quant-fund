<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, RouterLink } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const username = ref('')
const nickname = ref('')
const password = ref('')

async function submit() {
  await auth.register(username.value, password.value, nickname.value)
  router.push('/dashboard')
}
</script>

<template>
  <main class="auth-screen">
    <section class="auth-panel">
      <h1>注册 QuantFund</h1>
      <p>自用优先，可扩展多用户数据隔离。</p>
      <form class="auth-form" @submit.prevent="submit">
        <label>用户名<input v-model="username" class="form-control" /></label>
        <label>昵称<input v-model="nickname" class="form-control" /></label>
        <label>密码<input v-model="password" class="form-control" type="password" /></label>
        <button class="primary-button">注册并进入</button>
      </form>
      <RouterLink to="/login" class="muted-link">已有账号？登录</RouterLink>
    </section>
  </main>
</template>
