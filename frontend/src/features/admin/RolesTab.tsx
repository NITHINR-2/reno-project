import { Badge } from '@/components/ui/Badge'
import { ErrorState } from '@/components/ui/ErrorState'
import { SkeletonRows } from '@/components/ui/Skeleton'
import { useRoles } from './queries'

/** Read-only: roles and their permissions come from the database migration, not the UI. */
export function RolesTab() {
  const { data, isPending, error, refetch } = useRoles()
  if (isPending) return <div className="p-4"><SkeletonRows rows={5} label="Loading roles" /></div>
  if (error) return <ErrorState title="Couldn't load roles" error={error} onRetry={() => void refetch()} />

  return (
    <div className="grid grid-cols-1 gap-4 p-4 lg:grid-cols-2">
      {data.content.map((role) => (
        <section key={role.code} aria-labelledby={`role-${role.code}`} className="rounded-lg border border-line p-4">
          <div className="flex items-center justify-between gap-2">
            <h3 id={`role-${role.code}`} className="font-semibold text-fg">{role.name}</h3>
            <Badge>{role.permissions.length} permissions</Badge>
          </div>
          {role.description && <p className="mt-1 text-sm text-fg-muted">{role.description}</p>}
          <ul className="mt-3 flex flex-wrap gap-1.5" aria-label={`${role.name} permissions`}>
            {role.permissions.map((p) => (
              <li key={p.code} title={p.description ?? undefined}>
                <Badge tone="info">{p.code}</Badge>
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  )
}
