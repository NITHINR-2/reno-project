import axios, { AxiosHeaders, type AxiosError, type InternalAxiosRequestConfig } from 'axios'

declare module 'axios' {
  interface AxiosRequestConfig {
    /** Don't attach the bearer token or try a refresh (login, refresh, logout). */
    skipAuth?: boolean
  }
}

type RetriableConfig = InternalAxiosRequestConfig & { _retried?: boolean }

/** Shared Axios instance. In dev, `/api` is proxied to the backend by Vite. */
export const api = axios.create({
  baseURL: '/api',
  // Same origin, so the HttpOnly refresh cookie (Path=/api/auth) is sent by the browser.
  withCredentials: true,
  headers: { Accept: 'application/json', 'X-Requested-With': 'XMLHttpRequest' },
})

// The access token lives in memory only (never in localStorage).
let accessToken: string | null = null

export const getAccessToken = () => accessToken
export const setAccessToken = (token: string | null) => {
  accessToken = token
}

let onSessionExpired: (() => void) | null = null
/** Called once a 401 can't be recovered by a refresh. */
export function setSessionExpiredHandler(handler: (() => void) | null) {
  onSessionExpired = handler
}

let refreshing: Promise<string | null> | null = null

async function doRefresh(): Promise<string | null> {
  try {
    const { data } = await api.post<{ accessToken: string }>('/auth/refresh', null, { skipAuth: true })
    setAccessToken(data.accessToken)
    return data.accessToken
  } catch {
    setAccessToken(null)
    return null
  }
}

/**
 * Single-flight refresh: concurrent callers share one request, so a rotating refresh token is
 * never sent twice (reuse would revoke the whole family). Across tabs, the Web Locks API
 * serialises refreshes so each tab sends the cookie the previous rotation set.
 */
export function refreshAccessToken(): Promise<string | null> {
  if (!refreshing) {
    const locks = typeof navigator !== 'undefined' ? navigator.locks : undefined
    const run = locks ? locks.request('fleet-auth-refresh', doRefresh) : doRefresh()
    refreshing = run.finally(() => {
      refreshing = null
    })
  }
  return refreshing
}

api.interceptors.request.use((config) => {
  if (!config.skipAuth && accessToken) {
    config.headers = AxiosHeaders.from(config.headers)
    config.headers.set('Authorization', `Bearer ${accessToken}`)
  }
  return config
})

api.interceptors.response.use(undefined, async (error: AxiosError) => {
  const config = error.config as RetriableConfig | undefined
  if (error.response?.status !== 401 || !config || config.skipAuth || config._retried) {
    throw error
  }
  config._retried = true
  const token = await refreshAccessToken()
  if (!token) {
    onSessionExpired?.()
    throw error
  }
  return api(config)
})
