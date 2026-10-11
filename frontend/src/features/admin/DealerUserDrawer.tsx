import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { Form } from '@/components/forms/Form'
import { SelectField } from '@/components/forms/SelectField'
import { TextField } from '@/components/forms/TextField'
import { Button } from '@/components/ui/Button'
import { Drawer } from '@/components/ui/Drawer'
import { useToast } from '@/components/ui/useToast'
import { reportError } from '@/lib/reportError'
import type { AdminUser, SetPasswordLink } from './api'
import { useCreateUser, useDealerOptions, useSetPasswordLink } from './queries'
import { SetPasswordLinkPanel } from './SetPasswordLinkPanel'

// Mirrors CreateUserRequest for a DEALER: a dealership and no password (the dealer sets it through the link).
const schema = z.object({
  dealerId: z.string().min(1, 'Choose the dealership.'),
  username: z.string().trim().regex(/^[a-z0-9][a-z0-9._-]{2,59}$/, '3–60 characters: lower-case letters, digits and . _ -'),
  fullName: z.string().trim().min(1, 'Enter a name.').max(150),
  email: z.string().trim().email('Enter a valid email.').max(255),
})
type Values = z.infer<typeof schema>

/** Creates a dealer login and issues its first set-password link in one step. */
export function DealerUserDrawer({ open, onClose }: { open: boolean; onClose: () => void }) {
  return (
    <Drawer open={open} onClose={onClose} title="New dealer user" description="The dealer sets their own password through a one-time link.">
      {open && <DealerUserForm onDone={onClose} />}
    </Drawer>
  )
}

function DealerUserForm({ onDone }: { onDone: () => void }) {
  const toast = useToast()
  const dealers = useDealerOptions()
  const create = useCreateUser()
  const issue = useSetPasswordLink()
  const [created, setCreated] = useState<AdminUser>()
  const [link, setLink] = useState<SetPasswordLink>()
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { dealerId: '', username: '', fullName: '', email: '' } })

  function issueLink(user: AdminUser) {
    issue.mutate(user.id, { onSuccess: setLink, onError: (e) => reportError(toast, e) })
  }

  if (created) {
    return (
      <div className="space-y-4">
        <p className="text-sm text-fg">
          <strong>{created.username}</strong> was created. They can't sign in until they set a password.
        </p>
        {link ? (
          <SetPasswordLinkPanel link={link} />
        ) : (
          <Button loading={issue.isPending} onClick={() => issueLink(created)}>
            Issue set-password link
          </Button>
        )}
        <div className="flex justify-end">
          <Button variant="secondary" onClick={onDone}>
            Done
          </Button>
        </div>
      </div>
    )
  }

  return (
    <Form
      form={form}
      aria-label="New dealer user"
      className="space-y-4"
      onSubmit={(v) =>
        create.mutate(
          { role: 'DEALER', dealerId: Number(v.dealerId), username: v.username, fullName: v.fullName, email: v.email },
          {
            onSuccess: (user) => {
              setCreated(user)
              toast.success('Dealer user created', user.username)
              issueLink(user)
            },
            onError: (e) => reportError(toast, e, form),
          },
        )
      }
    >
      <SelectField
        name="dealerId"
        label="Dealership"
        required
        placeholder={dealers.isPending ? 'Loading dealers…' : 'Choose a dealer'}
        options={(dealers.data?.content ?? []).map((d) => ({ value: String(d.id), label: `${d.name} (${d.dealerCode})` }))}
      />
      <TextField name="username" label="Username" autoComplete="off" required hint="For example dealer.chn-north" />
      <TextField name="fullName" label="Full name" required />
      <TextField name="email" label="Email" type="email" required />
      <div className="flex justify-end">
        <Button type="submit" loading={create.isPending}>
          Create and get link
        </Button>
      </div>
    </Form>
  )
}
