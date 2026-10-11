import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Role } from '@/features/auth/api'
import type { FleetStatus } from '@/features/fleet/api'
import { fakeServer, userFor } from '@/test/fakeServer'
import { detail, fleetHandlers, page } from '@/test/fleetFixtures'
import { renderApp } from '@/test/renderApp'

const MODULE: Partial<Record<Role, string>> = {
  SUPERVISOR: '/operations',
  MARKETING_MANAGER: '/fleet-evaluation',
  MARKETING_HEAD: '/executive-approval',
  FINANCE_TEAM: '/finance',
}

const AUDIT_OVERRIDE = {
  id: 77,
  actorUsername: 'supervisor',
  actorRole: 'SUPERVISOR',
  action: 'OVERRIDE',
  reason: 'Dealer has a framework agreement',
  before: { requiredDeposit: 6000000, notes: null },
  after: { requiredDeposit: 5000000, notes: null },
  createdAt: '2026-10-09T05:00:00Z',
}

type Handlers = Parameters<typeof fakeServer>[0]

async function openDrawer(role: Role, status: FleetStatus, extra: Handlers = {}, allowedActions: string[] = []) {
  const server = fakeServer({ ...fleetHandlers(userFor(role), detail(status, { allowedActions })), ...extra })
  renderApp(`${MODULE[role]}?request=1`)
  const drawer = await screen.findByRole('dialog')
  await within(drawer).findByRole('tablist')
  return { server, drawer, user: userEvent.setup() }
}

const overrideSwitch = (drawer: HTMLElement) => within(drawer).queryByRole('switch', { name: 'Supervisor override' })
const posts = (server: ReturnType<typeof fakeServer>, url: string) => server.calls.filter((c) => c.method === 'POST' && c.url === url)

describe('override visibility', () => {
  it.each(['MARKETING_MANAGER', 'MARKETING_HEAD', 'FINANCE_TEAM'] as Role[])('is hidden for %s', async (role) => {
    const { drawer } = await openDrawer(role, 'PAYMENT_PENDING')
    expect(overrideSwitch(drawer)).not.toBeInTheDocument()
    expect(within(drawer).queryByRole('form', { name: 'Supervisor override' })).not.toBeInTheDocument()
  })

  it('is offered to a Supervisor on an open request, off by default', async () => {
    const { drawer } = await openDrawer('SUPERVISOR', 'PAYMENT_PENDING')
    expect(overrideSwitch(drawer)).toHaveAttribute('aria-checked', 'false')
    expect(within(drawer).getByRole('tablist')).toBeInTheDocument()
  })

  it.each(['COMPLETED', 'CANCELLED'] as FleetStatus[])('is not offered on a %s request', async (status) => {
    const { drawer } = await openDrawer('SUPERVISOR', status)
    expect(overrideSwitch(drawer)).not.toBeInTheDocument()
  })
})

describe('override flow', () => {
  it('requires a reason and a change, shows old vs new, then posts only the changed fields', async () => {
    const { drawer, server, user } = await openDrawer('SUPERVISOR', 'PAYMENT_PENDING', {
      'GET /admin/audit-logs': () => [200, page([AUDIT_OVERRIDE])],
      'POST /fleet-requests/1/override': () => [200, detail('PAYMENT_PENDING', { requiredDeposit: 4000000, version: 4 })],
    })
    await user.click(overrideSwitch(drawer) as HTMLElement)
    expect(overrideSwitch(drawer)).toHaveAttribute('aria-checked', 'true')
    expect(within(drawer).getByText(/Override mode/)).toBeInTheDocument()
    const form = within(drawer).getByRole('form', { name: 'Supervisor override' })

    // Audit trail of earlier overrides.
    expect(await within(drawer).findByText('“Dealer has a framework agreement”')).toBeInTheDocument()

    // No reason → blocked on the client.
    await user.click(within(form).getByRole('button', { name: 'Review override' }))
    expect(await within(form).findByText('A reason is required for every override.')).toBeInTheDocument()

    // A reason but nothing changed → blocked.
    await user.type(within(form).getByLabelText(/Reason for override/), 'Board approved a lower deposit')
    await user.click(within(form).getByRole('button', { name: 'Review override' }))
    expect(await within(form).findByText('Change at least one field to override.')).toBeInTheDocument()
    expect(posts(server, '/fleet-requests/1/override')).toHaveLength(0)

    const deposit = within(form).getByLabelText(/Required deposit/)
    await user.clear(deposit)
    await user.type(deposit, '4000000')
    await user.clear(within(form).getByLabelText(/Production estimate/))
    await user.click(within(form).getByRole('button', { name: 'Review override' }))

    const dialog = await screen.findByRole('alertdialog', { name: 'Apply this override?' })
    const rows = within(within(dialog).getByRole('table', { name: 'Override changes' })).getAllByRole('row')
    expect(rows).toHaveLength(3)
    expect(rows[1]).toHaveTextContent('Required deposit₹50,00,000.00₹40,00,000.00')
    expect(rows[2]).toHaveTextContent('Production estimate6 weeks—')
    expect(dialog).toHaveTextContent('Board approved a lower deposit')
    expect(posts(server, '/fleet-requests/1/override')).toHaveLength(0)

    await user.click(within(dialog).getByRole('button', { name: 'Apply override' }))
    await waitFor(() => expect(posts(server, '/fleet-requests/1/override')).toHaveLength(1))
    expect(posts(server, '/fleet-requests/1/override')[0].body).toEqual({
      reason: 'Board approved a lower deposit',
      version: 3,
      changes: { requiredDeposit: '4000000', productionEstimateWeeks: null },
    })
    expect(await within(screen.getByRole('region', { name: 'Notifications' })).findByText('Override applied')).toBeInTheDocument()
  })

  it('Supervisor cancel needs a reason and posts it', async () => {
    const { drawer, server, user } = await openDrawer(
      'SUPERVISOR',
      'PAYMENT_PENDING',
      { 'POST /fleet-requests/1/cancel': () => [200, detail('CANCELLED', { cancelReason: 'Dealer withdrew' })] },
      ['CANCEL'],
    )
    await user.click(within(drawer).getByRole('button', { name: 'Cancel request' }))
    const dialog = await screen.findByRole('alertdialog', { name: 'Cancel this request?' })
    await user.click(within(dialog).getByRole('button', { name: 'Cancel request' }))
    expect(within(dialog).getByText('A reason is required.')).toBeInTheDocument()
    expect(posts(server, '/fleet-requests/1/cancel')).toHaveLength(0)
    await user.type(within(dialog).getByLabelText(/Reason/), 'Dealer withdrew')
    await user.click(within(dialog).getByRole('button', { name: 'Cancel request' }))
    await waitFor(() => expect(posts(server, '/fleet-requests/1/cancel')[0]?.body).toEqual({ version: 3, reason: 'Dealer withdrew' }))
  })
})
