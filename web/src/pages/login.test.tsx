/** @vitest-environment jsdom */
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach } from 'vitest'
import { describe, expect, it, vi } from 'vitest'

const authMethodsFixture = vi.hoisted(() => ({
  methods: [{ id: 'organization', methodType: 'ENTERPRISE_DISCOVERY' }],
  bootstrapEnabled: false,
  isError: false,
}))

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children }: { children: unknown }) => children,
  useNavigate: () => vi.fn(),
  useSearch: () => ({ returnTo: '' }),
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
      i18n: { resolvedLanguage: 'en' },
    }),
  }
})

vi.mock('lucide-react', () => ({
  ArrowRight: () => null,
  Eye: () => null,
  EyeOff: () => null,
  LockKeyhole: () => null,
  UserRound: () => null,
}))

vi.mock('@/api/client', () => ({
  getDirectAuthRuntimeConfig: () => ({ enabled: false }),
  getSessionBootstrapRuntimeConfig: () => ({ enabled: authMethodsFixture.bootstrapEnabled, provider: 'proxy' }),
}))

vi.mock('@/features/auth/auth-shell', () => ({
  AuthShell: ({ children }: { children: unknown }) => children,
}))

vi.mock('@/features/auth/enterprise-discovery-entry', () => ({
  EnterpriseDiscoveryEntry: () => <p>organization discovery</p>,
}))

vi.mock('@/features/auth/login-button', () => ({
  AuthMethodButtonList: () => null,
  LoginButton: () => null,
}))

vi.mock('@/features/auth/session-bootstrap-entry', () => ({
  SessionBootstrapEntry: () => <p>session bootstrap</p>,
}))

vi.mock('@/features/auth/use-auth-methods', () => ({
  useAuthMethods: () => ({ data: authMethodsFixture.methods, isError: authMethodsFixture.isError }),
}))

vi.mock('@/features/auth/use-password-login', () => ({
  usePasswordLogin: () => ({
    mutateAsync: vi.fn(),
    isPending: false,
    error: null,
  }),
}))

vi.mock('@/shared/ui/button', () => ({
  Button: ({ children, ...props }: React.ButtonHTMLAttributes<HTMLButtonElement>) => <button {...props}>{children}</button>,
}))

vi.mock('@/shared/ui/input', () => ({
  Input: (props: React.InputHTMLAttributes<HTMLInputElement>) => <input {...props} />,
}))

import { renderToStaticMarkup } from 'react-dom/server'
import { LoginPage } from './login'

describe('LoginPage', () => {
  afterEach(() => {
    cleanup()
    authMethodsFixture.methods = [{ id: 'organization', methodType: 'ENTERPRISE_DISCOVERY' }]
    authMethodsFixture.bootstrapEnabled = false
    authMethodsFixture.isError = false
  })

  it('exports a named component function', () => {
    expect(typeof LoginPage).toBe('function')
  })

  it('renders the login title and form elements', () => {
    const html = renderToStaticMarkup(<LoginPage />)

    expect(html).toContain('login.title')
    expect(html).toContain('login.subtitle')
    expect(html).toContain('login.submit')
    expect(html).toContain('login.tabPersonal')
    expect(html).toContain('login.tabEnterprise')
    expect(html).toContain('login.register')
  })

  it('replaces the password form with organization discovery and can switch back', () => {
    render(<LoginPage />)
    const personal = screen.getByRole('button', { name: 'login.tabPersonal' })
    const organization = screen.getByRole('button', { name: 'login.tabEnterprise' })

    expect(personal.getAttribute('aria-pressed')).toBe('true')
    expect(screen.getByLabelText('login.password').closest('[hidden]')).toBeNull()

    fireEvent.click(organization)
    expect(organization.getAttribute('aria-pressed')).toBe('true')
    expect(screen.getByLabelText('login.password').closest('[hidden]')).not.toBeNull()
    expect(screen.getByText('organization discovery').closest('[hidden]')).toBeNull()

    fireEvent.click(personal)
    expect(screen.getByLabelText('login.password').closest('[hidden]')).toBeNull()
    expect(screen.getByText('organization discovery').closest('[hidden]')).not.toBeNull()
  })

  it('does not show an organization entry when the server does not advertise one', () => {
    authMethodsFixture.methods = []
    render(<LoginPage />)

    expect(screen.queryByRole('button', { name: 'login.tabEnterprise' })).toBeNull()
    expect(screen.queryByRole('alert')).toBeNull()
    expect(screen.getByLabelText('login.password').closest('[hidden]')).toBeNull()
  })

  it('keeps password login available without a warning when the method catalog fails', () => {
    authMethodsFixture.methods = []
    authMethodsFixture.isError = true
    render(<LoginPage />)

    expect(screen.queryByRole('alert')).toBeNull()
    expect(screen.queryByRole('button', { name: 'login.tabEnterprise' })).toBeNull()
    expect(screen.getByRole('button', { name: 'login.submit' })).toBeTruthy()
  })

  it('hides an unusable session bootstrap entry when the web runtime is not configured', () => {
    authMethodsFixture.methods = [{ id: 'proxy', methodType: 'SESSION_BOOTSTRAP' }]
    render(<LoginPage />)

    expect(screen.queryByRole('button', { name: 'login.tabEnterprise' })).toBeNull()
    expect(screen.queryByText('session bootstrap')).toBeNull()
  })

  it('preserves configured session bootstrap when the method catalog is unavailable', () => {
    authMethodsFixture.methods = []
    authMethodsFixture.bootstrapEnabled = true
    authMethodsFixture.isError = true
    render(<LoginPage />)

    fireEvent.click(screen.getByRole('button', { name: 'login.tabEnterprise' }))
    expect(screen.getByText('session bootstrap').closest('[hidden]')).toBeNull()
  })
})
