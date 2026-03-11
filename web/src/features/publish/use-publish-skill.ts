import { useMutation } from '@tanstack/react-query'
import type { PublishResult } from '@/api/types'
import createClient from 'openapi-fetch'
import type { paths } from '@/api/generated/schema'

const client = createClient<paths>({ baseUrl: '' })

function getCsrfToken(): string | null {
  const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]+)/)
  return match ? decodeURIComponent(match[1]) : null
}

interface PublishSkillParams {
  namespace: string
  file: File
}

export function usePublishSkill() {
  return useMutation({
    mutationFn: async ({ namespace, file }: PublishSkillParams) => {
      const formData = new FormData()
      formData.append('file', file)

      const csrfToken = getCsrfToken()
      const headers: HeadersInit = {
        ...(csrfToken && { 'X-XSRF-TOKEN': csrfToken }),
      }

      const { data, error, response } = await client.POST('/api/v1/skills/{namespace}/publish' as any, {
        params: {
          path: {
            namespace,
          },
        },
        body: formData as any,
        headers,
      })

      if (error || !data) {
        throw new Error(`HTTP ${response.status}`)
      }

      return data as unknown as PublishResult
    },
  })
}
