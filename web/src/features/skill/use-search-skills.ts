import { useQuery } from '@tanstack/react-query'
import type { SearchParams, PagedResponse, SkillSummary } from '@/api/types'
import createClient from 'openapi-fetch'
import type { paths } from '@/api/generated/schema'

const client = createClient<paths>({ baseUrl: '' })

export function useSearchSkills(params: SearchParams) {
  return useQuery({
    queryKey: ['skills', params],
    queryFn: async () => {
      const { data, error, response } = await client.GET('/api/v1/skills' as any, {
        params: {
          query: {
            q: params.q,
            namespace: params.namespace,
            sort: params.sort,
            page: params.page,
            size: params.size,
          },
        },
      })
      if (error || !data) {
        throw new Error(`HTTP ${response.status}`)
      }
      return data as unknown as PagedResponse<SkillSummary>
    },
  })
}
