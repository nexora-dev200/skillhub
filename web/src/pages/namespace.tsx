import { useState, useEffect, useMemo } from 'react'
import { useNavigate, useParams } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ClipboardCopy, Download } from 'lucide-react'
import { NamespaceHeader } from '@/features/namespace/namespace-header'
import { SkillCard } from '@/features/skill/skill-card'
import { buildInstallTarget } from '@/features/skill/install-command'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { EmptyState } from '@/shared/components/empty-state'
import { Pagination } from '@/shared/components/pagination'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { useSearchSkills } from '@/shared/hooks/use-skill-queries'
import { useNamespaceDetail } from '@/shared/hooks/use-namespace-queries'
import { Button } from '@/shared/ui/button'

const PAGE_SIZE = 20
const NAMESPACE_BUNDLE_MAX_SKILLS = 20
const NAMESPACE_BUNDLE_MAX_SIZE = '100 MB'

/**
 * Public namespace page showing namespace metadata and the skills currently discoverable inside it.
 */
export function NamespacePage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { namespace } = useParams({ from: '/space/$namespace' })
  const [page, setPage] = useState(0)
  const [selectedSkillSlugs, setSelectedSkillSlugs] = useState<string[]>([])
  const [pendingDownloadSlugs, setPendingDownloadSlugs] = useState<string[] | null>(null)

  // Reset page when namespace changes
  useEffect(() => {
    setPage(0)
    setSelectedSkillSlugs([])
    setPendingDownloadSlugs(null)
  }, [namespace])

  useEffect(() => {
    setSelectedSkillSlugs([])
  }, [page])

  const { data: namespaceData, isLoading: isLoadingNamespace } = useNamespaceDetail(namespace)
  const { data: skillsData, isLoading: isLoadingSkills } = useSearchSkills({
    namespace,
    page,
    size: PAGE_SIZE,
  })

  const totalPages = skillsData ? Math.max(Math.ceil(skillsData.total / skillsData.size), 1) : 1
  const visibleSkills = skillsData?.items ?? []
  const selectedSlugSet = useMemo(() => new Set(selectedSkillSlugs), [selectedSkillSlugs])
  const hasSkills = visibleSkills.length > 0
  const selectedDownloadSlugs = selectedSkillSlugs.filter((slug) => visibleSkills.some((skill) => skill.slug === slug))
  const pendingDownloadCount = pendingDownloadSlugs
    ? pendingDownloadSlugs.length || skillsData?.total || visibleSkills.length
    : 0

  const handleSkillClick = (slug: string) => {
    navigate({ to: `/space/${namespace}/${encodeURIComponent(slug)}` })
  }

  const handleSkillSelectionChange = (slug: string, selected: boolean) => {
    setSelectedSkillSlugs((current) => {
      if (selected) {
        return current.includes(slug) ? current : [...current, slug]
      }
      return current.filter((item) => item !== slug)
    })
  }

  const buildNamespaceDownloadUrl = (slugs: string[]) => {
    const params = new URLSearchParams()
    slugs.forEach((slug) => params.append('skill', slug))
    const queryString = params.toString()
    return `/api/web/namespaces/${encodeURIComponent(namespace)}/skills/download${queryString ? `?${queryString}` : ''}`
  }

  const handleDownloadAll = () => {
    setPendingDownloadSlugs([])
  }

  const handleDownloadSelected = () => {
    setPendingDownloadSlugs(selectedDownloadSlugs)
  }

  const confirmDownload = () => {
    if (!pendingDownloadSlugs) {
      return
    }
    window.location.assign(buildNamespaceDownloadUrl(pendingDownloadSlugs))
  }

  const handleCopyInstallManifest = async () => {
    const manifest = visibleSkills
      .map((skill) => `skillhub install ${buildInstallTarget(skill.namespace, skill.slug)}`)
      .join('\n')
    await navigator.clipboard?.writeText(manifest)
  }

  if (isLoadingNamespace) {
    return (
      <div className="space-y-6 animate-fade-up">
        <div className="h-12 w-48 animate-shimmer rounded-lg" />
        <div className="h-6 w-96 animate-shimmer rounded-md" />
      </div>
    )
  }

  if (!namespaceData) {
    return <EmptyState title={t('namespace.notFound')} />
  }

  return (
    <div className="space-y-8 animate-fade-up">
      <NamespaceHeader namespace={namespaceData} />

      <div className="space-y-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-2xl font-bold font-heading">{t('namespace.skillList')}</h2>
          {hasSkills ? (
            <div className="flex flex-wrap items-center gap-2">
              <Button type="button" size="sm" variant="outline" onClick={handleCopyInstallManifest}>
                <ClipboardCopy className="h-4 w-4" />
                {t('namespace.copyInstallManifest')}
              </Button>
              <Button type="button" size="sm" variant="outline" onClick={handleDownloadSelected} disabled={selectedDownloadSlugs.length === 0}>
                <Download className="h-4 w-4" />
                {t('namespace.downloadSelected')}
              </Button>
              <Button type="button" size="sm" onClick={handleDownloadAll}>
                <Download className="h-4 w-4" />
                {t('namespace.downloadAll')}
              </Button>
            </div>
          ) : null}
        </div>
        {isLoadingSkills ? (
          <SkeletonList count={6} />
        ) : skillsData && skillsData.items.length > 0 ? (
          <>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
              {skillsData.items.map((skill, idx) => (
                <div key={skill.id} className={`relative animate-fade-up delay-${Math.min(idx + 1, 6)}`}>
                  <label className="absolute right-3 top-3 z-10 inline-flex h-8 w-8 items-center justify-center rounded-md border bg-background/90 shadow-sm">
                    <span className="sr-only">{t('namespace.selectSkill', { name: skill.displayName })}</span>
                    <input
                      type="checkbox"
                      className="h-4 w-4"
                      checked={selectedSlugSet.has(skill.slug)}
                      onChange={(event) => handleSkillSelectionChange(skill.slug, event.target.checked)}
                    />
                  </label>
                  <SkillCard
                    skill={skill}
                    onClick={() => handleSkillClick(skill.slug)}
                  />
                </div>
              ))}
            </div>

            {skillsData.total > PAGE_SIZE ? (
              <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
            ) : null}
          </>
        ) : (
          <EmptyState
            title={t('namespace.emptyTitle')}
            description={t('namespace.emptyDescription')}
          />
        )}
      </div>
      <ConfirmDialog
        open={pendingDownloadSlugs !== null}
        onOpenChange={(open) => {
          if (!open) {
            setPendingDownloadSlugs(null)
          }
        }}
        title={t('namespace.downloadConfirmTitle')}
        description={(
          <span className="block space-y-2">
            <span className="block">
              {t('namespace.downloadConfirmDescription', {
                count: pendingDownloadCount,
                namespace,
              })}
            </span>
            <span className="block">
              {t('namespace.downloadConfirmLimitHint', {
                maxSkills: NAMESPACE_BUNDLE_MAX_SKILLS,
                maxSize: NAMESPACE_BUNDLE_MAX_SIZE,
              })}
            </span>
          </span>
        )}
        confirmText={t('namespace.downloadConfirmAction')}
        onConfirm={confirmDownload}
      />
    </div>
  )
}
