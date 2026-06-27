import axios from 'axios'
import { ElMessage } from 'element-plus'
import type { MessageHandler } from 'element-plus'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'

declare module 'axios' {
  export interface AxiosRequestConfig {
    suppressErrorMessage?: boolean
  }
}

export const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 12000
})

let authExpiredHandled = false
let authExpiredMessage: MessageHandler | null = null

function isAuthRequest(url?: string) {
  return Boolean(url?.includes('/auth/login') || url?.includes('/auth/register'))
}

function resetAuthExpiredState() {
  authExpiredHandled = false
  authExpiredMessage?.close()
  authExpiredMessage = null
}

function handleAuthExpired() {
  if (authExpiredHandled) return
  authExpiredHandled = true
  const auth = useAuthStore()
  auth.logout()
  if (router.currentRoute.value.path !== '/login') {
    void router.replace('/login')
  }
  authExpiredMessage?.close()
  authExpiredMessage = ElMessage.warning({
    message: '登录已过期，请重新登录',
    grouping: true,
    duration: 3000
  })
}

http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers[auth.tokenName || 'Authorization'] = auth.token
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body.code === 'number') {
      if (body.code === 0) {
        if (isAuthRequest(response.config.url)) {
          resetAuthExpiredState()
        }
        return body.data
      }
      if (body.code === 401) {
        handleAuthExpired()
      }
      throw new Error(body.message || '请求失败')
    }
    if (isAuthRequest(response.config.url)) {
      resetAuthExpiredState()
    }
    return body
  },
  (error) => {
    if (error.response?.status === 401) {
      handleAuthExpired()
      return Promise.reject(error)
    }
    if (!error.config?.suppressErrorMessage) {
      ElMessage.error(error.message || '????')
    }
    return Promise.reject(error)
  }
)
