import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { saveBlob } from '@/lib/download'
import { dealerApi, type CatalogueQuery, type CreateOrderBody, type OrderQuery, type ReceiptBody } from './api'

export const dealerKeys = {
  all: ['dealer'] as const,
  catalogue: (q: CatalogueQuery) => ['dealer', 'catalogue', q] as const,
  orders: (q?: OrderQuery) => (q ? (['dealer', 'orders', q] as const) : (['dealer', 'orders'] as const)),
  order: (id: number) => ['dealer', 'order', id] as const,
  request: (id: number) => ['dealer', 'request', id] as const,
}

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
    onSettled: () => queryClient.invalidateQueries({ queryKey: dealerKeys.orders() }),
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
