import { AnimatePresence, motion } from 'motion/react'
import { useSearchParams } from 'react-router'
import { PageHeader } from '@/components/layout/PageHeader'
import { Card } from '@/components/ui/Card'
import { Tabs, type TabItem } from '@/components/ui/Tabs'
import { hasPermission, useAuthUser } from '@/features/auth/useAuth'
import { fade } from '@/lib/motion'
import { AuditLogTab } from './AuditLogTab'
import { DealersTab } from './DealersTab'
import { RolesTab } from './RolesTab'
import { UsersTab } from './UsersTab'

type AdminTab = 'users' | 'roles' | 'dealers' | 'audit'

/** Tabs follow the permissions; the server checks each call again. */
const TABS: (TabItem<AdminTab> & { anyOf: string[] })[] = [
  { id: 'users', label: 'Users', anyOf: ['ADMIN_USERS_MANAGE'] },
  { id: 'roles', label: 'Roles', anyOf: ['ADMIN_USERS_MANAGE'] },
  { id: 'dealers', label: 'Dealers', anyOf: ['ADMIN_DEALERS_MANAGE'] },
  { id: 'audit', label: 'Audit log', anyOf: ['AUDIT_LOG_READ'] },
]

export function AdminConsolePage() {
  const user = useAuthUser()
  const [params, setParams] = useSearchParams()
  const tabs = TABS.filter((t) => hasPermission(user, ...t.anyOf))
  const requested = params.get('tab')
  const active = tabs.find((t) => t.id === requested)?.id ?? tabs[0]?.id

  return (
    <>
      <PageHeader title="Admin Console" description="Users, roles, dealer onboarding and the audit log." />
      <Card>
        <Tabs
          tabs={tabs}
          active={active}
          onChange={(tab) => setParams({ tab }, { replace: true })}
          idPrefix="admin"
          label="Admin sections"
          className="px-2 sm:px-4"
        />
        <AnimatePresence mode="wait" initial={false}>
          <motion.div key={active} id="admin-panel" role="tabpanel" aria-labelledby={`admin-tab-${active}`} variants={fade} initial="initial" animate="animate" exit="exit">
            {active === 'users' && <UsersTab />}
            {active === 'roles' && <RolesTab />}
            {active === 'dealers' && <DealersTab />}
            {active === 'audit' && <AuditLogTab />}
          </motion.div>
        </AnimatePresence>
      </Card>
    </>
  )
}
