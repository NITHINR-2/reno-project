import { act, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { slaText } from '../sla'
import { SlaChip } from './SlaChip'

const NOW = new Date('2026-10-11T04:30:00Z').getTime()
const at = (hours: number) => new Date(NOW + hours * 3_600_000).toISOString()

describe('slaText', () => {
  it.each([
    ['ON_TRACK', 18, '18h left'],
    ['ON_TRACK', 0.75, '45m left'],
    ['ON_TRACK', 52, '2d 4h left'],
    ['ON_TRACK', 72, '3d left'],
    ['AT_RISK', 5.5, '5h left'],
    ['BREACHED', -6, 'Overdue 6h'],
    ['BREACHED', -0.25, 'Overdue 15m'],
    ['BREACHED', -49, 'Overdue 2d 1h'],
    // The server decides it's breached; until it does, a run-out clock just reads "Due now".
    ['AT_RISK', -0.1, 'Due now'],
  ] as const)('%s with %sh to go reads "%s"', (status, hours, text) => {
    expect(slaText(status, at(hours), NOW)).toBe(text)
  })
})

describe('SlaChip', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date', 'setInterval', 'clearInterval'] })
    vi.setSystemTime(NOW)
  })
  afterEach(() => vi.useRealTimers())

  it('takes its colour from the server status and pulses only when breached', () => {
    const { rerender } = render(<SlaChip status="ON_TRACK" dueAt={at(18)} />)
    expect(screen.getByLabelText('SLA on track: 18h left').querySelector('span')).toHaveClass('bg-green-50')

    rerender(<SlaChip status="AT_RISK" dueAt={at(3)} />)
    const risk = screen.getByLabelText('SLA at risk: 3h left')
    expect(risk.querySelector('span')).toHaveClass('bg-amber-50')
    expect(risk).not.toHaveClass('sla-pulse')

    rerender(<SlaChip status="BREACHED" dueAt={at(-6)} />)
    const overdue = screen.getByLabelText('SLA overdue: Overdue 6h')
    expect(overdue.querySelector('span')).toHaveClass('bg-red-50')
    expect(overdue).toHaveClass('sla-pulse')
  })

  it('counts down once a minute on the client', () => {
    render(<SlaChip status="ON_TRACK" dueAt={at(1)} />)
    expect(screen.getByText('1h left')).toBeInTheDocument()
    act(() => {
      vi.advanceTimersByTime(60_000)
    })
    expect(screen.getByText('59m left')).toBeInTheDocument()
  })

  it('shows "No SLA" for untimed and terminal stages', () => {
    render(<SlaChip status={null} dueAt={null} />)
    expect(screen.getByText('No SLA')).toBeInTheDocument()
  })
})
