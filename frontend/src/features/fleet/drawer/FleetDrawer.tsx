import { AnimatePresence, motion } from 'motion/react'
import { useState } from 'react'
import { Drawer } from '@/components/ui/Drawer'
import { ErrorState } from '@/components/ui/ErrorState'
import { SkeletonRows } from '@/components/ui/Skeleton'
import { StatusBadge } from '@/components/ui/Badge'
import { useAuthUser } from '@/features/auth/useAuth'
import { fade } from '@/lib/motion'
import type { FleetRequestDetail } from '../api'
import { useFleetDetail } from '../queries'
import { editability, visibleTabs, type DrawerTab } from '../rules'
import { AuditTab } from './AuditTab'
import { DrawerTabs } from './DrawerTabs'
import { FinanceTab } from './FinanceTab'
import { InventoryTab } from './InventoryTab'
import { OverviewTab } from './OverviewTab'
import { PaymentsTab } from './PaymentsTab'
import { ScheduleTab } from './ScheduleTab'
import { WorkflowActions } from './WorkflowActions'
import { WorkflowTab } from './WorkflowTab'

export function FleetDrawer({ requestId, onClose }: { requestId: number | null; onClose: () => void }) {
  // Keep the last id while the drawer animates out, so its content doesn't vanish mid-exit.
  const [shownId, setShownId] = useState(requestId)
  const [tab, setTab] = useState<DrawerTab>('overview')
  if (requestId !== null && requestId !== shownId) {
    setShownId(requestId)
    setTab('overview')
  }
  const id = requestId ?? shownId
  const { data } = useFleetDetail(id)
  const user = useAuthUser()

  return (
    <Drawer
      open={requestId !== null}
      onClose={onClose}
      title={<DrawerTitle id={id} />}
      footer={data && <WorkflowActions request={data} can={editability(user, data)} onOpenTab={setTab} />}
    >
      {id !== null && <DrawerBody key={id} id={id} tab={tab} onTabChange={setTab} />}
    </Drawer>
  )
}

function DrawerTitle({ id }: { id: number | null }) {
  const { data } = useFleetDetail(id)
  if (!data) return <>Fleet request</>
  return (
    <span className="flex flex-wrap items-center gap-2">
      <span className="font-mono">{data.requestNumber}</span>
      <StatusBadge status={data.status} />
    </span>
  )
}

type TabProps = { tab: DrawerTab; onTabChange: (tab: DrawerTab) => void }

function DrawerBody({ id, ...tabProps }: { id: number } & TabProps) {
  const { data, isPending, error, refetch } = useFleetDetail(id)
  if (isPending) return <SkeletonRows rows={6} label="Loading request" />
  if (error) return <ErrorState title="Couldn't load this request" error={error} onRetry={() => void refetch()} />
  return <DrawerTabsBody request={data} {...tabProps} />
}

function DrawerTabsBody({ request, tab, onTabChange: setActive }: { request: FleetRequestDetail } & TabProps) {
  const user = useAuthUser()
  const tabs = visibleTabs(user)
  const active = tabs.includes(tab) ? tab : 'overview'
  const can = editability(user, request)
  const idPrefix = `fleet-${request.id}`

  return (
    <>
      <DrawerTabs tabs={tabs} active={active} onChange={setActive} idPrefix={idPrefix} />
      <AnimatePresence mode="wait" initial={false}>
        <motion.div
          key={active}
          id={`${idPrefix}-panel`}
          role="tabpanel"
          aria-labelledby={`${idPrefix}-tab-${active}`}
          variants={fade}
          initial="initial"
          animate="animate"
          exit="exit"
          className="pt-4"
        >
          {active === 'overview' && <OverviewTab request={request} canEdit={can.details} />}
          {active === 'inventory' && <InventoryTab request={request} canEdit={can.stock} />}
          {active === 'finance' && <FinanceTab request={request} canEdit={can.quotation} />}
          {active === 'payments' && <PaymentsTab request={request} canRecord={can.recordReceipt} canApprove={can.approveReceipt} />}
          {active === 'workflow' && <WorkflowTab request={request} />}
          {active === 'schedule' && <ScheduleTab request={request} canProduction={can.production} canDispatch={can.dispatch} />}
          {active === 'audit' && <AuditTab request={request} />}
        </motion.div>
      </AnimatePresence>
    </>
  )
}
