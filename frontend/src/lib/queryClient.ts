import { QueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'

/** Retry once for network/5xx errors; never for 4xx (401/403/404/409 won't change on retry). */
function shouldRetry(failureCount: number, error: unknown) {
  const status = isAxiosError(error) ? error.response?.status : undefined
  if (status !== undefined && status >= 400 && status < 500) return false
  return failureCount < 1
}

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        retry: shouldRetry,
        refetchOnWindowFocus: false,
      },
    },
  })
}
