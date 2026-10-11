import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { fakeServer, sessionHandlers, userFor } from '@/test/fakeServer'
import { page } from '@/test/fleetFixtures'
import { renderApp } from '@/test/renderApp'

const DEALER = {
  id: 10, dealerCode: 'CHN-NORTH', name: 'Chennai North Dealership', regionId: 1, regionName: 'Chennai Region', gstin: null, contactName: null,
  contactEmail: null, contactPhone: null, addressLine: null, city: 'Chennai', state: 'Tamil Nadu', postalCode: null, status: 'ACTIVE', onboardedAt: null, version: 1,
}
const MODEL = {
  id: 1, code: 'DUSTER', name: 'Renault Duster', vehicleCategory: 'SUV', active: true, description: null, bodyType: 'SUV', seatingCapacity: 5,
  imageUrl: null, version: 3,
  variants: [{ id: 11, code: 'DUSTER-RXZ-P', name: 'Duster RXZ Petrol', fuelType: 'PETROL', transmission: 'MANUAL', unitPrice: 1200000, active: true, version: 0 }],
  colours: [{ id: 21, name: 'Pearl White', hexCode: '#F2F2EE', active: true, version: 0 }],
}
const LINK = { userId: 5, username: 'dealer.chn-north', url: 'http://portal.test/set-password#token=tok', expiresAt: '2026-10-14T05:00:00Z' }

function server(extra: Parameters<typeof fakeServer>[0] = {}) {
  return fakeServer({
    ...sessionHandlers(userFor('ADMIN')),
    'GET /admin/users': () => [200, page([])],
    'GET /dealers': () => [200, page([DEALER])],
    'GET /admin/audit-logs': () => [200, page([])],
    'GET /admin/catalogue/models': () => [200, page([MODEL])],
    'GET /admin/catalogue/models/1': () => [200, MODEL],
    ...extra,
  })
}

describe('Admin Console: dealer users', () => {
  it('creates a dealer user without a password and shows the one-time link', async () => {
    const s = server({
      'POST /admin/users': () => [201, { id: 5, username: 'dealer.chn-north', email: 'd@x.test', fullName: 'Chennai Desk', role: 'DEALER', active: true, lastLoginAt: null, version: 0, createdAt: '2026-10-11T05:00:00Z', dealerId: 10 }],
      'POST /admin/users/5/set-password-link': () => [200, LINK],
    })
    const u = userEvent.setup()
    renderApp('/admin')
    await u.click(await screen.findByRole('button', { name: 'New dealer user' }))
    const drawer = await screen.findByRole('dialog', { name: 'New dealer user' })
    await u.click(within(drawer).getByRole('button', { name: 'Create and get link' }))
    expect(await within(drawer).findByText('Choose the dealership.')).toBeInTheDocument()
    expect(s.count('POST /admin/users')).toBe(0)

    await waitFor(() => expect(within(drawer).getByRole('option', { name: 'Chennai North Dealership (CHN-NORTH)' })).toBeInTheDocument())
    await u.selectOptions(within(drawer).getByLabelText(/Dealership/), '10')
    await u.type(within(drawer).getByLabelText(/Username/), 'dealer.chn-north')
    await u.type(within(drawer).getByLabelText(/Full name/), 'Chennai Desk')
    await u.type(within(drawer).getByLabelText(/Email/), 'd@x.test')
    await u.click(within(drawer).getByRole('button', { name: 'Create and get link' }))

    expect(await within(drawer).findByDisplayValue(LINK.url)).toBeInTheDocument()
    expect(s.calls.find((c) => c.method === 'POST' && c.url === '/admin/users')?.body).toEqual({
      role: 'DEALER', dealerId: 10, username: 'dealer.chn-north', fullName: 'Chennai Desk', email: 'd@x.test',
    })
    expect(s.count('POST /admin/users/5/set-password-link')).toBe(1)
  })

  it('does not offer DEALER in the generic new-user role list', async () => {
    server()
    const u = userEvent.setup()
    renderApp('/admin')
    await u.click(await screen.findByRole('button', { name: 'New user' }))
    const drawer = await screen.findByRole('dialog', { name: 'New user' })
    expect(within(within(drawer).getByLabelText(/Role/)).queryByRole('option', { name: 'Dealer' })).not.toBeInTheDocument()
  })
})

describe('Admin Console: catalogue', () => {
  it('lists models and updates a variant price with its version', async () => {
    const s = server({ 'PATCH /admin/catalogue/variants/11': () => [200, { ...MODEL.variants[0], unitPrice: 1250000, version: 1 }] })
    const u = userEvent.setup()
    renderApp('/admin?tab=catalogue')
    const row = await screen.findAllByText('Renault Duster')
    await u.click(row[0])
    const drawer = await screen.findByRole('dialog', { name: 'Catalogue model' })
    const price = await within(drawer).findByLabelText(/List price/)
    await u.clear(price)
    await u.type(price, '1250000')
    await u.click(within(drawer).getByRole('button', { name: 'Save price' }))
    await waitFor(() => expect(s.count('PATCH /admin/catalogue/variants/11')).toBe(1))
    expect(s.calls.find((c) => c.method === 'PATCH')?.body).toEqual({ version: 0, unitPrice: 1250000 })
  })
})
