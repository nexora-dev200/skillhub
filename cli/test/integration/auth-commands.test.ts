import { afterEach, describe, expect, test } from 'bun:test'
import { createTempHome } from '../helpers/temp-env'
import { startFakeRegistry } from '../helpers/fake-registry'
import { runCli } from '../helpers/run-cli'

let registry: { url: string; stop: () => void } | undefined

afterEach(() => {
  registry?.stop()
  registry = undefined
})

describe('auth commands', () => {
  test('login stores registry and token only after whoami succeeds', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({ token: 'sk_ok', user: { handle: 'u1', displayName: 'User One' } })

    const result = await runCli(['login', '--registry', registry.url, '--token', 'sk_ok'], {
      HOME: env.home,
      USERPROFILE: env.home
    })

    expect(result.exitCode).toBe(0)
    expect(result.stdout).toContain('Logged in')
    expect(await Bun.file(`${env.home}/.skillhub/config.json`).json()).toMatchObject({ registry: registry.url })
    expect(await Bun.file(`${env.home}/.skillhub/credentials.json`).json()).toMatchObject({ tokens: { [registry.url]: 'sk_ok' } })
  })

  test('login fails with invalid token', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({ token: 'sk_ok' })

    const result = await runCli(['login', '--registry', registry.url, '--token', 'sk_bad'], {
      HOME: env.home,
      USERPROFILE: env.home
    })

    expect(result.exitCode).toBe(2)
    expect(result.stderr).toContain('authentication failed')
  })

  test('logout removes token', async () => {
    const env = await createTempHome()
    registry = await startFakeRegistry({ token: 'sk_ok', user: { handle: 'u1', displayName: 'User One' } })

    await runCli(['login', '--registry', registry.url, '--token', 'sk_ok'], {
      HOME: env.home,
      USERPROFILE: env.home
    })

    const result = await runCli(['logout', '--registry', registry.url], {
      HOME: env.home,
      USERPROFILE: env.home
    })

    expect(result.exitCode).toBe(0)
    expect(result.stdout).toContain('Logged out')
  })
})
