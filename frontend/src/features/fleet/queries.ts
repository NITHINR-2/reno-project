import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fleetApi, type DispatchBody, type ListParams, type ProductionBody, type ReceiptBody, type UpdateBody } from './api'

export const fleetKeys = {
  all: ['fleet'] as const,
  summary: () => [...fleetKeys.all, 'summary'] as const,
  list: (params: ListParams) => [...fleetKeys.all, 'list', params] as const,
  detail: (id: number) => [...fleetKeys.all, 'detail', id] as const,
  sub: (id: number, part: string) => [...fleetKeys.all, 'detail', id, part] as const,
}

const refKeys = { ref: (name: string) => ['ref', name] as const }
const REF_STALE = 5 * 60_000

export const useFleetSummary = () => useQuery({ queryKey: fleetKeys.summary(), queryFn: fleetApi.summary })

export const useFleetList = (params: ListParams) =>
  useQuery({ queryKey: fleetKeys.list(params), queryFn: () => fleetApi.list(params), placeholderData: keepPreviousData })

export const useFleetDetail = (id: number | null) =>
  useQuery({ queryKey: fleetKeys.detail(id ?? 0), queryFn: () => fleetApi.detail(id as number), enabled: id !== null })

const parts = {
  history: fleetApi.history,
  stockOptions: fleetApi.stockOptions,
  quotations: fleetApi.quotations,
  receipts: fleetApi.receipts,
  productionOrders: fleetApi.productionOrders,
  dispatches: fleetApi.dispatches,
  audit: fleetApi.audit,
}
type SubPart = keyof typeof parts
type PartData = { [K in SubPart]: Awaited<ReturnType<(typeof parts)[K]>> }

/** Per-tab data of one request; each tab mounts only when opened, so data loads on demand. */
export function useFleetPart<P extends SubPart>(id: number, part: P) {
  return useQuery<PartData[P]>({
    queryKey: fleetKeys.sub(id, part),
    queryFn: () => parts[part](id) as Promise<PartData[P]>,
  })
}

export function useReferenceData() {
  const options = { staleTime: REF_STALE }
  return {
    regions: useQuery({ queryKey: refKeys.ref('regions'), queryFn: fleetApi.regions, ...options }),
    dealers: useQuery({ queryKey: refKeys.ref('dealers'), queryFn: fleetApi.dealers, ...options }),
    models: useQuery({ queryKey: refKeys.ref('models'), queryFn: fleetApi.models, ...options }),
    plants: useQuery({ queryKey: refKeys.ref('plants'), queryFn: fleetApi.plants, ...options }),
  }
}

/** Every write can change the row, the summary and the request's tabs, so the whole fleet cache is invalidated. */
function useFleetMutation<V, R>(fn: (vars: V) => Promise<R>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSettled: () => queryClient.invalidateQueries({ queryKey: fleetKeys.all }),
  })
}

export const useUpdateRequest = (id: number) => useFleetMutation((body: UpdateBody) => fleetApi.update(id, body))
export const useMapStock = (id: number) =>
  useFleetMutation((body: { version: number; lines: { stockId: number; quantity: number }[] }) => fleetApi.mapStock(id, body))
export const useCalculateQuotation = (id: number) => useFleetMutation(() => fleetApi.calculateQuotation(id))
export const useIssueQuotation = () =>
  useFleetMutation(({ quotationId, version }: { quotationId: number; version: number }) => fleetApi.issueQuotation(quotationId, version))
export const useRecordReceipt = (id: number) => useFleetMutation((body: ReceiptBody) => fleetApi.recordReceipt(id, body))
export const useApproveReceipt = () => useFleetMutation((receiptId: number) => fleetApi.approveReceipt(receiptId))
export const useScheduleProduction = (id: number) => useFleetMutation((body: ProductionBody) => fleetApi.scheduleProduction(id, body))
export const useScheduleDispatch = (id: number) => useFleetMutation((body: DispatchBody) => fleetApi.scheduleDispatch(id, body))
