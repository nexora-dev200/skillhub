import type {
  User,
  OAuthProvider,
  ApiToken,
  CreateTokenRequest,
  CreateTokenResponse,
  ApiResponse,
} from './types'

// 基础 fetch 封装
async function fetchJson<T>(url: string, options?: RequestInit): Promise<T> {
  const res = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
  })

  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw new Error(error.message || `HTTP ${res.status}`)
  }

  return res.json()
}

// Auth API
export const authApi = {
  // 获取当前用户信息
  async getMe(): Promise<User | null> {
    try {
      return await fetchJson<User>('/api/v1/auth/me')
    } catch (error) {
      // 401 表示未登录，返回 null
      if (error instanceof Error && error.message.includes('401')) {
        return null
      }
      throw error
    }
  },

  // 获取可用的 OAuth 提供商
  async getProviders(): Promise<OAuthProvider[]> {
    const response = await fetchJson<ApiResponse<OAuthProvider[]>>('/api/v1/auth/providers')
    return response.data
  },

  // 登出
  async logout(): Promise<void> {
    await fetch('/api/v1/auth/logout', { method: 'POST' })
  },
}

// Token API
export const tokenApi = {
  // 获取所有 Token
  async getTokens(): Promise<ApiToken[]> {
    const response = await fetchJson<ApiResponse<ApiToken[]>>('/api/v1/tokens')
    return response.data
  },

  // 创建新 Token
  async createToken(request: CreateTokenRequest): Promise<CreateTokenResponse> {
    const response = await fetchJson<ApiResponse<CreateTokenResponse>>('/api/v1/tokens', {
      method: 'POST',
      body: JSON.stringify(request),
    })
    return response.data
  },

  // 删除 Token
  async deleteToken(tokenId: number): Promise<void> {
    await fetch(`/api/v1/tokens/${tokenId}`, {
      method: 'DELETE',
    })
  },
}
