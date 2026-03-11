import { useQuery } from '@tanstack/react-query'
import type { Namespace } from '@/api/types'
import createClient from 'openapi-fetch'
import type { paths } from '@/api/generated/schema'

const client = createClient<paths>({ baseUrl: '' })

export function useNamespaceDetail(slug: string) {
  return useQuery({
    queryKey: ['namespace', slug],
    queryFn: async () => {
      const { data, error, response } = await client.GET('/api/v1/namespaces/{slug}' as any, {
        params: {
          path: {
            slug,
          },
        },
      })
      if (error || !data) {
        throw new Error(`HTTP ${response.status}`)
      }
      return data as unknown as Namespace
    },
    enabled: !!slug,
  })
}
