import { useCallback, useMemo } from 'react'
import { useSearchParams } from 'react-router'
import type { SortState } from '@/components/tables/DataTable'
import { STATUSES, type FleetStatus, type ListParams } from './api'

/** Stage buckets, matching the backend summary's partition of the non-terminal statuses. */
export const STAGES = {
  marketing: { label: 'Marketing review', statuses: ['PENDING_MARKETING_REVIEW', 'RETURNED_TO_MARKETING', 'MARKETING_QUALIFIED'] },
  executive: { label: 'Executive approval', statuses: ['PENDING_EXECUTIVE_APPROVAL', 'EXECUTIVE_APPROVED'] },
  finance: { label: 'Finance', statuses: ['PENDING_FINANCE_CLEARANCE', 'QUOTATION_ISSUED', 'PAYMENT_PENDING', 'FUNDS_VERIFIED'] },
  operations: { label: 'Production & dispatch', statuses: ['FINANCIALLY_CLEARED', 'PRODUCTION_SCHEDULED', 'DISPATCH_SCHEDULED'] },
  active: { label: 'All active', statuses: STATUSES.filter((s) => s !== 'COMPLETED' && s !== 'CANCELLED') },
} satisfies Record<string, { label: string; statuses: FleetStatus[] }>
export type Stage = keyof typeof STAGES

export const PAGE_SIZE = 20
export const DEFAULT_SORT: SortState = { key: 'createdAt', direction: 'desc' }

const FILTER_KEYS = ['q', 'status', 'sla', 'regionId', 'dealerId', 'modelId', 'plantId', 'createdFrom', 'createdTo'] as const
export type FilterKey = (typeof FILTER_KEYS)[number]

/**
 * `status` holds either `stage:<name>` or a single status. Everything lives in the query string,
 * so a filtered view (and the open drawer, `?request=<id>`) can be shared as a link.
 */
export function useFleetFilters(defaultStage?: Stage) {
  const [params, setParams] = useSearchParams()

  const status = params.get('status') ?? (defaultStage ? `stage:${defaultStage}` : '')
  const sortParam = params.get('sort')
  const sort: SortState = sortParam
    ? { key: sortParam.split(',')[0], direction: sortParam.endsWith(',asc') ? 'asc' : 'desc' }
    : DEFAULT_SORT
  const page = Math.max(0, Number(params.get('page') ?? 0) || 0)
  const openId = Number(params.get('request')) || null

  const listParams = useMemo<ListParams>(() => {
    const value = (key: FilterKey) => params.get(key) || undefined
    return {
      page,
      size: PAGE_SIZE,
      sort: `${sort.key},${sort.direction}`,
      q: value('q'),
      status: statusesFor(status),
      regionId: value('regionId'),
      dealerId: value('dealerId'),
      modelId: value('modelId'),
      plantId: value('plantId'),
      createdFrom: value('createdFrom'),
      createdTo: value('createdTo'),
      sla: value('sla')?.split(','),
    }
  }, [params, page, sort.key, sort.direction, status])

  const update = useCallback(
    (changes: Record<string, string | null>, resetPage = true) => {
      setParams(
        (current) => {
          const next = new URLSearchParams(current)
          for (const [key, value] of Object.entries(changes)) {
            if (value === null || value === '') next.delete(key)
            else next.set(key, value)
          }
          if (resetPage) next.delete('page')
          return next
        },
        { replace: true },
      )
    },
    [setParams],
  )

  const setFilter = useCallback(
    (key: FilterKey, value: string) => update({ [key]: value || (key === 'status' ? 'all' : null) }),
    [update],
  )

  const clear = useCallback(() => update(Object.fromEntries(FILTER_KEYS.map((k) => [k, k === 'status' ? 'all' : null]))), [update])

  return {
    values: Object.fromEntries(FILTER_KEYS.map((k) => [k, k === 'status' ? status : (params.get(k) ?? '')])) as Record<FilterKey, string>,
    listParams,
    sort,
    openId,
    setFilter,
    setSort: (next: SortState) => update({ sort: `${next.key},${next.direction}` }),
    setPage: (next: number) => update({ page: next > 0 ? String(next) : null }, false),
    openRequest: (id: number | null) => update({ request: id === null ? null : String(id) }, false),
    clear,
  }
}

function statusesFor(value: string): FleetStatus[] | undefined {
  if (!value || value === 'all') return undefined
  if (value.startsWith('stage:')) return STAGES[value.slice(6) as Stage]?.statuses
  return (STATUSES as readonly string[]).includes(value) ? [value as FleetStatus] : undefined
}
