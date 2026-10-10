import { ArrowDown, ArrowUp, ArrowUpDown } from 'lucide-react'
import { AnimatePresence, motion } from 'motion/react'
import type { ReactNode } from 'react'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorState } from '@/components/ui/ErrorState'
import { Skeleton } from '@/components/ui/Skeleton'
import { cn } from '@/lib/cn'
import { listItem } from '@/lib/motion'
import type { Page } from '@/lib/page'
import { Pagination } from './Pagination'

export type Column<T> = {
  key: string
  header: string
  cell: (row: T) => ReactNode
  className?: string
  /** Shown on the stacked mobile card (default true). The first shown column is the card title. */
  mobile?: boolean
  /** Server sort property; makes the header a sort button. */
  sortKey?: string
}

export type SortState = { key: string; direction: 'asc' | 'desc' }

type DataTableProps<T> = {
  label: string
  columns: Column<T>[]
  data: Page<T> | undefined
  getRowId: (row: T) => string | number
  isLoading: boolean
  isFetching?: boolean
  error?: unknown
  onRetry?: () => void
  onPageChange: (page: number) => void
  onRowClick?: (row: T) => void
  emptyTitle?: string
  emptyDescription?: ReactNode
  sort?: SortState
  onSortChange?: (sort: SortState) => void
}

const SKELETON_ROWS = 5

/** Server-paged table: table from `md` up, stacked cards below it. Loading, empty and error states built in. */
export function DataTable<T>({
  label,
  columns,
  data,
  getRowId,
  isLoading,
  isFetching = false,
  error,
  onRetry,
  onPageChange,
  onRowClick,
  emptyTitle = 'Nothing here yet',
  emptyDescription,
  sort,
  onSortChange,
}: DataTableProps<T>) {
  if (error && !data) {
    return <ErrorState title={`Couldn't load ${label.toLowerCase()}`} error={error} onRetry={onRetry} />
  }

  if (isLoading || !data) {
    return (
      <div role="status" aria-label={`Loading ${label.toLowerCase()}`} className="space-y-2 p-4">
        {Array.from({ length: SKELETON_ROWS }, (_, i) => (
          <Skeleton key={i} className="h-11 w-full" />
        ))}
      </div>
    )
  }

  if (data.content.length === 0) {
    return <EmptyState title={emptyTitle} description={emptyDescription} />
  }

  const mobileColumns = columns.filter((c) => c.mobile !== false)
  const [titleColumn, ...fieldColumns] = mobileColumns

  return (
    <div aria-busy={isFetching || undefined} className={cn('transition-opacity duration-150', isFetching && 'opacity-60')}>
      <div className="hidden max-h-[70vh] overflow-auto md:block">
        <table className="w-full text-left text-sm" aria-label={label}>
          <thead className="sticky top-0 z-10 bg-surface-muted text-xs uppercase tracking-wide text-fg-muted">
            <tr>
              {columns.map((column) => (
                <th
                  key={column.key}
                  scope="col"
                  aria-sort={column.sortKey && sort?.key === column.sortKey ? (sort.direction === 'asc' ? 'ascending' : 'descending') : undefined}
                  className={cn('px-4 py-3 font-semibold whitespace-nowrap', column.className)}
                >
                  {column.sortKey && onSortChange ? (
                    <SortButton column={column} sort={sort} onSortChange={onSortChange} />
                  ) : (
                    column.header
                  )}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            <AnimatePresence initial={true}>
              {data.content.map((row, index) => (
                <motion.tr
                  key={getRowId(row)}
                  custom={index}
                  variants={listItem}
                  initial="initial"
                  animate="animate"
                  exit="exit"
                  onClick={onRowClick ? () => onRowClick(row) : undefined}
                  className={cn('bg-surface', onRowClick && 'cursor-pointer transition-colors duration-150 hover:bg-surface-muted')}
                >
                  {columns.map((column) => (
                    <td key={column.key} className={cn('px-4 py-3 align-middle', column.className)}>
                      {column.cell(row)}
                    </td>
                  ))}
                </motion.tr>
              ))}
            </AnimatePresence>
          </tbody>
        </table>
      </div>

      <ul className="divide-y divide-line md:hidden" aria-label={label}>
        {data.content.map((row, index) => (
          <motion.li key={getRowId(row)} custom={index} variants={listItem} initial="initial" animate="animate" className="p-4">
            {titleColumn && <div className="font-medium text-fg">{titleColumn.cell(row)}</div>}
            <dl className="mt-2 grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
              {fieldColumns.map((column) => (
                <div key={column.key} className="min-w-0">
                  <dt className="text-xs text-fg-muted">{column.header}</dt>
                  <dd className="truncate text-fg">{column.cell(row)}</dd>
                </div>
              ))}
            </dl>
            {onRowClick && (
              <Button variant="secondary" size="sm" className="mt-3 w-full" onClick={() => onRowClick(row)}>
                View
              </Button>
            )}
          </motion.li>
        ))}
      </ul>

      <Pagination page={data.page} size={data.size} totalElements={data.totalElements} totalPages={data.totalPages} onPageChange={onPageChange} />
    </div>
  )
}

function SortButton<T>({ column, sort, onSortChange }: { column: Column<T>; sort?: SortState; onSortChange: (sort: SortState) => void }) {
  const active = sort?.key === column.sortKey
  const Icon = !active ? ArrowUpDown : sort?.direction === 'asc' ? ArrowUp : ArrowDown
  return (
    <button
      type="button"
      onClick={() => onSortChange({ key: column.sortKey as string, direction: active && sort?.direction === 'asc' ? 'desc' : 'asc' })}
      className={cn('-mx-1 inline-flex items-center gap-1 rounded px-1 uppercase tracking-wide hover:text-fg focus-visible:outline-2 focus-visible:outline-accent', active && 'text-fg')}
    >
      {column.header}
      <Icon className="size-3.5" aria-hidden="true" />
    </button>
  )
}
