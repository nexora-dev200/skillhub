import { LoginButton } from '@/features/auth/login-button'

export function LoginPage() {
  return (
    <div className="flex min-h-[60vh] items-center justify-center">
      <div className="w-full max-w-sm space-y-6">
        <div className="space-y-2 text-center">
          <h1 className="text-3xl font-bold">登录 SkillHub</h1>
          <p className="text-muted-foreground">
            选择一个方式登录以继续
          </p>
        </div>
        <LoginButton />
      </div>
    </div>
  )
}
