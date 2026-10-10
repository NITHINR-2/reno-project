import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ConfirmDialog } from './ConfirmDialog'

describe('ConfirmDialog', () => {
  it('requires a reason before confirming, and Escape cancels', async () => {
    const onConfirm = vi.fn()
    const onCancel = vi.fn()
    const user = userEvent.setup()
    render(<ConfirmDialog open title="Cancel request?" requireReason tone="danger" confirmLabel="Cancel request" onConfirm={onConfirm} onCancel={onCancel} />)

    const dialog = screen.getByRole('alertdialog', { name: 'Cancel request?' })
    expect(dialog).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Cancel request' }))
    expect(screen.getByText('A reason is required.')).toBeInTheDocument()
    expect(onConfirm).not.toHaveBeenCalled()

    await user.type(screen.getByLabelText(/reason/i), '  Dealer withdrew  ')
    await user.click(screen.getByRole('button', { name: 'Cancel request' }))
    expect(onConfirm).toHaveBeenCalledWith('Dealer withdrew')

    await user.keyboard('{Escape}')
    expect(onCancel).toHaveBeenCalledOnce()
  })
})
