import { describe, expect, test } from 'bun:test'
import { runCli } from '../helpers/run-cli'

describe('help command', () => {
  test('prints detailed help for install', async () => {
    const result = await runCli(['help', 'install'])
    expect(result.exitCode).toBe(0)
    expect(result.stdout).toContain('Usage: skillhub install <slug>')
    expect(result.stdout).toContain('--agent <profile>')
  })
})
