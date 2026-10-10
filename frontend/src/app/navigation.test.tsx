import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Role } from '@/features/auth/api'
import { fakeServer, sessionHandlers, userFor } from '@/test/fakeServer'
import { renderApp } from '@/test/renderApp'

const ALL = ['Fleet Evaluation', 'Executive Approval', 'Executive Dashboard', 'Financial Clearance', 'Production & Dispatch', 'Admin Console']

const EXPECTED: Record<Role, { nav: string[]; home: string }> = {
  ADMIN: { nav: ['Admin Console'], home: '/admin' },
  SUPERVISOR: { nav: ALL, home: '/fleet-evaluation' },
  MARKETING_MANAGER: { nav: ['Fleet Evaluation'], home: '/fleet-evaluation' },
  MARKETING_HEAD: { nav: ['Executive Approval', 'Executive Dashboard'], home: '/executive-approval' },
  FINANCE_TEAM: { nav: ['Financial Clearance'], home: '/finance' },
}

async function navLinks() {
  const nav = await screen.findAllByRole('navigation', { name: 'Main' })
  return within(nav[0]).getAllByRole('link').map((link) => link.textContent)
}

describe('navigation per role', () => {
  it.each(Object.entries(EXPECTED) as [Role, (typeof EXPECTED)[Role]][])('%s sees only its modules and lands on its home', async (role, expected) => {
    fakeServer(sessionHandlers(userFor(role)))
    const { router } = renderApp('/')

    expect(await navLinks()).toEqual(expected.nav)
    expect(router.state.location.pathname).toBe(expected.home)
    expect(screen.getByText(`Test ${role}`)).toBeInTheDocument()
  })

  it('opens the mobile drawer and closes it after navigating', async () => {
    fakeServer(sessionHandlers(userFor('SUPERVISOR')))
    const user = userEvent.setup()
    const { router } = renderApp('/')
    await navLinks()

    await user.click(screen.getByRole('button', { name: 'Open navigation' }))
    const drawer = await screen.findByRole('dialog', { name: 'Navigation' })
    await user.click(within(drawer).getByRole('link', { name: 'Admin Console' }))

    expect(router.state.location.pathname).toBe('/admin')
    expect(screen.queryByRole('dialog', { name: 'Navigation' })).not.toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'Admin Console', level: 1 })).toBeInTheDocument()
  })
})

describe('forbidden URLs', () => {
  it.each([
    ['ADMIN', '/finance'],
    ['ADMIN', '/fleet-evaluation'],
    ['ADMIN', '/executive-dashboard'],
    ['MARKETING_MANAGER', '/admin'],
    ['MARKETING_MANAGER', '/executive-approval'],
    ['MARKETING_HEAD', '/finance'],
    ['FINANCE_TEAM', '/operations'],
    ['FINANCE_TEAM', '/fleet-evaluation'],
  ] as [Role, string][])('%s visiting %s is redirected to the 403 page', async (role, path) => {
    fakeServer(sessionHandlers(userFor(role)))
    const { router } = renderApp(path)

    expect(await screen.findByText(/403 — You don't have access/)).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/forbidden')
    expect(screen.getByText(path)).toBeInTheDocument()
  })

  it('SUPERVISOR can open every module', async () => {
    fakeServer(sessionHandlers(userFor('SUPERVISOR')))
    const { router } = renderApp('/operations')
    expect(await screen.findByRole('heading', { name: 'Production & Dispatch', level: 1 })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/operations')
  })

  it('shows a not-found page for unknown URLs', async () => {
    fakeServer(sessionHandlers(userFor('FINANCE_TEAM')))
    renderApp('/no-such-page')
    expect(await screen.findByText('Page not found')).toBeInTheDocument()
  })

  it('without a session, a protected URL goes to /login', async () => {
    fakeServer(sessionHandlers(null))
    const { router } = renderApp('/admin')
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
  })
})
