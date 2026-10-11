import { PackageX } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Dialog } from '@/components/ui/Dialog'
import { formatCount } from '@/lib/format'
import type { CartLine } from './cartContext'

export type ShortLine = { line: CartLine; available: number }

/**
 * Shown when the server refused the order because the plant has fewer free units than the dealer was shown. The dealer
 * either accepts what is free now (the rest is produced) or goes back to edit quantities.
 */
export function StockChangedDialog({
  shortLines,
  plantName,
  loading,
  onAccept,
  onEdit,
}: {
  shortLines: ShortLine[] | null
  plantName: string
  loading: boolean
  onAccept: () => void
  onEdit: () => void
}) {
  const single = shortLines?.length === 1 ? shortLines[0] : null
  const title = single ? `Only ${formatCount(single.available)} available` : 'Stock changed'
  return (
    <Dialog open={shortLines !== null} onClose={loading ? () => undefined : onEdit} title={title} role="alertdialog"
      description={`Someone ordered from ${plantName} since this page last refreshed.`}>
      <ul className="space-y-2 text-sm">
        {shortLines?.map(({ line, available }) => (
          <li key={line.key} className="flex gap-2 rounded-lg bg-surface-muted p-3">
            <PackageX className="mt-0.5 size-4 shrink-0 text-amber-600" aria-hidden="true" />
            <span>
              <span className="font-medium text-fg">
                {line.variantName}
                {line.colourName ? `, ${line.colourName}` : ''}
              </span>
              : {formatCount(available)} of the {formatCount(line.quantity)} you asked for can come from stock;{' '}
              {formatCount(line.quantity - available)} would be produced.
            </span>
          </li>
        ))}
      </ul>
      <div className="mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        <Button variant="secondary" onClick={onEdit} disabled={loading}>
          Edit quantity
        </Button>
        <Button onClick={onAccept} loading={loading}>
          {single ? `Order ${formatCount(single.available)} from stock, rest produced` : 'Order what is in stock, rest produced'}
        </Button>
      </div>
    </Dialog>
  )
}
