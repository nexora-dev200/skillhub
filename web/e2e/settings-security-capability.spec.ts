import { expect, test, type Page } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'

interface MockSessionUser {
  userId: string
  displayName: string
  email: string
  avatarUrl: string
  oauthProvider: string
  canChangePassword: boolean
  platformRoles: string[]
}

function apiEnvelope(data: unknown) {
  return {
    code: 0,
    msg: 'OK',
    data,
    timestamp: new Date().toISOString(),
    requestId: 'e2e-security-capability',
  }
}

async function mockSession(page: Page, user: MockSessionUser) {
  await page.route('**/api/v1/auth/me', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(apiEnvelope(user)),
    })
  })

  await page.route('**/api/web/me/namespaces', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(apiEnvelope([])),
    })
  })

  await page.route('**/api/web/notifications/unread-count', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(apiEnvelope({ count: 0 })),
    })
  })

  await page.route('**/api/web/notifications/sse', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'text/event-stream',
      body: ': ok\n\n',
    })
  })
}

test.describe('Security Settings capability', () => {
  test('shows the security menu entry and password form for local admin accounts', async ({ page }) => {
    await setEnglishLocale(page)
    await mockSession(page, {
      userId: 'local-admin',
      displayName: 'Local Admin',
      email: 'local-admin@example.test',
      avatarUrl: '',
      oauthProvider: '',
      canChangePassword: true,
      platformRoles: ['USER', 'SUPER_ADMIN'],
    })

    await page.goto('/settings/security')
    await expect(page.getByRole('heading', { name: 'Security Settings' })).toBeVisible()
    await expect(page.getByLabel('Current Password')).toBeVisible()
    await expect(page.getByLabel('New Password')).toBeVisible()

    await page.getByRole('button', { name: 'Local Admin' }).click()
    await expect(page.getByRole('link', { name: 'Security Settings' })).toBeVisible()
  })

  test('hides the security menu entry and form when password changes are unavailable', async ({ page }) => {
    await setEnglishLocale(page)
    await mockSession(page, {
      userId: 'oauth-only-user',
      displayName: 'OAuth Only User',
      email: 'oauth-only@example.test',
      avatarUrl: '',
      oauthProvider: 'github',
      canChangePassword: false,
      platformRoles: ['USER'],
    })

    await page.goto('/settings/security')

    await expect(page.getByRole('heading', { name: 'Security Settings' })).toBeVisible()
    await expect(page.getByText('Password changes are unavailable for this account.')).toBeVisible()
    await expect(page.getByLabel('Current Password')).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'Update Password' })).toHaveCount(0)

    await page.getByRole('button', { name: 'OAuth Only User' }).click()
    await expect(page.getByRole('link', { name: 'Security Settings' })).toHaveCount(0)
  })
})
