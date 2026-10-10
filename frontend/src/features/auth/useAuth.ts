import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { createContext, useContext } from 'react'
import { fetchCurrentUser, login, logout, type AuthUser, type Credentials } from './api'

export const authKeys = {
  me: ['auth', 'me'] as const,
}

export function useCurrentUser() {
  return useQuery({
    queryKey: authKeys.me,
    queryFn: fetchCurrentUser,
    staleTime: Infinity,
    retry: false,
  })
}

/** Provided by `RequireAuth`, which renders its children only while a user is signed in. */
export const AuthUserContext = createContext<AuthUser | null>(null)

/** The signed-in user; only call below a route guarded by `RequireAuth`. */
export function useAuthUser(): AuthUser {
  const user = useContext(AuthUserContext)
  if (!user) throw new Error('useAuthUser called outside RequireAuth')
  return user
}

/** Marks the session as ended first (so guards redirect), then drops every other cached response. */
export function endSession(queryClient: QueryClient) {
  queryClient.setQueryData(authKeys.me, null)
  queryClient.removeQueries({ predicate: (q) => q.queryKey[0] !== authKeys.me[0] })
}

export function hasPermission(user: AuthUser | null | undefined, ...anyOf: string[]) {
  return !!user && anyOf.some((code) => user.permissions.includes(code))
}

export function useLogin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (credentials: Credentials) => login(credentials),
    onSuccess: async () => {
      queryClient.removeQueries({ predicate: (q) => q.queryKey[0] !== 'auth' })
      await queryClient.fetchQuery({ queryKey: authKeys.me, queryFn: fetchCurrentUser, staleTime: 0 })
    },
  })
}

export function useLogout() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: logout,
    // Drop every cached server response even if the request failed: the token is gone either way.
    onSettled: () => endSession(queryClient),
  })
}
