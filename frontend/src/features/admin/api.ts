import { z } from 'zod'
import { ROLES } from '@/features/auth/api'
import { api } from '@/lib/api'
import { pageSchema, type Page } from '@/lib/page'

export const DEALER_STATUSES = ['PENDING_ONBOARDING', 'ACTIVE', 'SUSPENDED'] as const
export type DealerStatus = (typeof DEALER_STATUSES)[number]

export const userSchema = z.object({
  id: z.number(),
  username: z.string(),
  email: z.string(),
  fullName: z.string(),
  role: z.enum(ROLES),
  active: z.boolean(),
  lastLoginAt: z.string().nullish(),
  version: z.number(),
  createdAt: z.string(),
  dealerId: z.number().nullish(),
})
export type AdminUser = z.infer<typeof userSchema>

export const roleSchema = z.object({
  code: z.enum(ROLES),
  name: z.string(),
  description: z.string().nullish(),
  permissions: z.array(z.object({ code: z.string(), description: z.string().nullish() })),
})
export type RoleInfo = z.infer<typeof roleSchema>

export const dealerSchema = z.object({
  id: z.number(),
  dealerCode: z.string(),
  name: z.string(),
  regionId: z.number(),
  regionName: z.string().nullish(),
  gstin: z.string().nullish(),
  contactName: z.string().nullish(),
  contactEmail: z.string().nullish(),
  contactPhone: z.string().nullish(),
  addressLine: z.string().nullish(),
  city: z.string(),
  state: z.string(),
  postalCode: z.string().nullish(),
  status: z.enum(DEALER_STATUSES),
  onboardedAt: z.string().nullish(),
  version: z.number(),
})
export type Dealer = z.infer<typeof dealerSchema>

export const auditLogSchema = z.object({
  id: z.number(),
  actorUsername: z.string().nullish(),
  actorRole: z.string().nullish(),
  action: z.string(),
  entityType: z.string(),
  entityId: z.string().nullish(),
  before: z.unknown().nullish(),
  after: z.unknown().nullish(),
  reason: z.string().nullish(),
  ipAddress: z.string().nullish(),
  createdAt: z.string(),
})
export type AuditLog = z.infer<typeof auditLogSchema>

export const slaPolicySchema = z.object({
  id: z.number(),
  stage: z.string(),
  hoursAllowed: z.number(),
  warningThresholdPct: z.number(),
  active: z.boolean(),
  version: z.number(),
  updatedAt: z.string().nullish(),
})
export type SlaPolicy = z.infer<typeof slaPolicySchema>
export type SlaPolicyBody = { version: number; hoursAllowed: number; warningThresholdPct: number; active: boolean }

const regionSchema = z.object({ id: z.number(), name: z.string() })

export type UserQuery = { page: number; q?: string; role?: string; active?: string }
export type DealerQuery = { page: number; q?: string; regionId?: string; status?: string }
export type AuditQuery = { page: number; actorUsername?: string; action?: string; entityType?: string; entityId?: string; from?: string; to?: string }

/** HQ users need a password; DEALER users need a dealerId and set their own password through a link. */
export type CreateUserBody = { username: string; email: string; fullName: string; role: string; password?: string; dealerId?: number }

export const setPasswordLinkSchema = z.object({ userId: z.number(), username: z.string(), url: z.string(), expiresAt: z.string() })
export type SetPasswordLink = z.infer<typeof setPasswordLinkSchema>
export type UpdateUserBody = { version: number; email?: string; fullName?: string; role?: string }
export type DealerBody = {
  dealerCode?: string
  name: string
  regionId: number
  gstin?: string
  contactName?: string
  contactEmail?: string
  contactPhone?: string
  addressLine?: string
  city: string
  state: string
  postalCode?: string
  status?: DealerStatus
  version?: number
}

const clean = (params: object) => Object.fromEntries(Object.entries(params).filter(([, v]) => v !== '' && v !== undefined))

async function getPage<T extends z.ZodType>(url: string, item: T, params: object): Promise<Page<z.infer<T>>> {
  const { data } = await api.get<unknown>(url, { params: clean(params) })
  return pageSchema(item).parse(data)
}

export const adminApi = {
  users: (q: UserQuery) => getPage('/admin/users', userSchema, { size: 20, sort: 'username', ...q }),
  roles: () => getPage('/admin/roles', roleSchema, { size: 20 }),
  dealers: (q: DealerQuery) => getPage('/dealers', dealerSchema, { size: 20, sort: 'name', ...q }),
  auditLogs: (q: AuditQuery) => getPage('/admin/audit-logs', auditLogSchema, { size: 20, ...q }),
  regions: () => getPage('/regions', regionSchema, { size: 100 }),
  slaPolicies: () => getPage('/admin/sla-policies', slaPolicySchema, { size: 20, sort: 'id' }),
  updateSlaPolicy: async (id: number, body: SlaPolicyBody) =>
    slaPolicySchema.parse((await api.patch<unknown>(`/admin/sla-policies/${id}`, body)).data),

  createUser: async (body: CreateUserBody) => userSchema.parse((await api.post<unknown>('/admin/users', body)).data),
  setPasswordLink: async (id: number) => setPasswordLinkSchema.parse((await api.post<unknown>(`/admin/users/${id}/set-password-link`, {})).data),
  dealerOptions: () => getPage('/dealers', dealerSchema, { size: 100, sort: 'name' }),
  updateUser: async (id: number, body: UpdateUserBody) => userSchema.parse((await api.patch<unknown>(`/admin/users/${id}`, body)).data),
  setActive: async (id: number, active: boolean) =>
    userSchema.parse((await api.post<unknown>(`/admin/users/${id}/${active ? 'activate' : 'deactivate'}`, {})).data),
  createDealer: async (body: DealerBody) => dealerSchema.parse((await api.post<unknown>('/dealers', body)).data),
  updateDealer: async (id: number, body: DealerBody) => dealerSchema.parse((await api.patch<unknown>(`/dealers/${id}`, body)).data),
}
