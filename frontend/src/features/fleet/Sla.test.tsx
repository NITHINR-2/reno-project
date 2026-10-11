import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Role } from '@/features/auth/api'
import { fakeServer, userFor } from '@/test/fakeServer'
import { fleetHandlers, row } from '@/test/fleetFixtures'
import { renderApp } from '@/test/renderApp'

const listCalls = (server: ReturnType<typeof fakeServer>) => server.calls.filter((c) => c.method === 'GET' && c.url === '/fleet-requests')

describe('SLA in the fleet workspace', () => {
  it('shows a server-status chip on each row', async () => {
    fakeServer(
      fleetHandlers(userFor('MARKETING_MANAGER'), undefined, [
        row(),
        row({ id: 2, requestNumber: 'FLT-2026-1001', slaStatus: 'ON_TRACK', slaDueAt: '2099-01-01T00:00:00Z' }),
        row({ id: 3, requestNumber: 'FLT-2026-1002', status: 'RETURNED_TO_MARKETING', slaStatus: null, slaDueAt: null }),
      ]),
    )
    renderApp('/fleet-evaluation')
    const rows = within(await screen.findByRole('table', { name: 'Fleet requests' })).getAllByRole('row')
    expect(within(rows[1]).getByLabelText(/^SLA overdue: Overdue/)).toBeInTheDocument()
    expect(within(rows[2]).getByLabelText(/^SLA on track: .* left$/)).toBeInTheDocument()
    expect(within(rows[3]).getByText('No SLA')).toBeInTheDocument()
  })

  it.each([
    ['MARKETING_MANAGER', '/fleet-evaluation', '3 at risk · 1 overdue'],
    ['FINANCE_TEAM', '/finance', '1 at risk · 1 overdue'],
    ['MARKETING_HEAD', '/executive-approval', 'No requests at risk'],
  ] as [Role, string, string][])('%s sees the at-risk count for its own stage', async (role, path, text) => {
    fakeServer(fleetHandlers(userFor(role)))
    renderApp(path)
    expect(await screen.findByRole('button', { name: text === 'No requests at risk' ? text : /at risk.*overdue/ })).toHaveTextContent(text)
  })

  it('the at-risk count and the Overdue filter narrow the table on the server', async () => {
    const server = fakeServer(fleetHandlers(userFor('MARKETING_MANAGER')))
    const user = userEvent.setup()
    const { router } = renderApp('/fleet-evaluation')
    await screen.findByRole('table', { name: 'Fleet requests' })

    await user.click(await screen.findByRole('button', { name: /3 at risk, 1 overdue/ }))
    await waitFor(() => expect(listCalls(server).at(-1)?.params.sla).toEqual(['AT_RISK', 'BREACHED']))

    await user.selectOptions(screen.getByLabelText('SLA'), 'Overdue')
    await waitFor(() => expect(listCalls(server).at(-1)?.params.sla).toEqual(['BREACHED']))
    expect(router.state.location.search).toContain('sla=BREACHED')
    // The stage filter is kept alongside the SLA filter.
    expect(listCalls(server).at(-1)?.params.status).toEqual(['PENDING_MARKETING_REVIEW', 'RETURNED_TO_MARKETING', 'MARKETING_QUALIFIED'])

    // Clearing it goes back to the earlier (still cached) unfiltered query.
    await user.selectOptions(screen.getByLabelText('SLA'), 'Any SLA')
    await waitFor(() => expect(router.state.location.search).not.toContain('sla='))
  })

  it('the drawer shows the chip, stage start and due time', async () => {
    fakeServer(fleetHandlers(userFor('MARKETING_MANAGER')))
    renderApp('/fleet-evaluation?request=1')
    const drawer = await screen.findByRole('dialog')
    expect(await within(drawer).findAllByLabelText(/^SLA overdue/)).toHaveLength(2)
    expect(within(drawer).getByText('In this stage since')).toBeInTheDocument()
    expect(within(drawer).getByText(/^due /)).toBeInTheDocument()
  })
})
