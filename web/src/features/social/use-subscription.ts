import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ApiError, fetchJson, getCsrfHeaders, WEB_API_PREFIX } from '@/api/client'

interface SubscriptionStatus {
  subscribed: boolean
}

async function getSubscriptionStatus(skillId: number): Promise<SubscriptionStatus> {
  try {
    const subscribed = await fetchJson<boolean>(`${WEB_API_PREFIX}/skills/${skillId}/subscription`)
    return { subscribed }
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return { subscribed: false }
    }
    throw error
  }
}

async function toggleSubscription(skillId: number, subscribed: boolean): Promise<void> {
  if (subscribed) {
    await fetchJson<void>(`${WEB_API_PREFIX}/skills/${skillId}/subscription`, {
      method: 'DELETE',
      headers: getCsrfHeaders(),
    })
  } else {
    await fetchJson<void>(`${WEB_API_PREFIX}/skills/${skillId}/subscription`, {
      method: 'PUT',
      headers: getCsrfHeaders(),
    })
  }
}

export function useSubscription(skillId: number, enabled = true) {
  return useQuery({
    queryKey: ['skills', skillId, 'subscription'],
    queryFn: () => getSubscriptionStatus(skillId),
    enabled: !!skillId && enabled,
  })
}

export function useToggleSubscription(skillId: number) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (subscribed: boolean) => toggleSubscription(skillId, subscribed),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['skills', skillId, 'subscription'] })
      queryClient.invalidateQueries({ queryKey: ['skills'] })
      queryClient.invalidateQueries({ queryKey: ['skills', 'subscriptions'] })
    },
  })
}
