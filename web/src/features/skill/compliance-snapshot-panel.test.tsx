import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'
import { ComplianceSnapshotPanel } from './compliance-snapshot-panel'

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, values?: Record<string, number>) =>
        key === 'compliance.mappingCount' ? `${values?.count} mappings` : key,
    }),
  }
})

describe('ComplianceSnapshotPanel', () => {
  it('renders compliance mappings and evidence', () => {
    const html = renderToStaticMarkup(
      <ComplianceSnapshotPanel
        snapshot={{
          schemaVersion: '1.0',
          digest: 'sha256:12345678901234567890',
          items: [
            {
              standard: 'mitre-attack',
              version: 'v19.1',
              controlId: 'T1059',
              title: 'Command and Scripting Interpreter',
              evidence: [{ type: 'packaged-file', path: 'references/standards.md', sha256: 'abc' }],
            },
          ],
        }}
      />,
    )

    expect(html).toContain('compliance.title')
    expect(html).toContain('1 mappings')
    expect(html).toContain('mitre-attack')
    expect(html).toContain('T1059')
    expect(html).toContain('references/standards.md')
  })

  it('renders nothing when there are no compliance mappings', () => {
    const html = renderToStaticMarkup(<ComplianceSnapshotPanel snapshot={{ schemaVersion: '1.0', items: [], digest: 'sha256:empty' }} />)

    expect(html).toBe('')
  })
})
