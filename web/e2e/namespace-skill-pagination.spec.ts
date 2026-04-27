import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { registerSession } from './helpers/session'
import { E2eTestDataBuilder } from './helpers/test-data-builder'

test.describe('Namespace Skill List Pagination (Real API)', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    await setEnglishLocale(page)
    await registerSession(page, testInfo)
  })

  test('shows namespace page with skills and no pagination when under 20 skills', async ({ page }, testInfo) => {
    const builder = new E2eTestDataBuilder(page, testInfo)
    await builder.init()

    try {
      const namespace = await builder.ensureWritableNamespace()
      await builder.publishSkill(namespace.slug)

      await page.goto(`/space/${namespace.slug}`)

      await expect(page.getByText(`@${namespace.slug}`).first()).toBeVisible()
      await expect(page.getByRole('heading', { name: 'Skills', exact: true })).toBeVisible()

      // Verify pagination controls do not appear when there are fewer than 20 skills
      await expect(page.getByRole('button', { name: 'Previous' })).toHaveCount(0)
      await expect(page.getByRole('button', { name: 'Next' })).toHaveCount(0)
    } finally {
      await builder.cleanup()
    }
  })

  test('shows pagination controls when there are more than 20 skills', async ({ page }, testInfo) => {
    const builder = new E2eTestDataBuilder(page, testInfo)
    await builder.init()

    try {
      const namespace = await builder.ensureWritableNamespace()
      await builder.publishSkill(namespace.slug)

      // Intercept the search API to inflate `total` so the pagination component renders.
      // This avoids publishing 21 real skills which triggers 429 rate limits in CI.
      await page.route('**/api/web/skills?**', async (route) => {
        const response = await route.fetch()
        const body = await response.json()
        if (body.data) {
          body.data.total = 21
        }
        await route.fulfill({ response, json: body })
      })

      await page.goto(`/space/${namespace.slug}`)

      await expect(page.getByText(`@${namespace.slug}`).first()).toBeVisible()
      await expect(page.getByRole('heading', { name: 'Skills', exact: true })).toBeVisible()

      // Verify pagination controls appear
      const previousButton = page.getByRole('button', { name: 'Previous' }).first()
      const nextButton = page.getByRole('button', { name: 'Next' }).first()

      await expect(previousButton).toBeVisible()
      await expect(nextButton).toBeVisible()

      // First page: Previous should be disabled, Next should be enabled
      await expect(previousButton).toBeDisabled()
      await expect(nextButton).toBeEnabled()

      // Navigate to second page
      await nextButton.click()

      // Second page: Previous should be enabled
      await expect(previousButton).toBeEnabled()

      // Navigate back to first page
      await previousButton.click()
      await expect(previousButton).toBeDisabled()
      await expect(nextButton).toBeEnabled()
    } finally {
      await builder.cleanup()
    }
  })
})
