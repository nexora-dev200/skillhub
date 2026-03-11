import { useQuery } from '@tanstack/react-query'
import type { SkillVersion } from '@/api/types'
import createClient from 'openapi-fetch'
import type { paths } from '@/api/generated/schema'

const client = createClient<paths>({ baseUrl: '' })

export function useSkillVersions(namespace: string, slug: string) {
  return useQuery({
    queryKey: ['skill-versions', namespace, slug],
    queryFn: async () => {
      const { data, error, response } = await client.GET('/api/v1/skills/{namespace}/{slug}/versions' as any, {
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
      return data as unknown as SkillVersion[]
    },
    enabled: !!namespace && !!slug,
  })
}
