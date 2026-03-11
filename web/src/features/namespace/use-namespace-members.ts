import { useQuery } from '@tanstack/react-query'
import type { NamespaceMember } from '@/api/types'
import createClient from 'openapi-fetch'
import type { paths } from '@/api/generated/schema'

const client = createClient<paths>({ baseUrl: '' })

export function useNamespaceMembers(slug: string) {
  return useQuery({
    queryKey: ['namespace-members', slug],
    queryFn: async () => {
      const { data, error, response } = await client.GET('/api/v1/namespaces/{slug}/members' as any, {
        params: {
          path: {
            slug,
          },
        },
      })
      if (error || !data) {
        throw new Error(`HTTP ${response.status}`)
      }
      return data as unknown as NamespaceMember[]
    },
    enabled: !!slug,
  })
}
