import { expect, test } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { registerSession } from './helpers/session'
import { E2eTestDataBuilder } from './helpers/test-data-builder'

test.describe('Skill Subscription (Real API)', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    await setEnglishLocale(page)
    await registerSession(page, testInfo)
  })

  test('subscribe and unsubscribe to a skill', async ({ page }, testInfo) => {
    const builder = new E2eTestDataBuilder(page, testInfo)
    await builder.init()

    try {
      const namespace = await builder.ensureWritableNamespace()
      const skill = await builder.publishSkill(namespace.slug)

      await page.goto(`/space/${namespace.slug}/${skill.slug}`)

      await expect(page.getByRole('heading', { level: 1 })).toBeVisible()

      const subscribeButton = page.getByRole('button', { name: /Subscribe/ })
      await expect(subscribeButton).toBeVisible()

      const initialCount = await subscribeButton.textContent()
      const initialCountMatch = initialCount?.match(/\((\d+)\)/)
      const initialCountValue = initialCountMatch ? Number.parseInt(initialCountMatch[1], 10) : 0

      await subscribeButton.click()

      await expect(page.getByRole('button', { name: /Subscribed/ })).toBeVisible()

      const subscribedButton = page.getByRole('button', { name: /Subscribed/ })
      const subscribedCount = await subscribedButton.textContent()
      const subscribedCountMatch = subscribedCount?.match(/\((\d+)\)/)
      const subscribedCountValue = subscribedCountMatch ? Number.parseInt(subscribedCountMatch[1], 10) : 0

      expect(subscribedCountValue).toBe(initialCountValue + 1)

      await subscribedButton.click()

      await expect(page.getByRole('button', { name: /Subscribe/ })).toBeVisible()

      const unsubscribedButton = page.getByRole('button', { name: /Subscribe/ })
      const unsubscribedCount = await unsubscribedButton.textContent()
      const unsubscribedCountMatch = unsubscribedCount?.match(/\((\d+)\)/)
      const unsubscribedCountValue = unsubscribedCountMatch ? Number.parseInt(unsubscribedCountMatch[1], 10) : 0

      expect(unsubscribedCountValue).toBe(initialCountValue)
    } finally {
      await builder.cleanup()
    }
  })

  test('shows subscribed skill in My Subscriptions page', async ({ page }, testInfo) => {
    const builder = new E2eTestDataBuilder(page, testInfo)
    await builder.init()

    try {
      const namespace = await builder.ensureWritableNamespace()
      const skill = await builder.publishSkill(namespace.slug)

      await page.goto(`/space/${namespace.slug}/${skill.slug}`)

      const subscribeButton = page.getByRole('button', { name: /Subscribe/ })
      await expect(subscribeButton).toBeVisible()
      await subscribeButton.click()

      await expect(page.getByRole('button', { name: /Subscribed/ })).toBeVisible()

      await page.goto('/dashboard/subscriptions')

      await expect(page.getByRole('heading', { name: 'My Subscriptions' })).toBeVisible()
      await expect(page.getByText(`@${skill.namespace}`).first()).toBeVisible()
    } finally {
      await builder.cleanup()
    }
  })
})

