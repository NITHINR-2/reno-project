import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'
import { fakeServer, sessionHandlers, userFor } from '@/test/fakeServer'
import { page } from '@/test/fleetFixtures'
import { renderApp } from '@/test/renderApp'
import { todayIST } from '@/lib/format'
import { validateCart } from './cart/cartRules'
import { MAX_PROOF_BYTES, proofFileError } from './orders/proofRules'

type Handlers = Parameters<typeof fakeServer>[0]

const DUSTER = {
  id: 1,
  code: 'DUSTER',
  name: 'Renault Duster',
  description: 'Rugged five-seat SUV.',
  bodyType: 'SUV',
  seatingCapacity: 5,
  imageUrl: null,
  variants: [
    { id: 11, code: 'DUSTER-RXZ-P', name: 'Duster RXZ Petrol', fuelType: 'PETROL', transmission: 'MANUAL', unitPrice: 1200000 },
    { id: 12, code: 'DUSTER-RXZ-AT', name: 'Duster RXZ Automatic', fuelType: 'PETROL', transmission: 'AUTOMATIC', unitPrice: 1400000 },
  ],
  colours: [
    { id: 21, name: 'Pearl White', hexCode: '#F2F2EE' },
    { id: 22, name: 'Moonlight Silver', hexCode: '#B9BCC0' },
  ],
}

const line = (extra = {}) => ({
  id: 501,
  requestNumber: 'FLT-2026-1001',
  orderId: 99,
  orderNumber: 'DO-2026-1000',
  lineNo: 1,
  modelName: 'Renault Duster',
  variantCode: 'DUSTER-RXZ-P',
  variantName: 'Duster RXZ Petrol',
  colour: 'Pearl White',
  quantity: 5,
  status: 'IN_REVIEW',
  requestedDeliveryDate: '2099-01-15',
  createdAt: '2026-10-11T05:00:00Z',
  canCancel: true,
  canUploadPaymentProof: false,
  version: 0,
  ...extra,
})
const order = (lines: unknown[]) => ({ id: 99, orderNumber: 'DO-2026-1000', status: 'IN_REVIEW', requestedDeliveryDate: '2099-01-15', notes: null, createdAt: '2026-10-11T05:00:00Z', lines })

function dealerServer(extra: Handlers = {}) {
  return fakeServer({
    ...sessionHandlers(userFor('DEALER')),
    'GET /dealer/catalogue': () => [200, page([DUSTER])],
    'GET /dealer/orders/99': () => [200, order([line()])],
    'GET /dealer/requests/501': () => [200, { request: line(), quotation: null, receipts: [] }],
    ...extra,
  })
}

const posts = (server: ReturnType<typeof fakeServer>, url: string) => server.calls.filter((c) => c.method === 'POST' && c.url === url)

beforeEach(() => sessionStorage.clear())

describe('cart rules', () => {
  const cart = (quantity: number, requestedDeliveryDate = '') => ({ lines: [{ key: '11:21', quantity }], requestedDeliveryDate, notes: '' })

  it.each([
    [0, 'At least 1 unit.'],
    [10_001, 'At most 10,000 units.'],
    [2.5, 'Whole units only.'],
    [Number.NaN, 'Enter a quantity.'],
  ])('rejects quantity %s', (quantity, message) => {
    expect(validateCart(cart(quantity) as never)).toEqual({ 'lines.11:21': message })
  })

  it('rejects today or a past date, accepts a future one or none', () => {
    expect(validateCart(cart(1, todayIST()) as never)).toEqual({ requestedDeliveryDate: 'Choose a date after today.' })
    expect(validateCart(cart(1, '2020-01-01') as never)).toEqual({ requestedDeliveryDate: 'Choose a date after today.' })
    expect(validateCart(cart(1, '2099-12-31') as never)).toEqual({})
    expect(validateCart(cart(1) as never)).toEqual({})
  })

  it('rejects an empty cart', () => {
    expect(validateCart({ lines: [], requestedDeliveryDate: '', notes: '' })).toEqual({ form: 'Your cart is empty.' })
  })
})

describe('Catalogue and cart', () => {
  it('adds the chosen variant and colour, validates the cart, submits once and clears it', async () => {
    const server = dealerServer({
      'POST /dealer/orders': () => [201, order([line({ quantity: 5 })])],
    })
    const u = userEvent.setup()
    const { router } = renderApp('/dealer/catalogue')
    const card = (await screen.findByRole('heading', { name: 'Renault Duster' })).closest('article') as HTMLElement

    await u.click(within(card).getByRole('radio', { name: 'Moonlight Silver' }))
    await u.click(within(card).getByRole('radio', { name: /Duster RXZ Automatic/ }))
    const qty = within(card).getByLabelText('Quantity')
    await u.clear(qty)
    await u.type(qty, '3')
    await u.click(within(card).getByRole('button', { name: 'Add Renault Duster to cart' }))
    await u.click(screen.getByRole('button', { name: 'Open cart, 3 units' }))

    const drawer = await screen.findByRole('dialog', { name: 'Your cart' })
    const lineQty = within(drawer).getByLabelText('Quantity for Duster RXZ Automatic in Moonlight Silver')
    await u.clear(lineQty)
    await u.type(lineQty, '0')
    fireEvent.change(within(drawer).getByLabelText('Requested delivery date'), { target: { value: todayIST() } })
    await u.click(within(drawer).getByRole('button', { name: 'Place order' }))
    expect(await within(drawer).findByText('At least 1 unit.')).toBeInTheDocument()
    expect(within(drawer).getByText('Choose a date after today.')).toBeInTheDocument()
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()

    await u.clear(lineQty)
    await u.type(lineQty, '5')
    fireEvent.change(within(drawer).getByLabelText('Requested delivery date'), { target: { value: '2099-01-15' } })
    await u.click(within(drawer).getByRole('button', { name: 'Place order' }))
    const confirm = await screen.findByRole('alertdialog', { name: 'Place this order?' })
    await u.click(within(confirm).getByRole('button', { name: 'Place order' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/dealer/orders/99'))
    expect(posts(server, '/dealer/orders')).toHaveLength(1)
    expect(posts(server, '/dealer/orders')[0].body).toEqual({
      lines: [{ variantId: 12, quantity: 5, colourId: 22 }],
      requestedDeliveryDate: '2099-01-15',
    })
    expect(JSON.parse(sessionStorage.getItem('fleet-cart:1') ?? '{}').lines).toEqual([])
    expect(await screen.findByRole('heading', { name: 'Order DO-2026-1000', level: 1 })).toBeInTheDocument()
  })

  it('keeps the cart when the order is rejected', async () => {
    const server = dealerServer({ 'POST /dealer/orders': () => [409, { title: 'Conflict', status: 409, detail: 'Vehicle variant DUSTER-RXZ-P is not active.' }] })
    const u = userEvent.setup()
    renderApp('/dealer/catalogue')
    await u.click(await screen.findByRole('button', { name: 'Add Renault Duster to cart' }))
    await u.click(screen.getByRole('button', { name: 'Open cart, 1 units' }))
    await u.click(within(await screen.findByRole('dialog', { name: 'Your cart' })).getByRole('button', { name: 'Place order' }))
    await u.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Place order' }))
    expect(await screen.findByText(/is not active/)).toBeInTheDocument()
    expect(posts(server, '/dealer/orders')).toHaveLength(1)
    expect(JSON.parse(sessionStorage.getItem('fleet-cart:1') ?? '{}').lines).toHaveLength(1)
  })
})

describe('payment proof', () => {
  const file = (name: string, type: string, size = 10) => new File([new Uint8Array(size)], name, { type })

  it.each([
    [file('slip.pdf', 'application/pdf'), undefined],
    [file('slip.PNG', 'image/png'), undefined],
    [file('photo.jpeg', 'image/jpeg'), undefined],
    [file('photo.jpg', ''), undefined],
    [file('notes.txt', 'text/plain'), 'Upload a PDF, PNG or JPEG file.'],
    [file('fake.pdf', 'text/plain'), 'Upload a PDF, PNG or JPEG file.'],
    [file('slip.png', 'application/pdf'), 'Upload a PDF, PNG or JPEG file.'],
    [file('empty.pdf', 'application/pdf', 0), 'The file is empty.'],
    [file('big.pdf', 'application/pdf', MAX_PROOF_BYTES + 1), 'The file is larger than 5 MB.'],
    [null, 'Attach the payment proof.'],
  ])('%o → %s', (f, expected) => {
    expect(proofFileError(f)).toBe(expected)
  })

  it('blocks a bad file without calling the API, then uploads a valid one', async () => {
    const awaiting = line({ status: 'AWAITING_PAYMENT', canCancel: false, canUploadPaymentProof: true })
    const server = dealerServer({
      'GET /dealer/orders/99': () => [200, order([awaiting])],
      'GET /dealer/requests/501': () => [200, { request: awaiting, quotation: null, receipts: [] }],
      'POST /dealer/requests/501/payment-proofs': () => [201, { id: 7, receiptNumber: 'RCT-2026-00007', receiptType: 'DEPOSIT', paymentMethod: 'NEFT', transactionReference: 'UTR123', amount: 1000000, receivedDate: '2026-10-10', status: 'RECORDED', hasProof: true, createdAt: '2026-10-11T05:00:00Z' }],
    })
    const u = userEvent.setup({ applyAccept: false })
    renderApp('/dealer/orders/99')
    const form = await screen.findByRole('form', { name: 'Payment proof for FLT-2026-1001' })
    await u.type(within(form).getByLabelText(/Transaction reference/), 'UTR123')
    await u.type(within(form).getByLabelText(/Amount/), '1000000')
    const input = within(form).getByLabelText(/Proof file/)

    await u.upload(input, file('notes.txt', 'text/plain'))
    expect(within(form).getByText('Upload a PDF, PNG or JPEG file.')).toBeInTheDocument()
    await u.click(within(form).getByRole('button', { name: 'Upload proof' }))
    await u.upload(input, file('big.pdf', 'application/pdf', MAX_PROOF_BYTES + 1))
    expect(within(form).getByText('The file is larger than 5 MB.')).toBeInTheDocument()
    await u.click(within(form).getByRole('button', { name: 'Upload proof' }))
    expect(posts(server, '/dealer/requests/501/payment-proofs')).toHaveLength(0)

    await u.upload(input, file('slip.pdf', 'application/pdf'))
    await u.click(within(form).getByRole('button', { name: 'Upload proof' }))
    await waitFor(() => expect(posts(server, '/dealer/requests/501/payment-proofs')).toHaveLength(1))
    const body = posts(server, '/dealer/requests/501/payment-proofs')[0].body as FormData
    expect((body.get('file') as File).name).toBe('slip.pdf')
    expect(JSON.parse(await (body.get('receipt') as Blob).text())).toMatchObject({ transactionReference: 'UTR123', amount: 1000000, receiptType: 'DEPOSIT' })
  })
})

describe('order detail', () => {
  it('shows the status timeline and cancels a line in review with a reason', async () => {
    const server = dealerServer({
      'POST /dealer/requests/501/cancel': () => [200, { request: line({ status: 'CANCELLED', canCancel: false }), quotation: null, receipts: [] }],
    })
    const u = userEvent.setup()
    renderApp('/dealer/orders/99')
    const timeline = await screen.findByRole('list', { name: 'Progress of FLT-2026-1001' })
    expect(within(timeline).getByText('In review').closest('li')).toHaveAttribute('aria-current', 'step')
    await u.click(await screen.findByRole('button', { name: 'Cancel this line' }))
    const dialog = await screen.findByRole('alertdialog')
    await u.type(within(dialog).getByLabelText(/Reason/), 'Budget moved')
    await u.click(within(dialog).getByRole('button', { name: 'Cancel line' }))
    await waitFor(() => expect(posts(server, '/dealer/requests/501/cancel')).toHaveLength(1))
    expect(posts(server, '/dealer/requests/501/cancel')[0].body).toEqual({ reason: 'Budget moved', version: 0 })
  })
})
