import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { fakeServer } from '@/test/fakeServer'
import { renderApp } from '@/test/renderApp'

const CHECK = 'POST /public/set-password/check'
const SUBMIT = 'POST /public/set-password'

describe('Set password page', () => {
  it('reads the token from the fragment, removes it from the URL, and sets the password', async () => {
    const server = fakeServer({
      [CHECK]: () => [200, { username: 'dealer.chn-north', expiresAt: '2026-10-14T05:00:00Z' }],
      [SUBMIT]: () => [204],
    })
    const u = userEvent.setup()
    const { router } = renderApp('/set-password#token=abc123')
    expect(await screen.findByText('dealer.chn-north')).toBeInTheDocument()
    expect(router.state.location.hash).toBe('')
    expect(server.calls.find((c) => c.url === '/public/set-password/check')?.body).toEqual({ token: 'abc123' })

    await u.type(screen.getByLabelText(/^New password/), 'Dealer-Portal-2026!')
    await u.type(screen.getByLabelText(/^Confirm password/), 'Something-else-2026')
    await u.click(screen.getByRole('button', { name: 'Set password' }))
    expect(await screen.findByText("The passwords don't match.")).toBeInTheDocument()
    expect(server.count(SUBMIT)).toBe(0)

    await u.clear(screen.getByLabelText(/^Confirm password/))
    await u.type(screen.getByLabelText(/^Confirm password/), 'Dealer-Portal-2026!')
    await u.click(screen.getByRole('button', { name: 'Set password' }))
    expect(await screen.findByText('Password set')).toBeInTheDocument()
    expect(server.calls.find((c) => c.url === '/public/set-password')?.body).toEqual({ token: 'abc123', password: 'Dealer-Portal-2026!' })
    expect(screen.getByRole('link', { name: 'sign in' })).toHaveAttribute('href', '/login')
  })

  it('rejects a password containing the username', async () => {
    const server = fakeServer({ [CHECK]: () => [200, { username: 'dealer.chn-north', expiresAt: '2026-10-14T05:00:00Z' }] })
    const u = userEvent.setup()
    renderApp('/set-password#token=abc123')
    await screen.findByText('dealer.chn-north')
    await u.type(screen.getByLabelText(/^New password/), 'my-dealer.chn-north-pass')
    await u.type(screen.getByLabelText(/^Confirm password/), 'my-dealer.chn-north-pass')
    await u.click(screen.getByRole('button', { name: 'Set password' }))
    expect(await screen.findByText("Mustn't contain your username.")).toBeInTheDocument()
    expect(server.count(SUBMIT)).toBe(0)
  })

  it.each([
    [410, 'This set-password link has already been used.', 'This link can no longer be used'],
    [404, 'This set-password link is not valid.', 'This link is not valid'],
  ])('a %s from the check shows "%s"', async (status, detail, title) => {
    fakeServer({ [CHECK]: () => [status, { title: 'x', status, detail }] })
    renderApp('/set-password#token=used')
    expect(await screen.findByText(title)).toBeInTheDocument()
    expect(screen.getByText(new RegExp(detail))).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Set password' })).not.toBeInTheDocument()
  })

  it('without a token it asks for the full link and calls nothing', async () => {
    const server = fakeServer({})
    renderApp('/set-password')
    expect(await screen.findByText('This link is incomplete')).toBeInTheDocument()
    await waitFor(() => expect(server.calls).toHaveLength(0))
  })
})
