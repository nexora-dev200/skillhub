export function SkeletonCard() {
  return (
    <div className="rounded-lg border bg-card p-4 animate-pulse">
      <div className="h-4 bg-muted rounded w-3/4 mb-3"></div>
      <div className="h-3 bg-muted rounded w-full mb-2"></div>
      <div className="h-3 bg-muted rounded w-5/6"></div>
      <div className="flex gap-2 mt-4">
        <div className="h-3 bg-muted rounded w-16"></div>
        <div className="h-3 bg-muted rounded w-16"></div>
        <div className="h-3 bg-muted rounded w-16"></div>
      </div>
    </div>
  )
}

interface SkeletonListProps {
  count?: number
}

export function SkeletonList({ count = 6 }: SkeletonListProps) {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
      {Array.from({ length: count }).map((_, i) => (
        <SkeletonCard key={i} />
      ))}
    </div>
  )
}
