import { FileDown } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { useToast } from '@/components/ui/useToast'
import { reportError } from '@/lib/reportError'
import type { Quotation } from '../api'
import { useQuotationPdf } from '../queries'

/** Downloads the server-rendered PDF. Drafts come back watermarked "DRAFT"; issued ones from their frozen snapshot. */
export function QuotationPdfButton({ quotation, compact = false }: { quotation: Quotation; compact?: boolean }) {
  const toast = useToast()
  const pdf = useQuotationPdf()
  const issued = quotation.status === 'ISSUED' || quotation.status === 'SUPERSEDED'
  return (
    <Button
      size="sm"
      variant={compact ? 'ghost' : 'secondary'}
      loading={pdf.isPending}
      aria-label={`Download ${quotation.quotationNumber} PDF${issued ? '' : ' (draft)'}`}
      icon={<FileDown className="size-4" aria-hidden="true" />}
      onClick={() =>
        pdf.mutate(quotation.id, {
          onSuccess: (fileName) => toast.success('PDF downloaded', fileName),
          onError: (error) => reportError(toast, error),
        })
      }
    >
      {compact ? 'PDF' : issued ? 'Download PDF' : 'Draft PDF'}
    </Button>
  )
}
