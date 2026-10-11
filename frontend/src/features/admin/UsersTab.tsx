import { UserPlus } from 'lucide-react'
import { useState } from 'react'
import { inputClass } from '@/components/forms/Field'
import { DataTable, type Column } from '@/components/tables/DataTable'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ROLE_LABELS, ROLES } from '@/features/auth/api'
import { formatDateTime } from '@/lib/format'
import type { AdminUser, UserQuery } from './api'
import { FilterBar, Labeled } from './FilterBar'
import { useUsers } from './queries'
import { UserDrawer } from './UserDrawer'

const columns: Column<AdminUser>[] = [
  { key: 'username', header: 'Username', cell: (u) => <span className="font-mono">{u.username}</span> },
  { key: 'name', header: 'Name', cell: (u) => u.fullName },
  { key: 'email', header: 'Email', cell: (u) => u.email, mobile: false },
  { key: 'role', header: 'Role', cell: (u) => <Badge tone="accent">{ROLE_LABELS[u.role]}</Badge> },
  { key: 'active', header: 'Status', cell: (u) => <Badge tone={u.active ? 'success' : 'neutral'}>{u.active ? 'Active' : 'Inactive'}</Badge> },
  { key: 'login', header: 'Last sign-in', cell: (u) => formatDateTime(u.lastLoginAt), mobile: false },
]

export function UsersTab() {
  const [query, setQuery] = useState<UserQuery>({ page: 0 })
  const [editing, setEditing] = useState<AdminUser | 'new' | null>(null)
  const users = useUsers(query)
  const set = (patch: Partial<UserQuery>) => setQuery((q) => ({ ...q, ...patch, page: 0 }))

  return (
    <>
      <FilterBar
        actions={
          <Button onClick={() => setEditing('new')} icon={<UserPlus className="size-4" aria-hidden="true" />}>
            New user
          </Button>
        }
      >
        <Labeled label="Search users">
          {(id) => <input id={id} type="search" placeholder="Username, name or email" value={query.q ?? ''} onChange={(e) => set({ q: e.target.value })} className={inputClass} />}
        </Labeled>
        <Labeled label="Role">
          {(id) => (
            <select id={id} value={query.role ?? ''} onChange={(e) => set({ role: e.target.value })} className={inputClass}>
              <option value="">All roles</option>
              {ROLES.map((r) => (
                <option key={r} value={r}>
                  {ROLE_LABELS[r]}
                </option>
              ))}
            </select>
          )}
        </Labeled>
        <Labeled label="Status">
          {(id) => (
            <select id={id} value={query.active ?? ''} onChange={(e) => set({ active: e.target.value })} className={inputClass}>
              <option value="">All</option>
              <option value="true">Active</option>
              <option value="false">Inactive</option>
            </select>
          )}
        </Labeled>
      </FilterBar>
      <DataTable
        label="Users"
        columns={columns}
        data={users.data}
        getRowId={(u) => u.id}
        isLoading={users.isPending}
        isFetching={users.isFetching && !users.isPending}
        error={users.error}
        onRetry={() => void users.refetch()}
        onPageChange={(page) => setQuery((q) => ({ ...q, page }))}
        onRowClick={setEditing}
        emptyTitle="No users match"
      />
      <UserDrawer target={editing} onClose={() => setEditing(null)} />
    </>
  )
}
