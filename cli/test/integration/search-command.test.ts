import { afterEach, describe, expect, test } from 'bun:test'
import { startFakeRegistry } from '../helpers/fake-registry'
import { runCli } from '../helpers/run-cli'

let registry: { url: string; stop: () => void } | undefined

afterEach(() => {
  registry?.stop()
  registry = undefined
})

describe('search command', () => {
  test('prints compact search table', async () => {
    registry = await startFakeRegistry({
      searchItems: [{ namespace: 'global', slug: 'pdf-parser', latestVersion: '1.2.0', summary: 'Parse PDFs' }]
    })

    const result = await runCli(['search', 'pdf', '--registry', registry.url])

    expect(result.exitCode).toBe(0)
    expect(result.stdout).toContain('global/pdf-parser')
    expect(result.stdout).toContain('1.2.0')
  })

  test('search json output', async () => {
    registry = await startFakeRegistry({
      searchItems: [{ namespace: 'global', slug: 'pdf-parser', latestVersion: '1.2.0', summary: 'Parse PDFs' }]
    })

    const result = await runCli(['search', 'pdf', '--registry', registry.url, '--json'])

    expect(result.exitCode).toBe(0)
    const json = JSON.parse(result.stdout)
    expect(json.ok).toBe(true)
    expect(json.items).toHaveLength(1)
    expect(json.items[0].slug).toBe('pdf-parser')
  })
})
