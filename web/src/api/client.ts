import createClient from 'openapi-fetch'
import type { paths } from './generated/schema'
import type { CreateTokenRequest, CreateTokenResponse, User } from './types'

const client = createClient<paths>({ baseUrl: '' })

function getCsrfToken(): string | null {
  const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]+)/)
  return match ? decodeURIComponent(match[1]) : null
}

function withCsrf(headers?: HeadersInit): HeadersInit {
  const csrfToken = getCsrfToken()
  if (!csrfToken) {
    return headers ?? {}
  }

  return {
    ...headers,
    'X-XSRF-TOKEN': csrfToken,
  }
}

async function unwrap<T>(promise: Promise<{ data?: T; error?: unknown; response: Response }>): Promise<T> {
  const { data, error, response } = await promise
  if (response.status === 401) {
    throw new Error('HTTP 401')
  }
  if (error) {
    throw new Error(`HTTP ${response.status}`)
  }
  if (data === undefined) {
    throw new Error(`HTTP ${response.status}`)
  }
  return data
}

export async function getCurrentUser(): Promise<User | null> {
  try {
    return await unwrap(client.GET('/api/v1/auth/me'))
  } catch (error) {
    if (error instanceof Error && error.message === 'HTTP 401') {
      return null
    }
    throw error
  }
}

export const authApi = {
  getMe: getCurrentUser,

  async getProviders() {
    const response = await unwrap(client.GET('/api/v1/auth/providers'))
    return response.data
  },

  async logout(): Promise<void> {
    const { response, error } = await client.POST('/api/v1/auth/logout', {
      headers: withCsrf(),
    })
    if (error || (response.status !== 200 && response.status !== 204)) {
      throw new Error(`HTTP ${response.status}`)
    }
  },
}

export const tokenApi = {
  async getTokens() {
    const response = await unwrap(client.GET('/api/v1/tokens'))
    return response.data
  },

  async createToken(request: CreateTokenRequest): Promise<CreateTokenResponse> {
    const response = await unwrap(client.POST('/api/v1/tokens', {
      headers: withCsrf({
        'Content-Type': 'application/json',
      }),
      body: request,
    }))
    return response.data
  },

  async deleteToken(tokenId: number): Promise<void> {
    const { response, error } = await client.DELETE('/api/v1/tokens/{id}', {
      params: {
        path: {
          id: tokenId,
        },
      },
      headers: withCsrf(),
    })
    if (error || response.status !== 204) {
      throw new Error(`HTTP ${response.status}`)
    }
  },
}
