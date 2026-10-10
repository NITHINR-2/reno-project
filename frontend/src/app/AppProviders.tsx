import { QueryClientProvider, type QueryClient } from '@tanstack/react-query'
import { MotionConfig } from 'motion/react'
import { useEffect, type ReactNode } from 'react'
import { ToastProvider } from '@/components/ui/Toast'
import { useToast } from '@/components/ui/useToast'
import { endSession } from '@/features/auth/useAuth'
import { setSessionExpiredHandler } from '@/lib/api'
import { ThemeProvider } from '@/lib/theme'

/** When a refresh fails mid-session, drop cached data; the route guard then sends the user to /login. */
function SessionWatcher({ queryClient }: { queryClient: QueryClient }) {
  const toast = useToast()
  useEffect(() => {
    setSessionExpiredHandler(() => {
      endSession(queryClient)
      toast.info('Your session has ended', 'Please sign in again.')
    })
    return () => setSessionExpiredHandler(null)
  }, [queryClient, toast])
  return null
}

export function AppProviders({ queryClient, children }: { queryClient: QueryClient; children: ReactNode }) {
  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <MotionConfig reducedMotion="user">
          <ToastProvider>
            <SessionWatcher queryClient={queryClient} />
            {children}
          </ToastProvider>
        </MotionConfig>
      </ThemeProvider>
    </QueryClientProvider>
  )
}
