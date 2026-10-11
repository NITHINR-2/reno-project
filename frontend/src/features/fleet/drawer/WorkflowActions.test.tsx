import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Role } from '@/features/auth/api'
import type { FleetStatus } from '@/features/fleet/api'
import { fakeServer, userFor } from '@/test/fakeServer'
import { detail, fleetHandlers, page } from '@/test/fleetFixtures'
import { renderApp } from '@/test/renderApp'

const MODULE: Record<Role, string> = {
  ADMIN: '/admin',
  SUPERVISOR: '/operations',
  MARKETING_MANAGER: '/fleet-evaluation',
  MARKETING_HEAD: '/executive-approval',
  FINANCE_TEAM: '/finance',
}

type Handlers = Parameters<typeof fakeServer>[0]

async function openDrawer(role: Role, status: FleetStatus, allowedActions: string[], extra: Handlers = {}) {
  const server = fakeServer({ ...fleetHandlers(userFor(role), detail(status, { allowedActions })), ...extra })
  renderApp(`${MODULE[role]}?request=1`)
  const drawer = await screen.findByRole('dialog')
  await within(drawer).findByRole('tablist')
  return { server, drawer, user: userEvent.setup() }
}

const actionNames = (drawer: HTMLElement) =>
  within(drawer).queryByRole('group', { name: 'Workflow actions' }) === null
    ? []
    : within(within(drawer).getByRole('group', { name: 'Workflow actions' })).getAllByRole('button').map((b) => b.textContent)

const findToast = (text: string) => within(screen.getByRole('region', { name: 'Notifications' })).findByText(text)

const posts = (server: ReturnType<typeof fakeServer>, url: string) => server.calls.filter((c) => c.method === 'POST' && c.url === url)

describe('workflow action buttons per role and state', () => {
  it.each([
    ['MARKETING_MANAGER', 'PENDING_MARKETING_REVIEW', ['QUALIFY'], ['Qualify']],
    ['MARKETING_MANAGER', 'MARKETING_QUALIFIED', ['FORWARD_TO_EXECUTIVE'], ['Forward to Marketing Head']],
    ['MARKETING_MANAGER', 'RETURNED_TO_MARKETING', ['RESUBMIT'], ['Resubmit for review']],
    ['MARKETING_HEAD', 'PENDING_EXECUTIVE_APPROVAL', ['APPROVE', 'REJECT'], ['Approve', 'Reject']],
    ['MARKETING_HEAD', 'EXECUTIVE_APPROVED', ['FORWARD_TO_FINANCE'], ['Forward to Finance']],
    // The server never offers ISSUE_QUOTATION up front (seen live); the shortcuts follow role + state.
    ['FINANCE_TEAM', 'PENDING_FINANCE_CLEARANCE', [], ['Quotation…', 'Receipts…']],
    ['FINANCE_TEAM', 'FUNDS_VERIFIED', ['GRANT_CLEARANCE'], ['Receipts…', 'Grant clearance']],
    ['SUPERVISOR', 'FINANCIALLY_CLEARED', ['SCHEDULE_PRODUCTION', 'CANCEL'], ['Schedule production…']],
    ['SUPERVISOR', 'DISPATCH_SCHEDULED', ['COMPLETE', 'CANCEL'], ['Mark completed']],
    // System steps and actions the server didn't offer never show a button.
    ['FINANCE_TEAM', 'PAYMENT_PENDING', ['AWAIT_PAYMENT', 'VERIFY_FUNDS'], ['Receipts…']],
    ['MARKETING_HEAD', 'PAYMENT_PENDING', [], []],
    ['MARKETING_HEAD', 'PENDING_MARKETING_REVIEW', [], []],
  ] as [Role, FleetStatus, string[], string[]][])('%s in %s sees %j', async (role, status, allowed, buttons) => {
    const { drawer } = await openDrawer(role, status, allowed)
    expect(actionNames(drawer)).toEqual(buttons)
  })
})

describe('transitions', () => {
  it('Marketing Manager qualifies after confirming; the request version is sent and a toast shows', async () => {
    const { drawer, server, user } = await openDrawer('MARKETING_MANAGER', 'PENDING_MARKETING_REVIEW', ['QUALIFY'], {
      'POST /fleet-requests/1/submit-for-qualification': () => [200, detail('MARKETING_QUALIFIED', { version: 4 })],
    })
    await user.click(within(drawer).getByRole('button', { name: 'Qualify' }))
    const dialog = await screen.findByRole('alertdialog', { name: 'Qualify this request?' })
    expect(posts(server, '/fleet-requests/1/submit-for-qualification')).toHaveLength(0)

    await user.click(within(dialog).getByRole('button', { name: 'Qualify' }))
    await waitFor(() => expect(posts(server, '/fleet-requests/1/submit-for-qualification')).toHaveLength(1))
    expect(posts(server, '/fleet-requests/1/submit-for-qualification')[0].body).toEqual({ version: 3 })
    expect(await findToast('Request qualified')).toBeInTheDocument()
    // The detail is re-fetched (queries invalidated) after the mutation.
    await waitFor(() => expect(server.calls.filter((c) => c.url === '/fleet-requests/1').length).toBeGreaterThan(1))
  })

  it('Reject needs a reason before anything is sent', async () => {
    const { drawer, server, user } = await openDrawer('MARKETING_HEAD', 'PENDING_EXECUTIVE_APPROVAL', ['APPROVE', 'REJECT'], {
      'POST /fleet-requests/1/reject': () => [200, detail('RETURNED_TO_MARKETING', { returnReason: 'Deposit too low' })],
    })
    await user.click(within(drawer).getByRole('button', { name: 'Reject' }))
    const dialog = await screen.findByRole('alertdialog', { name: 'Reject and return to Marketing?' })
    await user.click(within(dialog).getByRole('button', { name: 'Reject' }))
    expect(within(dialog).getByText('A reason is required.')).toBeInTheDocument()
    expect(posts(server, '/fleet-requests/1/reject')).toHaveLength(0)

    await user.type(within(dialog).getByLabelText(/Reason/), 'Deposit too low')
    await user.click(within(dialog).getByRole('button', { name: 'Reject' }))
    await waitFor(() => expect(posts(server, '/fleet-requests/1/reject')).toHaveLength(1))
    expect(posts(server, '/fleet-requests/1/reject')[0].body).toEqual({ version: 3, reason: 'Deposit too low' })
    expect(await findToast('Returned to Marketing')).toBeInTheDocument()
  })

  it('Cancelling the confirmation sends nothing', async () => {
    const { drawer, server, user } = await openDrawer('FINANCE_TEAM', 'FUNDS_VERIFIED', ['GRANT_CLEARANCE'])
    await user.click(within(drawer).getByRole('button', { name: 'Grant clearance' }))
    const dialog = await screen.findByRole('alertdialog')
    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }))
    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument())
    expect(posts(server, '/fleet-requests/1/grant-clearance')).toHaveLength(0)
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })

  it('Escape closes only the confirmation, not the drawer underneath', async () => {
    const { drawer, user } = await openDrawer('MARKETING_HEAD', 'PENDING_EXECUTIVE_APPROVAL', ['APPROVE', 'REJECT'])
    await user.click(within(drawer).getByRole('button', { name: 'Approve' }))
    await screen.findByRole('alertdialog')
    await user.keyboard('{Escape}')
    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument())
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })

  it('a server conflict shows the ProblemDetail in an error toast', async () => {
    const { drawer, user } = await openDrawer('MARKETING_HEAD', 'EXECUTIVE_APPROVED', ['FORWARD_TO_FINANCE'], {
      'POST /fleet-requests/1/forward-to-finance': () => [409, { title: 'Conflict', status: 409, detail: 'The request was changed by someone else.' }],
    })
    await user.click(within(drawer).getByRole('button', { name: 'Forward to Finance' }))
    await user.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Forward to Finance' }))
    const toast = await screen.findByRole('alert')
    expect(toast).toHaveTextContent('Conflict')
    expect(toast).toHaveTextContent('changed by someone else')
  })

  it('Schedule production… opens the Schedule tab; the form validates and confirms before posting', async () => {
    const { drawer, server, user } = await openDrawer('SUPERVISOR', 'FINANCIALLY_CLEARED', ['SCHEDULE_PRODUCTION'], {
      'POST /fleet-requests/1/schedule-production': () => [200, detail('PRODUCTION_SCHEDULED')],
    })
    await user.click(within(drawer).getByRole('button', { name: 'Schedule production…' }))
    expect(within(drawer).getByRole('tab', { name: 'Schedule' })).toHaveAttribute('aria-selected', 'true')
    const form = await within(drawer).findByRole('form', { name: 'Schedule production' })

    await user.click(within(form).getByRole('button', { name: 'Schedule production' }))
    expect(await within(form).findByText('Enter a completion date.')).toBeInTheDocument()

    await user.clear(within(form).getByLabelText(/Start/))
    await user.type(within(form).getByLabelText(/Start/), '2099-03-10')
    await user.type(within(form).getByLabelText(/Estimated completion/), '2099-03-01')
    await user.click(within(form).getByRole('button', { name: 'Schedule production' }))
    expect(await within(form).findByText('Must be on or after the start.')).toBeInTheDocument()

    await user.clear(within(form).getByLabelText(/Estimated completion/))
    await user.type(within(form).getByLabelText(/Estimated completion/), '2099-04-20')
    await user.click(within(form).getByRole('button', { name: 'Schedule production' }))
    const dialog = await screen.findByRole('alertdialog', { name: 'Schedule production?' })
    expect(dialog).toHaveTextContent('20 units')
    expect(posts(server, '/fleet-requests/1/schedule-production')).toHaveLength(0)
    await user.click(within(dialog).getByRole('button', { name: 'Schedule production' }))
    await waitFor(() => expect(posts(server, '/fleet-requests/1/schedule-production')).toHaveLength(1))
    expect(posts(server, '/fleet-requests/1/schedule-production')[0].body).toEqual({
      scheduledStart: '2099-03-10',
      estimatedCompletion: '2099-04-20',
      version: 3,
    })
    expect(await findToast('Production scheduled')).toBeInTheDocument()
  })
})

const QUOTATION = {
  id: 9,
  quotationNumber: 'QT-2026-00009',
  status: 'DRAFT',
  paymentStatus: null,
  gstRate: 0.18,
  baseAmount: 60000000,
  gstAmount: 10800000,
  totalAmount: 70800000,
  requiredDeposit: 5000000,
  depositReceived: 1000000,
  depositShortfall: 4000000,
  issueDate: null,
  validUntil: null,
  issuedBy: null,
  createdBy: 'Fatima Finance',
  termsText: 'Prices ex-works Pune.',
  lines: [
    { lineNo: 1, description: 'Renault Duster RXZ Petrol', quantity: 50, unitPrice: 1200000, baseAmount: 60000000, gstRate: 0.18, gstAmount: 10800000, lineTotal: 70800000 },
  ],
  version: 0,
  createdAt: '2026-10-10T05:00:00Z',
}

describe('finance', () => {
  it('shows the breakdown exactly as the API returned it, after Calculate', async () => {
    let calculated = false
    const { drawer, server, user } = await openDrawer('FINANCE_TEAM', 'PENDING_FINANCE_CLEARANCE', [], {
      'GET /fleet-requests/1/quotations': () => [200, page(calculated ? [QUOTATION] : [])],
      'POST /fleet-requests/1/quotations/calculate': () => {
        calculated = true
        return [200, QUOTATION]
      },
    })
    await user.click(within(drawer).getByRole('button', { name: 'Quotation…' }))
    const panel = within(drawer).getByRole('tabpanel')
    expect(await within(panel).findByText('No quotation yet.')).toBeInTheDocument()

    await user.click(within(panel).getByRole('button', { name: 'Calculate' }))
    await waitFor(() => expect(posts(server, '/fleet-requests/1/quotations/calculate')).toHaveLength(1))
    expect(await findToast('Quotation calculated')).toBeInTheDocument()

    const breakdown = within(drawer).getByRole('tabpanel')
    expect(await within(breakdown).findByText('Quotation QT-2026-00009')).toBeInTheDocument()
    const term = (label: string) => within(breakdown).getByText(label).nextElementSibling
    expect(term('Base amount')).toHaveTextContent('₹6,00,00,000.00')
    expect(term('GST rate')).toHaveTextContent('18%')
    expect(term('GST amount')).toHaveTextContent('₹1,08,00,000.00')
    expect(term('Total')).toHaveTextContent('₹7,08,00,000.00')
    expect(term('Deposit received')).toHaveTextContent('₹10,00,000.00')
    expect(term('Deposit shortfall')).toHaveTextContent('₹40,00,000.00')

    const line = within(within(breakdown).getByRole('table', { name: 'Quotation lines' })).getAllByRole('row')[1]
    expect(line).toHaveTextContent('Renault Duster RXZ Petrol')
    expect(line).toHaveTextContent('₹12,00,000.00')
    expect(line).toHaveTextContent('₹7,08,00,000.00')
    expect(within(breakdown).getByText(/Prices ex-works Pune/)).toBeInTheDocument()
  })

  it('issues a draft only after confirming, sending the quotation version', async () => {
    const { drawer, server, user } = await openDrawer('FINANCE_TEAM', 'PENDING_FINANCE_CLEARANCE', [], {
      'GET /fleet-requests/1/quotations': () => [200, page([QUOTATION])],
      'POST /quotations/9/issue': () => [200, { ...QUOTATION, status: 'ISSUED', issueDate: '2026-10-11', validUntil: '2026-11-10' }],
    })
    const finance = await (async () => {
      await user.click(within(drawer).getByRole('tab', { name: 'Finance' }))
      return within(drawer).getByRole('tabpanel')
    })()
    await user.click(await within(finance).findByRole('button', { name: 'Issue' }))
    const dialog = await screen.findByRole('alertdialog', { name: 'Issue QT-2026-00009?' })
    expect(dialog).toHaveTextContent('Issuing is not payment')
    await user.click(within(dialog).getByRole('button', { name: 'Issue quotation' }))
    await waitFor(() => expect(posts(server, '/quotations/9/issue')).toHaveLength(1))
    expect(posts(server, '/quotations/9/issue')[0].body).toEqual({ version: 0 })
    expect(await findToast('Quotation issued')).toBeInTheDocument()
  })

  it('validates a receipt on the client, then confirms before recording', async () => {
    const { drawer, server, user } = await openDrawer('FINANCE_TEAM', 'PAYMENT_PENDING', [], {
      'POST /fleet-requests/1/payment-receipts': () => [
        200,
        { id: 5, receiptNumber: 'RCPT-2026-00005', receiptType: 'DEPOSIT', paymentMethod: 'RTGS', transactionReference: 'UTR-77', amount: 4000000, receivedDate: '2026-10-10', status: 'RECORDED', version: 0 },
      ],
    })
    await user.click(within(drawer).getByRole('tab', { name: 'Payments' }))
    const form = await within(drawer).findByRole('form', { name: 'Record a receipt' })

    await user.click(within(form).getByRole('button', { name: 'Record receipt' }))
    expect(await within(form).findByText('Enter the bank reference.')).toBeInTheDocument()
    expect(within(form).getByText('Enter an amount in rupees, up to 2 decimals.')).toBeInTheDocument()

    await user.type(within(form).getByLabelText(/Transaction reference/), 'UTR#77')
    await user.type(within(form).getByLabelText(/Amount/), '0')
    await user.clear(within(form).getByLabelText(/Received on/))
    await user.type(within(form).getByLabelText(/Received on/), '2099-01-01')
    await user.click(within(form).getByRole('button', { name: 'Record receipt' }))
    expect(await within(form).findByText('Letters, digits, spaces and / _ . - only.')).toBeInTheDocument()
    expect(within(form).getByText('Must be more than zero.')).toBeInTheDocument()
    expect(within(form).getByText("Can't be in the future.")).toBeInTheDocument()
    expect(posts(server, '/fleet-requests/1/payment-receipts')).toHaveLength(0)

    await user.clear(within(form).getByLabelText(/Transaction reference/))
    await user.type(within(form).getByLabelText(/Transaction reference/), 'UTR-77')
    await user.clear(within(form).getByLabelText(/Amount/))
    await user.type(within(form).getByLabelText(/Amount/), '4000000')
    await user.selectOptions(within(form).getByLabelText(/Method/), 'RTGS')
    await user.clear(within(form).getByLabelText(/Received on/))
    await user.type(within(form).getByLabelText(/Received on/), '2026-10-10')
    await user.click(within(form).getByRole('button', { name: 'Record receipt' }))

    const dialog = await screen.findByRole('alertdialog', { name: 'Record this receipt?' })
    expect(dialog).toHaveTextContent('₹40,00,000.00')
    await user.click(within(dialog).getByRole('button', { name: 'Record receipt' }))
    await waitFor(() => expect(posts(server, '/fleet-requests/1/payment-receipts')).toHaveLength(1))
    expect(posts(server, '/fleet-requests/1/payment-receipts')[0].body).toEqual({
      receiptType: 'DEPOSIT',
      paymentMethod: 'RTGS',
      transactionReference: 'UTR-77',
      amount: '4000000',
      receivedDate: '2026-10-10',
    })
    expect(await findToast('Receipt recorded')).toBeInTheDocument()
  })
})
