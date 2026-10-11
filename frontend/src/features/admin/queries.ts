import { keepPreviousData, useMutation, useQuery, useQueryClient, type QueryKey } from '@tanstack/react-query'
import { adminApi, type AuditQuery, type SlaPolicyBody, type CreateUserBody, type DealerBody, type DealerQuery, type UpdateUserBody, type UserQuery } from './api'

export const adminKeys = {
  users: (q?: UserQuery) => (q ? (['admin', 'users', q] as const) : (['admin', 'users'] as const)),
  roles: ['admin', 'roles'] as const,
  dealers: (q?: DealerQuery) => (q ? (['admin', 'dealers', q] as const) : (['admin', 'dealers'] as const)),
  audit: (q?: AuditQuery) => (q ? (['admin', 'audit', q] as const) : (['admin', 'audit'] as const)),
  regions: ['ref', 'regions'] as const,
  sla: ['admin', 'sla'] as const,
}

export const useUsers = (q: UserQuery) => useQuery({ queryKey: adminKeys.users(q), queryFn: () => adminApi.users(q), placeholderData: keepPreviousData })
export const useRoles = () => useQuery({ queryKey: adminKeys.roles, queryFn: adminApi.roles, staleTime: 5 * 60_000 })
export const useDealers = (q: DealerQuery) => useQuery({ queryKey: adminKeys.dealers(q), queryFn: () => adminApi.dealers(q), placeholderData: keepPreviousData })
export const useAuditLogs = (q: AuditQuery) => useQuery({ queryKey: adminKeys.audit(q), queryFn: () => adminApi.auditLogs(q), placeholderData: keepPreviousData })
export const useSlaPolicies = () => useQuery({ queryKey: adminKeys.sla, queryFn: adminApi.slaPolicies })
export const useRegions = () => useQuery({ queryKey: adminKeys.regions, queryFn: adminApi.regions, staleTime: 5 * 60_000 })

/** Every admin write is audited, so the audit list is invalidated along with the edited collection. */
function useAdminMutation<V, R>(fn: (v: V) => Promise<R>, ...collections: QueryKey[]) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSettled: () =>
      Promise.all([...collections, adminKeys.audit()].map((queryKey) => queryClient.invalidateQueries({ queryKey }))),
  })
}

export const useCreateUser = () => useAdminMutation((b: CreateUserBody) => adminApi.createUser(b), adminKeys.users())
export const useSetPasswordLink = () => useAdminMutation((id: number) => adminApi.setPasswordLink(id))
export const useDealerOptions = () => useQuery({ queryKey: ['admin', 'dealer-options'], queryFn: adminApi.dealerOptions, staleTime: 60_000 })
export const useUpdateUser = () => useAdminMutation(({ id, ...b }: UpdateUserBody & { id: number }) => adminApi.updateUser(id, b), adminKeys.users())
export const useSetUserActive = () =>
  useAdminMutation(({ id, active }: { id: number; active: boolean }) => adminApi.setActive(id, active), adminKeys.users())
// Dealers feed the fleet filters too, so their reference list is refreshed as well.
export const useCreateDealer = () => useAdminMutation((b: DealerBody) => adminApi.createDealer(b), adminKeys.dealers(), ['ref', 'dealers'])
export const useUpdateDealer = () =>
  useAdminMutation(({ id, ...b }: DealerBody & { id: number }) => adminApi.updateDealer(id, b), adminKeys.dealers(), ['ref', 'dealers'])
// New clocks use the edited policy, so fleet views (which show SLA counts) are refreshed too.
export const useUpdateSlaPolicy = () =>
  useAdminMutation(({ id, ...b }: SlaPolicyBody & { id: number }) => adminApi.updateSlaPolicy(id, b), adminKeys.sla, ['fleet'])
