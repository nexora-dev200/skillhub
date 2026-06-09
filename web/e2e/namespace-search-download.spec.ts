import { expect, test, type Page } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'

const namespaceSlug = 'product-managers'

const skillFixtures = [
  {
    id: 7101,
    slug: 'roadmap-agent',
    displayName: 'Roadmap Agent',
    summary: 'Turns product strategy into roadmap drafts.',
    downloadCount: 12,
    starCount: 3,
    ratingAvg: 4.8,
    ratingCount: 4,
    namespace: namespaceSlug,
    updatedAt: '2026-06-01T00:00:00Z',
    canSubmitPromotion: false,
    headlineVersion: { id: 8101, version: '1.0.0', status: 'PUBLISHED' },
    publishedVersion: { id: 8101, version: '1.0.0', status: 'PUBLISHED' },
  },
  {
    id: 7102,
    slug: 'requirements-agent',
    displayName: 'Requirements Agent',
    summary: 'Helps product managers refine user stories.',
    downloadCount: 8,
    starCount: 2,
    ratingAvg: 4.5,
    ratingCount: 2,
    namespace: namespaceSlug,
    updatedAt: '2026-06-02T00:00:00Z',
    canSubmitPromotion: false,
    headlineVersion: { id: 8102, version: '1.1.0', status: 'PUBLISHED' },
    publishedVersion: { id: 8102, version: '1.1.0', status: 'PUBLISHED' },
  },
  {
    id: 7201,
    slug: 'backend-agent',
    displayName: 'Backend Agent',
    summary: 'A skill outside the selected namespace.',
    downloadCount: 20,
    starCount: 6,
    ratingAvg: 4.2,
    ratingCount: 5,
    namespace: 'developers',
    updatedAt: '2026-06-03T00:00:00Z',
    canSubmitPromotion: false,
    headlineVersion: { id: 8201, version: '2.0.0', status: 'PUBLISHED' },
    publishedVersion: { id: 8201, version: '2.0.0', status: 'PUBLISHED' },
  },
]

function envelope(data: unknown, code = 0, msg = 'success') {
  return JSON.stringify({
    code,
    msg,
    data,
    timestamp: '2026-06-04T00:00:00Z',
    requestId: 'e2e-namespace-search-download',
  })
}

async function mockCommonApi(page: Page, options?: { authenticated?: boolean }) {
  await page.route('**/api/v1/auth/me', async (route) => {
    if (options?.authenticated) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: envelope({
          userId: 'e2e-product-manager',
          displayName: 'E2E Product Manager',
          email: 'pm@example.com',
          platformRoles: [],
        }),
      })
      return
    }

    await route.fulfill({
      status: 401,
      contentType: 'application/json',
      body: envelope(null, 401, 'Unauthorized'),
    })
  })
  await page.route('**/api/v1/auth/providers**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: envelope([]),
    })
  })
  await page.route('**/api/v1/auth/methods**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: envelope([]),
    })
  })
  await page.route('**/api/web/labels', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: envelope([]),
    })
  })
  await page.route('**/api/web/me/namespaces', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: envelope([]),
    })
  })
  await page.route('**/api/web/notifications/unread-count', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: envelope({ count: 0 }),
    })
  })
  await page.route('**/api/web/notifications/sse', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'text/event-stream',
      body: '',
    })
  })
  await page.route(/\/api\/web\/skills\/\d+\/star$/, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: envelope(false),
    })
  })
}

async function mockSearchApi(page: Page) {
  const requests: URL[] = []

  await page.route(/\/api\/web\/skills\?/, async (route) => {
    const url = new URL(route.request().url())
    requests.push(url)

    const q = (url.searchParams.get('q') ?? '').trim().toLowerCase()
    const namespace = (url.searchParams.get('namespace') ?? '').trim().toLowerCase()
    const pageNumber = Number(url.searchParams.get('page') ?? '0')
    const pageSize = Number(url.searchParams.get('size') ?? '12')
    const items = skillFixtures.filter((skill) => {
      const matchesNamespace = !namespace || skill.namespace === namespace
      const matchesQuery = !q
        || skill.displayName.toLowerCase().includes(q)
        || skill.summary.toLowerCase().includes(q)
        || skill.slug.toLowerCase().includes(q)
      return matchesNamespace && matchesQuery
    })

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: envelope({
        items,
        total: items.length,
        page: pageNumber,
        size: pageSize,
      }),
    })
  })

  return requests
}

async function mockNamespaceApi(page: Page) {
  await page.route(`**/api/web/namespaces/${namespaceSlug}`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: envelope({
        id: 5101,
        slug: namespaceSlug,
        displayName: 'Product Managers',
        description: 'Skills curated for product and requirements work.',
        type: 'TEAM',
        status: 'ACTIVE',
        createdAt: '2026-06-01T00:00:00Z',
        updatedAt: '2026-06-02T00:00:00Z',
      }),
    })
  })

  await page.route(`**/api/web/namespaces/${namespaceSlug}/skills/download**`, async (route) => {
    await route.fulfill({
      status: 200,
      headers: {
        'Content-Type': 'application/zip',
        'Content-Disposition': `attachment; filename="${namespaceSlug}-skills.zip"`,
      },
      body: 'PK',
    })
  })
}

test.describe('Namespace Search and Download', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('submits @namespace search input as separate namespace and keyword URL parameters', async ({ page }) => {
    await mockCommonApi(page)
    const requests = await mockSearchApi(page)

    await page.goto('/search')
    await page.getByPlaceholder('Search skills...').fill(`@${namespaceSlug} roadmap`)
    await page.getByRole('button', { name: 'Search', exact: true }).click()

    await expect(page).toHaveURL(new RegExp(`namespace=${namespaceSlug}`))
    await expect(page).toHaveURL(/q=roadmap/)
    await expect(page.getByRole('button', { name: `@${namespaceSlug}` })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Roadmap Agent' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Backend Agent' })).toHaveCount(0)
    await expect.poll(() => requests.some((url) =>
      url.searchParams.get('namespace') === namespaceSlug
      && url.searchParams.get('q') === 'roadmap',
    )).toBe(true)
  })

  test('clears the namespace filter while preserving the keyword and sort mode', async ({ page }) => {
    await mockCommonApi(page)
    const requests = await mockSearchApi(page)

    await page.goto(`/search?q=roadmap&namespace=${namespaceSlug}&sort=downloads&page=1&starredOnly=false`)
    await page.getByRole('button', { name: `@${namespaceSlug}` }).click()

    await expect(page).toHaveURL(/q=roadmap/)
    await expect(page).toHaveURL(/sort=downloads/)
    await expect(page).toHaveURL(/page=0/)
    await expect(page).not.toHaveURL(new RegExp(`namespace=${namespaceSlug}`))
    await expect.poll(() => requests.some((url) =>
      url.searchParams.get('q') === 'roadmap'
      && !url.searchParams.has('namespace')
      && url.searchParams.get('sort') === 'downloads',
    )).toBe(true)
  })

  test('copies the current page install manifest and gates selected download until a skill is checked', async ({ page, context }) => {
    await context.grantPermissions(['clipboard-read', 'clipboard-write'])
    await mockCommonApi(page, { authenticated: true })
    await mockSearchApi(page)
    await mockNamespaceApi(page)

    await page.goto(`/space/${namespaceSlug}`)

    const selectedDownloadButton = page.getByRole('button', { name: 'Download selected on this page' })
    await expect(selectedDownloadButton).toBeDisabled()

    await page.getByLabel('Select Roadmap Agent').check()
    await expect(selectedDownloadButton).toBeEnabled()

    await page.getByRole('button', { name: 'Copy current page install list' }).click()
    const clipboardText = await page.evaluate(() => navigator.clipboard.readText())

    expect(clipboardText).toContain(`skillhub install ${namespaceSlug}--roadmap-agent`)
    expect(clipboardText).toContain(`skillhub install ${namespaceSlug}--requirements-agent`)
  })

  test('downloads only selected namespace skills with skill query parameters', async ({ page }) => {
    await mockCommonApi(page, { authenticated: true })
    await mockSearchApi(page)
    await mockNamespaceApi(page)

    await page.goto(`/space/${namespaceSlug}`)
    await page.getByLabel('Select Roadmap Agent').check()

    await page.getByRole('button', { name: 'Download selected on this page' }).click()
    await expect(page.getByRole('dialog', { name: 'Confirm namespace download' })).toBeVisible()
    await expect(page.getByText('This will request up to 1 skill package from @product-managers.')).toBeVisible()
    await expect(page.getByText('Unavailable skills may be skipped. Synchronous downloads are limited to 20 skills and 100 MB.')).toBeVisible()

    const [request, response] = await Promise.all([
      page.waitForRequest((request) => request.url().includes(`/api/web/namespaces/${namespaceSlug}/skills/download`)),
      page.waitForResponse((response) => response.url().includes(`/api/web/namespaces/${namespaceSlug}/skills/download`)),
      page.getByRole('button', { name: 'Download', exact: true }).click(),
    ])

    const downloadUrl = new URL(request.url())
    expect(response.headers()['content-disposition']).toContain(`${namespaceSlug}-skills.zip`)
    expect(downloadUrl.searchParams.getAll('skill')).toEqual(['roadmap-agent'])
  })

  test('downloads the full namespace bundle without skill query parameters', async ({ page }) => {
    await mockCommonApi(page, { authenticated: true })
    await mockSearchApi(page)
    await mockNamespaceApi(page)

    await page.goto(`/space/${namespaceSlug}`)

    await page.getByRole('button', { name: 'Download all' }).click()
    await expect(page.getByRole('dialog', { name: 'Confirm namespace download' })).toBeVisible()
    await expect(page.getByText('This will request up to 2 skill packages from @product-managers.')).toBeVisible()
    await expect(page.getByText('Unavailable skills may be skipped. Synchronous downloads are limited to 20 skills and 100 MB.')).toBeVisible()

    const [request, response] = await Promise.all([
      page.waitForRequest((request) => request.url().includes(`/api/web/namespaces/${namespaceSlug}/skills/download`)),
      page.waitForResponse((response) => response.url().includes(`/api/web/namespaces/${namespaceSlug}/skills/download`)),
      page.getByRole('button', { name: 'Download', exact: true }).click(),
    ])

    const downloadUrl = new URL(request.url())
    expect(response.headers()['content-disposition']).toContain(`${namespaceSlug}-skills.zip`)
    expect(downloadUrl.searchParams.getAll('skill')).toEqual([])
  })
})
