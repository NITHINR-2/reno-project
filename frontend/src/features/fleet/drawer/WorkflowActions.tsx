import { useState } from 'react'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { useToast } from '@/components/ui/useToast'
import type { FleetRequestDetail } from '../api'
import { useTransition } from '../queries'
import type { DrawerTab, Editability } from '../rules'
import { tabShortcuts, WORKFLOW_ACTIONS, type TransitionAction } from '../workflowActions'
import { reportError } from '@/lib/reportError'

type Props = { request: FleetRequestDetail; can: Editability; onOpenTab: (tab: DrawerTab) => void }

/** Drawer footer: one button per action the server says this user may take now. */
export function WorkflowActions({ request, can, onOpenTab }: Props) {
  const toast = useToast()
  const transition = useTransition(request.id)
  const [pending, setPending] = useState<TransitionAction | null>(null)

  const actions = [
    ...tabShortcuts(can),
    ...request.allowedActions.flatMap((code) => (WORKFLOW_ACTIONS[code] ? [{ code, ...WORKFLOW_ACTIONS[code] }] : [])),
  ]
  if (actions.length === 0) return null

  function confirm(reason: string) {
    if (!pending) return
    transition.mutate(
      { endpoint: pending.endpoint, version: request.version, reason: reason || undefined },
      {
        onSuccess: (saved) => toast.success(pending.success, `${saved.requestNumber} is now ${saved.status.replaceAll('_', ' ').toLowerCase()}.`),
        onError: (error) => reportError(toast, error),
        onSettled: () => setPending(null),
      },
    )
  }

  return (
    <>
      <div role="group" aria-label="Workflow actions" className="flex w-full flex-col-reverse gap-2 sm:flex-row sm:flex-wrap sm:justify-end">
        {actions.map((action) =>
          action.kind === 'tab' ? (
            <Button key={action.code} variant="secondary" onClick={() => onOpenTab(action.tab)}>
              {action.label}
            </Button>
          ) : (
            <Button key={action.code} variant={action.tone} onClick={() => setPending(action)}>
              {action.label}
            </Button>
          ),
        )}
      </div>
      <ConfirmDialog
        open={pending !== null}
        title={pending?.confirmTitle ?? ''}
        description={pending?.confirmText}
        confirmLabel={pending?.label}
        tone={pending?.tone === 'danger' ? 'danger' : 'primary'}
        requireReason={pending?.requireReason}
        loading={transition.isPending}
        onCancel={() => setPending(null)}
        onConfirm={confirm}
      />
    </>
  )
}
