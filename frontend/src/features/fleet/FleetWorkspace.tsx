import { Card } from '@/components/ui/Card'
import { PageHeader } from '@/components/layout/PageHeader'
import { FleetFilters } from './components/FleetFilters'
import { FleetTable } from './components/FleetTable'
import { MetricCards } from './components/MetricCards'
import { FleetDrawer } from './drawer/FleetDrawer'
import { useFleetFilters, type Stage } from './filters'

type FleetWorkspaceProps = { title: string; description: string; defaultStage?: Stage }

/** Summary cards, filterable server-paged table and the request drawer. Each module opens it on its own stage. */
export function FleetWorkspace({ title, description, defaultStage }: FleetWorkspaceProps) {
  const filters = useFleetFilters(defaultStage)

  return (
    <>
      <PageHeader title={title} description={description} />
      <MetricCards activeStage={filters.values.status} onSelectStage={(stage) => filters.setFilter('status', `stage:${stage}`)} />
      <Card>
        <FleetFilters
          values={filters.values}
          sort={filters.sort}
          onChange={filters.setFilter}
          onSortChange={filters.setSort}
          onClear={filters.clear}
        />
        <FleetTable
          params={filters.listParams}
          sort={filters.sort}
          onSortChange={filters.setSort}
          onPageChange={filters.setPage}
          onOpen={filters.openRequest}
        />
      </Card>
      <FleetDrawer requestId={filters.openId} onClose={() => filters.openRequest(null)} />
    </>
  )
}
