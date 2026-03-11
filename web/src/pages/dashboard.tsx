import { useAuth } from '@/features/auth/use-auth'
import { TokenList } from '@/features/token/token-list'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card'

export function DashboardPage() {
  const { user } = useAuth()

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold">Dashboard</h1>
        <p className="text-muted-foreground mt-1">
          管理你的账户和 API Tokens
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>用户信息</CardTitle>
          <CardDescription>你的账户详情</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex items-center gap-4">
            {user?.avatarUrl && (
              <img
                src={user.avatarUrl}
                alt={user.displayName}
                className="h-16 w-16 rounded-full"
              />
            )}
            <div className="space-y-1">
              <div className="text-lg font-semibold">{user?.displayName}</div>
              <div className="text-sm text-muted-foreground">{user?.email}</div>
              <div className="text-xs text-muted-foreground">
                通过 {user?.oauthProvider} 登录
              </div>
            </div>
          </div>
          {user?.platformRoles && user.platformRoles.length > 0 && (
            <div className="space-y-2">
              <div className="text-sm font-medium">平台角色</div>
              <div className="flex flex-wrap gap-2">
                {user.platformRoles.map((role) => (
                  <span
                    key={role}
                    className="inline-flex items-center rounded-md bg-primary/10 px-2 py-1 text-xs font-medium text-primary"
                  >
                    {role}
                  </span>
                ))}
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      <TokenList />
    </div>
  )
}
