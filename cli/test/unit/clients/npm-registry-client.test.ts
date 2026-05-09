import { describe, expect, test } from 'bun:test'
import { NpmRegistryClient } from '../../../src/clients/npm-registry-client'

describe('NpmRegistryClient', () => {
  test('classifies network failures as CLI errors', async () => {
    const failingFetch = (async () => {
      throw new TypeError('fetch failed')
    }) as unknown as typeof fetch
    const client = new NpmRegistryClient(failingFetch)

    await expect(client.latestVersion()).rejects.toMatchObject({
      message: 'npm registry unreachable',
      exitCode: 3
    })
  })
})
