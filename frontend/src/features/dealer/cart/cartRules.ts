import { z } from 'zod'
import { todayIST } from '@/lib/format'
import type { CreateOrderBody } from '../api'
import type { CartState } from './cartContext'

export const MAX_LINES = 20
export const MAX_QUANTITY = 10_000

/** Mirrors CreateDealerOrderRequest (1–20 lines, 1–10,000 units, a future date, notes ≤ 2000); the server re-checks. */
export const cartSchema = z.object({
  lines: z
    .array(
      z.object({
        key: z.string(),
        quantity: z
          .number({ error: 'Enter a quantity.' })
          .int('Whole units only.')
          .min(1, 'At least 1 unit.')
          .max(MAX_QUANTITY, `At most ${MAX_QUANTITY.toLocaleString('en-IN')} units.`),
      }),
    )
    .min(1, 'Your cart is empty.')
    .max(MAX_LINES, `An order can have at most ${MAX_LINES} lines.`),
  requestedDeliveryDate: z
    .string()
    .refine((d) => d === '' || /^\d{4}-\d{2}-\d{2}$/.test(d), 'Enter a valid date.')
    .refine((d) => d === '' || d > todayIST(), 'Choose a date after today.'),
  notes: z.string().max(2000, 'At most 2000 characters.'),
})

/** Field errors keyed by `lines.<key>`, `requestedDeliveryDate`, `notes` or `form`. Empty when the cart is valid. */
export function validateCart(cart: CartState): Record<string, string> {
  const result = cartSchema.safeParse(cart)
  if (result.success) return {}
  const errors: Record<string, string> = {}
  for (const issue of result.error.issues) {
    const [first, index] = issue.path
    const field = first === 'lines' && typeof index === 'number' ? `lines.${cart.lines[index].key}` : first === 'lines' ? 'form' : String(first)
    errors[field] ??= issue.message
  }
  return errors
}

export function toOrderBody(cart: CartState): CreateOrderBody {
  const notes = cart.notes.trim()
  return {
    lines: cart.lines.map((l) => ({ variantId: l.variantId, quantity: l.quantity, ...(l.colourId ? { colourId: l.colourId } : {}) })),
    ...(cart.requestedDeliveryDate ? { requestedDeliveryDate: cart.requestedDeliveryDate } : {}),
    ...(notes ? { notes } : {}),
  }
}
