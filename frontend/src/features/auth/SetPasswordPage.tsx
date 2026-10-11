import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery } from '@tanstack/react-query'
import { AlertTriangle, CheckCircle2, KeyRound } from 'lucide-react'
import { motion } from 'motion/react'
import { useEffect, useState, type ReactNode } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useLocation, useNavigate } from 'react-router'
import { z } from 'zod'
import { Form } from '@/components/forms/Form'
import { TextField } from '@/components/forms/TextField'
import { Wordmark } from '@/components/layout/Wordmark'
import { Button } from '@/components/ui/Button'
import { Skeleton } from '@/components/ui/Skeleton'
import { formatDateTime } from '@/lib/format'
import { page } from '@/lib/motion'
import { toProblem } from '@/lib/problem'
import { setPasswordApi } from './setPasswordApi'

const passwordSchema = (username: string) =>
  z
    .object({
      password: z.string().min(12, 'At least 12 characters.').max(128, 'At most 128 characters.'),
      confirm: z.string(),
    })
    .refine((v) => !v.password.toLowerCase().includes(username.toLowerCase()), { path: ['password'], message: "Mustn't contain your username." })
    .refine((v) => v.password === v.confirm, { path: ['confirm'], message: "The passwords don't match." })
type PasswordForm = z.infer<ReturnType<typeof passwordSchema>>

/** Public page behind the one-time link (`/set-password#token=…`). The token is read once, then removed from the URL. */
export function SetPasswordPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const [token] = useState(() => new URLSearchParams(location.hash.replace(/^#/, '')).get('token') ?? '')
  useEffect(() => {
    if (location.hash) navigate(location.pathname, { replace: true })
  }, [location.hash, location.pathname, navigate])

  const check = useQuery({
    queryKey: ['public', 'set-password', token],
    queryFn: () => setPasswordApi.check(token),
    enabled: token !== '',
    retry: false,
    staleTime: Infinity,
  })
  const submit = useMutation({ mutationFn: (password: string) => setPasswordApi.submit(token, password) })
  const problem = check.error ? toProblem(check.error) : submit.error ? toProblem(submit.error) : null

  return (
    <main className="flex min-h-dvh flex-col items-center bg-surface-muted px-4 py-10 text-fg">
      <Wordmark className="mb-8" />
      <motion.section variants={page} initial="initial" animate="animate" aria-labelledby="set-password-title" className="w-full max-w-md rounded-xl border border-line bg-surface p-6 shadow-sm">
        <h1 id="set-password-title" className="flex items-center gap-2 text-lg font-semibold">
          <KeyRound className="size-5 text-accent-strong" aria-hidden="true" /> Set your password
        </h1>
        {token === '' ? (
          <Notice title="This link is incomplete">Open the full link you were given, or ask your administrator for a new one.</Notice>
        ) : submit.isSuccess ? (
          <Notice tone="ok" title="Password set">
            You can now <Link to="/login" className="font-medium underline">sign in</Link> as <strong>{check.data?.username}</strong>.
          </Notice>
        ) : problem && (problem.status === 404 || problem.status === 410 || check.error) ? (
          <Notice title={problem.status === 404 ? 'This link is not valid' : problem.status === 429 ? 'Too many attempts' : 'This link can no longer be used'}>
            {problem.status === 429 ? 'Please wait a minute and try again.' : problem.detail} Ask your administrator for a new link if you need one.
          </Notice>
        ) : check.data ? (
          <PasswordFields username={check.data.username} expiresAt={check.data.expiresAt} pending={submit.isPending} error={problem?.detail} onSubmit={(p) => submit.mutate(p)} />
        ) : (
          <div role="status" aria-label="Checking your link" className="mt-5 space-y-3">
            <Skeleton className="h-5 w-2/3" />
            <Skeleton className="h-11 w-full" />
            <Skeleton className="h-11 w-full" />
          </div>
        )}
      </motion.section>
    </main>
  )
}

function PasswordFields({ username, expiresAt, pending, error, onSubmit }: { username: string; expiresAt: string; pending: boolean; error?: string; onSubmit: (password: string) => void }) {
  const form = useForm<PasswordForm>({ resolver: zodResolver(passwordSchema(username)), defaultValues: { password: '', confirm: '' } })
  return (
    <Form form={form} onSubmit={(v) => onSubmit(v.password)} aria-label="Set password" className="mt-4 space-y-4">
      <p className="text-sm text-fg-muted">
        Account <strong className="text-fg">{username}</strong>. This link works once and expires {formatDateTime(expiresAt)}.
      </p>
      {/* Lets password managers file the new password under the right account. */}
      <input type="text" name="username" autoComplete="username" value={username} readOnly hidden />
      <TextField name="password" label="New password" type="password" autoComplete="new-password" required hint="12–128 characters, not containing your username." />
      <TextField name="confirm" label="Confirm password" type="password" autoComplete="new-password" required />
      {error && <p role="alert" className="text-sm text-red-600">{error}</p>}
      <Button type="submit" className="w-full" loading={pending}>
        Set password
      </Button>
    </Form>
  )
}

function Notice({ title, children, tone = 'bad' }: { title: string; children: ReactNode; tone?: 'ok' | 'bad' }) {
  const Icon = tone === 'ok' ? CheckCircle2 : AlertTriangle
  return (
    <div role={tone === 'ok' ? 'status' : 'alert'} className="mt-5 flex gap-3">
      <Icon className={tone === 'ok' ? 'size-6 shrink-0 text-green-600' : 'size-6 shrink-0 text-amber-600'} aria-hidden="true" />
      <div>
        <p className="font-medium">{title}</p>
        <p className="mt-1 text-sm text-fg-muted">{children}</p>
      </div>
    </div>
  )
}
