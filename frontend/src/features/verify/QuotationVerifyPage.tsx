import { useQuery } from '@tanstack/react-query'
import { motion } from 'motion/react'
import { AlertTriangle, BadgeCheck, SearchX } from 'lucide-react'
import type { ReactNode } from 'react'
import { useParams } from 'react-router'
import { Wordmark } from '@/components/layout/Wordmark'
import { Button } from '@/components/ui/Button'
import { Skeleton } from '@/components/ui/Skeleton'
import { formatDate, formatINR, statusLabel } from '@/lib/format'
import { page } from '@/lib/motion'
import { toProblem } from '@/lib/problem'
import { verifyApi } from './api'

/** Public page behind the quotation QR code. Shows only what the server returns: number, date, total, status. */
export function QuotationVerifyPage() {
  const { token = '' } = useParams()
  const query = useQuery({
    queryKey: ['public', 'verify-quotation', token],
    queryFn: () => verifyApi.quotation(token),
    retry: false,
    staleTime: Infinity,
  })
  const problem = query.error ? toProblem(query.error) : null

  return (
    <main className="flex min-h-dvh flex-col items-center bg-surface-muted px-4 py-10 text-fg">
      <Wordmark className="mb-8" />
      <motion.section variants={page} initial="initial" animate="animate" aria-labelledby="verify-title" className="w-full max-w-md rounded-xl border border-line bg-surface p-6 shadow-sm">
        <h1 id="verify-title" className="text-sm font-semibold uppercase tracking-wide text-fg-muted">
          Quotation verification
        </h1>
        {query.isPending && (
          <div role="status" aria-label="Checking quotation" className="mt-5 space-y-3">
            <Skeleton className="h-8 w-2/3" />
            <Skeleton className="h-5 w-full" />
            <Skeleton className="h-5 w-5/6" />
          </div>
        )}
        {query.data && (
          <>
            <Result tone="ok" icon={<BadgeCheck className="size-7" aria-hidden="true" />} title="Genuine">
              This quotation was issued through the Renault Group Fleet Portal.
            </Result>
            <dl className="mt-5 divide-y divide-line text-sm">
              <Row label="Quotation no." value={<span className="font-mono">{query.data.quotationNumber}</span>} />
              <Row label="Issue date" value={formatDate(query.data.issueDate)} />
              <Row label="Total" value={<strong className="tabular-nums">{formatINR(query.data.totalAmount)}</strong>} />
              <Row label="Status" value={statusLabel(query.data.status)} />
            </dl>
            <p className="mt-4 text-xs text-fg-muted">Compare these figures with the document you received. If they differ, do not rely on it.</p>
          </>
        )}
        {problem?.status === 404 && (
          <Result tone="bad" icon={<SearchX className="size-7" aria-hidden="true" />} title="Not found">
            No quotation matches this verification code. The document may not be genuine, or the link was copied incompletely.
          </Result>
        )}
        {problem && problem.status !== 404 && (
          <Result tone="warn" icon={<AlertTriangle className="size-7" aria-hidden="true" />} title={problem.status === 429 ? 'Too many checks' : "Couldn't check right now"}>
            {problem.status === 429 ? 'Please wait a minute and try again.' : problem.detail}
            <div className="mt-3">
              <Button size="sm" variant="secondary" loading={query.isFetching} onClick={() => void query.refetch()}>
                Try again
              </Button>
            </div>
          </Result>
        )}
      </motion.section>
    </main>
  )
}

const TONES = {
  ok: 'bg-green-50 text-green-800 dark:bg-green-950 dark:text-green-200',
  bad: 'bg-red-50 text-red-800 dark:bg-red-950 dark:text-red-200',
  warn: 'bg-amber-50 text-amber-900 dark:bg-amber-950 dark:text-amber-100',
} as const

function Result({ tone, icon, title, children }: { tone: keyof typeof TONES; icon: ReactNode; title: string; children: ReactNode }) {
  return (
    <div role={tone === 'ok' ? 'status' : 'alert'} className={`mt-5 flex items-start gap-3 rounded-lg p-4 ${TONES[tone]}`}>
      <span className="shrink-0">{icon}</span>
      <div>
        <p className="text-lg font-semibold">{title}</p>
        <div className="text-sm">{children}</div>
      </div>
    </div>
  )
}

function Row({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-4 py-2.5">
      <dt className="text-fg-muted">{label}</dt>
      <dd className="text-right">{value}</dd>
    </div>
  )
}
