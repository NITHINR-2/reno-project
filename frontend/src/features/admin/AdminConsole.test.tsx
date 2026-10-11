import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Role } from '@/features/auth/api'
import { fakeServer, sessionHandlers, userFor } from '@/test/fakeServer'
import { page } from '@/test/fleetFixtures'
import { renderApp } from '@/test/renderApp'

const user = (id: number, username: string, role: Role, active = true) => ({
  id,
  username,
  email: `${username}@fleet.local`,
  fullName: `User ${username}`,
  role,
  active,
  lastLoginAt: null,
  version: 2,
  createdAt: '2026-10-01T05:00:00Z',
})
const USERS = [user(1, 'admin', 'ADMIN'), user(2, 'boss', 'SUPERVISOR'), user(3, 'meera', 'MARKETING_MANAGER')]
const DEALER = {
  id: 10, dealerCode: 'CHN-N', name: 'Chennai North Dealership', regionId: 1, regionName: 'Chennai Region', gstin: null, contactName: null,
  contactEmail: null, contactPhone: null, addressLine: null, city: 'Chennai', state: 'Tamil Nadu', postalCode: '600001',
  status: 'PENDING_ONBOARDING', onboardedAt: null, version: 1,
}
const AUDIT = {
  id: 501, actorUsername: 'boss', actorRole: 'SUPERVISOR', action: 'OVERRIDE', entityType: 'FLEET_REQUEST', entityId: '1',
  before: { requiredDeposit: 5000000 }, after: { requiredDeposit: 4000000 }, reason: 'Board approval', ipAddress: '10.0.0.5', createdAt: '2026-10-10T05:00:00Z',
}

type Handlers = Parameters<typeof fakeServer>[0]

function adminServer(role: Role, extra: Handlers = {}) {
  const me = { ...userFor(role), id: 1 }
  return fakeServer({
    ...sessionHandlers(me),
    'GET /admin/users': () => [200, page(USERS)],
    'GET /admin/roles': () => [200, page([{ code: 'ADMIN', name: 'Administrator', description: 'Admin Console only.', permissions: [{ code: 'ADMIN_USERS_MANAGE', description: 'Manage users' }, { code: 'AUDIT_LOG_READ', description: 'View audit logs' }] }])],
    'GET /dealers': () => [200, page([DEALER])],
    'GET /admin/audit-logs': () => [200, page([AUDIT])],
    'GET /regions': () => [200, page([{ id: 1, name: 'Chennai Region' }])],
    'GET /admin/sla-policies': () => [200, page([
      { id: 1, stage: 'PENDING_MARKETING_REVIEW', hoursAllowed: 48, warningThresholdPct: 75, active: true, version: 0, updatedAt: null },
      { id: 5, stage: 'PAYMENT_PENDING', hoursAllowed: 168, warningThresholdPct: 75, active: true, version: 2, updatedAt: null },
    ])],
    ...extra,
  })
}

const posts = (server: ReturnType<typeof fakeServer>, url: string) => server.calls.filter((c) => c.method === 'POST' && c.url === url)
const toast = (text: string) => within(screen.getByRole('region', { name: 'Notifications' })).findByText(text)

async function openTab(name: string, u: ReturnType<typeof userEvent.setup>) {
  await u.click(await screen.findByRole('tab', { name }))
  return screen.getByRole('tabpanel')
}

describe('Admin sees only the Admin Console', () => {
  it('lands on the Admin Console with every admin tab and no fleet modules', async () => {
    adminServer('ADMIN')
    const { router } = renderApp('/')
    expect(await screen.findByRole('heading', { name: 'Admin Console', level: 1 })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/admin')
    const nav = screen.getAllByRole('navigation', { name: 'Main' })[0]
    expect(within(nav).getAllByRole('link').map((l) => l.textContent)).toEqual(['Admin Console'])
    expect(screen.getAllByRole('tab').map((t) => t.textContent)).toEqual(['Users', 'Roles', 'Dealers', 'Audit log'])
    const table = await screen.findByRole('table', { name: 'Users' })
    expect(within(table).getAllByRole('row')).toHaveLength(4)
  })

  it.each(['/fleet-evaluation', '/finance', '/operations', '/executive-dashboard'])('is redirected to 403 from %s', async (path) => {
    adminServer('ADMIN')
    const { router } = renderApp(path)
    expect(await screen.findByText(/403 — You don't have access/)).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/forbidden')
  })

  it('a Marketing Manager cannot open the Admin Console', async () => {
    adminServer('MARKETING_MANAGER')
    renderApp('/admin')
    expect(await screen.findByText(/403 — You don't have access/)).toBeInTheDocument()
  })
})

describe('users', () => {
  it('validates a new user, never offers SUPERVISOR to an Admin, then creates it', async () => {
    const server = adminServer('ADMIN', { 'POST /admin/users': () => [201, user(9, 'ravi.f', 'FINANCE_TEAM')] })
    const u = userEvent.setup()
    renderApp('/admin')
    await u.click(await screen.findByRole('button', { name: 'New user' }))
    const form = await screen.findByRole('form', { name: 'New user' })

    const roles = within(within(form).getByLabelText(/Role/)).getAllByRole('option').map((o) => o.textContent)
    expect(roles).not.toContain('Supervisor')

    await u.type(within(form).getByLabelText(/Username/), 'Ravi F')
    await u.type(within(form).getByLabelText(/Initial password/), 'short')
    await u.click(within(form).getByRole('button', { name: 'Create user' }))
    expect(await within(form).findByText(/lower-case letters, digits/)).toBeInTheDocument()
    expect(within(form).getByText('At least 12 characters.')).toBeInTheDocument()
    expect(within(form).getByText('Enter a valid email.')).toBeInTheDocument()

    await u.clear(within(form).getByLabelText(/Username/))
    await u.type(within(form).getByLabelText(/Username/), 'ravi.f')
    await u.type(within(form).getByLabelText(/Full name/), 'Ravi Finance')
    await u.type(within(form).getByLabelText(/Email/), 'ravi@fleet.local')
    await u.selectOptions(within(form).getByLabelText(/Role/), 'FINANCE_TEAM')
    await u.clear(within(form).getByLabelText(/Initial password/))
    await u.type(within(form).getByLabelText(/Initial password/), 'ravi.f-secret-2026')
    await u.click(within(form).getByRole('button', { name: 'Create user' }))
    expect(await within(form).findByText("Mustn't contain the username.")).toBeInTheDocument()

    await u.clear(within(form).getByLabelText(/Initial password/))
    await u.type(within(form).getByLabelText(/Initial password/), 'Correct-Horse-Battery-9')
    await u.click(within(form).getByRole('button', { name: 'Create user' }))
    await waitFor(() => expect(posts(server, '/admin/users')).toHaveLength(1))
    expect(posts(server, '/admin/users')[0].body).toEqual({
      username: 'ravi.f', email: 'ravi@fleet.local', fullName: 'Ravi Finance', role: 'FINANCE_TEAM', password: 'Correct-Horse-Battery-9',
    })
    expect(await toast('User created')).toBeInTheDocument()
  })

  it('locks Supervisor accounts and self-deactivation for an Admin; deactivation is confirmed', async () => {
    const server = adminServer('ADMIN', { 'POST /admin/users/3/deactivate': () => [200, user(3, 'meera', 'MARKETING_MANAGER', false)] })
    const u = userEvent.setup()
    renderApp('/admin')
    const table = await screen.findByRole('table', { name: 'Users' })

    await u.click(within(table).getByText('boss'))
    let drawer = await screen.findByRole('dialog')
    expect(within(drawer).getByRole('note')).toHaveTextContent('Only a Supervisor can change a Supervisor account.')
    expect(within(drawer).getByRole('button', { name: 'Deactivate' })).toBeDisabled()
    await u.click(within(drawer).getByRole('button', { name: 'Close' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    await u.click(within(table).getByText('admin'))
    drawer = await screen.findByRole('dialog')
    expect(within(drawer).getByRole('note')).toHaveTextContent("You can't change your own role or deactivate yourself.")
    expect(within(drawer).getByLabelText(/Role/)).toBeDisabled()
    await u.click(within(drawer).getByRole('button', { name: 'Close' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    await u.click(within(table).getByText('meera'))
    drawer = await screen.findByRole('dialog')
    await u.click(within(drawer).getByRole('button', { name: 'Deactivate' }))
    const confirm = await screen.findByRole('alertdialog', { name: 'Deactivate meera?' })
    expect(posts(server, '/admin/users/3/deactivate')).toHaveLength(0)
    await u.click(within(confirm).getByRole('button', { name: 'Deactivate' }))
    await waitFor(() => expect(posts(server, '/admin/users/3/deactivate')).toHaveLength(1))
    expect(await toast('User deactivated')).toBeInTheDocument()
  })

  it('a Supervisor may grant the Supervisor role', async () => {
    adminServer('SUPERVISOR')
    const u = userEvent.setup()
    renderApp('/admin')
    await u.click(await screen.findByRole('button', { name: 'New user' }))
    const form = await screen.findByRole('form', { name: 'New user' })
    expect(within(within(form).getByLabelText(/Role/)).getAllByRole('option').map((o) => o.textContent)).toContain('Supervisor')
  })
})

describe('roles, dealers and audit log', () => {
  it('lists roles with their permissions', async () => {
    adminServer('ADMIN')
    const u = userEvent.setup()
    renderApp('/admin')
    const panel = await openTab('Roles', u)
    const list = await within(panel).findByRole('list', { name: 'Administrator permissions' })
    expect(within(list).getAllByRole('listitem').map((i) => i.textContent)).toEqual(['ADMIN_USERS_MANAGE', 'AUDIT_LOG_READ'])
  })

  it('validates dealer onboarding and confirms a status change', async () => {
    const server = adminServer('ADMIN', {
      'POST /dealers': () => [201, { ...DEALER, id: 11, dealerCode: 'PUN-W', name: 'Pune West Motors' }],
      'PATCH /dealers/10': () => [200, { ...DEALER, status: 'ACTIVE', version: 2 }],
    })
    const u = userEvent.setup()
    renderApp('/admin?tab=dealers')
    await screen.findByRole('table', { name: 'Dealers' })

    await u.click(screen.getByRole('button', { name: 'Onboard dealer' }))
    const form = await screen.findByRole('form', { name: 'Onboard dealer' })
    await u.type(within(form).getByLabelText(/Dealer code/), 'pun w')
    await u.type(within(form).getByLabelText(/GSTIN/), '27ABCDE1234F1Z')
    await u.type(within(form).getByLabelText(/PIN code/), '012345')
    await u.click(within(form).getByRole('button', { name: 'Onboard dealer' }))
    expect(await within(form).findByText('Upper-case letters, digits and hyphens.')).toBeInTheDocument()
    expect(within(form).getByText('Enter a valid 15-character GSTIN.')).toBeInTheDocument()
    expect(within(form).getByText('Enter a 6-digit PIN code.')).toBeInTheDocument()
    expect(within(form).getByText('Choose a region.')).toBeInTheDocument()

    await u.clear(within(form).getByLabelText(/Dealer code/))
    await u.type(within(form).getByLabelText(/Dealer code/), 'PUN-W')
    await u.type(within(form).getByLabelText(/^Name/), 'Pune West Motors')
    await u.selectOptions(within(form).getByLabelText(/Region/), '1')
    await u.clear(within(form).getByLabelText(/GSTIN/))
    await u.type(within(form).getByLabelText(/GSTIN/), '27ABCDE1234F1Z5')
    await u.clear(within(form).getByLabelText(/PIN code/))
    await u.type(within(form).getByLabelText(/PIN code/), '411001')
    await u.type(within(form).getByLabelText(/City/), 'Pune')
    await u.type(within(form).getByLabelText(/State/), 'Maharashtra')
    await u.click(within(form).getByRole('button', { name: 'Onboard dealer' }))
    await waitFor(() => expect(posts(server, '/dealers')).toHaveLength(1))
    expect(posts(server, '/dealers')[0].body).toEqual({
      dealerCode: 'PUN-W', name: 'Pune West Motors', regionId: 1, gstin: '27ABCDE1234F1Z5', city: 'Pune', state: 'Maharashtra', postalCode: '411001',
    })
    expect(await toast('Dealer onboarded')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    await u.click(within(screen.getByRole('table', { name: 'Dealers' })).getByText('Chennai North Dealership'))
    const edit = await screen.findByRole('form', { name: 'Edit dealer' })
    await u.selectOptions(within(edit).getByLabelText(/Status/), 'ACTIVE')
    await u.click(within(edit).getByRole('button', { name: 'Save changes' }))
    const confirm = await screen.findByRole('alertdialog', { name: 'Change status to active?' })
    expect(server.calls.some((c) => c.method === 'PATCH')).toBe(false)
    await u.click(within(confirm).getByRole('button', { name: 'Change status' }))
    await waitFor(() => expect(server.calls.find((c) => c.method === 'PATCH')?.body).toMatchObject({ version: 1, status: 'ACTIVE', name: 'Chennai North Dealership' }))
  })

  it('filters the audit log and shows before/after of an entry', async () => {
    const server = adminServer('ADMIN')
    const u = userEvent.setup()
    renderApp('/admin?tab=audit')
    const table = await screen.findByRole('table', { name: 'Audit log' })
    expect(within(table).getByText('OVERRIDE')).toBeInTheDocument()

    await u.type(screen.getByLabelText('Action'), 'OVERRIDE')
    await u.type(screen.getByLabelText('Entity type'), 'FLEET_REQUEST')
    await waitFor(() =>
      expect(server.calls.filter((c) => c.url === '/admin/audit-logs').at(-1)?.params).toMatchObject({ action: 'OVERRIDE', entityType: 'FLEET_REQUEST', page: 0 }),
    )

    await u.click(within(table).getByText('Board approval'))
    const drawer = await screen.findByRole('dialog')
    expect(drawer).toHaveTextContent('10.0.0.5')
    expect(drawer).toHaveTextContent('"requiredDeposit": 5000000')
    expect(drawer).toHaveTextContent('"requiredDeposit": 4000000')
  })
})

describe('SLA policies', () => {
  it('only a Supervisor gets the SLA tab', async () => {
    adminServer('ADMIN')
    const { unmount } = renderApp('/admin?tab=sla')
    await screen.findByRole('table', { name: 'Users' })
    expect(screen.queryByRole('tab', { name: 'SLA' })).not.toBeInTheDocument()
    unmount()

    adminServer('SUPERVISOR')
    renderApp('/admin?tab=sla')
    const table = await screen.findByRole('table', { name: 'SLA policies' })
    const rows = within(table).getAllByRole('row')
    expect(rows[1]).toHaveTextContent('Pending marketing review48h75% (36h)Active')
    expect(rows[2]).toHaveTextContent('Payment pending168h75% (126h)Active')
  })

  it('validates and saves a policy with its version', async () => {
    const server = adminServer('SUPERVISOR', {
      'PATCH /admin/sla-policies/5': () => [200, { id: 5, stage: 'PAYMENT_PENDING', hoursAllowed: 120, warningThresholdPct: 80, active: true, version: 3, updatedAt: null }],
    })
    const u = userEvent.setup()
    renderApp('/admin?tab=sla')
    await u.click(within(await screen.findByRole('table', { name: 'SLA policies' })).getByText('Payment pending'))
    const form = await screen.findByRole('form', { name: 'Edit SLA policy' })

    const hours = within(form).getByLabelText(/Hours allowed/)
    const pct = within(form).getByLabelText(/At risk from/)
    await u.clear(hours)
    await u.type(hours, '0')
    await u.clear(pct)
    await u.type(pct, '100')
    await u.click(within(form).getByRole('button', { name: 'Save policy' }))
    expect(await within(form).findByText('Between 1 and 8,760 hours.')).toBeInTheDocument()
    expect(within(form).getByText('Between 1 and 99%.')).toBeInTheDocument()
    expect(server.calls.some((c) => c.method === 'PATCH')).toBe(false)

    await u.clear(hours)
    await u.type(hours, '120')
    await u.clear(pct)
    await u.type(pct, '80')
    expect(within(form).getByText('At risk after 96h, overdue after 120h.')).toBeInTheDocument()
    await u.click(within(form).getByRole('button', { name: 'Save policy' }))
    await waitFor(() =>
      expect(server.calls.find((c) => c.method === 'PATCH')?.body).toEqual({ version: 2, hoursAllowed: 120, warningThresholdPct: 80, active: true }),
    )
    expect(await toast('SLA policy updated')).toBeInTheDocument()
  })
})
