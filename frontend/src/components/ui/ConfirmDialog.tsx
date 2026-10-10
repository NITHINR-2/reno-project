import { useId, useState, type ReactNode } from 'react'
import { Button } from './Button'
import { Dialog } from './Dialog'

type ConfirmDialogProps = {
  open: boolean
  title: string
  description?: ReactNode
  confirmLabel?: string
  tone?: 'primary' | 'danger'
  /** Ask for a reason (rejections, cancellations, Supervisor overrides). The server still enforces it. */
  requireReason?: boolean
  loading?: boolean
  onConfirm: (reason: string) => void
  onCancel: () => void
}

export function ConfirmDialog({ open, onCancel, ...rest }: ConfirmDialogProps) {
  // The dialog panel unmounts after closing, so the reason resets each time it opens.
  return (
    <Dialog open={open} onClose={rest.loading ? () => undefined : onCancel} title={rest.title} description={rest.description} role="alertdialog">
      <ConfirmBody onCancel={onCancel} {...rest} />
    </Dialog>
  )
}

function ConfirmBody({
  confirmLabel = 'Confirm',
  tone = 'primary',
  requireReason = false,
  loading = false,
  onConfirm,
  onCancel,
}: Omit<ConfirmDialogProps, 'open' | 'title' | 'description'>) {
  const [reason, setReason] = useState('')
  const [touched, setTouched] = useState(false)
  const reasonId = useId()
  const missing = requireReason && reason.trim().length === 0

  return (
    <form
      noValidate
      onSubmit={(event) => {
        event.preventDefault()
        setTouched(true)
        if (!missing) onConfirm(reason.trim())
      }}
    >
      {requireReason && (
        <div>
          <label htmlFor={reasonId} className="text-sm font-medium text-fg">
            Reason <span className="text-red-600">*</span>
          </label>
          <textarea
            id={reasonId}
            data-autofocus
            rows={3}
            value={reason}
            maxLength={1000}
            onChange={(event) => setReason(event.target.value)}
            aria-invalid={touched && missing}
            aria-describedby={touched && missing ? `${reasonId}-error` : undefined}
            className="mt-1 w-full rounded-lg border border-line bg-surface px-3 py-2 text-sm text-fg focus-visible:outline-2 focus-visible:outline-accent aria-invalid:border-red-500"
          />
          {touched && missing && (
            <p id={`${reasonId}-error`} className="mt-1 text-sm text-red-600">
              A reason is required.
            </p>
          )}
        </div>
      )}
      <div className="mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        <Button variant="secondary" onClick={onCancel} disabled={loading}>
          Cancel
        </Button>
        <Button type="submit" variant={tone} loading={loading}>
          {confirmLabel}
        </Button>
      </div>
    </form>
  )
}
