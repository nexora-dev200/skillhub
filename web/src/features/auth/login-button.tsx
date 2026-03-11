import { useQuery } from '@tanstack/react-query'
import { authApi } from '@/api/client'
import { Button } from '@/shared/ui/button'
import type { OAuthProvider } from '@/api/types'

export function LoginButton() {
  const { data, isLoading } = useQuery<OAuthProvider[]>({
    queryKey: ['auth', 'providers'],
    queryFn: authApi.getProviders,
  })

  const providers = data ?? []

  if (isLoading) {
    return (
      <div className="space-y-3">
        <Button className="w-full" disabled>
          加载中...
        </Button>
      </div>
    )
  }

  return (
    <div className="space-y-3">
      {providers.map((provider) => (
        <Button
          key={provider.id}
          className="w-full"
          onClick={() => {
            window.location.href = provider.authorizationUrl
          }}
        >
          使用 {provider.name} 登录
        </Button>
      ))}
    </div>
  )
}
