/** PENDING_MARKETING_REVIEW -> "Pending marketing review". */
export function statusLabel(status: string) {
  const text = status.replaceAll('_', ' ').toLowerCase()
  return text.charAt(0).toUpperCase() + text.slice(1)
}

const IST = 'Asia/Kolkata'
const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2, maximumFractionDigits: 2 })
const count = new Intl.NumberFormat('en-IN')

/** ₹12,00,000.00 — Indian digit grouping. Display only; amounts are calculated by the backend. */
export const formatINR = (value: number | null | undefined) => (value == null ? '—' : inr.format(value))
export const formatCount = (value: number) => count.format(value)

/** `2026-10-11` (a LocalDate) → 11 Oct 2026, without shifting the day through a time zone. */
export function formatDate(value: string | null | undefined) {
  if (!value) return '—'
  const [y, m, d] = value.slice(0, 10).split('-').map(Number)
  return new Date(Date.UTC(y, m - 1, d)).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric', timeZone: 'UTC' })
}

/** An Instant shown in IST. */
export function formatDateTime(value: string | null | undefined) {
  if (!value) return '—'
  return new Date(value).toLocaleString('en-IN', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit', timeZone: IST })
}

/** Today in IST as yyyy-mm-dd (for date inputs and "not in the past" hints). */
export function todayIST() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: IST }).format(new Date())
}

/** 45m · 18h · 2d 4h — a compact span for SLA countdowns (whole minutes, rounded down). */
export function formatSpan(ms: number) {
  const minutes = Math.floor(Math.abs(ms) / 60_000)
  if (minutes < 60) return `${minutes}m`
  const hours = Math.floor(minutes / 60)
  if (hours < 48) return `${hours}h`
  const days = Math.floor(hours / 24)
  const rest = hours % 24
  return rest ? `${days}d ${rest}h` : `${days}d`
}
