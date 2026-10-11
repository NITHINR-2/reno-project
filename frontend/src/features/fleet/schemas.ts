import { z } from 'zod'

export const STATUSES = [
  'PENDING_MARKETING_REVIEW',
  'RETURNED_TO_MARKETING',
  'MARKETING_QUALIFIED',
  'PENDING_EXECUTIVE_APPROVAL',
  'EXECUTIVE_APPROVED',
  'PENDING_FINANCE_CLEARANCE',
  'QUOTATION_ISSUED',
  'PAYMENT_PENDING',
  'FUNDS_VERIFIED',
  'FINANCIALLY_CLEARED',
  'PRODUCTION_SCHEDULED',
  'DISPATCH_SCHEDULED',
  'COMPLETED',
  'CANCELLED',
] as const
export type FleetStatus = (typeof STATUSES)[number]

export const SLA_STATUSES = ['ON_TRACK', 'AT_RISK', 'BREACHED'] as const
export type SlaStatus = (typeof SLA_STATUSES)[number]
/** Computed by the server; null when the stage has no SLA (untimed or terminal). */
const slaStatus = z.enum(SLA_STATUSES).nullish()

// Money arrives as JSON numbers from BigDecimal; the UI only displays it, never calculates with it.
const money = z.number()

export const summaryRowSchema = z.object({
  id: z.number(),
  requestNumber: z.string(),
  dealerId: z.number(),
  dealerName: z.string(),
  regionName: z.string().nullish(),
  modelName: z.string(),
  variantName: z.string(),
  plantName: z.string().nullish(),
  quantity: z.number(),
  allocatedQty: z.number(),
  productionRequired: z.number(),
  requestedDeliveryDate: z.string().nullish(),
  status: z.enum(STATUSES),
  stageEnteredAt: z.string().nullish(),
  slaDueAt: z.string().nullish(),
  slaStatus,
  version: z.number(),
  createdAt: z.string(),
  updatedAt: z.string(),
})
export type FleetRequestRow = z.infer<typeof summaryRowSchema>

export const metricsSchema = z.object({
  activeRequests: z.number(),
  inMarketing: z.number(),
  inExecutiveApproval: z.number(),
  inFinance: z.number(),
  inOperations: z.number(),
  unitsToProduce: z.number(),
  completed: z.number(),
  cancelled: z.number(),
  slaAtRisk: z.number(),
  slaBreached: z.number(),
  slaByStage: z.record(z.string(), z.object({ atRisk: z.number(), breached: z.number() })),
})
export type FleetMetrics = z.infer<typeof metricsSchema>

const allocationSchema = z.object({
  id: z.number(),
  stockId: z.number(),
  locationId: z.number(),
  locationCode: z.string(),
  locationName: z.string(),
  quantity: z.number(),
  status: z.string(),
  allocatedBy: z.string().nullish(),
  createdAt: z.string(),
})

export const detailSchema = z.object({
  id: z.number(),
  requestNumber: z.string(),
  status: z.enum(STATUSES),
  stageEnteredAt: z.string().nullish(),
  slaWarningAt: z.string().nullish(),
  slaDueAt: z.string().nullish(),
  slaStatus,
  dealer: z.object({ id: z.number(), code: z.string(), name: z.string(), regionId: z.number().nullish(), regionName: z.string().nullish() }),
  variant: z.object({ id: z.number(), code: z.string(), name: z.string(), modelId: z.number(), modelName: z.string(), unitPrice: money }),
  plant: z.object({ id: z.number(), code: z.string(), name: z.string() }).nullish(),
  quantity: z.number(),
  allocatedQty: z.number(),
  productionRequired: z.number(),
  eligibleStockAvailable: z.number(),
  requiredDeposit: money,
  depositReceived: money,
  productionEstimateWeeks: z.number().nullish(),
  requestedDeliveryDate: z.string().nullish(),
  notes: z.string().nullish(),
  returnReason: z.string().nullish(),
  cancelReason: z.string().nullish(),
  createdBy: z.object({ id: z.number(), fullName: z.string() }).nullish(),
  assignedTo: z.object({ id: z.number(), fullName: z.string() }).nullish(),
  stockMappedAt: z.string().nullish(),
  allocations: z.array(allocationSchema),
  allowedActions: z.array(z.string()),
  version: z.number(),
  createdAt: z.string(),
  updatedAt: z.string(),
})
export type FleetRequestDetail = z.infer<typeof detailSchema>

export const stockSchema = z.object({
  id: z.number(),
  locationId: z.number(),
  locationCode: z.string(),
  locationName: z.string(),
  locationType: z.string(),
  variantName: z.string(),
  quantityAvailable: z.number(),
  quantityAllocated: z.number(),
})
export type StockOption = z.infer<typeof stockSchema>

export const quotationSchema = z.object({
  id: z.number(),
  quotationNumber: z.string(),
  status: z.string(),
  paymentStatus: z.string().nullish(),
  gstRate: z.number(),
  baseAmount: money,
  gstAmount: money,
  totalAmount: money,
  requiredDeposit: money,
  depositReceived: money,
  depositShortfall: money,
  issueDate: z.string().nullish(),
  validUntil: z.string().nullish(),
  issuedBy: z.string().nullish(),
  createdBy: z.string().nullish(),
  termsText: z.string().nullish(),
  /** SHA-256 of the PDF frozen on issue; null for drafts. */
  pdfSha256: z.string().nullish(),
  lines: z
    .array(
      z.object({
        lineNo: z.number(),
        description: z.string(),
        quantity: z.number(),
        unitPrice: money,
        baseAmount: money,
        gstRate: z.number(),
        gstAmount: money,
        lineTotal: money,
      }),
    )
    .default([]),
  version: z.number(),
  createdAt: z.string(),
})
export type Quotation = z.infer<typeof quotationSchema>

export const RECEIPT_TYPES = ['DEPOSIT', 'PAYMENT'] as const
export const PAYMENT_METHODS = ['NEFT', 'RTGS', 'IMPS', 'UPI', 'CHEQUE', 'DEMAND_DRAFT'] as const

export const receiptSchema = z.object({
  id: z.number(),
  receiptNumber: z.string(),
  receiptType: z.enum(RECEIPT_TYPES),
  paymentMethod: z.string(),
  transactionReference: z.string(),
  amount: money,
  receivedDate: z.string(),
  status: z.string(),
  recordedBy: z.string().nullish(),
  reviewedBy: z.string().nullish(),
  version: z.number(),
})
export type Receipt = z.infer<typeof receiptSchema>

export const historySchema = z.object({
  id: z.number(),
  fromStatus: z.string().nullish(),
  toStatus: z.string().nullish(),
  action: z.string(),
  actorName: z.string().nullish(),
  actorRole: z.string().nullish(),
  reason: z.string().nullish(),
  override: z.boolean(),
  createdAt: z.string(),
})
export type HistoryEntry = z.infer<typeof historySchema>

export const productionOrderSchema = z.object({
  id: z.number(),
  productionNumber: z.string(),
  plantName: z.string(),
  quantity: z.number(),
  scheduledStart: z.string(),
  estimatedCompletion: z.string(),
  status: z.string(),
  scheduledBy: z.string().nullish(),
})
export const dispatchSchema = z.object({
  id: z.number(),
  dispatchNumber: z.string(),
  fromLocationName: z.string().nullish(),
  quantity: z.number(),
  scheduledDate: z.string(),
  carrier: z.string().nullish(),
  vehicleReference: z.string().nullish(),
  status: z.string(),
  scheduledBy: z.string().nullish(),
})
export type ProductionOrder = z.infer<typeof productionOrderSchema>
export type Dispatch = z.infer<typeof dispatchSchema>

export const auditSchema = z.object({
  id: z.number(),
  actorUsername: z.string().nullish(),
  actorRole: z.string().nullish(),
  action: z.string(),
  reason: z.string().nullish(),
  before: z.unknown().nullish(),
  after: z.unknown().nullish(),
  createdAt: z.string(),
})
export type AuditEntry = z.infer<typeof auditSchema>

export const refSchema = z.object({ id: z.number(), name: z.string() })
export type Ref = z.infer<typeof refSchema>
