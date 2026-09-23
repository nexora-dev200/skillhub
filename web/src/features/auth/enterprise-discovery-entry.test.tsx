/** @vitest-environment jsdom */
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { EnterpriseDiscoveryEntry } from './enterprise-discovery-entry'

const discoverEnterpriseLogin = vi.hoisted(() => vi.fn())

vi.mock('@/api/client', () => ({
  authApi: { discoverEnterpriseLogin },
}))

function renderEntry() {
  const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <EnterpriseDiscoveryEntry returnTo="/dashboard" />
    </QueryClientProvider>,
  )
}

describe('EnterpriseDiscoveryEntry', () => {
  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('shows the organization identifier field without another expansion step', () => {
    renderEntry()

    expect(screen.getByPlaceholderText('login.organizationIdentifier')).toBeTruthy()
  })

  it('uses server discovery and displays only returned organization login options', async () => {
    discoverEnterpriseLogin.mockResolvedValue({
      publicMethods: [],
      organizations: [{
        slug: 'team',
        displayName: 'Team',
        loginOptions: [{ displayName: 'Company OIDC', methodType: 'ENTERPRISE_REDIRECT', actionUrl: '/api/v1/auth/enterprise/test/start' }],
      }],
    })
    renderEntry()

    fireEvent.change(screen.getByPlaceholderText('login.organizationIdentifier'), { target: { value: 'team' } })
    fireEvent.click(screen.getByRole('button', { name: 'login.organizationContinue' }))

    await waitFor(() => expect(screen.getByText('Company OIDC')).toBeTruthy())
    expect(discoverEnterpriseLogin).toHaveBeenCalledWith('team', '/dashboard')
  })

  it('shows a neutral result when the server has no available organization method', async () => {
    discoverEnterpriseLogin.mockResolvedValue({ publicMethods: [], organizations: [] })
    renderEntry()

    fireEvent.change(screen.getByPlaceholderText('login.organizationIdentifier'), { target: { value: 'unknown' } })
    fireEvent.click(screen.getByRole('button', { name: 'login.organizationContinue' }))

    await waitFor(() => expect(screen.getByText('login.organizationNotFound')).toBeTruthy())
  })
})
