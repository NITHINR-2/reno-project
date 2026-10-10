/** PENDING_MARKETING_REVIEW -> "Pending marketing review". */
export function statusLabel(status: string) {
  const text = status.replaceAll('_', ' ').toLowerCase()
  return text.charAt(0).toUpperCase() + text.slice(1)
}
