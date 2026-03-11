import { Button } from '@/shared/ui/button'

interface PaginationProps {
  page: number
  totalPages: number
  onPageChange: (page: number) => void
}

export function Pagination({ page, totalPages, onPageChange }: PaginationProps) {
  return (
    <div className="flex items-center justify-center gap-2">
      <Button
        variant="outline"
        size="sm"
        onClick={() => onPageChange(page - 1)}
        disabled={page <= 1}
      >
        上一页
      </Button>
      <span className="text-sm text-muted-foreground">
        第 {page} / {totalPages} 页
      </span>
      <Button
        variant="outline"
        size="sm"
        onClick={() => onPageChange(page + 1)}
        disabled={page >= totalPages}
      >
        下一页
      </Button>
    </div>
  )
}
