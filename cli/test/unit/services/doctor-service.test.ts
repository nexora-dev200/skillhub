import { describe, expect, test } from 'bun:test'
import { mkdtemp, mkdir, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { runDoctor } from '../../../src/services/doctor-service'

async function setupSkillDir(cwd: string, agentDir: string, slug: string, metadata: Record<string, string>) {
  const skillDir = join(cwd, agentDir, 'skills', slug)
  const metaDir = join(skillDir, '.skillhub')
  await mkdir(metaDir, { recursive: true })
  await writeFile(join(metaDir, 'metadata.json'), JSON.stringify(metadata))
  return skillDir
}

describe('doctor-service', () => {
  test('rebuilds inventory from metadata files', async () => {
    const cwd = await mkdtemp(join(tmpdir(), 'doctor-test-'))
    const home = await mkdtemp(join(tmpdir(), 'doctor-home-'))

    await setupSkillDir(cwd, '.codex', 'pdf-parser', {
      registry: 'https://skill.xfyun.cn',
      namespace: 'global',
      slug: 'pdf-parser',
      version: '1.2.0',
      agent: 'codex',
      installedAt: '2026-04-20T12:00:00Z'
    })

    const result = await runDoctor(cwd, home)
    expect(result.itemsRestored).toBe(1)
    expect(result.targetsRestored).toBe(1)
    expect(result.skipped).toHaveLength(0)
    expect(result.conflicts).toHaveLength(0)
  })

  test('skips directories without metadata', async () => {
    const cwd = await mkdtemp(join(tmpdir(), 'doctor-test-'))
    const home = await mkdtemp(join(tmpdir(), 'doctor-home-'))

    await mkdir(join(cwd, '.codex', 'skills', 'no-meta'), { recursive: true })

    const result = await runDoctor(cwd, home)
    expect(result.itemsRestored).toBe(0)
    expect(result.skipped).toHaveLength(1)
    expect(result.skipped[0]!.reason).toBe('no .skillhub/metadata.json')
  })

  test('detects version conflicts', async () => {
    const cwd = await mkdtemp(join(tmpdir(), 'doctor-test-'))
    const home = await mkdtemp(join(tmpdir(), 'doctor-home-'))

    await setupSkillDir(cwd, '.codex', 'pdf-parser', {
      registry: 'https://skill.xfyun.cn', namespace: 'global', slug: 'pdf-parser',
      version: '1.0.0', agent: 'codex', installedAt: '2026-04-20T12:00:00Z'
    })
    await setupSkillDir(cwd, '.claude', 'pdf-parser', {
      registry: 'https://skill.xfyun.cn', namespace: 'global', slug: 'pdf-parser',
      version: '2.0.0', agent: 'claude-code', installedAt: '2026-04-20T12:00:00Z'
    })

    const result = await runDoctor(cwd, home)
    expect(result.itemsRestored).toBe(0)
    expect(result.conflicts).toHaveLength(1)
    expect(result.conflicts[0]!.versions).toContain('1.0.0')
    expect(result.conflicts[0]!.versions).toContain('2.0.0')
  })
})
