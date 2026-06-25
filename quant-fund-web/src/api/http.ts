import axios from 'axios'
import { ElMessage } from 'element-plus'
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

function handleAuthExpired() {
  const auth = useAuthStore()
  auth.logout()
  void router.push('/login')
  ElMessage.warning('登录已过期，请重新登录')
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
      if (body.code === 0) return body.data
      if (body.code === 401) {
        handleAuthExpired()
      }
      throw new Error(body.message || '请求失败')
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
