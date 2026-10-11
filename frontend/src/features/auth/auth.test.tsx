import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { landingFor } from '@/app/navigation'
import type { AuthUser } from '@/features/auth/api'
import { fakeServer, sessionHandlers, token, userFor } from '@/test/fakeServer'
import { renderApp } from '@/test/renderApp'

describe('login and logout', () => {
  it('sends an anonymous visitor to /login, signs in and returns to the requested page', async () => {
    const finance = userFor('FINANCE_TEAM')
    let signedIn = false
    const server = fakeServer({
      'POST /auth/refresh': () => [401, { title: 'Unauthorized', status: 401 }],
      'POST /auth/login': (config) => {
        const body = JSON.parse(String(config.data)) as { username: string; password: string }
        if (body.password !== 'correct-horse') return [401, { title: 'Unauthorized', status: 401, detail: 'Invalid username or password.' }]
        signedIn = true
        return token('login-token')
      },
      'GET /auth/me': () => (signedIn ? [200, finance] : [401, { status: 401 }]),
    })
    const user = userEvent.setup()
    const { router } = renderApp('/finance')

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')

    // Client-side validation first.
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByText('Enter your username.')).toBeInTheDocument()
    expect(server.count('POST /auth/login')).toBe(0)

    await user.type(screen.getByLabelText(/username/i), 'finance')
    await user.type(screen.getByLabelText(/password/i), 'wrong')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid username or password.')

    await user.clear(screen.getByLabelText(/password/i))
    await user.type(screen.getByLabelText(/password/i), 'correct-horse')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('heading', { name: 'Financial Clearance', level: 1 })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/finance')
    expect(screen.getByText('Test FINANCE_TEAM')).toBeInTheDocument()

    // The in-memory token is sent as a bearer header; nothing goes to localStorage.
    const me = server.calls.filter((c) => c.url === '/auth/me').at(-1)
    expect(me?.headers.get('Authorization')).toBe('Bearer login-token')
    expect(JSON.stringify({ ...localStorage })).not.toContain('login-token')
  })

  it('restores the session from the refresh cookie after a reload', async () => {
    const server = fakeServer(sessionHandlers(userFor('MARKETING_MANAGER')))
    renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Fleet Evaluation', level: 1 })).toBeInTheDocument()
    expect(server.count('POST /auth/refresh')).toBe(1)
    expect(server.calls.find((c) => c.url === '/auth/refresh')?.headers.get('X-Requested-With')).toBe('XMLHttpRequest')
  })

  it('signs out: calls logout with X-Requested-With, clears the session and shows the login page', async () => {
    const server = fakeServer(sessionHandlers(userFor('MARKETING_HEAD')))
    const user = userEvent.setup()
    const { router, queryClient } = renderApp('/executive-approval')

    await screen.findByRole('heading', { name: 'Executive Approval', level: 1 })
    await user.click(screen.getByRole('button', { name: 'Sign out' }))

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
    const logout = server.calls.find((c) => c.url === '/auth/logout')
    expect(logout?.method).toBe('POST')
    expect(logout?.headers.get('X-Requested-With')).toBe('XMLHttpRequest')
    expect(queryClient.getQueryData(['auth', 'me'])).toBeNull()
    await waitFor(() => expect(screen.getByText('Signed out')).toBeInTheDocument())
  })

  it('after a sign-out, the next user lands on their own home, not the page the previous user was on', async () => {
    let current: AuthUser | null = userFor('MARKETING_HEAD')
    fakeServer({
      'POST /auth/refresh': () => (current ? token('t') : [401, { title: 'Unauthorized', status: 401 }]),
      'GET /auth/me': () => (current ? [200, current] : [401, { status: 401 }]),
      'POST /auth/logout': () => {
        current = null
        return [204]
      },
      'POST /auth/login': (config) => {
        const { username } = JSON.parse(String(config.data)) as { username: string }
        current = userFor(username === 'finance' ? 'FINANCE_TEAM' : 'MARKETING_HEAD')
        return token('login-token')
      },
      'GET /fleet-requests/summary': () => [500, { status: 500 }],
      'GET /fleet-requests': () => [500, { status: 500 }],
    })
    const user = userEvent.setup()
    const { router } = renderApp('/executive-approval')
    await screen.findByRole('heading', { name: 'Executive Approval', level: 1 })

    await user.click(screen.getByRole('button', { name: 'Sign out' }))
    await screen.findByRole('heading', { name: 'Sign in' })
    await user.type(screen.getByLabelText(/username/i), 'finance')
    await user.type(screen.getByLabelText(/password/i), 'any-password')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/finance'))
    expect(screen.queryByText(/access denied/i)).not.toBeInTheDocument()
  })
})

describe('landingFor', () => {
  const finance = userFor('FINANCE_TEAM')
  it.each([
    ['/finance?request=4', '/finance?request=4'],
    ['/executive-dashboard', '/'],
    ['/admin', '/'],
    ['/forbidden', '/'],
    ['/no-such-page', '/'],
    ['//evil.example', '/'],
    [undefined, '/'],
  ])('%s → %s for Finance', (from, expected) => {
    expect(landingFor(finance, from)).toBe(expected)
  })
})
