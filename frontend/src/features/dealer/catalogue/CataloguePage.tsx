import { CarFront, ShoppingCart } from 'lucide-react'
import { motion } from 'motion/react'
import { useSearchParams } from 'react-router'
import { inputClass } from '@/components/forms/Field'
import { PageHeader } from '@/components/layout/PageHeader'
import { Pagination } from '@/components/tables/Pagination'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorState } from '@/components/ui/ErrorState'
import { Skeleton } from '@/components/ui/Skeleton'
import { FilterBar, Labeled } from '@/features/admin/FilterBar'
import { statusLabel } from '@/lib/format'
import { listItem } from '@/lib/motion'
import { BODY_TYPES, FUEL_TYPES, TRANSMISSIONS, type CatalogueQuery } from '../api'
import { useCart } from '../cart/cartContext'
import { useCatalogue } from '../queries'
import { ModelCard } from './ModelCard'

const FILTER_KEYS = ['q', 'bodyType', 'fuelType', 'transmission', 'minPrice', 'maxPrice'] as const

/** Filters live in the URL so a filtered catalogue can be shared or bookmarked. */
export function CataloguePage() {
  const cart = useCart()
  const [params, setParams] = useSearchParams()
  const query: CatalogueQuery = { page: Number(params.get('page') ?? 0) || 0 }
  for (const key of FILTER_KEYS) {
    const value = params.get(key)
    if (value) query[key] = value
  }
  const priceError = query.minPrice && query.maxPrice && Number(query.minPrice) > Number(query.maxPrice)
  // An inverted range is shown as a field error and left out of the request instead of producing a 400.
  const catalogue = useCatalogue(priceError ? { ...query, minPrice: undefined, maxPrice: undefined } : query)

  function set(key: (typeof FILTER_KEYS)[number] | 'page', value: string) {
    setParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        if (value) next.set(key, value)
        else next.delete(key)
        if (key !== 'page') next.delete('page')
        return next
      },
      { replace: true },
    )
  }

  const select = (key: 'bodyType' | 'fuelType' | 'transmission', label: string, values: readonly string[]) => (
    <Labeled label={label}>
      {(id) => (
        <select id={id} value={query[key] ?? ''} onChange={(e) => set(key, e.target.value)} className={inputClass}>
          <option value="">Any</option>
          {values.map((v) => (
            <option key={v} value={v}>
              {statusLabel(v)}
            </option>
          ))}
        </select>
      )}
    </Labeled>
  )

  return (
    <>
      <PageHeader
        title="Catalogue"
        description="Choose a variant and colour, then place one order for everything in your cart. Prices are ex-GST list prices."
        actions={
          <Button onClick={cart.open} icon={<ShoppingCart className="size-4" aria-hidden="true" />} aria-label={`Open cart, ${cart.count} units`}>
            Cart ({cart.count})
          </Button>
        }
      />
      <Card className="mb-6">
        <FilterBar>
          <Labeled label="Search models">
            {(id) => <input id={id} type="search" placeholder="Name or code" value={query.q ?? ''} onChange={(e) => set('q', e.target.value)} className={inputClass} />}
          </Labeled>
          {select('bodyType', 'Body type', BODY_TYPES)}
          {select('fuelType', 'Fuel', FUEL_TYPES)}
          {select('transmission', 'Transmission', TRANSMISSIONS)}
          <Labeled label="Min price (₹)">
            {(id) => <input id={id} type="number" min={0} step={10000} inputMode="numeric" value={query.minPrice ?? ''} onChange={(e) => set('minPrice', e.target.value)} className={inputClass} />}
          </Labeled>
          <Labeled label="Max price (₹)">
            {(id) => (
              <input
                id={id}
                type="number"
                min={0}
                step={10000}
                inputMode="numeric"
                value={query.maxPrice ?? ''}
                onChange={(e) => set('maxPrice', e.target.value)}
                aria-invalid={!!priceError}
                className={inputClass}
              />
            )}
          </Labeled>
        </FilterBar>
        {priceError && <p role="alert" className="px-4 pb-3 text-sm text-red-600">Min price must not exceed max price.</p>}
      </Card>

      {catalogue.error && !catalogue.data ? (
        <ErrorState title="Couldn't load the catalogue" error={catalogue.error} onRetry={() => void catalogue.refetch()} />
      ) : catalogue.isPending ? (
        <div role="status" aria-label="Loading catalogue" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {Array.from({ length: 6 }, (_, i) => (
            <Skeleton key={i} className="h-96 w-full rounded-xl" />
          ))}
        </div>
      ) : catalogue.data.content.length === 0 ? (
        <EmptyState title="No models match" description="Try fewer filters." icon={<CarFront className="size-6" aria-hidden="true" />} />
      ) : (
        <>
          <ul aria-label="Models" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {catalogue.data.content.map((model, index) => (
              <motion.li key={model.id} custom={index} variants={listItem} initial="initial" animate="animate">
                <ModelCard model={model} />
              </motion.li>
            ))}
          </ul>
          <Pagination
            page={catalogue.data.page}
            size={catalogue.data.size}
            totalElements={catalogue.data.totalElements}
            totalPages={catalogue.data.totalPages}
            onPageChange={(page) => set('page', String(page))}
          />
        </>
      )}
    </>
  )
}
