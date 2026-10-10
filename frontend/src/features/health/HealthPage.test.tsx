import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '@/lib/api'
import { HealthPage } from './HealthPage'

vi.mock('@/lib/api', () => ({ api: { get: vi.fn() } }))

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <HealthPage />
    </QueryClientProvider>,
  )
}

describe('HealthPage', () => {
  beforeEach(() => {
    vi.mocked(api.get).mockReset()
  })

  it('shows the backend and database status', async () => {
    vi.mocked(api.get).mockResolvedValue({
      data: { status: 'UP', database: 'UP', version: '0.0.1-SNAPSHOT', timestamp: '2026-10-10T10:00:00Z' },
    })

    renderPage()

    expect(screen.getByRole('status', { name: /loading health status/i })).toBeInTheDocument()
    expect(await screen.findByText('0.0.1-SNAPSHOT')).toBeInTheDocument()
    expect(screen.getAllByText('UP')).toHaveLength(2)
    expect(api.get).toHaveBeenCalledWith('/health', expect.anything())
  })

  it('shows an error when the backend is unreachable', async () => {
    vi.mocked(api.get).mockRejectedValue(new Error('Network Error'))

    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent(/backend unreachable/i)
  })
})
