import { BarChart3, Download, Table2 } from 'lucide-react'
import { AnimatePresence, motion } from 'motion/react'
import { useId, useState, type ReactNode } from 'react'
import { Button } from '@/components/ui/Button'
import { cn } from '@/lib/cn'
import { downloadCsv, type CsvColumn } from '@/lib/csv'
import { fade } from '@/lib/motion'

export type TableColumn<T> = CsvColumn<T> & { display?: (row: T) => ReactNode; numeric?: boolean }

type ChartCardProps<T> = {
  title: string
  description?: ReactNode
  /** Short spoken summary of what the chart shows. */
  summary: string
  rows: T[]
  columns: TableColumn<T>[]
  csvName: string
  /** Start in the table view (for widgets that are tables first). */
  tableFirst?: boolean
  height?: string
  className?: string
  toolbar?: ReactNode
  children: ReactNode
}

/**
 * A dashboard widget: chart, an equivalent table view (also what screen readers and CSV export use), and an empty
 * state. The plot area has a fixed height so loading, empty and loaded states never shift the layout.
 */
export function ChartCard<T>({
  title,
  description,
  summary,
  rows,
  columns,
  csvName,
  tableFirst = false,
  height = 'h-72',
  className,
  toolbar,
  children,
}: ChartCardProps<T>) {
  const [table, setTable] = useState(tableFirst)
  const titleId = useId()
  const empty = rows.length === 0

  return (
    <section aria-labelledby={titleId} className={cn('flex flex-col rounded-xl border border-line bg-surface p-4 shadow-sm', className)}>
      <div className="mb-3 flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <h2 id={titleId} className="text-base font-semibold text-fg">
            {title}
          </h2>
          {description && <div className="mt-0.5 text-xs text-fg-muted">{description}</div>}
        </div>
        <div className="flex flex-wrap items-center gap-1">
          {toolbar}
          <Button
            size="sm"
            variant="ghost"
            aria-pressed={table}
            aria-label={table ? `Show ${title} as a chart` : `Show ${title} as a table`}
            icon={table ? <BarChart3 className="size-4" aria-hidden="true" /> : <Table2 className="size-4" aria-hidden="true" />}
            onClick={() => setTable((t) => !t)}
            disabled={empty}
          >
            {table ? 'Chart' : 'Table'}
          </Button>
          <Button
            size="sm"
            variant="ghost"
            aria-label={`Export ${title} as CSV`}
            icon={<Download className="size-4" aria-hidden="true" />}
            onClick={() => downloadCsv(csvName, columns, rows)}
            disabled={empty}
          >
            CSV
          </Button>
        </div>
      </div>
      <div className={cn('relative', !table && height)}>
        <AnimatePresence mode="wait" initial={false}>
          {empty ? (
            <motion.p key="empty" variants={fade} initial="initial" animate="animate" exit="exit" className={cn('flex items-center justify-center text-sm text-fg-muted', height)}>
              No data for this period
            </motion.p>
          ) : table ? (
            <motion.div key="table" variants={fade} initial="initial" animate="animate" exit="exit" className="max-h-80 overflow-auto rounded-lg border border-line">
              <DataTableView title={title} rows={rows} columns={columns} />
            </motion.div>
          ) : (
            <motion.figure key="chart" variants={fade} initial="initial" animate="animate" exit="exit" role="img" aria-label={summary} className="h-full">
              {children}
            </motion.figure>
          )}
        </AnimatePresence>
      </div>
    </section>
  )
}

function DataTableView<T>({ title, rows, columns }: { title: string; rows: T[]; columns: TableColumn<T>[] }) {
  return (
    <table className="w-full text-sm" aria-label={title}>
      <thead className="sticky top-0 bg-surface-muted text-xs uppercase tracking-wide text-fg-muted">
        <tr>
          {columns.map((c) => (
            <th key={c.header} scope="col" className={cn('px-3 py-2', c.numeric ? 'text-right' : 'text-left')}>
              {c.header}
            </th>
          ))}
        </tr>
      </thead>
      <tbody className="divide-y divide-line tabular-nums">
        {rows.map((row, i) => (
          <tr key={i}>
            {columns.map((c) => (
              <td key={c.header} className={cn('px-3 py-2', c.numeric ? 'text-right' : 'text-left')}>
                {c.display ? c.display(row) : c.value(row)}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  )
}
