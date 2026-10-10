import { Card } from '@/components/ui/Card'
import { EmptyState } from '@/components/ui/EmptyState'
import { PageHeader } from '@/components/layout/PageHeader'
import type { NavItem } from '@/app/navigation'

/** Module landing page; each module's real screens arrive in later phases. */
export function ModulePage({ item, description }: { item: NavItem; description: string }) {
  const Icon = item.icon
  return (
    <>
      <PageHeader title={item.label} description={description} />
      <Card>
        <EmptyState icon={<Icon className="size-6" aria-hidden="true" />} title={`${item.label} is ready for its screens`} description="This module's views are built in the next phase." />
      </Card>
    </>
  )
}
