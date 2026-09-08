/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { SkillSuite } from '@/api/types'
import { SuiteDetailPage } from './suite-detail'

const mocks = vi.hoisted(() => ({
  navigate: vi.fn(),
  detail: { data: undefined as SkillSuite | undefined, isLoading: false, error: null as Error | null },
  submit: { mutateAsync: vi.fn(), isPending: false },
}))

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => mocks.navigate,
  useParams: () => ({ namespace: 'global', slug: 'care-workflow' }),
  useSearch: () => ({ version: '1.0.0' }),
  Link: ({ children, params, search, ...props }: {
    children: ReactNode
    params: { namespace: string; slug: string }
    search?: { returnTo?: string }
    className?: string
    'aria-label'?: string
  }) => (
    <a
      href={`/space/${params.namespace}/${params.slug}?returnTo=${encodeURIComponent(search?.returnTo ?? '')}`}
      className={props.className}
      aria-label={props['aria-label']}
    >
      {children}
    </a>
  ),
}))
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))
vi.mock('@/features/skill/markdown-renderer', () => ({
  MarkdownRenderer: ({ content }: { content: string }) => <div data-testid="suite-overview">{content}</div>,
}))
vi.mock('@/features/suite/suite-management-actions', () => ({ SuiteManagementActions: () => null }))
vi.mock('@/shared/hooks/use-suite-queries', () => ({
  useSuiteDetail: () => mocks.detail,
  useSuiteVersions: () => ({ data: [] }),
  useSubmitSuite: () => mocks.submit,
}))

function suite(): SkillSuite {
  return {
    id: 1,
    versionId: 10,
    namespace: 'global',
    slug: 'care-workflow',
    displayName: 'Care Workflow',
    summary: 'A short description for discovery.',
    overview: '## Workflow\n\nRun the entry skill first.',
    version: '1.0.0',
    status: 'PUBLISHED',
    visibility: 'PUBLIC',
    suiteStatus: 'ACTIVE',
    hidden: false,
    allowedActions: [],
    available: false,
    members: [
      {
        skillId: 11,
        skillVersionId: 110,
        namespace: 'global',
        slug: 'medical-records',
        displayName: 'Medical Records',
        summary: 'Structures medical records.',
        version: '1.0.0',
        fingerprint: 'sha256:available',
        position: 0,
        entry: true,
        browsable: true,
      },
      {
        namespace: 'global',
        slug: 'deleted-helper',
        version: '2.0.0',
        fingerprint: 'sha256:deleted',
        position: 1,
        entry: false,
        browsable: false,
        blockingReason: 'DELETED',
      },
    ],
  }
}

describe('SuiteDetailPage', () => {
  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('separates the Markdown overview from browsable member skills', () => {
    mocks.detail = { data: suite(), isLoading: false, error: null }

    render(<SuiteDetailPage />)

    expect(screen.getByTestId('suite-overview').textContent).toContain('Run the entry skill first.')
    expect(screen.queryByText('Medical Records')).toBeNull()

    fireEvent.click(screen.getByRole('tab', { name: 'suite.membersTab' }))

    expect(screen.getByText('Medical Records')).not.toBeNull()
    expect(screen.getByText('Structures medical records.')).not.toBeNull()
    expect(screen.getByRole('link', { name: 'suite.viewMember' }).getAttribute('href'))
      .toContain('/space/global/medical-records')
  })

  it('keeps a deleted member as a non-navigable historical card', () => {
    mocks.detail = { data: suite(), isLoading: false, error: null }

    render(<SuiteDetailPage />)
    fireEvent.click(screen.getByRole('tab', { name: 'suite.membersTab' }))

    const deletedLabels = screen.getAllByText('@global/deleted-helper')
    expect(deletedLabels.every((label) => label.closest('a') === null)).toBe(true)
    expect(screen.getAllByRole('link')).toHaveLength(1)
  })
})
