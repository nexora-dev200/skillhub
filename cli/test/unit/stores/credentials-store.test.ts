import { describe, expect, test } from 'bun:test'
import { createTempHome } from '../../helpers/temp-env'
import { CredentialsStore } from '../../../src/stores/credentials-store'

describe('CredentialsStore', () => {
  test('stores tokens under user home .skillhub only', async () => {
    const env = await createTempHome()
    const store = new CredentialsStore(env.home)
    await store.setToken('https://registry.example.com', 'sk_test')
    expect(await store.getToken('https://registry.example.com')).toBe('sk_test')
    expect(store.path).toBe(`${env.home}/.skillhub/credentials.json`)
    expect(await Bun.file(`${env.cwd}/credentials.json`).exists()).toBe(false)
  })
})
