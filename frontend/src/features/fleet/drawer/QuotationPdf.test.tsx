import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Role } from '@/features/auth/api'
import type { FleetStatus } from '@/features/fleet/api'
import { fakeServer, userFor } from '@/test/fakeServer'
import { detail, fleetHandlers, page } from '@/test/fleetFixtures'
import { renderApp } from '@/test/renderApp'

const ISSUED = {
  id: 9,
  quotationNumber: 'QT-2026-00009',
  status: 'ISSUED',
  paymentStatus: 'PARTIALLY_PAID',
  gstRate: 0.18,
  baseAmount: 60000000,
  gstAmount: 10800000,
  totalAmount: 70800000,
  requiredDeposit: 5000000,
  depositReceived: 1000000,
  depositShortfall: 4000000,
  issueDate: '2026-10-11',
  validUntil: '2026-11-10',
  issuedBy: 'Fatima Finance',
  createdBy: 'Fatima Finance',
  termsText: null,
  pdfSha256: 'a'.repeat(64),
  lines: [],
  version: 1,
  createdAt: '2026-10-10T05:00:00Z',
}
const DRAFT = { ...ISSUED, id: 8, quotationNumber: 'QT-2026-00008', status: 'CANCELLED', issueDate: null, validUntil: null, pdfSha256: null }

const MODULE: Partial<Record<Role, string>> = { FINANCE_TEAM: '/finance', MARKETING_HEAD: '/executive-approval' }

async function openFinance(role: Role, status: FleetStatus, pdf: () => [number, unknown]) {
  const server = fakeServer({
    ...fleetHandlers(userFor(role), detail(status)),
    'GET /fleet-requests/1/quotations': () => [200, page([ISSUED, DRAFT])],
    'GET /quotations/9/pdf': pdf,
    'GET /quotations/8/pdf': pdf,
  })
  renderApp(`${MODULE[role]}?request=1`)
  const drawer = await screen.findByRole('dialog')
  const user = userEvent.setup()
  await user.click(await within(drawer).findByRole('tab', { name: 'Finance' }))
  return { server, user, panel: within(drawer).getByRole('tabpanel') }
}

describe('quotation PDF download', () => {
  let click: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    URL.createObjectURL = vi.fn(() => 'blob:pdf')
    URL.revokeObjectURL = vi.fn()
    click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
  })
  afterEach(() => click.mockRestore())

  it('downloads the server-rendered PDF as a blob and shows the frozen document hash', async () => {
    const blob = new Blob(['%PDF-1.7'], { type: 'application/pdf' })
    const { server, user, panel } = await openFinance('MARKETING_HEAD', 'PAYMENT_PENDING', () => [200, blob])

    expect(await within(panel).findByText('a'.repeat(64))).toBeInTheDocument()
    expect(within(panel).queryByRole('button', { name: 'Calculate' })).not.toBeInTheDocument()

    await user.click(within(panel).getByRole('button', { name: 'Download QT-2026-00009 PDF' }))
    await waitFor(() => expect(click).toHaveBeenCalledTimes(1))
    const call = server.calls.find((c) => c.url === '/quotations/9/pdf')
    expect(call?.headers.get('Accept')).toContain('application/pdf')
    expect(URL.createObjectURL).toHaveBeenCalledWith(blob)
    expect(await screen.findByRole('region', { name: 'Notifications' })).toHaveTextContent('PDF downloaded')

    // History rows offer the (draft-watermarked) PDF of older quotations too.
    expect(within(panel).getByRole('button', { name: 'Download QT-2026-00008 PDF (draft)' })).toBeInTheDocument()
  })

  it('reports the ProblemDetail when the server refuses, decoding the blob error body', async () => {
    const problem = new Blob([JSON.stringify({ title: 'Forbidden', status: 403, detail: 'You may not download this.' })], {
      type: 'application/problem+json',
    })
    const { user, panel } = await openFinance('FINANCE_TEAM', 'PAYMENT_PENDING', () => [403, problem])

    await user.click(await within(panel).findByRole('button', { name: 'Download QT-2026-00009 PDF' }))
    expect(await screen.findByRole('region', { name: 'Notifications' })).toHaveTextContent('You may not download this.')
    expect(click).not.toHaveBeenCalled()
  })
})
