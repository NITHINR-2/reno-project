import { z } from 'zod'
import { api } from '@/lib/api'
import { blobErrorToJson, fileNameFrom } from '@/lib/download'
import { pageSchema, type Page } from '@/lib/page'

export const FUEL_TYPES = ['PETROL', 'DIESEL', 'CNG', 'HYBRID', 'ELECTRIC'] as const
export const TRANSMISSIONS = ['MANUAL', 'AUTOMATIC', 'AMT', 'CVT', 'DCT'] as const
export const BODY_TYPES = ['HATCHBACK', 'SEDAN', 'SUV', 'COMPACT_SUV', 'MPV', 'VAN', 'PICKUP'] as const
/** Mirrors the backend's DealerRequestStatus, in pipeline order. */
export const DEALER_STATUSES = [
  'IN_REVIEW',
  'PROCESSING',
  'AWAITING_PAYMENT',
  'CONFIRMED',
  'IN_PRODUCTION',
  'DISPATCH_SCHEDULED',
  'DELIVERED',
  'CANCELLED',
] as const
export const RECEIPT_TYPES = ['DEPOSIT', 'PAYMENT'] as const
export const PAYMENT_METHODS = ['NEFT', 'RTGS', 'IMPS', 'UPI', 'CHEQUE', 'DEMAND_DRAFT'] as const

export type FuelType = (typeof FUEL_TYPES)[number]
export type Transmission = (typeof TRANSMISSIONS)[number]
export type DealerStatus = (typeof DEALER_STATUSES)[number]

const variantSchema = z.object({
  id: z.number(),
  code: z.string(),
  name: z.string(),
  fuelType: z.enum(FUEL_TYPES),
  transmission: z.enum(TRANSMISSIONS),
  unitPrice: z.number(),
})
const colourSchema = z.object({ id: z.number(), name: z.string(), hexCode: z.string() })

export const catalogueModelSchema = z.object({
  id: z.number(),
  code: z.string(),
  name: z.string(),
  description: z.string().nullish(),
  bodyType: z.enum(BODY_TYPES).nullish(),
  seatingCapacity: z.number().nullish(),
  imageUrl: z.string().nullish(),
  variants: z.array(variantSchema),
  colours: z.array(colourSchema),
})
export type CatalogueModel = z.infer<typeof catalogueModelSchema>
export type CatalogueVariant = z.infer<typeof variantSchema>
export type CatalogueColour = z.infer<typeof colourSchema>

const requestSchema = z.object({
  id: z.number(),
  requestNumber: z.string(),
  orderId: z.number().nullish(),
  orderNumber: z.string().nullish(),
  lineNo: z.number().nullish(),
  modelName: z.string(),
  variantCode: z.string(),
  variantName: z.string(),
  colour: z.string().nullish(),
  quantity: z.number(),
  plantName: z.string().nullish(),
  fromStock: z.number(),
  toProduce: z.number(),
  status: z.enum(DEALER_STATUSES),
  requestedDeliveryDate: z.string().nullish(),
  createdAt: z.string(),
  canCancel: z.boolean(),
  canUploadPaymentProof: z.boolean(),
  version: z.number(),
})
export type DealerRequest = z.infer<typeof requestSchema>

export const orderSummarySchema = z.object({
  id: z.number(),
  orderNumber: z.string(),
  status: z.enum(DEALER_STATUSES),
  lineCount: z.number(),
  totalQuantity: z.number(),
  requestedDeliveryDate: z.string().nullish(),
  createdAt: z.string(),
})
export type OrderSummary = z.infer<typeof orderSummarySchema>

export const orderDetailSchema = z.object({
  id: z.number(),
  orderNumber: z.string(),
  status: z.enum(DEALER_STATUSES),
  requestedDeliveryDate: z.string().nullish(),
  notes: z.string().nullish(),
  createdAt: z.string(),
  lines: z.array(requestSchema),
})
export type OrderDetail = z.infer<typeof orderDetailSchema>

const quotationSchema = z.object({
  id: z.number(),
  quotationNumber: z.string(),
  issueDate: z.string().nullish(),
  validUntil: z.string().nullish(),
  baseAmount: z.number(),
  gstRate: z.number(),
  gstAmount: z.number(),
  totalAmount: z.number(),
  requiredDeposit: z.number(),
  depositReceived: z.number(),
  depositShortfall: z.number(),
  paymentStatus: z.string(),
})
export type DealerQuotation = z.infer<typeof quotationSchema>

export const receiptSchema = z.object({
  id: z.number(),
  receiptNumber: z.string(),
  receiptType: z.enum(RECEIPT_TYPES),
  paymentMethod: z.enum(PAYMENT_METHODS),
  transactionReference: z.string(),
  amount: z.number(),
  receivedDate: z.string(),
  status: z.string(),
  hasProof: z.boolean(),
  createdAt: z.string(),
})
export type DealerReceipt = z.infer<typeof receiptSchema>

export const requestDetailSchema = z.object({
  request: requestSchema,
  quotation: quotationSchema.nullish(),
  receipts: z.array(receiptSchema),
})
export type RequestDetail = z.infer<typeof requestDetailSchema>

export const plantSchema = z.object({ id: z.number(), code: z.string(), name: z.string(), city: z.string(), state: z.string() })
export type Plant = z.infer<typeof plantSchema>

export const availabilitySchema = z.object({
  plantId: z.number(),
  asOf: z.string(),
  items: z.array(z.object({ variantId: z.number(), colourId: z.number().nullish(), available: z.number() })),
})
export type PlantAvailability = z.infer<typeof availabilitySchema>

/** Free units of a variant in a colour (or uncoloured) at the plant, from the last availability fetch. */
export function availableFor(data: PlantAvailability | undefined, variantId: number, colourId: number | undefined): number | undefined {
  if (!data) return undefined
  const item = data.items.find((i) => i.variantId === variantId && (i.colourId ?? undefined) === colourId)
  return item?.available ?? 0
}

/** The 409 body when the plant's stock changed between display and order (one entry per short line). */
export const stockConflictSchema = z.object({
  plantId: z.number(),
  lines: z.array(
    z.object({
      lineNo: z.number(),
      variantId: z.number(),
      colourId: z.number().nullish(),
      quantity: z.number(),
      expectedFromStock: z.number(),
      available: z.number(),
    }),
  ),
})
export type StockConflict = z.infer<typeof stockConflictSchema>

export type CatalogueQuery = {
  page: number
  q?: string
  bodyType?: string
  fuelType?: string
  transmission?: string
  minPrice?: string
  maxPrice?: string
}
export type OrderQuery = { page: number; q?: string }
export type OrderLineBody = { variantId: number; quantity: number; colourId?: number; expectedFromStock?: number }
export type CreateOrderBody = { plantId?: number; lines: OrderLineBody[]; requestedDeliveryDate?: string; notes?: string }
export type ReceiptBody = {
  receiptType: (typeof RECEIPT_TYPES)[number]
  paymentMethod: (typeof PAYMENT_METHODS)[number]
  transactionReference: string
  amount: number
  receivedDate: string
}

const clean = (params: object) => Object.fromEntries(Object.entries(params).filter(([, v]) => v !== '' && v !== undefined))

async function getPage<T extends z.ZodType>(url: string, item: T, params: object): Promise<Page<z.infer<T>>> {
  const { data } = await api.get<unknown>(url, { params: clean(params) })
  return pageSchema(item).parse(data)
}

export const dealerApi = {
  catalogue: (q: CatalogueQuery) => getPage('/dealer/catalogue', catalogueModelSchema, { size: 12, sort: 'name', ...q }),
  plants: async () => z.array(plantSchema).parse((await api.get<unknown>('/dealer/plants')).data),
  availability: async (plantId: number) =>
    availabilitySchema.parse((await api.get<unknown>('/dealer/catalogue/availability', { params: { plantId } })).data),
  orders: (q: OrderQuery) => getPage('/dealer/orders', orderSummarySchema, { size: 20, ...q }),
  order: async (id: number) => orderDetailSchema.parse((await api.get<unknown>(`/dealer/orders/${id}`)).data),
  request: async (id: number) => requestDetailSchema.parse((await api.get<unknown>(`/dealer/requests/${id}`)).data),
  createOrder: async (body: CreateOrderBody) => orderDetailSchema.parse((await api.post<unknown>('/dealer/orders', body)).data),
  cancel: async (id: number, reason: string, version: number) =>
    requestDetailSchema.parse((await api.post<unknown>(`/dealer/requests/${id}/cancel`, { reason, version })).data),
  /** Multipart: the file plus the receipt as a JSON part. `onProgress` gets 0–1. */
  uploadProof: async (id: number, file: File, receipt: ReceiptBody, onProgress?: (fraction: number) => void) => {
    const form = new FormData()
    form.append('file', file)
    form.append('receipt', new Blob([JSON.stringify(receipt)], { type: 'application/json' }))
    const { data } = await api.post<unknown>(`/dealer/requests/${id}/payment-proofs`, form, {
      onUploadProgress: (event) => onProgress?.(event.total ? event.loaded / event.total : (event.progress ?? 0)),
    })
    return receiptSchema.parse(data)
  },
  quotationPdf: async (requestId: number) => {
    try {
      const res = await api.get<Blob>(`/dealer/requests/${requestId}/quotation/pdf`, {
        responseType: 'blob',
        headers: { Accept: 'application/pdf, application/problem+json' },
      })
      const disposition = res.headers['content-disposition'] as string | undefined
      return { blob: res.data, fileName: fileNameFrom(disposition) ?? `quotation-${requestId}.pdf` }
    } catch (error) {
      throw await blobErrorToJson(error)
    }
  },
}
