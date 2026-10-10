import type { FleetRequestDetail, FleetRequestRow, FleetStatus } from '@/features/fleet/api'
import type { AuthUser } from '@/features/auth/api'
import { sessionHandlers } from './fakeServer'

export const page = <T,>(content: T[], extra: { page?: number; size?: number; totalElements?: number; totalPages?: number } = {}) => ({
  content,
  page: extra.page ?? 0,
  size: extra.size ?? 20,
  totalElements: extra.totalElements ?? content.length,
  totalPages: extra.totalPages ?? (content.length ? 1 : 0),
})

export const row = (overrides: Partial<FleetRequestRow> = {}): FleetRequestRow => ({
  id: 1,
  requestNumber: 'FLT-2026-0891',
  dealerId: 10,
  dealerName: 'Chennai North Dealership',
  regionName: 'Chennai Region',
  modelName: 'Renault Duster',
  variantName: 'RXZ Petrol',
  plantName: 'Pune Plant',
  quantity: 50,
  allocatedQty: 30,
  productionRequired: 20,
  requestedDeliveryDate: '2026-12-15',
  status: 'PENDING_MARKETING_REVIEW',
  version: 3,
  createdAt: '2026-10-01T05:00:00Z',
  updatedAt: '2026-10-02T05:00:00Z',
  ...overrides,
})

export const detail = (status: FleetStatus = 'PENDING_MARKETING_REVIEW', overrides: Partial<FleetRequestDetail> = {}): FleetRequestDetail => ({
  id: 1,
  requestNumber: 'FLT-2026-0891',
  status,
  dealer: { id: 10, code: 'CHN-N', name: 'Chennai North Dealership', regionId: 1, regionName: 'Chennai Region' },
  variant: { id: 5, code: 'DUS-RXZ-P', name: 'RXZ Petrol', modelId: 2, modelName: 'Renault Duster', unitPrice: 1200000 },
  plant: { id: 1, code: 'PUN', name: 'Pune Plant' },
  quantity: 50,
  allocatedQty: 30,
  productionRequired: 20,
  eligibleStockAvailable: 0,
  requiredDeposit: 5000000,
  depositReceived: 1000000,
  productionEstimateWeeks: 6,
  requestedDeliveryDate: null,
  notes: null,
  returnReason: null,
  cancelReason: null,
  createdBy: { id: 3, fullName: 'Meera Marketing' },
  assignedTo: null,
  stockMappedAt: '2026-10-02T05:00:00Z',
  allocations: [
    { id: 7, stockId: 70, locationId: 700, locationCode: 'CHN-YARD', locationName: 'Chennai Yard', quantity: 30, status: 'ACTIVE', allocatedBy: 'Meera', createdAt: '2026-10-02T05:00:00Z' },
  ],
  allowedActions: [],
  version: 3,
  createdAt: '2026-10-01T05:00:00Z',
  updatedAt: '2026-10-02T05:00:00Z',
  ...overrides,
})

export const metrics = {
  activeRequests: 12,
  inMarketing: 4,
  inExecutiveApproval: 3,
  inFinance: 2,
  inOperations: 3,
  unitsToProduce: 140,
  completed: 5,
  cancelled: 1,
  byStatus: {},
}

/** A signed-in `user` and a backend holding one request in the given detail state. */
export function fleetHandlers(user: AuthUser, request: FleetRequestDetail = detail(), rows: FleetRequestRow[] = [row()]) {
  const empty = () => [200, page([])] as [number, unknown]
  return {
    ...sessionHandlers(user),
    'GET /fleet-requests/summary': () => [200, metrics] as [number, unknown],
    'GET /fleet-requests': () => [200, page(rows, { totalElements: 45, totalPages: 3 })] as [number, unknown],
    [`GET /fleet-requests/${request.id}`]: () => [200, request] as [number, unknown],
    [`GET /fleet-requests/${request.id}/history`]: () =>
      [200, page([{ id: 1, fromStatus: null, toStatus: 'PENDING_MARKETING_REVIEW', action: 'CREATED', actorName: 'Meera Marketing', actorRole: 'MARKETING_MANAGER', reason: null, override: false, createdAt: '2026-10-01T05:00:00Z' }])] as [number, unknown],
    [`GET /fleet-requests/${request.id}/stock-options`]: empty,
    [`GET /fleet-requests/${request.id}/quotations`]: empty,
    [`GET /fleet-requests/${request.id}/payment-receipts`]: empty,
    [`GET /fleet-requests/${request.id}/production-orders`]: empty,
    [`GET /fleet-requests/${request.id}/dispatches`]: empty,
    'GET /admin/audit-logs': empty,
    'GET /regions': () => [200, page([{ id: 1, name: 'Chennai Region' }])] as [number, unknown],
    'GET /dealers': () => [200, page([{ id: 10, name: 'Chennai North Dealership' }])] as [number, unknown],
    'GET /vehicle-models': () => [200, page([{ id: 2, name: 'Renault Duster' }])] as [number, unknown],
    'GET /plants': () => [200, page([{ id: 1, name: 'Pune Plant' }])] as [number, unknown],
  }
}
