import { zodResolver } from '@hookform/resolvers/zod'
import { Upload } from 'lucide-react'
import { motion } from 'motion/react'
import { useId, useRef, useState } from 'react'
import { useForm } from 'react-hook-form'
import { inputClass } from '@/components/forms/Field'
import { Form } from '@/components/forms/Form'
import { SelectField } from '@/components/forms/SelectField'
import { TextField } from '@/components/forms/TextField'
import { Button } from '@/components/ui/Button'
import { useToast } from '@/components/ui/useToast'
import { statusLabel, todayIST } from '@/lib/format'
import { duration } from '@/lib/motion'
import { reportError } from '@/lib/reportError'
import { PAYMENT_METHODS, RECEIPT_TYPES } from '../api'
import { useUploadProof } from '../queries'
import { proofFileError, receiptFormSchema, type ReceiptForm } from './proofRules'

/** Upload a bank slip or payment screenshot; it becomes a receipt that Finance verifies before it counts. */
export function PaymentProofForm({ requestId, label }: { requestId: number; label: string }) {
  const toast = useToast()
  const upload = useUploadProof()
  const [file, setFile] = useState<File | null>(null)
  const [fileError, setFileError] = useState<string>()
  const [progress, setProgress] = useState(0)
  const fileInput = useRef<HTMLInputElement>(null)
  const fileId = useId()
  const form = useForm<ReceiptForm>({
    resolver: zodResolver(receiptFormSchema),
    defaultValues: { receiptType: 'DEPOSIT', paymentMethod: 'NEFT', transactionReference: '', receivedDate: todayIST() },
  })

  function onSubmit(values: ReceiptForm) {
    const problem = proofFileError(file)
    setFileError(problem)
    if (problem || !file) return
    setProgress(0)
    upload.mutate(
      { id: requestId, file, receipt: values, onProgress: setProgress },
      {
        onSuccess: (receipt) => {
          toast.success('Payment proof uploaded', `${receipt.receiptNumber} is waiting for Finance to verify it.`)
          form.reset()
          setFile(null)
          if (fileInput.current) fileInput.current.value = ''
        },
        onError: (error) => reportError(toast, error, form),
      },
    )
  }

  return (
    <Form form={form} onSubmit={onSubmit} aria-label={label} className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-2">
        <SelectField name="receiptType" label="Type" required options={RECEIPT_TYPES.map((v) => ({ value: v, label: statusLabel(v) }))} />
        <SelectField name="paymentMethod" label="Method" required options={PAYMENT_METHODS.map((v) => ({ value: v, label: v.replace('_', ' ') }))} />
        <TextField name="transactionReference" label="Transaction reference" required maxLength={100} hint="UTR, cheque or UPI reference" />
        <TextField name="amount" label="Amount (₹)" type="number" step="0.01" min="0.01" inputMode="decimal" required />
        <TextField name="receivedDate" label="Payment date" type="date" max={todayIST()} required />
        <div>
          <label htmlFor={fileId} className="mb-1 block text-sm font-medium text-fg">
            Proof file<span className="ml-0.5 text-red-600" aria-hidden="true">*</span>
          </label>
          <input
            ref={fileInput}
            id={fileId}
            type="file"
            accept="application/pdf,image/png,image/jpeg,.pdf,.png,.jpg,.jpeg"
            onChange={(e) => {
              const chosen = e.target.files?.[0] ?? null
              setFile(chosen)
              setFileError(chosen ? proofFileError(chosen) : undefined)
            }}
            aria-invalid={!!fileError}
            aria-describedby={fileError ? `${fileId}-error` : `${fileId}-hint`}
            className={`${inputClass} file:mr-3 file:rounded file:border-0 file:bg-surface-muted file:px-2 file:py-1 file:text-sm`}
          />
          {fileError ? (
            <p id={`${fileId}-error`} className="mt-1 text-sm text-red-600">{fileError}</p>
          ) : (
            <p id={`${fileId}-hint`} className="mt-1 text-xs text-fg-muted">PDF, PNG or JPEG, up to 5 MB.</p>
          )}
        </div>
      </div>
      {upload.isPending && (
        <div role="progressbar" aria-label="Upload progress" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(progress * 100)} className="h-2 overflow-hidden rounded-full bg-surface-muted">
          <motion.div className="h-full origin-left bg-accent-strong" initial={{ scaleX: 0 }} animate={{ scaleX: progress }} transition={{ duration: duration.base }} />
        </div>
      )}
      <div className="flex justify-end">
        <Button type="submit" loading={upload.isPending} icon={<Upload className="size-4" aria-hidden="true" />}>
          Upload proof
        </Button>
      </div>
    </Form>
  )
}
