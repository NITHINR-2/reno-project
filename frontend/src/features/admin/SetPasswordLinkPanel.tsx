import { Check, Copy, Link2 } from 'lucide-react'
import { useState } from 'react'
import { Button } from '@/components/ui/Button'
import { formatDateTime } from '@/lib/format'
import type { SetPasswordLink } from './api'

/** Shows a freshly issued one-time link. It is never shown again, so it must be copied now. */
export function SetPasswordLinkPanel({ link }: { link: SetPasswordLink }) {
  const [copied, setCopied] = useState(false)
  async function copy() {
    try {
      await navigator.clipboard.writeText(link.url)
      setCopied(true)
    } catch {
      setCopied(false)
    }
  }
  return (
    <section aria-label="Set-password link" className="rounded-lg border border-accent-strong/40 bg-accent/10 p-4">
      <p className="flex items-center gap-2 text-sm font-semibold text-fg">
        <Link2 className="size-4" aria-hidden="true" /> One-time set-password link for {link.username}
      </p>
      <p className="mt-1 text-xs text-fg-muted">
        Send it to the user over a trusted channel. It works once, expires {formatDateTime(link.expiresAt)}, and replaces any earlier link. It won't be shown again.
      </p>
      <div className="mt-3 flex flex-col gap-2 sm:flex-row">
        <input readOnly value={link.url} aria-label="Link" onFocus={(e) => e.target.select()} className="min-h-11 flex-1 rounded-lg border border-line bg-surface px-3 font-mono text-xs text-fg" />
        <Button variant="secondary" onClick={() => void copy()} icon={copied ? <Check className="size-4" aria-hidden="true" /> : <Copy className="size-4" aria-hidden="true" />}>
          {copied ? 'Copied' : 'Copy link'}
        </Button>
      </div>
    </section>
  )
}
