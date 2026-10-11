import { CarFront, Check, Plus, Users, Warehouse } from 'lucide-react'
import { motion } from 'motion/react'
import { useId, useState } from 'react'
import { inputClass } from '@/components/forms/Field'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { useToast } from '@/components/ui/useToast'
import { cn } from '@/lib/cn'
import { formatINR, statusLabel } from '@/lib/format'
import { duration, ease } from '@/lib/motion'
import { availableFor, type CatalogueModel, type PlantAvailability } from '../api'
import { unitsInCart, useCart } from '../cart/cartContext'
import { MAX_QUANTITY } from '../cart/cartRules'

/** One catalogue model: pick a variant, a colour and a quantity, then add it to the cart. */
/** {@code availability} is the selected plant's free stock; the card subtracts what is already in the cart. */
export function ModelCard({ model, availability, plantName }: { model: CatalogueModel; availability?: PlantAvailability; plantName?: string }) {
  const cart = useCart()
  const toast = useToast()
  const [variantId, setVariantId] = useState(model.variants[0]?.id)
  const [colourId, setColourId] = useState(model.colours[0]?.id)
  const [quantity, setQuantity] = useState('1')
  const [qtyError, setQtyError] = useState<string>()
  const ids = useId()
  const variant = model.variants.find((v) => v.id === variantId) ?? model.variants[0]
  const colour = model.colours.find((c) => c.id === colourId)
  const lowest = Math.min(...model.variants.map((v) => v.unitPrice))
  // Display only: the server re-checks under row locks when the order is placed.
  const atPlant = availableFor(availability, variant.id, colour?.id)
  const left = atPlant === undefined ? undefined : Math.max(0, atPlant - unitsInCart(cart.lines, variant.id, colour?.id))

  function addToCart() {
    const qty = Number(quantity)
    if (!Number.isInteger(qty) || qty < 1 || qty > MAX_QUANTITY) {
      setQtyError(`Enter 1–${MAX_QUANTITY.toLocaleString('en-IN')} units.`)
      return
    }
    setQtyError(undefined)
    cart.add({
      modelName: model.name,
      variantId: variant.id,
      variantName: variant.name,
      colourId: colour?.id,
      colourName: colour?.name,
      colourHex: colour?.hexCode,
      unitPrice: variant.unitPrice,
      quantity: qty,
    })
    toast.success('Added to cart', `${qty} × ${variant.name}${colour ? `, ${colour.name}` : ''}`)
  }

  return (
    <motion.article
      whileHover={{ y: -2 }}
      transition={{ duration: duration.base, ease: ease.out }}
      aria-labelledby={`${ids}-name`}
      className="flex flex-col overflow-hidden rounded-xl border border-line bg-surface shadow-sm transition-shadow duration-200 hover:shadow-md"
    >
      <div className="relative aspect-[16/9] bg-surface-muted">
        {model.imageUrl ? (
          <img src={model.imageUrl} alt={model.name} loading="lazy" className="size-full object-cover" />
        ) : (
          <div className="flex size-full items-center justify-center text-fg-muted" aria-hidden="true">
            <CarFront className="size-16 opacity-40" />
          </div>
        )}
      </div>
      <div className="flex flex-1 flex-col gap-4 p-4">
        <div>
          <div className="flex flex-wrap items-start justify-between gap-2">
            <h2 id={`${ids}-name`} className="text-lg font-semibold text-fg">{model.name}</h2>
            <p className="text-sm text-fg-muted">
              from <span className="font-semibold text-fg">{formatINR(lowest)}</span>
            </p>
          </div>
          <div className="mt-1 flex flex-wrap gap-1.5">
            {model.bodyType && <Badge>{statusLabel(model.bodyType)}</Badge>}
            {model.seatingCapacity && (
              <Badge>
                <Users className="size-3" aria-hidden="true" />
                {model.seatingCapacity} seats
              </Badge>
            )}
          </div>
          {model.description && <p className="mt-2 text-sm text-fg-muted">{model.description}</p>}
        </div>

        <fieldset>
          <legend className="mb-1 text-sm font-medium text-fg">Variant</legend>
          <div className="space-y-1.5">
            {model.variants.map((v) => (
              <label
                key={v.id}
                className={cn(
                  'flex min-h-11 cursor-pointer items-center gap-3 rounded-lg border px-3 py-2 text-sm transition-colors duration-150',
                  v.id === variant.id ? 'border-accent-strong bg-accent/10' : 'border-line hover:bg-surface-muted',
                )}
              >
                <input type="radio" name={`${ids}-variant`} value={v.id} checked={v.id === variant.id} onChange={() => setVariantId(v.id)} className="accent-[var(--color-accent-strong)]" />
                <span className="min-w-0 flex-1">
                  <span className="block font-medium text-fg">{v.name}</span>
                  <span className="text-xs text-fg-muted">
                    {statusLabel(v.fuelType)} · {statusLabel(v.transmission)}
                  </span>
                </span>
                <span className="font-medium text-fg">{formatINR(v.unitPrice)}</span>
              </label>
            ))}
          </div>
        </fieldset>

        {model.colours.length > 0 && (
          <fieldset>
            <legend className="mb-1 text-sm font-medium text-fg">
              Colour{colour && <span className="font-normal text-fg-muted">: {colour.name}</span>}
            </legend>
            <div className="flex flex-wrap gap-2">
              {model.colours.map((c) => (
                <label key={c.id} className="relative inline-flex size-11 cursor-pointer items-center justify-center rounded-full">
                  <input type="radio" name={`${ids}-colour`} value={c.id} checked={c.id === colourId} onChange={() => setColourId(c.id)} aria-label={c.name} className="peer sr-only" />
                  <span
                    className="size-8 rounded-full ring-1 ring-line transition-transform duration-150 peer-checked:ring-2 peer-checked:ring-accent-strong peer-checked:ring-offset-2 peer-checked:ring-offset-surface peer-focus-visible:outline-2 peer-focus-visible:outline-accent"
                    style={{ backgroundColor: c.hexCode }}
                    aria-hidden="true"
                  />
                  {c.id === colourId && <Check className="pointer-events-none absolute size-4 text-white mix-blend-difference" aria-hidden="true" />}
                </label>
              ))}
            </div>
          </fieldset>
        )}

        {plantName && left !== undefined && (
          <p aria-live="polite" className={cn('flex items-center gap-1.5 text-sm', left > 0 ? 'text-green-700 dark:text-green-400' : 'text-amber-700 dark:text-amber-400')}>
            <Warehouse className="size-4" aria-hidden="true" />
            {left > 0 ? `${left} available at ${plantName}` : `None free at ${plantName} — made to order`}
          </p>
        )}

        <div className="mt-auto flex items-end gap-2">
          <div className="w-28">
            <label htmlFor={`${ids}-qty`} className="mb-1 block text-sm font-medium text-fg">Quantity</label>
            <input
              id={`${ids}-qty`}
              type="number"
              inputMode="numeric"
              min={1}
              max={MAX_QUANTITY}
              value={quantity}
              onChange={(e) => setQuantity(e.target.value)}
              aria-invalid={!!qtyError}
              aria-describedby={qtyError ? `${ids}-qty-error` : undefined}
              className={inputClass}
            />
          </div>
          <Button className="flex-1" onClick={addToCart} icon={<Plus className="size-4" aria-hidden="true" />} aria-label={`Add ${model.name} to cart`}>
            Add to cart
          </Button>
        </div>
        {qtyError && <p id={`${ids}-qty-error`} className="-mt-2 text-sm text-red-600">{qtyError}</p>}
      </div>
    </motion.article>
  )
}
