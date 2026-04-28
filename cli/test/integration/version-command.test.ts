import { describe, expect, test } from 'bun:test'
import { mkdtemp, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { runCli } from '../helpers/run-cli'

describe('version command', () => {
  test('prints human readable version', async () => {
    const result = await runCli(['version'])
    expect(result.exitCode).toBe(0)
    expect(result.stdout).toContain('SkillHub CLI')
    expect(result.stdout).toContain('0.1.0')
    expect(result.stderr).toBe('')
  })

  test('prints json version', async () => {
    const result = await runCli(['version', '--json'])
    expect(result.exitCode).toBe(0)
    expect(JSON.parse(result.stdout)).toEqual({
      ok: true,
      version: '0.1.0'
    })
  })

  test('built npm artifact runs on node without Bun runtime', async () => {
    const dir = await mkdtemp(join(tmpdir(), 'skillhub-node-build-'))
    const outfile = join(dir, 'index.js')
    await writeFile(join(dir, 'package.json'), JSON.stringify({ type: 'module' }))
    const build = Bun.spawn({
      cmd: ['bun', 'build', 'src/index.ts', '--target=node', `--outfile=${outfile}`],
      cwd: new URL('../../', import.meta.url).pathname,
      stdout: 'pipe',
      stderr: 'pipe'
    })
    expect(await build.exited).toBe(0)

    const node = Bun.spawn({
      cmd: ['node', outfile, 'version'],
      stdout: 'pipe',
      stderr: 'pipe'
    })
    const [stdout, stderr, exitCode] = await Promise.all([
      new Response(node.stdout).text(),
      new Response(node.stderr).text(),
      node.exited
    ])

    expect(exitCode).toBe(0)
    expect(stdout).toContain('SkillHub CLI')
    expect(stderr).toBe('')
  })
})
