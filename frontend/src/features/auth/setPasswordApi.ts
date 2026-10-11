import { z } from 'zod'
import { api } from '@/lib/api'

const checkSchema = z.object({ username: z.string(), expiresAt: z.string() })
export type SetPasswordCheck = z.infer<typeof checkSchema>

/** Public, rate-limited endpoints; the token travels in the body so it never lands in server logs. */
export const setPasswordApi = {
  check: async (token: string) => checkSchema.parse((await api.post<unknown>('/public/set-password/check', { token }, { skipAuth: true })).data),
  submit: async (token: string, password: string) => {
    await api.post('/public/set-password', { token, password }, { skipAuth: true })
  },
}
