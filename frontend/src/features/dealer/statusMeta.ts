import type { Tone } from '@/components/ui/Badge'
import type { DealerStatus } from './api'

export const DEALER_STATUS_LABELS: Record<DealerStatus, string> = {
  IN_REVIEW: 'In review',
  PROCESSING: 'Processing',
  AWAITING_PAYMENT: 'Awaiting payment',
  CONFIRMED: 'Confirmed',
  IN_PRODUCTION: 'In production',
  DISPATCH_SCHEDULED: 'Dispatch scheduled',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled',
}

export const DEALER_STATUS_TONES: Record<DealerStatus, Tone> = {
  IN_REVIEW: 'info',
  PROCESSING: 'accent',
  AWAITING_PAYMENT: 'warning',
  CONFIRMED: 'success',
  IN_PRODUCTION: 'info',
  DISPATCH_SCHEDULED: 'info',
  DELIVERED: 'success',
  CANCELLED: 'danger',
}

/** The open path, in order (CANCELLED is shown separately). */
export const PIPELINE: DealerStatus[] = ['IN_REVIEW', 'PROCESSING', 'AWAITING_PAYMENT', 'CONFIRMED', 'IN_PRODUCTION', 'DISPATCH_SCHEDULED', 'DELIVERED']
