import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Role } from '@/features/auth/api'
import type { FleetStatus } from '@/features/fleet/api'
import { fakeServer, userFor } from '@/test/fakeServer'
import { detail, fleetHandlers, page } from '@/test/fleetFixtures'
import { renderApp } from '@/test/renderApp'

const MODULE: Record<Exclude<Role, 'DEALER'>, string> = {
  ADMIN: '/admin',
  SUPERVISOR: '/operations',
  MARKETING_MANAGER: '/fleet-evaluation',
  MARKETING_HEAD: '/executive-approval',
  FINANCE_TEAM: '/finance',
}

async function openDrawer(role: Exclude<Role, 'DEALER'>, status: FleetStatus, allowedActions: string[] = [], extra = {}) {
  const server = fakeServer({ ...fleetHandlers(userFor(role), detail(status, { allowedActions })), ...extra })
  renderApp(`${MODULE[role]}?request=1`)
  const drawer = await screen.findByRole('dialog')
  await within(drawer).findByRole('tablist')
  return { server, drawer, user: userEvent.setup() }
}

const tabNames = (drawer: HTMLElement) => within(drawer).getAllByRole('tab').map((t) => t.textContent)

async function openTab(drawer: HTMLElement, name: string, user: ReturnType<typeof userEvent.setup>) {
  await user.click(within(drawer).getByRole('tab', { name }))
  await waitFor(() => expect(within(drawer).getByRole('tab', { name })).toHaveAttribute('aria-selected', 'true'))
  return within(drawer).getByRole('tabpanel')
}

describe('drawer tabs per role', () => {
  it.each([
    ['MARKETING_MANAGER', ['Overview', 'Inventory', 'Workflow', 'Schedule']],
    ['MARKETING_HEAD', ['Overview', 'Inventory', 'Finance', 'Payments', 'Workflow', 'Schedule']],
    ['FINANCE_TEAM', ['Overview', 'Inventory', 'Finance', 'Payments', 'Workflow', 'Schedule']],
    ['SUPERVISOR', ['Overview', 'Inventory', 'Finance', 'Payments', 'Workflow', 'Schedule', 'Audit']],
  ] as [Exclude<Role, 'DEALER'>, string[]][])('%s sees only the tabs it may read', async (role, tabs) => {
    const { drawer } = await openDrawer(role, 'PENDING_MARKETING_REVIEW')
    expect(tabNames(drawer)).toEqual(tabs)
  })
})

describe('drawer editability by role and state', () => {
  it('Marketing Manager edits details and maps stock in marketing review', async () => {
    const { drawer, server, user } = await openDrawer('MARKETING_MANAGER', 'PENDING_MARKETING_REVIEW', [], {
      'PATCH /fleet-requests/1': () => [200, detail('PENDING_MARKETING_REVIEW', { quantity: 60, version: 4 })],
    })
    const form = await within(drawer).findByRole('form', { name: 'Edit request' })
    expect(within(drawer).queryByRole('note')).not.toBeInTheDocument()

    const quantity = within(form).getByLabelText(/Quantity/)
    await user.clear(quantity)
    await user.type(quantity, '60')
    await user.click(within(form).getByRole('button', { name: 'Save changes' }))
    await waitFor(() => expect(server.calls.some((c) => c.method === 'PATCH')).toBe(true))
    const patch = server.calls.find((c) => c.method === 'PATCH')
    expect(patch?.body).toMatchObject({ version: 3, quantity: 60, requiredDeposit: '5000000.00' })

    const inventory = await openTab(drawer, 'Inventory', user)
    expect(await within(inventory).findByRole('button', { name: 'Save mapping' })).toBeInTheDocument()
    // The fully-allocated yard row stays editable even though it is no longer a stock "option".
    expect(within(inventory).getByLabelText('Units from Chennai Yard')).toHaveValue('30')
  })

  it('rejects an invalid edit on the client before calling the server', async () => {
    const { drawer, server, user } = await openDrawer('MARKETING_MANAGER', 'PENDING_MARKETING_REVIEW')
    const form = await within(drawer).findByRole('form', { name: 'Edit request' })
    const deposit = within(form).getByLabelText(/Required deposit/)
    await user.clear(deposit)
    await user.type(deposit, '12.345')
    await user.click(within(form).getByRole('button', { name: 'Save changes' }))
    expect(await within(form).findByText('Enter an amount in rupees, up to 2 decimals.')).toBeInTheDocument()
    expect(server.calls.some((c) => c.method === 'PATCH')).toBe(false)
  })

  it('Marketing Manager sees a read-only request once it has left marketing review', async () => {
    const { drawer, user } = await openDrawer('MARKETING_MANAGER', 'PAYMENT_PENDING')
    expect(await within(drawer).findByRole('note')).toHaveTextContent(/pending marketing review/)
    expect(within(drawer).queryByRole('form')).not.toBeInTheDocument()
    expect(within(drawer).getByText('₹50,00,000.00')).toBeInTheDocument()

    const inventory = await openTab(drawer, 'Inventory', user)
    expect(within(inventory).queryByRole('button', { name: 'Save mapping' })).not.toBeInTheDocument()
    expect(within(inventory).getByText('Chennai Yard')).toBeInTheDocument()
  })

  it('Marketing Head reads finance and payments but cannot change them', async () => {
    const { drawer, user } = await openDrawer('MARKETING_HEAD', 'PENDING_FINANCE_CLEARANCE')
    expect(within(drawer).queryByRole('form')).not.toBeInTheDocument()

    const finance = await openTab(drawer, 'Finance', user)
    expect(await within(finance).findByText('No quotation yet.')).toBeInTheDocument()
    expect(within(finance).queryByRole('button', { name: 'Calculate' })).not.toBeInTheDocument()

    const payments = await openTab(drawer, 'Payments', user)
    expect(await within(payments).findByText('No receipts recorded.')).toBeInTheDocument()
    expect(within(payments).queryByRole('form', { name: 'Record a receipt' })).not.toBeInTheDocument()
  })

  it('Finance calculates quotations and records receipts in the finance stage, but cannot edit details', async () => {
    const { drawer, user } = await openDrawer('FINANCE_TEAM', 'PENDING_FINANCE_CLEARANCE', [], {
      'GET /fleet-requests/1/payment-receipts': () => [
        200,
        page([{ id: 4, receiptNumber: 'RCPT-2026-00004', receiptType: 'DEPOSIT', paymentMethod: 'NEFT', transactionReference: 'UTR123', amount: 1000000, receivedDate: '2026-10-05', status: 'RECORDED', recordedBy: 'fin', reviewedBy: null, version: 0 }]),
      ],
    })
    expect(await within(drawer).findByRole('note')).toBeInTheDocument()
    expect(within(drawer).queryByRole('form', { name: 'Edit request' })).not.toBeInTheDocument()

    const finance = await openTab(drawer, 'Finance', user)
    expect(await within(finance).findByRole('button', { name: 'Calculate' })).toBeInTheDocument()

    const payments = await openTab(drawer, 'Payments', user)
    expect(await within(payments).findByRole('form', { name: 'Record a receipt' })).toBeInTheDocument()
    expect(within(payments).getByRole('button', { name: 'Approve' })).toBeInTheDocument()
  })

  it('Finance cannot calculate or record once the request has moved on to operations', async () => {
    const { drawer, user } = await openDrawer('FINANCE_TEAM', 'FINANCIALLY_CLEARED')
    const finance = await openTab(drawer, 'Finance', user)
    expect(await within(finance).findByText(/calculated and issued by Finance while/)).toBeInTheDocument()
    expect(within(finance).queryByRole('button', { name: 'Calculate' })).not.toBeInTheDocument()
    const payments = await openTab(drawer, 'Payments', user)
    await within(payments).findByText('No receipts recorded.')
    expect(within(payments).queryByRole('form', { name: 'Record a receipt' })).not.toBeInTheDocument()
  })

  it('Supervisor schedules production only when the server allows it, and sees the audit log', async () => {
    const { drawer, user } = await openDrawer('SUPERVISOR', 'FINANCIALLY_CLEARED', ['SCHEDULE_PRODUCTION', 'CANCEL'])
    const schedule = await openTab(drawer, 'Schedule', user)
    expect(await within(schedule).findByRole('form', { name: 'Schedule production' })).toBeInTheDocument()
    expect(within(schedule).queryByRole('form', { name: 'Schedule dispatch' })).not.toBeInTheDocument()

    const audit = await openTab(drawer, 'Audit', user)
    expect(await within(audit).findByText('No audit entries.')).toBeInTheDocument()
  })

  it('Supervisor schedule tab is read-only before clearance', async () => {
    const { drawer, user } = await openDrawer('SUPERVISOR', 'PAYMENT_PENDING', [])
    const schedule = await openTab(drawer, 'Schedule', user)
    expect(await within(schedule).findByText(/scheduled by a Supervisor once/)).toBeInTheDocument()
    expect(within(schedule).queryByRole('form')).not.toBeInTheDocument()
  })
})
