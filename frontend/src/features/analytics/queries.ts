import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { analyticsApi, type AnalyticsQuery } from './api'

export const analyticsKeys = {
  all: ['analytics'] as const,
  executive: (q: AnalyticsQuery | null) => ['analytics', 'executive', q] as const,
}

/** Read-only; previous figures stay on screen while a new filter loads, so the layout never jumps. */
export function useExecutiveAnalytics(q: AnalyticsQuery | null) {
  return useQuery({
    queryKey: analyticsKeys.executive(q),
    queryFn: () => analyticsApi.executive(q as AnalyticsQuery),
    enabled: q !== null,
    placeholderData: keepPreviousData,
    staleTime: 60_000,
  })
}
