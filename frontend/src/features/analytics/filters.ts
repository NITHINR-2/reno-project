import { useCallback, useMemo } from 'react'
import { useSearchParams } from 'react-router'
import { todayIST } from '@/lib/format'
import type { AnalyticsQuery } from './api'

/** Rolling windows ending today (IST), so a shared preset link stays current; Custom pins its dates in the URL. */
export const RANGES = {
  '30d': { label: '30 days', days: 30 },
  quarter: { label: 'Quarter', days: 90 },
  year: { label: 'Year', days: 365 },
  custom: { label: 'Custom', days: null },
} as const
export type Range = keyof typeof RANGES

const DATE = /^\d{4}-\d{2}-\d{2}$/

/** Adds days to a `YYYY-MM-DD` date without time-zone drift. */
export function addDays(date: string, days: number) {
  const d = new Date(`${date}T00:00:00Z`)
  d.setUTCDate(d.getUTCDate() + days)
  return d.toISOString().slice(0, 10)
}

export function presetDates(range: Exclude<Range, 'custom'>, today = todayIST()) {
  return { from: addDays(today, -(RANGES[range].days - 1)), to: today }
}

/** Dashboard filters live in the query string (`range`, `from`, `to`, `region`, `plant`), so a view can be shared. */
export function useDashboardFilters() {
  const [params, setParams] = useSearchParams()
  const rawRange = params.get('range')
  const range: Range = rawRange && rawRange in RANGES ? (rawRange as Range) : '30d'
  const region = params.get('region') ?? ''
  const plant = params.get('plant') ?? ''
  const customFrom = params.get('from') ?? ''
  const customTo = params.get('to') ?? ''

  const { query, error } = useMemo((): { query: AnalyticsQuery | null; error: string | null } => {
    const scope = { ...(region && { region }), ...(plant && { plant }) }
    if (range !== 'custom') return { query: { ...presetDates(range), ...scope }, error: null }
    if (!DATE.test(customFrom) || !DATE.test(customTo)) return { query: null, error: 'Choose both dates.' }
    if (customFrom > customTo) return { query: null, error: 'The start date must be on or before the end date.' }
    return { query: { from: customFrom, to: customTo, ...scope }, error: null }
  }, [range, customFrom, customTo, region, plant])

  const update = useCallback(
    (changes: Record<string, string>) =>
      setParams(
        (prev) => {
          const next = new URLSearchParams(prev)
          for (const [key, value] of Object.entries(changes)) {
            if (value) next.set(key, value)
            else next.delete(key)
          }
          return next
        },
        { replace: true },
      ),
    [setParams],
  )

  const setRange = useCallback(
    (next: Range) => {
      if (next === 'custom') {
        // Start the custom range from what is on screen.
        const current = query ?? presetDates('30d')
        update({ range: 'custom', from: customFrom || current.from, to: customTo || current.to })
      } else {
        update({ range: next === '30d' ? '' : next, from: '', to: '' })
      }
    },
    [query, customFrom, customTo, update],
  )

  return { range, region, plant, customFrom, customTo, query, error, setRange, update }
}
