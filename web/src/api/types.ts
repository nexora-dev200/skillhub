// API 类型定义

export interface User {
  userId: number
  displayName: string
  email: string
  avatarUrl: string
  oauthProvider: string
  platformRoles: string[]
}

export interface OAuthProvider {
  id: string
  name: string
  authorizationUrl: string
}

export interface ApiToken {
  tokenId: number
  name: string
  tokenPrefix: string
  createdAt: string
  lastUsedAt: string | null
  expiresAt: string | null
}

export interface CreateTokenRequest {
  name: string
  expiresInDays?: number
}

export interface CreateTokenResponse {
  token: string
  tokenId: number
  name: string
  tokenPrefix: string
  createdAt: string
  expiresAt: string | null
}

export interface ApiResponse<T> {
  data: T
  message?: string
}

export interface ApiError {
  error: string
  message: string
  path: string
  timestamp: string
  requestId: string
}
