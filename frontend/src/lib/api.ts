import axios from 'axios'

/** Shared Axios instance. In dev, `/api` is proxied to the backend by Vite. */
export const api = axios.create({
  baseURL: '/api',
  headers: { Accept: 'application/json' },
})
