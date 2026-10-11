import { z } from 'zod'
import { api } from '@/lib/api'

/** Public result: deliberately only these fields (no dealer, contact or deposit details). */
export const verificationSchema = z.object({
  result: z.literal('GENUINE'),
  quotationNumber: z.string(),
  issueDate: z.string(),
  totalAmount: z.number(),
  currency: z.string(),
  status: z.string(),
})
export type Verification = z.infer<typeof verificationSchema>

export const verifyApi = {
  quotation: async (token: string) =>
    verificationSchema.parse((await api.get<unknown>(`/public/quotations/verify/${encodeURIComponent(token)}`)).data),
}
