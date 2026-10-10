import { ChevronRight } from 'lucide-react'
import { Link, useMatches } from 'react-router'

export type RouteHandle = { crumb?: string }

export function Breadcrumbs() {
  const crumbs = useMatches()
    .filter((match) => (match.handle as RouteHandle | undefined)?.crumb)
    .map((match) => ({ path: match.pathname, label: (match.handle as RouteHandle).crumb as string }))

  return (
    <nav aria-label="Breadcrumb" className="min-w-0">
      <ol className="flex items-center gap-1 text-sm text-fg-muted">
        {crumbs.map((crumb, index) => {
          const last = index === crumbs.length - 1
          return (
            <li key={crumb.path} className={last ? 'flex min-w-0 items-center gap-1' : 'hidden items-center gap-1 sm:flex'}>
              {index > 0 && <ChevronRight className="size-4 shrink-0" aria-hidden="true" />}
              {last ? (
                <span aria-current="page" className="truncate font-medium text-fg">
                  {crumb.label}
                </span>
              ) : (
                <Link to={crumb.path} className="hover:text-fg">
                  {crumb.label}
                </Link>
              )}
            </li>
          )
        })}
      </ol>
    </nav>
  )
}
