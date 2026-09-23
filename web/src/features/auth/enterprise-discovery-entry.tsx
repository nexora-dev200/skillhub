import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ArrowRight, Building2 } from 'lucide-react'
import { authApi } from '@/api/client'
import { withBasePath } from '@/shared/lib/base-path'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'

interface EnterpriseDiscoveryEntryProps {
  returnTo: string
}

/** The server resolves the organization and returns only its available login connections. */
export function EnterpriseDiscoveryEntry({ returnTo }: EnterpriseDiscoveryEntryProps) {
  const { t } = useTranslation()
  const [identifier, setIdentifier] = useState('')
  const discovery = useMutation({
    mutationFn: (value: string) => authApi.discoverEnterpriseLogin(value, returnTo),
    meta: { skipGlobalErrorHandler: true },
  })

  return (
    <div className="space-y-3">
      <form
        className="space-y-3"
        onSubmit={(event) => {
          event.preventDefault()
          const value = identifier.trim()
          if (value) discovery.mutate(value)
        }}
      >
        <div className="space-y-2">
          <label htmlFor="organization-identifier" className="text-sm font-medium">{t('login.organizationIdentifier')}</label>
          <div className="relative">
            <Building2 aria-hidden="true" className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              id="organization-identifier"
              autoComplete="email"
              className="h-11 pl-10 xl:h-10"
              maxLength={320}
              placeholder={t('login.organizationIdentifier')}
              value={identifier}
              onChange={(event) => {
                setIdentifier(event.target.value)
                discovery.reset()
              }}
            />
          </div>
        </div>
        <p className="text-sm text-muted-foreground">{t('login.organizationLoginHint')}</p>
        <Button type="submit" disabled={!identifier.trim() || discovery.isPending} className="h-11 w-full bg-[#315b86] text-white hover:bg-[#25496d] dark:bg-sky-300 dark:text-sky-950 dark:hover:bg-sky-200 xl:h-10">
          {discovery.isPending ? t('login.organizationSearching') : t('login.organizationContinue')}
          <ArrowRight aria-hidden="true" className="ml-2 h-4 w-4" />
        </Button>
      </form>

      {discovery.isError ? <p role="alert" className="text-sm text-destructive">{discovery.error.message}</p> : null}
      {discovery.data && discovery.data.organizations.length === 0 ? (
        <p role="status" className="text-sm text-muted-foreground">{t('login.organizationNotFound')}</p>
      ) : null}
      {discovery.data?.organizations.map((organization) => (
        <div key={organization.slug} className="space-y-2 rounded-xl border border-border p-4">
          <p className="text-sm font-medium">{organization.displayName}</p>
          {organization.loginOptions.map((option) => (
            <Button
              key={option.actionUrl}
              type="button"
              variant="outline"
              className="w-full justify-between"
              onClick={() => {
                if (option.actionUrl.startsWith('/api/v1/auth/enterprise/')) {
                  window.location.href = withBasePath(option.actionUrl)
                }
              }}
            >
              {option.displayName}
              <ArrowRight aria-hidden="true" className="h-4 w-4" />
            </Button>
          ))}
        </div>
      ))}
    </div>
  )
}
