import { z } from 'zod'
import { api } from '@/lib/api'
import { pageSchema, type Page } from '@/lib/page'
import {
  auditSchema,
  detailSchema,
  dispatchSchema,
  historySchema,
  metricsSchema,
  productionOrderSchema,
  quotationSchema,
  receiptSchema,
  refSchema,
  stockSchema,
  summaryRowSchema,
  type FleetStatus,
  type PAYMENT_METHODS,
  type RECEIPT_TYPES,
} from './schemas'

export * from './schemas'

export type ListParams = {
  page: number
  size: number
  sort: string
  q?: string
  status?: FleetStatus[]
  regionId?: string
  dealerId?: string
  modelId?: string
  plantId?: string
  createdFrom?: string
  createdTo?: string
}

async function getPage<T extends z.ZodType>(url: string, item: T, params?: object): Promise<Page<z.infer<T>>> {
  // `paramsSerializer` repeats array keys (status=A&status=B), which Spring binds to a list.
  const { data } = await api.get<unknown>(url, { params, paramsSerializer: { indexes: null } })
  return pageSchema(item).parse(data)
}

export const fleetApi = {
  summary: async () => metricsSchema.parse((await api.get<unknown>('/fleet-requests/summary')).data),
  list: (params: ListParams) => getPage('/fleet-requests', summaryRowSchema, params),
  detail: async (id: number) => detailSchema.parse((await api.get<unknown>(`/fleet-requests/${id}`)).data),
  history: (id: number) => getPage(`/fleet-requests/${id}/history`, historySchema, { size: 100 }),
  stockOptions: (id: number) => getPage(`/fleet-requests/${id}/stock-options`, stockSchema, { size: 50 }),
  quotations: (id: number) => getPage(`/fleet-requests/${id}/quotations`, quotationSchema, { size: 20, sort: 'id,desc' }),
  receipts: (id: number) => getPage(`/fleet-requests/${id}/payment-receipts`, receiptSchema, { size: 50 }),
  productionOrders: (id: number) => getPage(`/fleet-requests/${id}/production-orders`, productionOrderSchema),
  dispatches: (id: number) => getPage(`/fleet-requests/${id}/dispatches`, dispatchSchema),
  audit: (id: number) =>
    getPage('/admin/audit-logs', auditSchema, { entityType: 'FLEET_REQUEST', entityId: String(id), size: 50 }),
  regions: () => getPage('/regions', refSchema, { size: 100 }),
  plants: () => getPage('/plants', refSchema, { size: 100 }),
  dealers: () => getPage('/dealers', refSchema, { size: 100, sort: 'name' }),
  models: () => getPage('/vehicle-models', refSchema, { size: 100, sort: 'name' }),

  update: async (id: number, body: UpdateBody) => detailSchema.parse((await api.patch<unknown>(`/fleet-requests/${id}`, body)).data),
  mapStock: async (id: number, body: { version: number; lines: { stockId: number; quantity: number }[] }) =>
    detailSchema.parse((await api.put<unknown>(`/fleet-requests/${id}/stock-allocations`, body)).data),
  calculateQuotation: async (id: number) =>
    quotationSchema.parse((await api.post<unknown>(`/fleet-requests/${id}/quotations/calculate`, {})).data),
  issueQuotation: async (quotationId: number, version: number) =>
    quotationSchema.parse((await api.post<unknown>(`/quotations/${quotationId}/issue`, { version })).data),
  recordReceipt: async (id: number, body: ReceiptBody) =>
    receiptSchema.parse((await api.post<unknown>(`/fleet-requests/${id}/payment-receipts`, body)).data),
  approveReceipt: async (receiptId: number) =>
    receiptSchema.parse((await api.post<unknown>(`/payment-receipts/${receiptId}/approve`, {})).data),
  scheduleProduction: async (id: number, body: ProductionBody) =>
    detailSchema.parse((await api.post<unknown>(`/fleet-requests/${id}/schedule-production`, body)).data),
  scheduleDispatch: async (id: number, body: DispatchBody) =>
    detailSchema.parse((await api.post<unknown>(`/fleet-requests/${id}/schedule-dispatch`, body)).data),
}

/** Money travels as a decimal string so it never passes through a float on the way to BigDecimal. */
export type UpdateBody = {
  version: number
  quantity?: number
  requiredDeposit?: string
  productionEstimateWeeks?: number | null
  requestedDeliveryDate?: string | null
  notes?: string | null
}
export type ReceiptBody = {
  receiptType: (typeof RECEIPT_TYPES)[number]
  paymentMethod: (typeof PAYMENT_METHODS)[number]
  transactionReference: string
  amount: string
  receivedDate: string
}
export type ProductionBody = { scheduledStart: string; estimatedCompletion: string; notes?: string; version: number }
export type DispatchBody = { scheduledDate: string; carrier?: string; vehicleReference?: string; notes?: string; version: number }
