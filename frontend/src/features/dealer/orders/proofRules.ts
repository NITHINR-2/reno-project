import { z } from 'zod'
import { todayIST } from '@/lib/format'
import { PAYMENT_METHODS, RECEIPT_TYPES } from '../api'

export const MAX_PROOF_BYTES = 5 * 1024 * 1024
const ALLOWED = new Map([
  ['application/pdf', ['pdf']],
  ['image/png', ['png']],
  ['image/jpeg', ['jpg', 'jpeg']],
])

/**
 * Mirrors the server's checks for a quick answer; the server still decides by the file's bytes, not its name or type.
 * Returns an error message, or undefined when the file looks acceptable.
 */
export function proofFileError(file: File | null | undefined): string | undefined {
  if (!file) return 'Attach the payment proof.'
  if (file.size === 0) return 'The file is empty.'
  if (file.size > MAX_PROOF_BYTES) return 'The file is larger than 5 MB.'
  const extension = file.name.split('.').pop()?.toLowerCase() ?? ''
  // A known type needs a matching extension; some browsers report no type, then the extension decides.
  const forType = ALLOWED.get(file.type)
  const ok = forType ? forType.includes(extension) : file.type === '' && [...ALLOWED.values()].some((exts) => exts.includes(extension))
  return ok ? undefined : 'Upload a PDF, PNG or JPEG file.'
}

/** Mirrors RecordReceiptRequest. */
export const receiptFormSchema = z.object({
  receiptType: z.enum(RECEIPT_TYPES),
  paymentMethod: z.enum(PAYMENT_METHODS),
  transactionReference: z
    .string()
    .trim()
    .min(1, 'Enter the UTR, cheque or transaction number.')
    .max(100)
    .regex(/^[A-Za-z0-9][A-Za-z0-9 /_.-]*$/, 'Letters, digits, spaces and / _ . - only.'),
  amount: z
    .number({ error: 'Enter the amount.' })
    .positive('Must be more than ₹0.')
    .max(9_999_999_999_999.99)
    .refine((v) => Math.round(v * 100) === v * 100, 'At most 2 decimal places.'),
  receivedDate: z
    .string()
    .regex(/^\d{4}-\d{2}-\d{2}$/, 'Enter the payment date.')
    .refine((d) => d <= todayIST(), "Can't be in the future."),
})
export type ReceiptForm = z.infer<typeof receiptFormSchema>
