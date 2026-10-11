import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { Form } from '@/components/forms/Form'
import { SelectField } from '@/components/forms/SelectField'
import { TextField } from '@/components/forms/TextField'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Drawer } from '@/components/ui/Drawer'
import { useToast } from '@/components/ui/useToast'
import { ROLE_LABELS, ROLES, type Role } from '@/features/auth/api'
import { useAuthUser } from '@/features/auth/useAuth'
import { reportError } from '@/lib/reportError'
import type { AdminUser, SetPasswordLink } from './api'
import { useCreateUser, useSetPasswordLink, useSetUserActive, useUpdateUser } from './queries'
import { SetPasswordLinkPanel } from './SetPasswordLinkPanel'

// Mirrors CreateUserRequest / UpdateUserRequest; the server re-validates and enforces the role rules.
const base = { email: z.string().trim().email('Enter a valid email.').max(255), fullName: z.string().trim().min(1, 'Enter a name.').max(150), role: z.enum(ROLES) }
const createSchema = z
  .object({
    ...base,
    username: z.string().trim().regex(/^[a-z0-9][a-z0-9._-]{2,59}$/, '3–60 characters: lower-case letters, digits and . _ -'),
    password: z.string().min(12, 'At least 12 characters.').max(128),
  })
  .refine((v) => !v.username || !v.password.toLowerCase().includes(v.username.toLowerCase()), { path: ['password'], message: "Mustn't contain the username." })
const editSchema = z.object(base)

type Props = { target: AdminUser | 'new' | null; onClose: () => void }

export function UserDrawer({ target, onClose }: Props) {
  const title = target === 'new' ? 'New user' : target ? target.fullName : ''
  return (
    <Drawer open={target !== null} onClose={onClose} title={title} description={target && target !== 'new' ? target.username : undefined}>
      {target === 'new' && <CreateUserForm onDone={onClose} />}
      {target && target !== 'new' && <EditUserForm key={target.id} user={target} onDone={onClose} />}
    </Drawer>
  )
}

/**
 * Only a Supervisor may grant SUPERVISOR or manage Supervisor accounts (server rule, mirrored). DEALER accounts are
 * created from "New dealer user" (they need a dealership) and can't change role, so DEALER is never offered here.
 */
function roleOptions(viewerRole: Role) {
  return ROLES.filter((r) => r !== 'DEALER' && (viewerRole === 'SUPERVISOR' || r !== 'SUPERVISOR')).map((r) => ({ value: r, label: ROLE_LABELS[r] }))
}

function CreateUserForm({ onDone }: { onDone: () => void }) {
  const viewer = useAuthUser()
  const toast = useToast()
  const create = useCreateUser()
  const form = useForm<z.infer<typeof createSchema>>({
    resolver: zodResolver(createSchema),
    defaultValues: { username: '', email: '', fullName: '', role: 'MARKETING_MANAGER', password: '' },
  })
  return (
    <Form
      form={form}
      aria-label="New user"
      className="space-y-4"
      onSubmit={(v) =>
        create.mutate(v, {
          onSuccess: (u) => {
            toast.success('User created', `${u.username} can now sign in.`)
            onDone()
          },
          onError: (e) => reportError(toast, e, form),
        })
      }
    >
      <TextField name="username" label="Username" autoComplete="off" required />
      <TextField name="fullName" label="Full name" required />
      <TextField name="email" label="Email" type="email" required />
      <SelectField name="role" label="Role" required options={roleOptions(viewer.role)} />
      <TextField name="password" label="Initial password" type="password" autoComplete="new-password" required hint="12–128 characters. Share it securely; it is never shown again." />
      <div className="flex justify-end">
        <Button type="submit" loading={create.isPending}>
          Create user
        </Button>
      </div>
    </Form>
  )
}

function EditUserForm({ user, onDone }: { user: AdminUser; onDone: () => void }) {
  const viewer = useAuthUser()
  const toast = useToast()
  const update = useUpdateUser()
  const setActive = useSetUserActive()
  const [confirming, setConfirming] = useState(false)
  const form = useForm<z.infer<typeof editSchema>>({
    resolver: zodResolver(editSchema),
    defaultValues: { email: user.email, fullName: user.fullName, role: user.role },
  })
  const issueLink = useSetPasswordLink()
  const [link, setLink] = useState<SetPasswordLink>()
  const self = viewer.id === user.id
  const protectedAccount = user.role === 'SUPERVISOR' && viewer.role !== 'SUPERVISOR'
  const dealerAccount = user.role === 'DEALER'

  return (
    <>
      {(self || protectedAccount) && (
        <p role="note" className="mb-4 rounded-lg bg-surface-muted px-3 py-2 text-sm text-fg-muted">
          {protectedAccount ? 'Only a Supervisor can change a Supervisor account.' : "You can't change your own role or deactivate yourself."}
        </p>
      )}
      <Form
        form={form}
        aria-label="Edit user"
        className="space-y-4"
        onSubmit={(v) =>
          update.mutate(
            { id: user.id, version: user.version, email: v.email, fullName: v.fullName, role: self || dealerAccount ? undefined : v.role },
            {
              onSuccess: () => {
                toast.success('User updated', user.username)
                onDone()
              },
              onError: (e) => reportError(toast, e, form),
            },
          )
        }
      >
        <fieldset disabled={protectedAccount} className="space-y-4">
          <TextField name="fullName" label="Full name" required />
          <TextField name="email" label="Email" type="email" required />
          <SelectField
            name="role"
            label="Role"
            required
            disabled={self || dealerAccount}
            hint={dealerAccount ? 'Dealer accounts keep their role.' : undefined}
            options={protectedAccount || dealerAccount ? [{ value: user.role, label: ROLE_LABELS[user.role] }] : roleOptions(viewer.role)}
          />
        </fieldset>
        <div className="flex flex-col-reverse gap-2 border-t border-line pt-4 sm:flex-row sm:justify-between">
          <Button variant={user.active ? 'danger' : 'secondary'} disabled={self || protectedAccount} onClick={() => setConfirming(true)}>
            {user.active ? 'Deactivate' : 'Activate'}
          </Button>
          <Button type="submit" loading={update.isPending} disabled={protectedAccount}>
            Save changes
          </Button>
        </div>
      </Form>
      {!self && !protectedAccount && user.active && (
        <div className="mt-6 space-y-3 border-t border-line pt-4">
          <p className="text-sm text-fg-muted">
            {dealerAccount ? 'Send the dealer a one-time link to set (or reset) their password.' : 'Reset the password with a one-time link.'}
          </p>
          {link ? (
            <SetPasswordLinkPanel link={link} />
          ) : (
            <Button
              variant="secondary"
              loading={issueLink.isPending}
              onClick={() => issueLink.mutate(user.id, { onSuccess: setLink, onError: (e) => reportError(toast, e) })}
            >
              Issue set-password link
            </Button>
          )}
        </div>
      )}
      <ConfirmDialog
        open={confirming}
        title={user.active ? `Deactivate ${user.username}?` : `Activate ${user.username}?`}
        description={user.active ? 'They are signed out at once and cannot sign in until reactivated.' : 'They can sign in again with their existing password.'}
        confirmLabel={user.active ? 'Deactivate' : 'Activate'}
        tone={user.active ? 'danger' : 'primary'}
        loading={setActive.isPending}
        onCancel={() => setConfirming(false)}
        onConfirm={() =>
          setActive.mutate(
            { id: user.id, active: !user.active },
            {
              onSuccess: (u) => {
                toast.success(u.active ? 'User activated' : 'User deactivated', u.username)
                onDone()
              },
              onError: (e) => reportError(toast, e),
              onSettled: () => setConfirming(false),
            },
          )
        }
      />
    </>
  )
}
