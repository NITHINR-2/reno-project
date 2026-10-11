import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { z } from 'zod'
import { BODY_TYPES, FUEL_TYPES, TRANSMISSIONS } from '@/features/dealer/api'
import { api } from '@/lib/api'
import { pageSchema } from '@/lib/page'
import { adminKeys } from '../queries'

const variantSchema = z.object({
  id: z.number(),
  code: z.string(),
  name: z.string(),
  fuelType: z.enum(FUEL_TYPES),
  transmission: z.enum(TRANSMISSIONS),
  unitPrice: z.number(),
  active: z.boolean(),
  version: z.number(),
})
const colourSchema = z.object({ id: z.number(), name: z.string(), hexCode: z.string(), active: z.boolean(), version: z.number() })

export const adminModelSchema = z.object({
  id: z.number(),
  code: z.string(),
  name: z.string(),
  vehicleCategory: z.string(),
  active: z.boolean(),
  description: z.string().nullish(),
  bodyType: z.enum(BODY_TYPES).nullish(),
  seatingCapacity: z.number().nullish(),
  imageUrl: z.string().nullish(),
  version: z.number(),
  variants: z.array(variantSchema),
  colours: z.array(colourSchema),
})
export type AdminModel = z.infer<typeof adminModelSchema>
export type AdminVariant = z.infer<typeof variantSchema>
export type AdminColour = z.infer<typeof colourSchema>

export type ModelQuery = { page: number; q?: string; active?: string }
export type ModelBody = {
  code?: string
  name?: string
  vehicleCategory?: string
  description?: string
  bodyType?: string
  seatingCapacity?: number
  imageUrl?: string
  active?: boolean
  version?: number
}
export type VariantBody = { code?: string; name?: string; fuelType?: string; transmission?: string; unitPrice?: number; active?: boolean; version?: number }
export type ColourBody = { name?: string; hexCode?: string; active?: boolean; version?: number }

const BASE = '/admin/catalogue'
const clean = (params: object) => Object.fromEntries(Object.entries(params).filter(([, v]) => v !== '' && v !== undefined))

export const catalogueAdminApi = {
  models: async (q: ModelQuery) =>
    pageSchema(adminModelSchema).parse((await api.get<unknown>(`${BASE}/models`, { params: clean({ size: 20, sort: 'name', ...q }) })).data),
  model: async (id: number) => adminModelSchema.parse((await api.get<unknown>(`${BASE}/models/${id}`)).data),
  createModel: async (body: ModelBody) => adminModelSchema.parse((await api.post<unknown>(`${BASE}/models`, body)).data),
  updateModel: async (id: number, body: ModelBody) => adminModelSchema.parse((await api.patch<unknown>(`${BASE}/models/${id}`, body)).data),
  createVariant: async (modelId: number, body: VariantBody) => variantSchema.parse((await api.post<unknown>(`${BASE}/models/${modelId}/variants`, body)).data),
  updateVariant: async (id: number, body: VariantBody) => variantSchema.parse((await api.patch<unknown>(`${BASE}/variants/${id}`, body)).data),
  createColour: async (modelId: number, body: ColourBody) => colourSchema.parse((await api.post<unknown>(`${BASE}/models/${modelId}/colours`, body)).data),
  updateColour: async (id: number, body: ColourBody) => colourSchema.parse((await api.patch<unknown>(`${BASE}/colours/${id}`, body)).data),
}

export const catalogueKeys = {
  all: ['admin', 'catalogue'] as const,
  models: (q: ModelQuery) => ['admin', 'catalogue', 'models', q] as const,
  model: (id: number) => ['admin', 'catalogue', 'model', id] as const,
}

export const useAdminModels = (q: ModelQuery) =>
  useQuery({ queryKey: catalogueKeys.models(q), queryFn: () => catalogueAdminApi.models(q), placeholderData: keepPreviousData })
export const useAdminModel = (id: number) => useQuery({ queryKey: catalogueKeys.model(id), queryFn: () => catalogueAdminApi.model(id) })

/** Catalogue writes are audited and change what dealers see, so the admin lists, audit log and dealer catalogue refresh. */
export function useCatalogueMutation<V, R>(fn: (v: V) => Promise<R>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSettled: () =>
      Promise.all([catalogueKeys.all, adminKeys.audit(), ['dealer', 'catalogue']].map((queryKey) => queryClient.invalidateQueries({ queryKey }))),
  })
}
