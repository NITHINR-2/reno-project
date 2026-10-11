import { PageHeader } from '@/components/layout/PageHeader'
import { Card } from '@/components/ui/Card'
import { ErrorState } from '@/components/ui/ErrorState'
import { Skeleton } from '@/components/ui/Skeleton'
import { cn } from '@/lib/cn'
import { formatDate } from '@/lib/format'
import { DemandCard, StockVsProductionChart, TopDealersChart } from './components/BreakdownCharts'
import { DashboardFilters } from './components/DashboardFilters'
import { MonthlyValueChart, PipelineChart, StageDurationChart } from './components/FlowCharts'
import { KpiStrip } from './components/KpiStrip'
import { useDashboardFilters } from './filters'
import { useExecutiveAnalytics } from './queries'

/** Executive dashboard (Marketing Head, Supervisor). Every figure comes from GET /api/analytics/executive. */
export function ExecutiveDashboardPage() {
  const filters = useDashboardFilters()
  const { data, error, isPending, isPlaceholderData, refetch } = useExecutiveAnalytics(filters.query)
  const period = data?.period

  return (
    <>
      <PageHeader
        title="Executive Dashboard"
        description={
          period
            ? `${formatDate(period.from)} – ${formatDate(period.to)}, compared with ${formatDate(period.previousFrom)} – ${formatDate(period.previousTo)}`
            : 'Order value, pipeline and SLA performance'
        }
      />
      <DashboardFilters filters={filters} />
      {error ? (
        <Card>
          <ErrorState title="Couldn't load the dashboard" error={error} onRetry={() => void refetch()} />
        </Card>
      ) : (
        <div aria-busy={isPending || isPlaceholderData} className={cn('transition-opacity duration-200', isPlaceholderData && 'opacity-60')}>
          <KpiStrip data={data} />
          {!data ? (
            <div role="status" aria-label="Loading charts" className="grid grid-cols-1 gap-4 lg:grid-cols-2">
              {Array.from({ length: 6 }, (_, i) => (
                <Skeleton key={i} className={cn('h-80 rounded-xl', i === 0 && 'lg:col-span-2')} />
              ))}
            </div>
          ) : (
            <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
              <MonthlyValueChart data={data.monthlyOrderValue} />
              <PipelineChart data={data.pipeline} />
              <StageDurationChart data={data.stageDurations} />
              <TopDealersChart data={data.topDealers} />
              <DemandCard byRegion={data.demandByRegion} byModel={data.demandByModel} />
              <StockVsProductionChart data={data.stockVsProduction} />
            </div>
          )}
        </div>
      )}
    </>
  )
}
