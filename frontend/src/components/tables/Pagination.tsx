import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from '@/components/ui/Button'

type PaginationProps = {
  /** Zero-based, as the backend pages. */
  page: number
  size: number
  totalElements: number
  totalPages: number
  onPageChange: (page: number) => void
}

export function Pagination({ page, size, totalElements, totalPages, onPageChange }: PaginationProps) {
  const from = totalElements === 0 ? 0 : page * size + 1
  const to = Math.min(totalElements, (page + 1) * size)
  return (
    <nav aria-label="Pagination" className="flex flex-wrap items-center justify-between gap-3 border-t border-line px-4 py-3 text-sm">
      <p className="text-fg-muted">
        Showing <span className="font-medium text-fg">{from}</span>–<span className="font-medium text-fg">{to}</span> of{' '}
        <span className="font-medium text-fg">{totalElements}</span>
      </p>
      <div className="flex items-center gap-2">
        <Button
          variant="secondary"
          size="sm"
          onClick={() => onPageChange(page - 1)}
          disabled={page <= 0}
          aria-label="Previous page"
          icon={<ChevronLeft className="size-4" aria-hidden="true" />}
        />
        <span className="min-w-20 text-center text-fg-muted">
          Page {Math.min(page + 1, Math.max(totalPages, 1))} of {Math.max(totalPages, 1)}
        </span>
        <Button
          variant="secondary"
          size="sm"
          onClick={() => onPageChange(page + 1)}
          disabled={page + 1 >= totalPages}
          aria-label="Next page"
          icon={<ChevronRight className="size-4" aria-hidden="true" />}
        />
      </div>
    </nav>
  )
}
