import { useReducedMotion } from 'motion/react'
import { formatINR } from '@/lib/format'

/** ₹7.08 Cr, ₹45.20 L, or the full amount below a lakh. Display only; the server's figures are exact. */
export function formatCompactINR(value: number | null | undefined) {
  if (value == null) return '—'
  const abs = Math.abs(value)
  if (abs >= 1e7) return `₹${(value / 1e7).toFixed(2)} Cr`
  if (abs >= 1e5) return `₹${(value / 1e5).toFixed(2)} L`
  return formatINR(value)
}

export function formatHours(hours: number | null | undefined) {
  if (hours == null) return '—'
  return hours < 48 ? `${hours.toFixed(1)} h` : `${(hours / 24).toFixed(1)} days`
}

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
/** `2026-10` → `Oct 26` */
export const monthLabel = (month: string) => `${MONTHS[Number(month.slice(5, 7)) - 1]} ${month.slice(2, 4)}`

export const COLOR = { primary: 'var(--chart-1)', secondary: 'var(--chart-2)', grid: 'var(--chart-grid)', axis: 'var(--chart-axis)' }

/** Recessive axes: muted ink, no axis or tick lines. */
export const axis = {
  stroke: COLOR.axis,
  tick: { fill: COLOR.axis, fontSize: 12 },
  tickLine: false,
  axisLine: false,
} as const

export const grid = { stroke: COLOR.grid, strokeDasharray: '0', vertical: false } as const

/** Charts animate in on load (ease-out) unless the user prefers reduced motion. */
export function useChartAnimation() {
  const reduce = useReducedMotion()
  return { isAnimationActive: !reduce, animationDuration: 500, animationEasing: 'ease-out' as const }
}
