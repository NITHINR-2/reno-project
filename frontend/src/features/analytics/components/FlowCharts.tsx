import { Bar, BarChart, CartesianGrid, Cell, LabelList, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { formatCount, formatINR, statusLabel } from '@/lib/format'
import type { ExecutiveAnalytics } from '../api'
import { ChartCard, type TableColumn } from './ChartCard'
import { ChartTooltip } from './ChartTooltip'
import { axis, COLOR, formatCompactINR, formatHours, grid, monthLabel, useChartAnimation } from './chartKit'

type Month = ExecutiveAnalytics['monthlyOrderValue'][number]
type Stage = ExecutiveAnalytics['pipeline'][number]
type Duration = ExecutiveAnalytics['stageDurations'][number]

const MONTH_COLUMNS: TableColumn<Month>[] = [
  { header: 'Month', value: (m) => m.month, display: (m) => monthLabel(m.month) },
  { header: 'Order value (INR)', value: (m) => m.orderValue.toFixed(2), display: (m) => formatINR(m.orderValue), numeric: true },
  { header: 'Requests', value: (m) => m.requests, numeric: true },
]

/** Single series over twelve months: a 2px line, ≥8px markers, no legend (the title names it). */
export function MonthlyValueChart({ data }: { data: Month[] }) {
  const animation = useChartAnimation()
  const rows = data.some((m) => m.requests > 0) ? data : []
  const peak = rows.reduce<Month | null>((best, m) => (!best || m.orderValue > best.orderValue ? m : best), null)
  return (
    <ChartCard
      title="Monthly order value"
      description="Last 12 months to the end of the period, before GST"
      summary={`Monthly order value for the last 12 months.${peak ? ` Highest in ${monthLabel(peak.month)} at ${formatCompactINR(peak.orderValue)}.` : ''}`}
      rows={rows}
      columns={MONTH_COLUMNS}
      csvName="monthly-order-value.csv"
      className="lg:col-span-2"
    >
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={rows} margin={{ top: 8, right: 12, bottom: 0, left: 4 }}>
          <CartesianGrid {...grid} />
          <XAxis dataKey="month" tickFormatter={monthLabel} {...axis} />
          <YAxis tickFormatter={(v: number) => formatCompactINR(v)} width={84} {...axis} />
          <Tooltip
            cursor={{ stroke: COLOR.axis, strokeWidth: 1 }}
            content={<ChartTooltip formatLabel={monthLabel} formatValue={(v) => formatINR(v)} />}
          />
          <Line
            type="monotone"
            dataKey="orderValue"
            name="Order value"
            stroke={COLOR.primary}
            strokeWidth={2}
            dot={{ r: 4, fill: COLOR.primary, strokeWidth: 0 }}
            activeDot={{ r: 6, stroke: 'var(--surface)', strokeWidth: 2 }}
            {...animation}
          />
        </LineChart>
      </ResponsiveContainer>
    </ChartCard>
  )
}

const STAGE_COLUMNS: TableColumn<Stage>[] = [
  { header: 'Stage', value: (s) => statusLabel(s.status) },
  { header: 'Requests', value: (s) => s.count, numeric: true },
]

/** Requests created in the period by their current stage, in workflow order. */
export function PipelineChart({ data }: { data: Stage[] }) {
  const animation = useChartAnimation()
  const rows = data.some((s) => s.count > 0) ? data.map((s) => ({ ...s, label: statusLabel(s.status) })) : []
  return (
    <ChartCard
      title="Pipeline"
      description="Requests created in the period, by current stage"
      summary={`Pipeline: ${rows.filter((s) => s.count > 0).map((s) => `${s.label} ${s.count}`).join(', ')}.`}
      rows={rows}
      columns={STAGE_COLUMNS}
      csvName="pipeline.csv"
      height="h-[26rem]"
    >
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={rows} layout="vertical" margin={{ top: 0, right: 32, bottom: 0, left: 4 }} barCategoryGap={4}>
          <XAxis type="number" hide allowDecimals={false} />
          <YAxis type="category" dataKey="label" width={150} interval={0} {...axis} />
          <Tooltip cursor={{ fill: COLOR.grid }} content={<ChartTooltip />} />
          <Bar dataKey="count" name="Requests" fill={COLOR.primary} radius={[0, 4, 4, 0]} {...animation}>
            <LabelList dataKey="count" position="right" className="fill-fg text-xs" formatter={(v: unknown) => (Number(v) > 0 ? formatCount(Number(v)) : '')} />
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </ChartCard>
  )
}

const DURATION_COLUMNS: TableColumn<Duration>[] = [
  { header: 'Stage', value: (d) => statusLabel(d.status), display: (d) => `${statusLabel(d.status)}${d.slowest ? ' (slowest)' : ''}` },
  { header: 'Average hours', value: (d) => d.avgHours.toFixed(1), display: (d) => formatHours(d.avgHours), numeric: true },
  { header: 'Stage visits', value: (d) => d.samples, numeric: true },
]

/** Average time per stage; the slowest bar takes the second palette colour and is also named in the description. */
export function StageDurationChart({ data }: { data: Duration[] }) {
  const animation = useChartAnimation()
  const rows = data.map((d) => ({ ...d, label: statusLabel(d.status) }))
  const slowest = rows.find((d) => d.slowest)
  return (
    <ChartCard
      title="Average time in each stage"
      description={slowest ? <>Slowest: <strong className="text-fg">{slowest.label}</strong> at {formatHours(slowest.avgHours)} on average</> : 'Finished stage visits that began in the period'}
      summary={`Average hours per stage.${slowest ? ` Slowest is ${slowest.label} at ${formatHours(slowest.avgHours)}.` : ''}`}
      rows={rows}
      columns={DURATION_COLUMNS}
      csvName="stage-durations.csv"
      height="h-[26rem]"
    >
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={rows} layout="vertical" margin={{ top: 0, right: 48, bottom: 0, left: 4 }} barCategoryGap={4}>
          <XAxis type="number" hide />
          <YAxis type="category" dataKey="label" width={150} interval={0} {...axis} />
          <Tooltip cursor={{ fill: COLOR.grid }} content={<ChartTooltip formatValue={(v) => formatHours(v)} />} />
          <Bar dataKey="avgHours" name="Average time" radius={[0, 4, 4, 0]} {...animation}>
            {rows.map((d) => (
              <Cell key={d.status} fill={d.slowest ? COLOR.secondary : COLOR.primary} />
            ))}
            <LabelList dataKey="avgHours" position="right" className="fill-fg text-xs" formatter={(v: unknown) => formatHours(Number(v))} />
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </ChartCard>
  )
}
