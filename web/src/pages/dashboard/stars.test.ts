// @vitest-environment jsdom

import { createElement } from 'react'
import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const navigate = vi.fn()

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => navigate,
  useLocation: () => ({
    pathname: '/dashboard/stars',
    searchStr: '?page=2',
    hash: '#saved',
  }),
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
    }),
  }
})

vi.mock('@/features/skill/skill-card', () => ({
  SkillCard: ({ onClick }: { onClick?: () => void }) => createElement('button', { onClick }, 'skill-card'),
}))

vi.mock('@/shared/components/pagination', () => ({
  Pagination: () => null,
}))

vi.mock('@/shared/hooks/use-user-queries', () => ({
  useMyStarsPage: () => ({
    data: {
      items: [{ id: 1, namespace: 'team-a', slug: 'demo skill' }],
      total: 1,
      page: 0,
      size: 12,
    },
    isLoading: false,
  }),
}))

vi.mock('@/shared/ui/card', () => ({
  Card: ({ children }: { children: unknown }) => children,
}))

vi.mock('@/shared/components/dashboard-page-header', () => ({
  DashboardPageHeader: () => null,
}))

import { MyStarsPage } from './stars'

describe('MyStarsPage', () => {
  beforeEach(() => navigate.mockClear())

  it('exports a named component function', () => {
    expect(typeof MyStarsPage).toBe('function')
  })

  it('preserves the favorites page when opening a skill', () => {
    render(createElement(MyStarsPage))

    fireEvent.click(screen.getByRole('button', { name: 'skill-card' }))

    expect(navigate).toHaveBeenCalledWith({
      to: '/space/team-a/demo%20skill',
      search: { returnTo: '/dashboard/stars?page=2#saved' },
    })
  })
})
