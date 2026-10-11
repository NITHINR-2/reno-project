import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { fakeServer, sessionHandlers } from '@/test/fakeServer'
import { renderApp } from '@/test/renderApp'

const TOKEN = '3vBM5ZxWC9PD_QIBB3RVbBQ77aEZXvb3C3c23ZIi_nM'
const URL_KEY = `GET /public/quotations/verify/${TOKEN}`

describe('public quotation verification', () => {
  it('shows Genuine with only the number, issue date, total and status, without signing in', async () => {
    const server = fakeServer({
      ...sessionHandlers(null),
      [URL_KEY]: () => [
        200,
        { result: 'GENUINE', quotationNumber: 'QT-2026-00009', issueDate: '2026-10-11', totalAmount: 70800000, currency: 'INR', status: 'ISSUED' },
      ],
    })
    const { router } = renderApp(`/verify/quotation/${TOKEN}`)

    expect(await screen.findByText('Genuine')).toBeInTheDocument()
    expect(screen.getByText('QT-2026-00009')).toBeInTheDocument()
    expect(screen.getByText('₹7,08,00,000.00')).toBeInTheDocument()
    expect(screen.getByText('Issued')).toBeInTheDocument()
    expect(screen.queryByText(/deposit/i)).not.toBeInTheDocument()
    expect(router.state.location.pathname).toBe(`/verify/quotation/${TOKEN}`)
    expect(server.count(URL_KEY)).toBe(1)
  })

  it('shows Not found for an unknown code', async () => {
    fakeServer({ ...sessionHandlers(null), [URL_KEY]: () => [404, { title: 'Not Found', status: 404, detail: 'No quotation matches this verification code.' }] })
    renderApp(`/verify/quotation/${TOKEN}`)
    expect(await screen.findByText('Not found')).toBeInTheDocument()
    expect(screen.queryByText('Genuine')).not.toBeInTheDocument()
  })

  it('asks the visitor to wait when rate-limited', async () => {
    fakeServer({ ...sessionHandlers(null), [URL_KEY]: () => [429, { title: 'Too Many Requests', status: 429, detail: 'Too many verification requests.' }] })
    renderApp(`/verify/quotation/${TOKEN}`)
    expect(await screen.findByText('Too many checks')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
  })
})
