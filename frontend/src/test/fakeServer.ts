import { AxiosError, AxiosHeaders, type AxiosAdapter, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { api, setAccessToken } from '@/lib/api'
import type { AuthUser, Role } from '@/features/auth/api'

type Reply = [status: number, body?: unknown]
type Handler = (config: InternalAxiosRequestConfig) => Reply | Promise<Reply>

export type Call = { method: string; url: string; headers: AxiosHeaders; params: Record<string, unknown>; body: unknown }

/** Mirrors V6__roles_permissions.sql. */
export const ROLE_PERMISSIONS: Record<Role, string[]> = {
  ADMIN: ['ADMIN_USERS_MANAGE', 'ADMIN_DEALERS_MANAGE', 'AUDIT_LOG_READ'],
  MARKETING_MANAGER: ['FLEET_REQUEST_READ', 'FLEET_REQUEST_WRITE', 'STOCK_MAP', 'MARKETING_FORWARD'],
  MARKETING_HEAD: ['FLEET_REQUEST_READ', 'EXECUTIVE_APPROVE', 'DEPOSIT_REVIEW', 'EXECUTIVE_DASHBOARD_READ', 'QUOTATION_READ'],
  FINANCE_TEAM: ['FLEET_REQUEST_READ', 'QUOTATION_READ', 'QUOTATION_ISSUE', 'PAYMENT_RECORD', 'PAYMENT_APPROVE', 'FINANCE_CLEARANCE'],
  SUPERVISOR: [
    'ADMIN_USERS_MANAGE', 'ADMIN_DEALERS_MANAGE', 'AUDIT_LOG_READ', 'FLEET_REQUEST_READ', 'FLEET_REQUEST_WRITE', 'STOCK_MAP',
    'MARKETING_FORWARD', 'EXECUTIVE_APPROVE', 'DEPOSIT_REVIEW', 'EXECUTIVE_DASHBOARD_READ', 'QUOTATION_READ', 'QUOTATION_ISSUE',
    'PAYMENT_RECORD', 'PAYMENT_APPROVE', 'FINANCE_CLEARANCE', 'PRODUCTION_SCHEDULE', 'DISPATCH_SCHEDULE', 'WORKFLOW_OVERRIDE',
    'REQUEST_CANCEL', 'REQUEST_REASSIGN', 'SLA_POLICY_MANAGE',
  ],
}

export function userFor(role: Role): AuthUser {
  return { id: 1, username: role.toLowerCase(), fullName: `Test ${role}`, email: null, role, permissions: ROLE_PERMISSIONS[role] }
}

/** Installs an Axios adapter answering from `handlers` keyed by "METHOD /path". Unmatched calls get 404. */
export function fakeServer(handlers: Record<string, Handler>) {
  const calls: Call[] = []
  const adapter: AxiosAdapter = async (config) => {
    const method = (config.method ?? 'get').toUpperCase()
    const url = config.url ?? ''
    const headers = AxiosHeaders.from(config.headers)
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : config.data
    calls.push({ method, url, headers, params: (config.params ?? {}) as Record<string, unknown>, body })
    const handler = handlers[`${method} ${url}`]
    const [status, data] = handler ? await handler(config) : [404, { title: 'Not Found', status: 404 }]
    const response: AxiosResponse = { data, status, statusText: String(status), headers: {}, config }
    const valid = config.validateStatus ? config.validateStatus(status) : status < 400
    if (valid) return response
    throw new AxiosError(`Request failed with status code ${status}`, AxiosError.ERR_BAD_RESPONSE, config, null, response)
  }
  api.defaults.adapter = adapter
  setAccessToken(null)
  return { calls, count: (key: string) => calls.filter((c) => `${c.method} ${c.url}` === key).length }
}

export const token = (value: string): Reply => [200, { accessToken: value, tokenType: 'Bearer', expiresIn: 900 }]

/** A server where the refresh cookie is valid (or not) and /auth/me returns `user`. */
export function sessionHandlers(user: AuthUser | null): Record<string, Handler> {
  return {
    'POST /auth/refresh': () => (user ? token('refreshed-token') : [401, { title: 'Unauthorized', status: 401 }]),
    'GET /auth/me': () => (user ? [200, user] : [401, { title: 'Unauthorized', status: 401 }]),
    'POST /auth/logout': () => [204],
  }
}
