import { cn } from '@/shared/lib/utils'

interface NamespaceBadgeProps {
  type: 'GLOBAL' | 'TEAM'
  name: string
  className?: string
}

export function NamespaceBadge({ type, name, className }: NamespaceBadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium',
        type === 'GLOBAL'
          ? 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200'
          : 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200',
        className
      )}
    >
      {name}
    </span>
  )
}
