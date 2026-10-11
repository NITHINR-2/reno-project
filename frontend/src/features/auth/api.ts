import { z } from 'zod'
import { api, getAccessToken, refreshAccessToken, setAccessToken } from '@/lib/api'

export const ROLES = ['ADMIN', 'SUPERVISOR', 'MARKETING_MANAGER', 'MARKETING_HEAD', 'FINANCE_TEAM', 'DEALER'] as const
export type Role = (typeof ROLES)[number]

export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: 'Administrator',
  SUPERVISOR: 'Supervisor',
  MARKETING_MANAGER: 'Marketing Manager',
  MARKETING_HEAD: 'Marketing Head',
  FINANCE_TEAM: 'Finance Team',
  DEALER: 'Dealer',
}

export const authUserSchema = z.object({
  id: z.number(),
  username: z.string(),
  fullName: z.string(),
  email: z.string().nullable().optional(),
  role: z.enum(ROLES),
  permissions: z.array(z.string()),
  /** Set for DEALER users only; the Dealer Portal API is scoped to it on the server. */
  dealerId: z.number().nullish(),
})

export type AuthUser = z.infer<typeof authUserSchema>

const tokenResponseSchema = z.object({ accessToken: z.string() })

export type Credentials = { username: string; password: string }

export async function login(credentials: Credentials): Promise<void> {
  const { data } = await api.post<unknown>('/auth/login', credentials, { skipAuth: true })
  setAccessToken(tokenResponseSchema.parse(data).accessToken)
}

export async function logout(): Promise<void> {
  try {
    await api.post('/auth/logout', null, { skipAuth: true })
  } finally {
    setAccessToken(null)
  }
}

/**
 * The signed-in user from `/auth/me`, or `null` when there is no session. After a reload the
 * in-memory token is gone, so the refresh cookie is tried first.
 */
export async function fetchCurrentUser(): Promise<AuthUser | null> {
  if (!getAccessToken() && !(await refreshAccessToken())) {
    return null
  }
  const { data } = await api.get<unknown>('/auth/me')
  return authUserSchema.parse(data)
}
