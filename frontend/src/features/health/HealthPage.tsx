import { Activity, AlertTriangle, CheckCircle2, Database, RefreshCw, XCircle } from 'lucide-react'
import { useHealth } from './useHealth'

function StatusBadge({ value }: { value: 'UP' | 'DOWN' }) {
  const up = value === 'UP'
  const Icon = up ? CheckCircle2 : XCircle
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-sm font-medium ${
        up ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'
      }`}
    >
      <Icon className="size-4" aria-hidden="true" />
      {value}
    </span>
  )
}

export function HealthPage() {
  const { data, isPending, isError, error, refetch, isFetching } = useHealth()

  return (
    <main className="mx-auto flex min-h-screen max-w-xl flex-col justify-center px-4 py-10">
      <header className="mb-6">
        <p className="text-sm font-semibold uppercase tracking-wider text-accent-strong">
          Renault Group Fleet Portal
        </p>
        <h1 className="mt-1 text-2xl font-bold text-charcoal">System health</h1>
      </header>

      <section
        aria-labelledby="backend-health"
        className="rounded-xl border border-gray-200 bg-white p-6 shadow-sm"
      >
        <div className="mb-4 flex items-center justify-between gap-4">
          <h2 id="backend-health" className="flex items-center gap-2 text-lg font-semibold">
            <Activity className="size-5 text-gray-500" aria-hidden="true" />
            Backend API
          </h2>
          <button
            type="button"
            onClick={() => void refetch()}
            disabled={isFetching}
            className="inline-flex min-h-11 items-center gap-2 rounded-lg border border-gray-300 px-3 text-sm font-medium transition-colors duration-150 hover:bg-gray-100 focus-visible:outline-2 focus-visible:outline-accent active:scale-[0.98] disabled:opacity-60"
          >
            <RefreshCw className={`size-4 ${isFetching ? 'animate-spin' : ''}`} aria-hidden="true" />
            Refresh
          </button>
        </div>

        {isPending && (
          <div role="status" aria-label="Loading health status" className="space-y-3">
            <div className="h-6 w-1/3 animate-pulse rounded bg-gray-200" />
            <div className="h-6 w-1/2 animate-pulse rounded bg-gray-200" />
          </div>
        )}

        {isError && (
          <div role="alert" className="flex items-start gap-3 rounded-lg bg-red-50 p-4 text-red-800">
            <AlertTriangle className="mt-0.5 size-5 shrink-0" aria-hidden="true" />
            <div>
              <p className="font-medium">Backend unreachable</p>
              <p className="text-sm">{error.message}</p>
            </div>
          </div>
        )}

        {data && (
          <dl className="grid grid-cols-[auto_1fr] items-center gap-x-6 gap-y-3 text-sm">
            <dt className="text-gray-500">Status</dt>
            <dd>
              <StatusBadge value={data.status} />
            </dd>
            <dt className="flex items-center gap-1.5 text-gray-500">
              <Database className="size-4" aria-hidden="true" />
              Database
            </dt>
            <dd>
              <StatusBadge value={data.database} />
            </dd>
            <dt className="text-gray-500">Version</dt>
            <dd className="font-mono">{data.version}</dd>
            <dt className="text-gray-500">Checked at</dt>
            <dd>
              <time dateTime={data.timestamp}>{new Date(data.timestamp).toLocaleString()}</time>
            </dd>
          </dl>
        )}
      </section>
    </main>
  )
}
