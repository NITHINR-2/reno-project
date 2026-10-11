import { formatCount } from '@/lib/format'

type TooltipEntry = { name?: string | number; value?: number | string; color?: string; dataKey?: string | number }

/** Hover tooltip in the app's surface colours; values in text ink, the colour swatch carries identity. */
export function ChartTooltip({
  active,
  label,
  payload,
  formatValue = (v) => formatCount(v),
  formatLabel = (l) => l,
}: {
  active?: boolean
  label?: string | number
  payload?: TooltipEntry[]
  formatValue?: (value: number, entry: TooltipEntry) => string
  formatLabel?: (label: string) => string
}) {
  if (!active || !payload?.length) return null
  return (
    <div className="rounded-lg border border-line bg-surface px-3 py-2 text-xs text-fg shadow-md">
      {label != null && <p className="mb-1 font-semibold">{formatLabel(String(label))}</p>}
      <ul className="space-y-0.5">
        {payload.map((entry) => (
          <li key={String(entry.dataKey ?? entry.name)} className="flex items-center gap-2">
            <span aria-hidden="true" className="size-2.5 rounded-sm" style={{ background: entry.color }} />
            <span className="text-fg-muted">{entry.name}</span>
            <span className="ml-auto pl-3 font-medium tabular-nums">{formatValue(Number(entry.value), entry)}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}
