import { describe, expect, it, vi } from 'vitest'
import { fakeServer, token } from '@/test/fakeServer'
import { api, getAccessToken, setAccessToken, setSessionExpiredHandler } from './api'

describe('api client', () => {
  it('runs one refresh for concurrent 401s, then retries each request with the new token', async () => {
    let refreshes = 0
    let releaseRefresh: () => void = () => undefined
    const server = fakeServer({
      'POST /auth/refresh': async () => {
        refreshes++
        await new Promise<void>((resolve) => (releaseRefresh = resolve))
        return token('fresh')
      },
      'GET /things': (config) =>
        config.headers.Authorization === 'Bearer fresh' ? [200, { ok: true }] : [401, { status: 401 }],
    })
    setAccessToken('expired')

    const requests = Promise.all([api.get('/things'), api.get('/things'), api.get('/things')])
    await vi.waitFor(() => expect(refreshes).toBe(1))
    releaseRefresh()
    const responses = await requests

    expect(responses.map((r) => r.data)).toEqual([{ ok: true }, { ok: true }, { ok: true }])
    expect(server.count('POST /auth/refresh')).toBe(1)
    expect(getAccessToken()).toBe('fresh')
    const refresh = server.calls.find((c) => c.url === '/auth/refresh')
    expect(refresh?.headers.get('X-Requested-With')).toBe('XMLHttpRequest')
    expect(refresh?.headers.get('Authorization')).toBeFalsy()
  })

  it('reports an expired session when the refresh fails, and does not loop', async () => {
    const server = fakeServer({
      'POST /auth/refresh': () => [401, { status: 401 }],
      'GET /things': () => [401, { status: 401 }],
    })
    const expired = vi.fn()
    setSessionExpiredHandler(expired)
    setAccessToken('expired')

    await expect(api.get('/things')).rejects.toMatchObject({ response: { status: 401 } })
    expect(expired).toHaveBeenCalledTimes(1)
    expect(server.count('GET /things')).toBe(1)
    expect(getAccessToken()).toBeNull()
    setSessionExpiredHandler(null)
  })

  it('never refreshes for 403', async () => {
    const server = fakeServer({ 'GET /things': () => [403, { status: 403 }] })
    setAccessToken('valid')
    await expect(api.get('/things')).rejects.toMatchObject({ response: { status: 403 } })
    expect(server.count('POST /auth/refresh')).toBe(0)
  })
})
