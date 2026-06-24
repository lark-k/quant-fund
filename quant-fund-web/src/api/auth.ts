import { http, USE_MOCK } from './http'
import { mockApi } from './mock'
import type { LoginResult, PasswordUpdateRequest, ProfileUpdateRequest, UserProfile } from '@/types/domain'

type BackendLoginResult = LoginResult & {
  tokenName?: string
  tokenValue?: string
  tokenTimeout?: number
}

function normalizeLoginResult(result: BackendLoginResult): LoginResult {
  return {
    token: result.token || result.tokenValue || '',
    tokenName: result.tokenName || 'Authorization',
    tokenTimeout: result.tokenTimeout,
    user: result.user
  }
}

export const authApi = {
  async login(username: string, password: string): Promise<LoginResult> {
    const result = USE_MOCK
      ? await mockApi.login(username, password)
      : await http.post('/auth/login', { username, password }) as BackendLoginResult
    return normalizeLoginResult(result)
  },
  async register(username: string, password: string, nickname: string): Promise<LoginResult> {
    const result = USE_MOCK
      ? await mockApi.register(username, password, nickname)
      : await http.post('/auth/register', { username, password, nickname }) as BackendLoginResult
    return normalizeLoginResult(result)
  },
  me(): Promise<UserProfile> {
    return USE_MOCK ? mockApi.me() : http.get('/auth/me')
  },
  updateProfile(request: ProfileUpdateRequest): Promise<UserProfile> {
    return USE_MOCK ? mockApi.updateProfile(request) : http.put('/auth/profile', request)
  },
  updatePassword(request: PasswordUpdateRequest): Promise<void> {
    return USE_MOCK ? mockApi.updatePassword(request) : http.put('/auth/password', request)
  }
}
