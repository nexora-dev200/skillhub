import { useQuery } from '@tanstack/react-query'
import type { SkillDetail } from '@/api/types'
import createClient from 'openapi-fetch'
import type { paths } from '@/api/generated/schema'

const client = createClient<paths>({ baseUrl: '' })

export function useSkillDetail(namespace: string, slug: string) {
  return useQuery({
    queryKey: ['skill', namespace, slug],
    queryFn: async () => {
      const { data, error, response } = await client.GET('/api/v1/skills/{namespace}/{slug}' as any, {
        params: {
          path: {
            namespace,
            slug,
          },
        },
      })
      if (error || !data) {
        throw new Error(`HTTP ${response.status}`)
      }
      return data as unknown as SkillDetail
    },
    enabled: !!namespace && !!slug,
  })
}
