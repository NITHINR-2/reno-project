import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { fakeServer, userFor } from '@/test/fakeServer'
import { fleetHandlers, row } from '@/test/fleetFixtures'
import { renderApp } from '@/test/renderApp'

const listCalls = (server: ReturnType<typeof fakeServer>) => server.calls.filter((c) => c.method === 'GET' && c.url === '/fleet-requests')

describe('fleet workspace', () => {
  it('renders the summary cards and the table from the API, filtered to the module stage', async () => {
    const server = fakeServer(
      fleetHandlers(userFor('MARKETING_MANAGER'), undefined, [
        row(),
        row({ id: 2, requestNumber: 'FLT-2026-1001', dealerName: 'Pune West Motors', regionName: 'West Region', status: 'RETURNED_TO_MARKETING', quantity: 12, allocatedQty: 0, productionRequired: 12 }),
      ]),
    )
    renderApp('/fleet-evaluation')

    const table = await screen.findByRole('table', { name: 'Fleet requests' })
    const rows = within(table).getAllByRole('row')
    expect(rows).toHaveLength(3)
    expect(within(rows[1]).getByText('FLT-2026-0891')).toBeInTheDocument()
    expect(within(rows[1]).getByText('Chennai North Dealership')).toBeInTheDocument()
    expect(within(rows[1]).getByText('Renault Duster')).toBeInTheDocument()
    expect(within(rows[1]).getByText('Pending marketing review')).toBeInTheDocument()
    expect(within(rows[2]).getByText('Returned to marketing')).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: 'Pagination' })).toHaveTextContent('Showing 1–20 of 45')

    const summary = screen.getByRole('region', { name: 'Summary' })
    expect(await within(summary).findByText('140')).toBeInTheDocument()
    expect(within(summary).getByRole('button', { name: /Marketing review/ })).toHaveAttribute('aria-pressed', 'true')

    const first = listCalls(server)[0]
    expect(first.params).toMatchObject({ page: 0, size: 20, sort: 'createdAt,desc', status: ['PENDING_MARKETING_REVIEW', 'RETURNED_TO_MARKETING', 'MARKETING_QUALIFIED'] })

    // Mobile card layout comes from the same data.
    expect(within(screen.getByRole('list', { name: 'Fleet requests' })).getAllByRole('button', { name: 'View' })).toHaveLength(2)
  })

  it('sends sort, search, filters and paging to the server', async () => {
    const server = fakeServer(fleetHandlers(userFor('SUPERVISOR')))
    const user = userEvent.setup()
    const { router } = renderApp('/finance')
    await screen.findByRole('table', { name: 'Fleet requests' })

    await user.click(within(screen.getByRole('table')).getByRole('button', { name: /Dealer/ }))
    await waitFor(() => expect(listCalls(server).at(-1)?.params.sort).toBe('dealer.name,asc'))

    await user.type(screen.getByLabelText('Search'), 'FLT-2026')
    await waitFor(() => expect(listCalls(server).at(-1)?.params.q).toBe('FLT-2026'))

    await user.selectOptions(screen.getByLabelText('Status'), 'PAYMENT_PENDING')
    await user.selectOptions(await screen.findByLabelText('Region'), '1')
    await waitFor(() => expect(listCalls(server).at(-1)?.params).toMatchObject({ status: ['PAYMENT_PENDING'], regionId: '1', q: 'FLT-2026' }))

    await user.click(screen.getByRole('button', { name: 'Next page' }))
    await waitFor(() => expect(listCalls(server).at(-1)?.params.page).toBe(1))
    expect(router.state.location.search).toContain('page=1')
    expect(router.state.location.search).toContain('regionId=1')

    await user.click(screen.getByRole('button', { name: 'Clear filters' }))
    await waitFor(() => expect(listCalls(server).at(-1)?.params).toMatchObject({ page: 0, status: undefined, regionId: undefined, q: undefined }))
  })

  it('shows the empty state when nothing matches', async () => {
    fakeServer(fleetHandlers(userFor('FINANCE_TEAM'), undefined, []))
    renderApp('/finance')
    expect(await screen.findByText('No fleet requests match')).toBeInTheDocument()
  })

  it('shows an error state with retry when the list fails', async () => {
    const handlers = fleetHandlers(userFor('MARKETING_HEAD'))
    let fail = true
    const server = fakeServer({
      ...handlers,
      'GET /fleet-requests': () => (fail ? [500, { title: 'Internal Server Error', status: 500, detail: 'Database unavailable.' }] : handlers['GET /fleet-requests']()),
    })
    const user = userEvent.setup()
    renderApp('/executive-approval')

    const alert = await screen.findByText("Couldn't load fleet requests", undefined, { timeout: 3000 })
    fail = false
    await user.click(within(alert.closest('[role="alert"]') as HTMLElement).getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('table', { name: 'Fleet requests' })).toBeInTheDocument()
    expect(listCalls(server).length).toBeGreaterThanOrEqual(2)
  })

  it('opens the drawer from a row and keeps it in the URL', async () => {
    fakeServer(fleetHandlers(userFor('MARKETING_MANAGER')))
    const user = userEvent.setup()
    const { router } = renderApp('/fleet-evaluation')

    await user.click(await within(await screen.findByRole('table')).findByText('Chennai North Dealership'))
    const drawer = await screen.findByRole('dialog')
    expect(await within(drawer).findByText('FLT-2026-0891', { selector: '.font-mono' })).toBeInTheDocument()
    expect(router.state.location.search).toContain('request=1')

    await user.click(within(drawer).getByRole('button', { name: 'Close' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(router.state.location.search).not.toContain('request=')
  })
})
