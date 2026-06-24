import { defineStore } from 'pinia'
import { authApi } from '@/api/auth'
import type { PasswordUpdateRequest, ProfileUpdateRequest, UserProfile } from '@/types/domain'

interface AuthState {
  token: string
  tokenName: string
  user: UserProfile | null
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: '',
    tokenName: 'Authorization',
    user: null
  }),
  getters: {
    isLoggedIn: (state) => Boolean(state.token),
    nickname: (state) => state.user?.nickname || 'Quant User'
  },
  actions: {
    async login(username: string, password: string) {
      const result = await authApi.login(username, password)
      this.token = result.token
      this.tokenName = result.tokenName || 'Authorization'
      this.user = result.user
    },
    async register(username: string, password: string, nickname: string) {
      const result = await authApi.register(username, password, nickname)
      this.token = result.token
      this.tokenName = result.tokenName || 'Authorization'
      this.user = result.user
    },
    async refreshProfile() {
      if (!this.token) return
      this.user = await authApi.me()
    },
    async updateProfile(request: ProfileUpdateRequest) {
      this.user = await authApi.updateProfile(request)
    },
    async updatePassword(request: PasswordUpdateRequest) {
      await authApi.updatePassword(request)
    },
    logout() {
      this.token = ''
      this.tokenName = 'Authorization'
      this.user = null
    }
  },
  persist: true
})
