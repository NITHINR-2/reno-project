import { ShoppingCart, Trash2 } from 'lucide-react'
import { AnimatePresence, motion } from 'motion/react'
import { useId, useState } from 'react'
import { useNavigate } from 'react-router'
import { inputClass } from '@/components/forms/Field'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Drawer } from '@/components/ui/Drawer'
import { EmptyState } from '@/components/ui/EmptyState'
import { useToast } from '@/components/ui/useToast'
import { formatCount, formatDate, formatINR, todayIST } from '@/lib/format'
import { listItem } from '@/lib/motion'
import { reportError } from '@/lib/reportError'
import { useCreateOrder } from '../queries'
import { useCart, type CartLine } from './cartContext'
import { MAX_QUANTITY, toOrderBody, validateCart } from './cartRules'

/** The dealer's cart: edit quantities, the requested delivery date and notes, then place one order. */
export function CartDrawer() {
  const cart = useCart()
  const toast = useToast()
  const navigate = useNavigate()
  const create = useCreateOrder()
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [confirming, setConfirming] = useState(false)
  const dateId = useId()
  const notesId = useId()
  // List-price estimate for orientation only; the HQ quotation is the price.
  const estimate = cart.lines.reduce((sum, l) => sum + (Number.isFinite(l.quantity) ? l.quantity * l.unitPrice : 0), 0)

  function review() {
    const found = validateCart(cart)
    setErrors(found)
    if (Object.keys(found).length === 0) setConfirming(true)
  }

  function submit() {
    create.mutate(toOrderBody(cart), {
      onSuccess: (order) => {
        cart.clear()
        cart.close()
        setErrors({})
        toast.success('Order placed', `${order.orderNumber} is with HQ for review.`)
        navigate(`/dealer/orders/${order.id}`)
      },
      onError: (error) => reportError(toast, error),
      onSettled: () => setConfirming(false),
    })
  }

  return (
    <>
      <Drawer
        open={cart.isOpen}
        onClose={cart.close}
        title="Your cart"
        description={cart.lines.length ? `${formatCount(cart.count)} units in ${cart.lines.length} line(s)` : undefined}
        footer={
          cart.lines.length > 0 && (
            <div className="flex w-full flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
              <p className="text-sm text-fg-muted">
                Estimated list value (ex-GST): <span className="font-semibold text-fg">{formatINR(estimate)}</span>
              </p>
              <Button onClick={review} icon={<ShoppingCart className="size-4" aria-hidden="true" />}>
                Place order
              </Button>
            </div>
          )
        }
      >
        {cart.lines.length === 0 ? (
          <EmptyState title="Your cart is empty" description="Add vehicles from the catalogue." icon={<ShoppingCart className="size-6" aria-hidden="true" />} />
        ) : (
          <div className="space-y-5">
            {errors.form && <p role="alert" className="text-sm text-red-600">{errors.form}</p>}
            <ul className="divide-y divide-line rounded-lg border border-line" aria-label="Cart lines">
              <AnimatePresence initial={false}>
                {cart.lines.map((line, index) => (
                  <motion.li key={line.key} layout custom={index} variants={listItem} initial="initial" animate="animate" exit="exit" className="p-3">
                    <CartLineRow line={line} error={errors[`lines.${line.key}`]} />
                  </motion.li>
                ))}
              </AnimatePresence>
            </ul>
            <div>
              <label htmlFor={dateId} className="mb-1 block text-sm font-medium text-fg">Requested delivery date</label>
              <input
                id={dateId}
                type="date"
                min={nextDay(todayIST())}
                value={cart.requestedDeliveryDate}
                onChange={(e) => cart.setDeliveryDate(e.target.value)}
                aria-invalid={!!errors.requestedDeliveryDate}
                aria-describedby={errors.requestedDeliveryDate ? `${dateId}-error` : `${dateId}-hint`}
                className={inputClass}
              />
              {errors.requestedDeliveryDate ? (
                <p id={`${dateId}-error`} className="mt-1 text-sm text-red-600">{errors.requestedDeliveryDate}</p>
              ) : (
                <p id={`${dateId}-hint`} className="mt-1 text-xs text-fg-muted">Optional. HQ confirms the actual schedule.</p>
              )}
            </div>
            <div>
              <label htmlFor={notesId} className="mb-1 block text-sm font-medium text-fg">Notes for HQ</label>
              <textarea id={notesId} rows={3} maxLength={2000} value={cart.notes} onChange={(e) => cart.setNotes(e.target.value)} className={inputClass} />
              {errors.notes && <p className="mt-1 text-sm text-red-600">{errors.notes}</p>}
            </div>
          </div>
        )}
      </Drawer>
      <ConfirmDialog
        open={confirming}
        title="Place this order?"
        description={`${cart.lines.length} line(s), ${formatCount(cart.count)} units${cart.requestedDeliveryDate ? `, requested for ${formatDate(cart.requestedDeliveryDate)}` : ''}. Each line goes to HQ for review; you can cancel a line while it is in review.`}
        confirmLabel="Place order"
        loading={create.isPending}
        onCancel={() => setConfirming(false)}
        onConfirm={submit}
      />
    </>
  )
}

function CartLineRow({ line, error }: { line: CartLine; error?: string }) {
  const cart = useCart()
  const qtyId = useId()
  return (
    <div className="flex flex-wrap items-start gap-3">
      <div className="min-w-0 flex-1">
        <p className="font-medium text-fg">{line.modelName}</p>
        <p className="text-sm text-fg-muted">{line.variantName}</p>
        {line.colourName && (
          <p className="mt-1 flex items-center gap-1.5 text-sm text-fg-muted">
            <span className="size-3 rounded-full ring-1 ring-line" style={{ backgroundColor: line.colourHex }} aria-hidden="true" />
            {line.colourName}
          </p>
        )}
        <p className="mt-1 text-xs text-fg-muted">{formatINR(line.unitPrice)} each (list, ex-GST)</p>
      </div>
      <div className="w-28">
        <label htmlFor={qtyId} className="sr-only">Quantity for {line.variantName}{line.colourName ? ` in ${line.colourName}` : ''}</label>
        <input
          id={qtyId}
          type="number"
          inputMode="numeric"
          min={1}
          max={MAX_QUANTITY}
          value={Number.isFinite(line.quantity) ? line.quantity : ''}
          onChange={(e) => cart.setQuantity(line.key, e.target.value === '' ? Number.NaN : Number(e.target.value))}
          aria-invalid={!!error}
          aria-describedby={error ? `${qtyId}-error` : undefined}
          className={inputClass}
        />
        {error && <p id={`${qtyId}-error`} className="mt-1 text-xs text-red-600">{error}</p>}
      </div>
      <Button
        variant="ghost"
        size="sm"
        aria-label={`Remove ${line.variantName}${line.colourName ? ` in ${line.colourName}` : ''}`}
        icon={<Trash2 className="size-4" aria-hidden="true" />}
        onClick={() => cart.remove(line.key)}
      />
    </div>
  )
}

function nextDay(isoDate: string) {
  const [y, m, d] = isoDate.split('-').map(Number)
  return new Date(Date.UTC(y, m - 1, d + 1)).toISOString().slice(0, 10)
}
