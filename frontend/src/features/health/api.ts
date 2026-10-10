import { z } from 'zod'
import { api } from '@/lib/api'

export const healthSchema = z.object({
  status: z.enum(['UP', 'DOWN']),
  database: z.enum(['UP', 'DOWN']),
  version: z.string(),
  timestamp: z.string(),
})

export type Health = z.infer<typeof healthSchema>

export async function fetchHealth(): Promise<Health> {
  // 503 still carries a valid body when the database is down.
  const { data } = await api.get<unknown>('/health', {
    validateStatus: (status) => status === 200 || status === 503,
  })
  return healthSchema.parse(data)
}
