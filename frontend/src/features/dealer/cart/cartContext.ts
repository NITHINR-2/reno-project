import { createContext, useContext } from 'react'

/** One cart line: a variant in one colour. Labels and the list price are for display only; the server prices orders. */
export type CartLine = {
  key: string
  modelName: string
  variantId: number
  variantName: string
  colourId?: number
  colourName?: string
  colourHex?: string
  unitPrice: number
  quantity: number
}

/** One cart = one order from one plant; the catalogue's plant picker sets {@code plantId}. */
export type CartState = { lines: CartLine[]; requestedDeliveryDate: string; notes: string; plantId?: number }

export type CartApi = CartState & {
  count: number
  isOpen: boolean
  open: () => void
  close: () => void
  /** Adds the line, or adds to the quantity of the same variant in the same colour. */
  add: (line: Omit<CartLine, 'key'>) => void
  setQuantity: (key: string, quantity: number) => void
  remove: (key: string) => void
  setDeliveryDate: (date: string) => void
  setNotes: (notes: string) => void
  setPlant: (plantId: number) => void
  clear: () => void
}

export const EMPTY_CART: CartState = { lines: [], requestedDeliveryDate: '', notes: '' }

/** Units of a variant in a colour already in the cart (they come out of the same plant's stock). */
export const unitsInCart = (lines: CartLine[], variantId: number, colourId?: number) =>
  lines.filter((l) => l.variantId === variantId && l.colourId === colourId).reduce((sum, l) => sum + (Number.isFinite(l.quantity) ? l.quantity : 0), 0)

export const lineKey = (variantId: number, colourId?: number) => `${variantId}:${colourId ?? '-'}`

export const CartContext = createContext<CartApi | null>(null)

export function useCart(): CartApi {
  const context = useContext(CartContext)
  if (!context) throw new Error('useCart must be used inside CartProvider')
  return context
}
