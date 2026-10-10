import { ShieldAlert } from 'lucide-react'
import { Link, useLocation } from 'react-router'
import { EmptyState } from '@/components/ui/EmptyState'

export function ForbiddenPage() {
  const from = (useLocation().state as { from?: string } | null)?.from
  return (
    <EmptyState
      icon={<ShieldAlert className="size-6" aria-hidden="true" />}
      title="403 — You don't have access to this page"
      description={
        <>
          {from ? <>Your role can't open <code className="font-mono">{from}</code>. </> : null}
          If you think you need access, ask an administrator.
        </>
      }
      action={
        <Link to="/" className="inline-flex min-h-11 items-center rounded-lg bg-accent px-4 text-sm font-medium text-charcoal hover:bg-accent-strong">
          Go to my home page
        </Link>
      }
    />
  )
}
