import { mkdir } from 'node:fs/promises'
import { join } from 'node:path'
import { afterEach, describe, expect, test } from 'bun:test'
import { strToU8, zipSync } from 'fflate'
import { createTempHome } from '../helpers/temp-env'
import { startFakeRegistry } from '../helpers/fake-registry'
import { runCli } from '../helpers/run-cli'

const TIMESTAMP_VERSION = '20260910.021100'

let stopServer: (() => void) | undefined

afterEach(() => {
  stopServer?.()
  stopServer = undefined
})

describe('--version parsing', () => {
  test('install preserves trailing zeros in a numeric-looking version', async () => {
    const env = await createTempHome()
    const registry = await startFakeRegistry({
      token: 'sk_ok',
      skills: [{
        namespace: 'global',
        slug: 'timestamped',
        version: TIMESTAMP_VERSION,
        zipBytes: zipSync({ 'SKILL.md': strToU8('# timestamped') })
      }]
    })
    stopServer = registry.stop
    const installDir = join(env.cwd, 'skills')
    await mkdir(installDir, { recursive: true })

    const result = await runCli([
      'install', '@global/timestamped',
      '--version', TIMESTAMP_VERSION,
      '--dir', installDir,
      '--registry', registry.url,
      '--token', 'sk_ok'
    ], { HOME: env.home, USERPROFILE: env.home })

    expect(result.exitCode).toBe(0)
    expect(registry.received.resolve?.version).toBe(TIMESTAMP_VERSION)
  })

  test('install preserves trailing zeros with the --version=value form', async () => {
    const env = await createTempHome()
    const registry = await startFakeRegistry({
      token: 'sk_ok',
      skills: [{
        namespace: 'global',
        slug: 'timestamped-equals',
        version: TIMESTAMP_VERSION,
        zipBytes: zipSync({ 'SKILL.md': strToU8('# timestamped equals') })
      }]
    })
    stopServer = registry.stop
    const installDir = join(env.cwd, 'skills-equals')
    await mkdir(installDir, { recursive: true })

    const result = await runCli([
      'install', '@global/timestamped-equals',
      `--version=${TIMESTAMP_VERSION}`,
      '--dir', installDir,
      '--registry', registry.url,
      '--token', 'sk_ok'
    ], { HOME: env.home, USERPROFILE: env.home })

    expect(result.exitCode).toBe(0)
    expect(registry.received.resolve?.version).toBe(TIMESTAMP_VERSION)
  })

  test('suite install preserves trailing zeros in a numeric-looking version', async () => {
    const env = await createTempHome()
    const received = { version: null as string | null }
    const server = Bun.serve({
      port: 0,
      fetch(request) {
        const url = new URL(request.url)
        if (url.pathname === '/.well-known/clawhub.json') {
          return Response.json({ apiBase: '/api/v1', capabilities: ['skill-suite-v1'] })
        }
        if (url.pathname === '/api/v1/suites/global/starter-pack/install-plan') {
          received.version = url.searchParams.get('version')
          return Response.json({ code: 404, message: 'stop after capturing version' }, { status: 404 })
        }
        return Response.json({ code: 404, message: 'not found' }, { status: 404 })
      }
    })
    stopServer = () => server.stop(true)
    const installDir = join(env.cwd, 'suite-skills')
    await mkdir(installDir, { recursive: true })

    const result = await runCli([
      'suite', 'install', '@global/starter-pack',
      '--version', TIMESTAMP_VERSION,
      '--dir', installDir,
      '--registry', `http://localhost:${server.port}`,
      '--token', 'sk_ok'
    ], { HOME: env.home, USERPROFILE: env.home })

    expect(result.exitCode).not.toBe(0)
    expect(received.version).toBe(TIMESTAMP_VERSION)
  })
})
