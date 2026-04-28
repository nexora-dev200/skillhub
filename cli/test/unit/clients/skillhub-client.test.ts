import { describe, expect, test } from 'bun:test'
import { SkillHubClient } from '../../../src/clients/skillhub-client'

describe('SkillHubClient', () => {
  test('uses the provided multipart file name when publishing', async () => {
    const fetchImpl = (async (_input: URL | RequestInfo, init?: RequestInit) => {
      const formData = init?.body as FormData
      const file = formData.get('file') as File
      expect(file.name).toBe('custom-skill.zip')
      expect(formData.get('visibility')).toBe('PRIVATE')
      return Response.json({
        data: {
          namespace: 'team',
          slug: 'custom-skill',
          version: '1.0.0',
          visibility: 'PRIVATE'
        }
      })
    }) as unknown as typeof fetch

    const client = new SkillHubClient('http://registry.test', 'token', fetchImpl)
    await expect(client.publish('team', new Blob(['zip'], { type: 'application/zip' }), 'PRIVATE', 'custom-skill.zip'))
      .resolves.toMatchObject({ slug: 'custom-skill' })
  })
})
