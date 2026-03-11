import { Outlet, Link } from '@tanstack/react-router'
import { useAuth } from '@/features/auth/use-auth'

export function Layout() {
  const { user, isLoading } = useAuth()

  return (
    <div className="min-h-screen bg-background">
      <header className="border-b">
        <div className="container mx-auto flex h-14 items-center justify-between px-4">
          <Link to="/" className="text-lg font-semibold hover:text-primary">
            SkillHub
          </Link>
          <nav className="flex items-center gap-4">
            {isLoading ? null : user ? (
              <>
                <Link
                  to="/dashboard"
                  className="text-sm hover:text-primary"
                  activeProps={{ className: 'text-primary font-medium' }}
                >
                  Dashboard
                </Link>
                <span className="text-sm text-muted-foreground">
                  {user.displayName}
                </span>
              </>
            ) : (
              <Link
                to="/login"
                className="text-sm hover:text-primary"
                activeProps={{ className: 'text-primary font-medium' }}
              >
                登录
              </Link>
            )}
          </nav>
        </div>
      </header>
      <main className="container mx-auto px-4 py-8">
        <Outlet />
      </main>
    </div>
  )
}
