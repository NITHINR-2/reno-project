import { Compass } from 'lucide-react'
import { Link } from 'react-router'
import { EmptyState } from '@/components/ui/EmptyState'

export function NotFoundPage() {
  return (
    <EmptyState
      icon={<Compass className="size-6" aria-hidden="true" />}
      title="Page not found"
      description="The page you're looking for doesn't exist or has moved."
      action={
        <Link to="/" className="inline-flex min-h-11 items-center rounded-lg bg-accent px-4 text-sm font-medium text-charcoal hover:bg-accent-strong">
          Go to my home page
        </Link>
      }
    />
  )
}
