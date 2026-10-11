import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { saveBlob } from '@/lib/download'
import { dealerApi, type CatalogueQuery, type CreateOrderBody, type OrderQuery, type ReceiptBody } from './api'

export const dealerKeys = {
  all: ['dealer'] as const,
  catalogue: (q: CatalogueQuery) => ['dealer', 'catalogue', q] as const,
  orders: (q?: OrderQuery) => (q ? (['dealer', 'orders', q] as const) : (['dealer', 'orders'] as const)),
  order: (id: number) => ['dealer', 'order', id] as const,
  request: (id: number) => ['dealer', 'request', id] as const,
  plants: ['dealer', 'plants'] as const,
  availability: (plantId: number) => ['dealer', 'availability', plantId] as const,
}

export const AVAILABILITY_REFRESH_MS = 15_000

export const usePlants = () => useQuery({ queryKey: dealerKeys.plants, queryFn: dealerApi.plants, staleTime: 5 * 60_000 })

/** Live-ish stock at a plant: refreshed every 15 s and whenever the window regains focus. Display only. */
export const usePlantAvailability = (plantId: number | undefined) =>
  useQuery({
    queryKey: dealerKeys.availability(plantId ?? 0),
    queryFn: () => dealerApi.availability(plantId as number),
    enabled: plantId !== undefined,
    refetchInterval: AVAILABILITY_REFRESH_MS,
    refetchOnWindowFocus: true,
    staleTime: 0,
  })

export const useCatalogue = (q: CatalogueQuery) =>
  useQuery({ queryKey: dealerKeys.catalogue(q), queryFn: () => dealerApi.catalogue(q), placeholderData: keepPreviousData })
export const useDealerOrders = (q: OrderQuery) =>
  useQuery({ queryKey: dealerKeys.orders(q), queryFn: () => dealerApi.orders(q), placeholderData: keepPreviousData })
export const useDealerOrder = (id: number) => useQuery({ queryKey: dealerKeys.order(id), queryFn: () => dealerApi.order(id) })
export const useDealerRequest = (id: number) => useQuery({ queryKey: dealerKeys.request(id), queryFn: () => dealerApi.request(id) })

export function useCreateOrder() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateOrderBody) => dealerApi.createOrder(body),
    // An order (or a 409) changes what the plant has free, so availability is refetched too.
    onSettled: () =>
      Promise.all([dealerKeys.orders(), ['dealer', 'availability']].map((queryKey) => queryClient.invalidateQueries({ queryKey }))),
  })
}

/** A cancel or an upload changes the line, its order's status and the order list, so the whole portal is refreshed. */
export function useCancelRequest() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, reason, version }: { id: number; reason: string; version: number }) => dealerApi.cancel(id, reason, version),
    onSettled: () => queryClient.invalidateQueries({ queryKey: dealerKeys.all }),
  })
}

export function useUploadProof() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, file, receipt, onProgress }: { id: number; file: File; receipt: ReceiptBody; onProgress?: (f: number) => void }) =>
      dealerApi.uploadProof(id, file, receipt, onProgress),
    onSettled: () => queryClient.invalidateQueries({ queryKey: dealerKeys.all }),
  })
}

export function useDealerQuotationPdf() {
  return useMutation({
    mutationFn: async (requestId: number) => {
      const { blob, fileName } = await dealerApi.quotationPdf(requestId)
      saveBlob(blob, fileName)
      return fileName
    },
  })
}
