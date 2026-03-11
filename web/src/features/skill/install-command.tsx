import { CopyButton } from '@/shared/components/copy-button'

interface InstallCommandProps {
  namespace: string
  slug: string
  version?: string
}

export function InstallCommand({ namespace, slug, version }: InstallCommandProps) {
  const command = version
    ? `skillhub install ${namespace}/${slug}@${version}`
    : `skillhub install ${namespace}/${slug}`

  return (
    <div className="flex items-center gap-2 p-3 bg-muted rounded-lg">
      <code className="flex-1 text-sm font-mono">{command}</code>
      <CopyButton text={command} />
    </div>
  )
}
