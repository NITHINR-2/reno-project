import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { Page } from '@/lib/page'
import { DataTable, type Column } from './DataTable'

type Row = { id: number; number: string; dealer: string }

const columns: Column<Row>[] = [
  { key: 'number', header: 'Request', cell: (r) => r.number },
  { key: 'dealer', header: 'Dealer', cell: (r) => r.dealer },
]

const page = (overrides: Partial<Page<Row>> = {}): Page<Row> => ({
  content: [
    { id: 1, number: 'FLT-2026-0891', dealer: 'Chennai North Dealership' },
    { id: 2, number: 'FLT-2026-1000', dealer: 'Pune West Motors' },
  ],
  page: 0,
  size: 2,
  totalElements: 5,
  totalPages: 3,
  ...overrides,
})

function renderTable(props: Partial<Parameters<typeof DataTable<Row>>[0]> = {}) {
  const onPageChange = vi.fn()
  render(
    <DataTable<Row>
      label="Fleet requests"
      columns={columns}
      data={page()}
      getRowId={(r) => r.id}
      isLoading={false}
      onPageChange={onPageChange}
      {...props}
    />,
  )
  return { onPageChange }
}

describe('DataTable', () => {
  it('shows skeletons while loading', () => {
    renderTable({ data: undefined, isLoading: true })
    expect(screen.getByRole('status', { name: 'Loading fleet requests' })).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('shows the empty state', () => {
    renderTable({ data: page({ content: [], totalElements: 0, totalPages: 0 }), emptyTitle: 'No fleet requests' })
    expect(screen.getByText('No fleet requests')).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('shows the error state with a working retry', async () => {
    const onRetry = vi.fn()
    renderTable({ data: undefined, error: new Error('Boom'), onRetry })
    const alert = screen.getByRole('alert')
    expect(alert).toHaveTextContent("Couldn't load fleet requests")
    expect(alert).toHaveTextContent('Boom')
    await userEvent.click(within(alert).getByRole('button', { name: 'Try again' }))
    expect(onRetry).toHaveBeenCalledOnce()
  })

  it('renders rows and the server page position', () => {
    renderTable()
    const table = screen.getByRole('table', { name: 'Fleet requests' })
    expect(within(table).getAllByRole('row')).toHaveLength(3)
    expect(within(table).getByText('FLT-2026-0891')).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: 'Pagination' })).toHaveTextContent('Showing 1–2 of 5')
    expect(screen.getByText('Page 1 of 3')).toBeInTheDocument()
  })

  it('pages through the server: previous disabled on the first page, next requests page 1', async () => {
    const { onPageChange } = renderTable()
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Next page' }))
    expect(onPageChange).toHaveBeenCalledWith(1)
  })

  it('disables next on the last page', () => {
    renderTable({ data: page({ page: 2, content: [{ id: 5, number: 'FLT-5', dealer: 'D' }] }) })
    expect(screen.getByRole('button', { name: 'Next page' })).toBeDisabled()
    expect(screen.getByRole('navigation', { name: 'Pagination' })).toHaveTextContent('Showing 5–5 of 5')
  })

  it('keeps showing data while a new page loads', () => {
    renderTable({ isFetching: true, error: new Error('transient') })
    expect(screen.getByRole('table')).toBeInTheDocument()
  })

  it('opens a row from the table and from the mobile card View button', async () => {
    const onRowClick = vi.fn()
    renderTable({ onRowClick })
    await userEvent.click(within(screen.getByRole('table')).getByText('Pune West Motors'))
    expect(onRowClick).toHaveBeenLastCalledWith(expect.objectContaining({ id: 2 }))

    const cards = screen.getByRole('list', { name: 'Fleet requests' })
    await userEvent.click(within(cards).getAllByRole('button', { name: 'View' })[0])
    expect(onRowClick).toHaveBeenLastCalledWith(expect.objectContaining({ id: 1 }))
  })
})
