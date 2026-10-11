import { z } from 'zod'
import { api } from '@/lib/api'
import { STATUSES } from '@/features/fleet/api'

const status = z.enum(STATUSES)
const kpi = z.object({ value: z.number().nullable(), previous: z.number().nullable(), changePct: z.number().nullable() })
const demand = z.object({ id: z.number(), name: z.string(), units: z.number(), orderValue: z.number(), requests: z.number() })

/** Mirrors ExecutiveAnalyticsDto. Every figure is computed by the backend; the browser only formats. */
export const executiveSchema = z.object({
  period: z.object({
    from: z.string(),
    to: z.string(),
    previousFrom: z.string(),
    previousTo: z.string(),
    regionId: z.number().nullable(),
    plantId: z.number().nullable(),
  }),
  kpis: z.object({
    totalOrderValue: kpi,
    requestCount: kpi,
    avgHoursToClearance: kpi,
    slaCompliancePct: kpi,
    overdueRequests: kpi,
  }),
  monthlyOrderValue: z.array(z.object({ month: z.string(), orderValue: z.number(), requests: z.number() })),
  pipeline: z.array(z.object({ status, count: z.number() })),
  stageDurations: z.array(z.object({ status, avgHours: z.number(), samples: z.number(), slowest: z.boolean() })),
  topDealers: z.array(
    z.object({ dealerId: z.number(), dealerName: z.string(), regionName: z.string().nullable(), orderValue: z.number(), requests: z.number() }),
  ),
  demandByRegion: z.array(demand),
  demandByModel: z.array(demand),
  stockVsProduction: z.object({
    fromStock: z.number(),
    toProduce: z.number(),
    byModel: z.array(z.object({ modelId: z.number(), modelName: z.string(), fromStock: z.number(), toProduce: z.number() })),
  }),
})
export type ExecutiveAnalytics = z.infer<typeof executiveSchema>
export type Kpi = z.infer<typeof kpi>
export type Demand = z.infer<typeof demand>

export type AnalyticsQuery = { from: string; to: string; region?: string; plant?: string }

export const analyticsApi = {
  executive: async (q: AnalyticsQuery) =>
    executiveSchema.parse((await api.get<unknown>('/analytics/executive', { params: q })).data),
}
