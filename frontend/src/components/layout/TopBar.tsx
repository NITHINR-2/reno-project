import { LogOut, Menu } from 'lucide-react'
import { useNavigate } from 'react-router'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { useToast } from '@/components/ui/useToast'
import { ROLE_LABELS } from '@/features/auth/api'
import { useAuthUser, useLogout } from '@/features/auth/useAuth'
import { Breadcrumbs } from './Breadcrumbs'
import { ThemeToggle } from './ThemeToggle'

export function TopBar({ onOpenMenu }: { onOpenMenu: () => void }) {
  const user = useAuthUser()
  const logout = useLogout()
  const navigate = useNavigate()
  const toast = useToast()

  function signOut() {
    logout.mutate(undefined, {
      onSettled: () => {
        navigate('/login', { replace: true })
        toast.info('Signed out')
      },
    })
  }

  return (
    <header className="sticky top-0 z-30 flex h-16 items-center gap-2 border-b border-line bg-surface/95 px-2 backdrop-blur sm:px-4 lg:px-6">
      <button
        type="button"
        onClick={onOpenMenu}
        aria-label="Open navigation"
        className="inline-flex size-11 items-center justify-center rounded-lg text-fg-muted hover:bg-surface-muted lg:hidden"
      >
        <Menu className="size-5" aria-hidden="true" />
      </button>
      <div className="min-w-0 flex-1">
        <Breadcrumbs />
      </div>
      <ThemeToggle />
      <div className="hidden min-w-0 text-right sm:block">
        <p className="truncate text-sm font-medium text-fg">{user.fullName}</p>
        <Badge tone="accent" className="mt-0.5">
          {ROLE_LABELS[user.role]}
        </Badge>
      </div>
      <Button
        variant="ghost"
        onClick={signOut}
        loading={logout.isPending}
        aria-label="Sign out"
        icon={<LogOut className="size-5" aria-hidden="true" />}
        className="px-3"
      >
        <span className="hidden md:inline">Sign out</span>
      </Button>
    </header>
  )
}
