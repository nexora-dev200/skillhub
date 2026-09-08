import { useMemo } from 'react'
import { Link, useNavigate, useParams, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { AlertTriangle, ArrowUpRight, Boxes, CheckCircle2, Copy, Wrench } from 'lucide-react'
import { useSuiteDetail, useSuiteVersions, useSubmitSuite } from '@/shared/hooks/use-suite-queries'
import { suiteBlockingReasonLabel, suiteStatusLabel, suiteVisibilityLabel } from '@/features/suite/suite-labels'
import { SuiteManagementActions } from '@/features/suite/suite-management-actions'
import { MarkdownRenderer } from '@/features/skill/markdown-renderer'
import { Card } from '@/shared/ui/card'
import { Button } from '@/shared/ui/button'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui/tabs'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { toast } from '@/shared/lib/toast'
import { APP_SHELL_PAGE_CLASS_NAME } from '@/app/page-shell-style'

export function SuiteDetailPage() {
  const { namespace, slug } = useParams({ from: '/suite/$namespace/$slug' })
  const { t } = useTranslation()
  const search = useSearch({ from: '/suite/$namespace/$slug' })
  const navigate = useNavigate()
  const { data: suite, isLoading, error } = useSuiteDetail(namespace, slug, search.version)
  const { data: versions } = useSuiteVersions(namespace, slug)
  const submitMutation = useSubmitSuite()
  const command = useMemo(
    () => suite ? `skillhub suite install @${suite.namespace}/${suite.slug}@${suite.version}` : '',
    [suite],
  )
  const suitePath = `/suite/${encodeURIComponent(namespace)}/${encodeURIComponent(slug)}`
  const returnTo = search.version
    ? `${suitePath}?version=${encodeURIComponent(search.version)}`
    : suitePath

  if (isLoading) return <div className={APP_SHELL_PAGE_CLASS_NAME}><SkeletonList count={3} /></div>
  if (!suite || error) {
    return <div className={APP_SHELL_PAGE_CLASS_NAME}><p className="text-destructive">{t('suite.notFound')}</p></div>
  }

  const handleSubmit = async () => {
    try {
      await submitMutation.mutateAsync({
        suiteId: suite.id,
        versionId: suite.versionId,
        privatePublish: suite.visibility === 'PRIVATE',
      })
      toast.success(suite.visibility === 'PRIVATE' ? t('suite.published') : t('suite.submitted'))
    } catch (submitError) {
      toast.error(t('suite.actionFailed'), submitError instanceof Error ? submitError.message : '')
    }
  }

  return (
    <div className={APP_SHELL_PAGE_CLASS_NAME}>
      <div className="flex flex-col justify-between gap-4 md:flex-row md:items-start">
        <div>
          <div className="mb-2 inline-flex items-center gap-2 text-sm font-medium text-primary">
            <Boxes className="h-4 w-4" /> @{suite.namespace}/{suite.slug}
          </div>
          <h1 className="text-4xl font-bold">{suite.displayName}</h1>
          <p className="mt-3 max-w-3xl text-muted-foreground">{suite.summary || t('suite.noSummary')}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          {suite.allowedActions.includes('EDIT') ? (
            <Button variant="outline" onClick={() => navigate({
              to: `/dashboard/suites/${suite.namespace}/${encodeURIComponent(suite.slug)}/edit`,
              search: { version: suite.version },
            })}>{t('suite.editDraft')}</Button>
          ) : null}
          {suite.allowedActions.includes('SUBMIT') || suite.allowedActions.includes('PUBLISH_PRIVATE') ? (
            <Button disabled={submitMutation.isPending} onClick={handleSubmit}>
              {suite.visibility === 'PRIVATE' ? t('suite.publishDirectly') : t('suite.submitReview')}
            </Button>
          ) : null}
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-4">
        <Card className="p-4"><p className="text-xs text-muted-foreground">{t('suite.version')}</p><p className="mt-1 font-mono font-semibold">v{suite.version}</p></Card>
        <Card className="p-4"><p className="text-xs text-muted-foreground">{t('suite.status')}</p><p className="mt-1 font-semibold">{suiteStatusLabel(t, suite.status)}</p></Card>
        <Card className="p-4"><p className="text-xs text-muted-foreground">{t('suite.visibility')}</p><p className="mt-1 font-semibold">{suiteVisibilityLabel(t, suite.visibility)}</p></Card>
        <Card className="p-4">
          <p className="text-xs text-muted-foreground">{t('suite.installStatus')}</p>
          <p className={`mt-1 flex items-center gap-1 font-semibold ${suite.available ? 'text-emerald-600' : 'text-destructive'}`}>
            {suite.available ? <CheckCircle2 className="h-4 w-4" /> : <AlertTriangle className="h-4 w-4" />}
            {suite.available ? t('suite.available') : t('suite.degraded')}
          </p>
        </Card>
      </div>

      <Card className="p-6">
        <h2 className="text-lg font-semibold">{t('suite.installCommand')}</h2>
        <div className="mt-3 flex items-center gap-2 rounded-lg bg-secondary p-3">
          <code className="min-w-0 flex-1 overflow-x-auto text-sm">{command}</code>
          <Button
            variant="outline"
            size="sm"
            onClick={async () => {
              await navigator.clipboard.writeText(command)
              toast.success(t('suite.commandCopied'))
            }}
          ><Copy className="h-4 w-4" /></Button>
        </div>
        {!suite.available ? <p className="mt-3 text-sm text-destructive">{t('suite.degradedDescription')}</p> : null}
      </Card>

      <Tabs defaultValue="overview">
        <TabsList className="w-full justify-start">
          <TabsTrigger value="overview">{t('suite.overviewTab')}</TabsTrigger>
          <TabsTrigger value="members">{t('suite.membersTab', { count: suite.members.length })}</TabsTrigger>
        </TabsList>

        <TabsContent value="overview" className="mt-5">
          <Card className="p-6 md:p-8">
            <h2 className="text-xl font-semibold">{t('suite.overviewTitle')}</h2>
            <div className="mt-5">
              {suite.overview ? (
                <MarkdownRenderer content={suite.overview} />
              ) : (
                <p className="text-sm leading-7 text-muted-foreground">
                  {suite.summary || t('suite.noOverview')}
                </p>
              )}
            </div>
          </Card>
        </TabsContent>

        <TabsContent value="members" className="mt-5">
          <div className="grid gap-4 md:grid-cols-2">
            {suite.members.map((member) => {
              const memberName = member.displayName || `@${member.namespace}/${member.slug}`
              const content = (
                <Card className="h-full p-5 transition-colors hover:border-primary/40">
                  <div className="flex items-start justify-between gap-4">
                    <div className="min-w-0">
                      <p className="flex items-center gap-2 font-semibold">
                        <Wrench className="h-4 w-4 shrink-0 text-primary" />
                        <span className="truncate">{memberName}</span>
                      </p>
                      <p className="mt-1 truncate text-xs text-muted-foreground">
                        @{member.namespace}/{member.slug}
                      </p>
                    </div>
                    {member.browsable && !member.blockingReason && member.skillId && member.skillVersionId ? (
                      <ArrowUpRight className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" />
                    ) : null}
                  </div>
                  {member.summary ? (
                    <p className="mt-4 line-clamp-3 text-sm leading-6 text-muted-foreground">{member.summary}</p>
                  ) : null}
                  <div className="mt-5 flex flex-wrap items-center gap-2 text-xs">
                    <span className="rounded-full bg-secondary px-2.5 py-1 font-mono">
                      {t('suite.pinnedVersion', { version: member.version })}
                    </span>
                    {member.entry ? (
                      <span className="rounded-full bg-primary/10 px-2.5 py-1 font-medium text-primary">
                        {t('suite.entrySkill')}
                      </span>
                    ) : null}
                    <span className={member.blockingReason ? 'text-destructive' : 'text-emerald-600'}>
                      {member.blockingReason
                        ? suiteBlockingReasonLabel(t, member.blockingReason)
                        : t('suite.available')}
                    </span>
                  </div>
                </Card>
              )

              return member.browsable && !member.blockingReason && member.skillId && member.skillVersionId ? (
                <Link
                  key={`${member.namespace}/${member.slug}@${member.version}`}
                  to="/space/$namespace/$slug"
                  params={{ namespace: member.namespace, slug: member.slug }}
                  search={{ returnTo }}
                  aria-label={t('suite.viewMember', { name: memberName })}
                  className="rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/70 focus-visible:ring-offset-2"
                >
                  {content}
                </Link>
              ) : (
                <div key={`${member.namespace}/${member.slug}@${member.version}`}>{content}</div>
              )
            })}
          </div>
        </TabsContent>
      </Tabs>

      {versions?.length ? (
        <Card className="p-6">
          <h2 className="text-lg font-semibold">{t('suite.versionHistory')}</h2>
          <div className="mt-4 flex flex-wrap gap-2">
            {versions.map((item) => (
              <Button
                key={item.id}
                variant={item.version === suite.version ? 'default' : 'outline'}
                size="sm"
                onClick={() => navigate({
                  to: `/suite/${namespace}/${encodeURIComponent(slug)}`,
                  search: { version: item.version },
                })}
              >v{item.version} · {suiteStatusLabel(t, item.status)}</Button>
            ))}
          </div>
        </Card>
      ) : null}

      <SuiteManagementActions suite={suite} />
    </div>
  )
}
