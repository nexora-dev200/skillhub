import { randomBytes } from 'node:crypto'
import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'

test.describe('Auth Entry', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('validates required fields and preserves returnTo on register link', async ({ page }) => {
    await page.goto('/login?returnTo=%2Fdashboard%2Ftokens')

    await expect(page.getByRole('heading', { name: 'Login to SkillHub' })).toBeVisible()

    await page.getByRole('button', { name: 'Login' }).click()
    await expect(page.getByText('Username is required')).toBeVisible()
    await expect(page.getByText('Password is required')).toBeVisible()

    await page.getByRole('link', { name: 'Sign up now' }).click()
    await expect(page).toHaveURL('/register?returnTo=%2Fdashboard%2Ftokens')

    await page.getByLabel('Email').fill('test@example.com')
    await page.getByRole('button', { name: 'Register & Login' }).click()
    await expect(page.getByText('Username is required')).toBeVisible()
    await expect(page.getByText('Password is required')).toBeVisible()

    await page.getByLabel('Email').fill('')
    await page.getByLabel('Email').blur()
    await expect(page.getByText('Email is required')).toBeVisible()
  })

  test('shows configured OAuth methods without exposing unsupported organization discovery', async ({ page }) => {
    await page.route('**/api/v1/auth/methods*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          msg: 'ok',
          data: [
            {
              id: 'github',
              methodType: 'OAUTH_REDIRECT',
              provider: 'github',
              displayName: 'GitHub',
              actionUrl: '/oauth2/authorization/github',
            },
            {
              id: 'enterprise-discovery',
              methodType: 'ENTERPRISE_DISCOVERY',
              provider: 'enterprise',
              displayName: 'Organization login',
              actionUrl: '/api/v1/auth/login-discovery',
            },
          ],
        }),
      })
    })

    await page.goto('/login')

    await expect(page.getByRole('button', { name: 'GitHub' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Organization login' })).toHaveCount(0)
  })

  test('keeps registration usable without configured OAuth methods', async ({ page }) => {
    await page.route('**/api/v1/auth/methods*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 0, msg: 'ok', data: [] }),
      })
    })

    await page.goto('/register')

    await expect(page.getByRole('button', { name: 'Register & Login' })).toBeVisible()
    await expect(page.getByText('Sign in directly with your existing OAuth account')).toHaveCount(0)
  })

  test('keeps configured session bootstrap available in the organization view', async ({ page }) => {
    await page.route('**/runtime-config.js', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/javascript',
        body: 'window.__SKILLHUB_RUNTIME_CONFIG__ = { authSessionBootstrapEnabled: "true", authSessionBootstrapProvider: "proxy", authSessionBootstrapAuto: "false" }',
      })
    })

    await page.goto('/login')
    await page.getByRole('button', { name: 'Organization login' }).click()

    await expect(page.getByRole('button', { name: 'Log in with work account' })).toBeVisible()
    await expect(page.getByLabel('Password', { exact: true })).toBeHidden()

    await page.getByRole('button', { name: 'Personal login' }).click()
    await expect(page.getByLabel('Password', { exact: true })).toBeVisible()
  })

  test('logs in to an explicit local destination on the isolated runtime', async ({ browser, page }) => {
    test.skip(process.env.SKILLHUB_RUNTIME_E2E !== '1', 'Requires an isolated runtime with local registration enabled')
    test.setTimeout(90_000)

    const suffix = randomBytes(6).toString('hex')
    const username = `pr901_${suffix}`
    const password = `${randomBytes(18).toString('base64url')}Aa1!`

    await page.goto('/register')
    await page.getByLabel('Username').fill(username)
    await page.getByLabel('Email').fill(`${username}@example.com`)
    await page.getByLabel('Password').fill(password)
    await page.getByRole('button', { name: 'Register & Login' }).click()
    await expect(page).toHaveURL('/')

    const loginPage = await browser.newPage()
    await setEnglishLocale(loginPage)
    await loginPage.goto('/login?returnTo=%2Fdashboard%2Ftokens')
    await expect.poll(() => loginPage.evaluate(async () => (await fetch('/api/v1/auth/me')).status)).toBe(401)

    await loginPage.getByLabel('Username').fill(username)
    await loginPage.getByLabel('Password', { exact: true }).fill(password)
    await loginPage.getByRole('button', { name: 'Login' }).click()
    await expect(loginPage).toHaveURL('/dashboard/tokens')
    await expect.poll(() => loginPage.evaluate(async () => (await fetch('/api/v1/auth/me')).status)).toBe(200)
    await loginPage.close()

    const defaultLoginPage = await browser.newPage()
    await setEnglishLocale(defaultLoginPage)
    await defaultLoginPage.goto('/login')
    await defaultLoginPage.getByLabel('Username').fill(username)
    await defaultLoginPage.getByLabel('Password', { exact: true }).fill(password)
    await defaultLoginPage.getByRole('button', { name: 'Login' }).click()
    await expect(defaultLoginPage).toHaveURL('/')
    await defaultLoginPage.close()
  })
})
