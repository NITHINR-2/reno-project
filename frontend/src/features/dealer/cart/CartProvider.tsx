import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { CartContext, EMPTY_CART, lineKey, type CartApi, type CartState } from './cartContext'

/**
 * Client-only cart for the signed-in dealer. Kept in sessionStorage per user so a reload doesn't lose it; it holds
 * no secrets and is cleared after a successful order. Storage failures (private mode, quota) just mean no persistence.
 */
export function CartProvider({ userId, children }: { userId: number; children: ReactNode }) {
  const storageKey = `fleet-cart:${userId}`
  const [state, setState] = useState<CartState>(() => load(storageKey))
  const [isOpen, setOpen] = useState(false)

  useEffect(() => {
    try {
      sessionStorage.setItem(storageKey, JSON.stringify(state))
    } catch {
      // Persistence is a convenience only.
    }
  }, [storageKey, state])

  const add = useCallback<CartApi['add']>((line) => {
    const key = lineKey(line.variantId, line.colourId)
    setState((s) => {
      const existing = s.lines.find((l) => l.key === key)
      const lines = existing
        ? s.lines.map((l) => (l.key === key ? { ...l, quantity: l.quantity + line.quantity } : l))
        : [...s.lines, { ...line, key }]
      return { ...s, lines }
    })
  }, [])

  const api = useMemo<CartApi>(
    () => ({
      ...state,
      count: state.lines.reduce((sum, l) => sum + (Number.isFinite(l.quantity) ? l.quantity : 0), 0),
      isOpen,
      open: () => setOpen(true),
      close: () => setOpen(false),
      add,
      setQuantity: (key, quantity) => setState((s) => ({ ...s, lines: s.lines.map((l) => (l.key === key ? { ...l, quantity } : l)) })),
      remove: (key) => setState((s) => ({ ...s, lines: s.lines.filter((l) => l.key !== key) })),
      setDeliveryDate: (requestedDeliveryDate) => setState((s) => ({ ...s, requestedDeliveryDate })),
      setNotes: (notes) => setState((s) => ({ ...s, notes })),
      setPlant: (plantId) => setState((s) => ({ ...s, plantId })),
      // The plant choice outlives an order: the next order is usually from the same plant.
      clear: () => setState((s) => ({ ...EMPTY_CART, plantId: s.plantId })),
    }),
    [state, isOpen, add],
  )

  return <CartContext.Provider value={api}>{children}</CartContext.Provider>
}

function load(storageKey: string): CartState {
  try {
    const raw = sessionStorage.getItem(storageKey)
    if (!raw) return EMPTY_CART
    const parsed = JSON.parse(raw) as Partial<CartState>
    return {
      lines: Array.isArray(parsed.lines) ? parsed.lines : [],
      requestedDeliveryDate: typeof parsed.requestedDeliveryDate === 'string' ? parsed.requestedDeliveryDate : '',
      notes: typeof parsed.notes === 'string' ? parsed.notes : '',
      plantId: typeof parsed.plantId === 'number' ? parsed.plantId : undefined,
    }
  } catch {
    return EMPTY_CART
  }
}
