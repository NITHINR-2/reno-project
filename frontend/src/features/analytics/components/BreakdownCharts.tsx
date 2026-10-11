import { useState } from 'react'
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { cn } from '@/lib/cn'
import { formatCount, formatINR } from '@/lib/format'
import type { Demand, ExecutiveAnalytics } from '../api'
import { ChartCard, type TableColumn } from './ChartCard'
import { ChartTooltip } from './ChartTooltip'
import { axis, COLOR, formatCompactINR, grid, useChartAnimation } from './chartKit'

type Dealer = ExecutiveAnalytics['topDealers'][number]
type Split = ExecutiveAnalytics['stockVsProduction']['byModel'][number]

const DEALER_COLUMNS: TableColumn<Dealer>[] = [
  { header: 'Dealer', value: (d) => d.dealerName },
  { header: 'Region', value: (d) => d.regionName ?? '' },
  { header: 'Order value (INR)', value: (d) => d.orderValue.toFixed(2), display: (d) => formatINR(d.orderValue), numeric: true },
  { header: 'Requests', value: (d) => d.requests, numeric: true },
]

export function TopDealersChart({ data }: { data: Dealer[] }) {
  const animation = useChartAnimation()
  return (
    <ChartCard
      title="Top 10 dealers"
      description="By order value in the period, before GST"
      summary={`Top dealers by order value: ${data.slice(0, 3).map((d) => `${d.dealerName} ${formatCompactINR(d.orderValue)}`).join(', ')}.`}
      rows={data}
      columns={DEALER_COLUMNS}
      csvName="top-dealers.csv"
      height="h-[26rem]"
    >
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} layout="vertical" margin={{ top: 0, right: 16, bottom: 0, left: 4 }} barCategoryGap={4}>
          <CartesianGrid {...grid} vertical horizontal={false} />
          <XAxis type="number" tickFormatter={(v: number) => formatCompactINR(v)} {...axis} />
          <YAxis type="category" dataKey="dealerName" width={160} interval={0} {...axis} />
          <Tooltip cursor={{ fill: COLOR.grid }} content={<ChartTooltip formatValue={(v) => formatINR(v)} />} />
          <Bar dataKey="orderValue" name="Order value" fill={COLOR.primary} radius={[0, 4, 4, 0]} {...animation} />
        </BarChart>
      </ResponsiveContainer>
    </ChartCard>
  )
}

const DEMAND_COLUMNS = (dimension: string): TableColumn<Demand>[] => [
  { header: dimension, value: (d) => d.name },
  { header: 'Units', value: (d) => d.units, display: (d) => formatCount(d.units), numeric: true },
  { header: 'Order value (INR)', value: (d) => d.orderValue.toFixed(2), display: (d) => formatINR(d.orderValue), numeric: true },
  { header: 'Requests', value: (d) => d.requests, numeric: true },
]

/** Units requested by region or by model; a table first, with the bar chart one click away. */
export function DemandCard({ byRegion, byModel }: { byRegion: Demand[]; byModel: Demand[] }) {
  const animation = useChartAnimation()
  const [dimension, setDimension] = useState<'region' | 'model'>('region')
  const rows = dimension === 'region' ? byRegion : byModel
  const label = dimension === 'region' ? 'Region' : 'Model'
  return (
    <ChartCard
      key={dimension}
      title={`Demand by ${dimension}`}
      description="Units requested in the period (excluding cancelled)"
      summary={`Units by ${dimension}: ${rows.map((d) => `${d.name} ${d.units}`).join(', ')}.`}
      rows={rows}
      columns={DEMAND_COLUMNS(label)}
      csvName={`demand-by-${dimension}.csv`}
      tableFirst
      toolbar={
        <div role="group" aria-label="Group demand by" className="mr-1 inline-flex rounded-lg border border-line p-0.5">
          {(['region', 'model'] as const).map((d) => (
            <button
              key={d}
              type="button"
              aria-pressed={dimension === d}
              onClick={() => setDimension(d)}
              className={cn(
                'min-h-9 rounded-md px-3 text-xs font-medium capitalize transition-colors duration-150 focus-visible:outline-2 focus-visible:outline-accent',
                dimension === d ? 'bg-accent text-charcoal' : 'text-fg-muted hover:bg-surface-muted',
              )}
            >
              {d}
            </button>
          ))}
        </div>
      }
    >
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={rows} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
          <CartesianGrid {...grid} />
          <XAxis dataKey="name" interval={0} {...axis} />
          <YAxis allowDecimals={false} width={48} {...axis} />
          <Tooltip cursor={{ fill: COLOR.grid }} content={<ChartTooltip />} />
          <Bar dataKey="units" name="Units" fill={COLOR.primary} radius={[4, 4, 0, 0]} maxBarSize={48} {...animation} />
        </BarChart>
      </ResponsiveContainer>
    </ChartCard>
  )
}

const SPLIT_COLUMNS: TableColumn<Split>[] = [
  { header: 'Model', value: (s) => s.modelName },
  { header: 'From stock', value: (s) => s.fromStock, display: (s) => formatCount(s.fromStock), numeric: true },
  { header: 'To produce', value: (s) => s.toProduce, display: (s) => formatCount(s.toProduce), numeric: true },
]

/** Two series, so a legend plus the totals named in text; a 2px surface gap separates the stacked segments. */
export function StockVsProductionChart({ data }: { data: ExecutiveAnalytics['stockVsProduction'] }) {
  const animation = useChartAnimation()
  const total = data.fromStock + data.toProduce
  const share = total > 0 ? Math.round((data.fromStock * 100) / total) : 0
  const rows = total > 0 ? data.byModel : []
  return (
    <ChartCard
      title="Stock against production"
      description={total > 0 ? `${formatCount(data.fromStock)} units from stock · ${formatCount(data.toProduce)} to produce (${share}% from stock)` : 'Units supplied from stock and units to be produced'}
      summary={`${data.fromStock} units from stock and ${data.toProduce} to produce, ${share}% from stock.`}
      rows={rows}
      columns={SPLIT_COLUMNS}
      csvName="stock-vs-production.csv"
      className="lg:col-span-2"
    >
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={rows} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
          <CartesianGrid {...grid} />
          <XAxis dataKey="modelName" interval={0} {...axis} />
          <YAxis allowDecimals={false} width={48} {...axis} />
          <Tooltip cursor={{ fill: COLOR.grid }} content={<ChartTooltip />} />
          <Legend iconType="square" wrapperStyle={{ fontSize: 12, color: COLOR.axis }} />
          <Bar dataKey="fromStock" name="From stock" stackId="units" fill={COLOR.primary} stroke="var(--surface)" strokeWidth={2} maxBarSize={48} {...animation} />
          <Bar dataKey="toProduce" name="To produce" stackId="units" fill={COLOR.secondary} stroke="var(--surface)" strokeWidth={2} radius={[4, 4, 0, 0]} maxBarSize={48} {...animation} />
        </BarChart>
      </ResponsiveContainer>
    </ChartCard>
  )
}
