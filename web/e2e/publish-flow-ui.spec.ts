import { expect, test } from '@playwright/test'
import path from 'node:path'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { registerSession } from './helpers/session'
import { E2eTestDataBuilder } from './helpers/test-data-builder'

test.describe('Publish Flow UI (Real API)', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    await setEnglishLocale(page)
    await registerSession(page, testInfo)
  })

  test('publishes a generated skill package from dashboard page', async ({ page }, testInfo) => {
    const builder = new E2eTestDataBuilder(page, testInfo)
    await builder.init()

    try {
      const namespace = await builder.ensureWritableNamespace()
      const packagePath = builder.createSkillPackageFile()

      await page.goto('/dashboard/publish')
      await expect(page.getByRole('heading', { name: 'Publish Skill' })).toBeVisible()

      const namespaceTrigger = page.locator('#namespace')
      await expect(namespaceTrigger).toBeVisible()
      await namespaceTrigger.click()
      const namespaceOption = page.getByRole('option', {
        name: new RegExp(`\\(@${namespace.slug}\\)`),
      }).first()
      await expect(namespaceOption).toBeVisible()
      await namespaceOption.evaluate((element: HTMLElement) => {
        element.scrollIntoView({ block: 'center' })
        element.click()
      })
      await expect(namespaceTrigger).toContainText(`@${namespace.slug}`)

      await page.locator('input[type="file"]').setInputFiles(packagePath)
      await expect(page.getByText(path.basename(packagePath))).toBeVisible()
      const confirmButton = page.getByRole('button', { name: 'Confirm Publish' })
      await expect(confirmButton).toBeEnabled()
      await Promise.all([
        page.waitForURL('**/dashboard/skills'),
        confirmButton.click(),
      ])

      await expect(page).toHaveURL('/dashboard/skills')
      await expect(page.getByRole('heading', { name: 'My Skills' })).toBeVisible()
    } finally {
      await builder.cleanup()
    }
  })
})
