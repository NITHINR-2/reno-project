import { zodResolver } from '@hookform/resolvers/zod'
import { AlertTriangle, LockKeyhole } from 'lucide-react'
import { motion } from 'motion/react'
import { useForm } from 'react-hook-form'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { z } from 'zod'
import { Form } from '@/components/forms/Form'
import { TextField } from '@/components/forms/TextField'
import { Wordmark } from '@/components/layout/Wordmark'
import { ThemeToggle } from '@/components/layout/ThemeToggle'
import { Button } from '@/components/ui/Button'
import { page } from '@/lib/motion'
import { toProblem } from '@/lib/problem'
import { useCurrentUser, useLogin } from './useAuth'

const loginSchema = z.object({
  username: z.string().trim().min(1, 'Enter your username.').max(255),
  password: z.string().min(1, 'Enter your password.').max(200),
})

type LoginValues = z.infer<typeof loginSchema>

export function LoginPage() {
  const { data: user } = useCurrentUser()
  const login = useLogin()
  const navigate = useNavigate()
  const from = (useLocation().state as { from?: string } | null)?.from ?? '/'
  const form = useForm<LoginValues>({ resolver: zodResolver(loginSchema), defaultValues: { username: '', password: '' } })

  if (user && !login.isPending) return <Navigate to={from} replace />

  const failure = login.error ? toProblem(login.error) : null
  const failureText = failure?.status === 401 ? 'Invalid username or password.' : failure?.detail

  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      <aside className="hidden flex-col justify-between bg-charcoal p-10 text-white lg:flex">
        <Wordmark className="text-lg" />
        <div>
          <p className="text-3xl font-semibold leading-tight">
            Fleet requests, approvals and dispatch
            <span className="block text-accent">in one place.</span>
          </p>
          <p className="mt-3 max-w-md text-sm text-gray-300">
            For Renault Group HQ teams: marketing, executive approval, finance, production and dispatch.
          </p>
        </div>
        <p className="text-xs text-gray-400">Authorised users only. Activity is audited.</p>
      </aside>

      <main className="relative flex items-center justify-center px-4 py-10">
        <div className="absolute right-3 top-3">
          <ThemeToggle />
        </div>
        <motion.div variants={page} initial="initial" animate="animate" className="w-full max-w-sm">
          <Wordmark className="mb-8 text-fg lg:hidden" />
          <h1 className="text-2xl font-bold tracking-tight text-fg">Sign in</h1>
          <p className="mt-1 text-sm text-fg-muted">Use your Fleet Portal account.</p>

          {failureText && (
            <div role="alert" className="mt-6 flex items-start gap-2 rounded-lg bg-red-50 p-3 text-sm text-red-800 dark:bg-red-950 dark:text-red-200">
              <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
              {failureText}
            </div>
          )}

          <Form
            form={form}
            aria-label="Sign in"
            className="mt-6 space-y-4"
            onSubmit={(values) =>
              login.mutate(values, { onSuccess: () => navigate(from, { replace: true }) })
            }
          >
            <TextField name="username" label="Username" autoComplete="username" autoCapitalize="none" spellCheck={false} required />
            <TextField name="password" label="Password" type="password" autoComplete="current-password" required />
            <Button type="submit" className="w-full" loading={login.isPending} icon={<LockKeyhole className="size-4" aria-hidden="true" />}>
              Sign in
            </Button>
          </Form>
        </motion.div>
      </main>
    </div>
  )
}
