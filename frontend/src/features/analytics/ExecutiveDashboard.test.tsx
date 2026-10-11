import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Role } from '@/features/auth/api'
import { todayIST } from '@/lib/format'
import { fakeServer, userFor } from '@/test/fakeServer'
import { fleetHandlers } from '@/test/fleetFixtures'
import { renderApp } from '@/test/renderApp'
import type { ExecutiveAnalytics } from './api'
import { addDays, presetDates } from './filters'

const KEY = 'GET /analytics/executive'
const STATUSES = [
  'PENDING_MARKETING_REVIEW', 'RETURNED_TO_MARKETING', 'MARKETING_QUALIFIED', 'PENDING_EXECUTIVE_APPROVAL', 'EXECUTIVE_APPROVED',
  'PENDING_FINANCE_CLEARANCE', 'QUOTATION_ISSUED', 'PAYMENT_PENDING', 'FUNDS_VERIFIED', 'FINANCIALLY_CLEARED',
  'PRODUCTION_SCHEDULED', 'DISPATCH_SCHEDULED', 'COMPLETED', 'CANCELLED',
] as const

const ANALYTICS: ExecutiveAnalytics = {
  period: { from: '2026-09-12', to: '2026-10-11', previousFrom: '2026-08-13', previousTo: '2026-09-11', regionId: null, plantId: null },
  kpis: {
    totalOrderValue: { value: 70800000, previous: 35400000, changePct: 100 },
    requestCount: { value: 12, previous: 15, changePct: -20 },
    avgHoursToClearance: { value: 120.5, previous: 100, changePct: 20.5 },
    slaCompliancePct: { value: 82.4, previous: null, changePct: null },
    overdueRequests: { value: 3, previous: 0, changePct: null },
  },
  monthlyOrderValue: Array.from({ length: 12 }, (_, i) => ({ month: `2026-${String(i + 1).padStart(2, '0')}`, orderValue: i * 1000000, requests: i })),
  pipeline: STATUSES.map((status, i) => ({ status, count: i % 3 })),
  stageDurations: [
    { status: 'PENDING_MARKETING_REVIEW', avgHours: 30, samples: 4, slowest: false },
    { status: 'PAYMENT_PENDING', avgHours: 150.5, samples: 2, slowest: true },
  ],
  topDealers: [
    { dealerId: 10, dealerName: 'Chennai North Dealership', regionName: 'Chennai Region', orderValue: 60000000, requests: 2 },
    { dealerId: 11, dealerName: '=HYPERLINK("x")', regionName: 'Mumbai Region', orderValue: 10800000, requests: 1 },
  ],
  demandByRegion: [{ id: 1, name: 'Chennai Region', units: 50, orderValue: 60000000, requests: 2 }],
  demandByModel: [{ id: 2, name: 'Renault Duster', units: 59, orderValue: 70800000, requests: 3 }],
  stockVsProduction: { fromStock: 30, toProduce: 29, byModel: [{ modelId: 2, modelName: 'Renault Duster', fromStock: 30, toProduce: 29 }] },
}

const EMPTY: ExecutiveAnalytics = {
  ...ANALYTICS,
  kpis: {
    totalOrderValue: { value: 0, previous: 0, changePct: null },
    requestCount: { value: 0, previous: 0, changePct: null },
    avgHoursToClearance: { value: null, previous: null, changePct: null },
    slaCompliancePct: { value: null, previous: null, changePct: null },
    overdueRequests: { value: 0, previous: 0, changePct: null },
  },
  monthlyOrderValue: ANALYTICS.monthlyOrderValue.map((m) => ({ ...m, orderValue: 0, requests: 0 })),
  pipeline: STATUSES.map((status) => ({ status, count: 0 })),
  stageDurations: [],
  topDealers: [],
  demandByRegion: [],
  demandByModel: [],
  stockVsProduction: { fromStock: 0, toProduce: 0, byModel: [] },
}

function open(path: string, role: Role = 'MARKETING_HEAD', body: ExecutiveAnalytics = ANALYTICS) {
  const server = fakeServer({ ...fleetHandlers(userFor(role)), [KEY]: () => [200, body] })
  const app = renderApp(path)
  return { server, ...app, user: userEvent.setup() }
}

const lastParams = (server: ReturnType<typeof fakeServer>) => server.calls.filter((c) => `${c.method} ${c.url}` === KEY).at(-1)?.params

describe('date ranges', () => {
  it('rolls presets back from today, inclusive', () => {
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28')
    expect(presetDates('30d', '2026-10-11')).toEqual({ from: '2026-09-12', to: '2026-10-11' })
    expect(presetDates('quarter', '2026-10-11')).toEqual({ from: '2026-07-14', to: '2026-10-11' })
    expect(presetDates('year', '2026-10-11')).toEqual({ from: '2025-10-12', to: '2026-10-11' })
  })
})

describe('executive dashboard', () => {
  // The route is lazy-loaded; load the chunk once up front so no test pays for it inside a findBy timeout.
  beforeAll(async () => {
    await import('./ExecutiveDashboardPage')
  }, 30_000) // first transform of Recharts can be slow on a busy machine
  let click: ReturnType<typeof vi.spyOn>
  beforeEach(() => {
    URL.createObjectURL = vi.fn(() => 'blob:csv')
    URL.revokeObjectURL = vi.fn()
    click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
  })
  afterEach(() => click.mockRestore())

  it('shows the server KPIs with their change against the previous period', async () => {
    const { server } = open('/executive-dashboard')
    const kpis = await screen.findByRole('region', { name: 'Key figures' })
    await waitFor(() => expect(within(kpis).getByText('₹7.08 Cr')).toBeInTheDocument())
    expect(within(kpis).getByText('+100.0%')).toBeInTheDocument()
    expect(within(kpis).getByText('-20.0%')).toBeInTheDocument()
    expect(within(kpis).getByText('5.0 days')).toBeInTheDocument()
    expect(within(kpis).getByText('82.4%')).toBeInTheDocument()
    expect(within(kpis).getByText('No earlier data')).toBeInTheDocument()
    expect(within(kpis).getByText('Previously 0')).toBeInTheDocument()
    // Longer clearance time is worse, even though the number went up.
    expect(within(kpis).getByText('+20.5%').parentElement).toHaveTextContent('(worse)')
    expect(lastParams(server)).toEqual(presetDates('30d'))
    expect(screen.getByText('Payment pending', { selector: 'strong' }).parentElement).toHaveTextContent('Slowest: Payment pending at 6.3 days')
  })

  it('keeps filters in the URL and sends them to the server', async () => {
    const { server, router, user } = open('/executive-dashboard')
    await screen.findByText('₹7.08 Cr')
    await user.click(screen.getByRole('button', { name: 'Quarter' }))
    await waitFor(() => expect(router.state.location.search).toBe('?range=quarter'))
    await waitFor(() => expect(lastParams(server)).toEqual(presetDates('quarter')))

    await user.selectOptions(await screen.findByLabelText('Region'), '1')
    await waitFor(() => expect(lastParams(server)).toEqual({ ...presetDates('quarter'), region: '1' }))
    expect(router.state.location.search).toContain('region=1')
  })

  it('opens a shared custom view exactly, and refuses an inverted range', async () => {
    const { server, user } = open('/executive-dashboard?range=custom&from=2025-06-01&to=2025-06-30&plant=1')
    await screen.findByText('₹7.08 Cr')
    expect(lastParams(server)).toEqual({ from: '2025-06-01', to: '2025-06-30', plant: '1' })
    expect(screen.getByRole('button', { name: 'Custom' })).toHaveAttribute('aria-pressed', 'true')

    const calls = server.count(KEY)
    const from = screen.getByLabelText('From')
    await user.clear(from)
    await user.type(from, '2025-07-15')
    expect(await screen.findByRole('alert')).toHaveTextContent('start date must be on or before')
    expect(server.count(KEY)).toBe(calls)
  })

  it('shows each widget as a table and exports it as safe CSV', async () => {
    const { user } = open('/executive-dashboard')
    await screen.findByText('₹7.08 Cr')
    await user.click(screen.getByRole('button', { name: 'Show Top 10 dealers as a table' }))
    const table = await screen.findByRole('table', { name: 'Top 10 dealers' })
    expect(within(table).getByText('Chennai North Dealership')).toBeInTheDocument()
    expect(within(table).getByText('₹6,00,00,000.00')).toBeInTheDocument()

    // Demand starts as a table and switches between region and model.
    expect(screen.getByRole('table', { name: 'Demand by region' })).toHaveTextContent('Chennai Region')
    await user.click(screen.getByRole('button', { name: 'model' }))
    expect(await screen.findByRole('table', { name: 'Demand by model' })).toHaveTextContent('Renault Duster')

    await user.click(screen.getByRole('button', { name: 'Export Top 10 dealers as CSV' }))
    const blob = vi.mocked(URL.createObjectURL).mock.calls[0][0] as Blob
    const csv = await blob.text()
    expect(csv).toContain('Dealer,Region,Order value (INR),Requests')
    expect(csv).toContain('Chennai North Dealership,Chennai Region,60000000.00,2')
    expect(csv).toContain(`"'=HYPERLINK(""x"")",Mumbai Region`)
    expect(click).toHaveBeenCalled()
  })

  it('says when a period has no data', async () => {
    open('/executive-dashboard', 'SUPERVISOR', EMPTY)
    await screen.findByRole('region', { name: 'Key figures' })
    expect(await screen.findAllByText('No data for this period')).toHaveLength(6)
    expect(screen.getByRole('button', { name: 'Export Top 10 dealers as CSV' })).toBeDisabled()
  })

  it('shows an error state with retry when the server fails', async () => {
    fakeServer({ ...fleetHandlers(userFor('MARKETING_HEAD')), [KEY]: () => [500, { title: 'Internal Server Error', status: 500, detail: 'boom' }] })
    renderApp('/executive-dashboard')
    // 5xx is retried once (about a second) before the error state shows.
    expect(await screen.findByText("Couldn't load the dashboard", undefined, { timeout: 4000 })).toBeInTheDocument()
  })

  it('is not available to Finance', async () => {
    const { router } = open('/executive-dashboard', 'FINANCE_TEAM')
    await waitFor(() => expect(router.state.location.pathname).toBe('/forbidden'))
  })
})

// Guard against the preset helper drifting from the IST date the page uses.
it('uses the IST calendar date for presets', () => {
  expect(presetDates('30d').to).toBe(todayIST())
})
