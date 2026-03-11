import { useQuery } from '@tanstack/react-query'
import type { SkillFile } from '@/api/types'
import createClient from 'openapi-fetch'
import type { paths } from '@/api/generated/schema'

const client = createClient<paths>({ baseUrl: '' })

export function useSkillFiles(namespace: string, slug: string, version: string) {
  return useQuery({
    queryKey: ['skill-files', namespace, slug, version],
    queryFn: async () => {
      const { data, error, response } = await client.GET('/api/v1/skills/{namespace}/{slug}/versions/{version}/files' as any, {
        params: {
          path: {
            namespace,
            slug,
            version,
          },
        },
      })
      if (error || !data) {
        throw new Error(`HTTP ${response.status}`)
      }
      return data as unknown as SkillFile[]
    },
    enabled: !!namespace && !!slug && !!version,
  })
}
