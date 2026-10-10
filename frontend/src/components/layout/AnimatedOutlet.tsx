import { AnimatePresence, motion } from 'motion/react'
import { useEffect } from 'react'
import { useLocation, useOutlet } from 'react-router'
import { page } from '@/lib/motion'
import { ErrorBoundary } from './ErrorBoundary'

/** Route content with a fade + 8px rise on navigation, and scroll-to-top. */
export function AnimatedOutlet() {
  const location = useLocation()
  const outlet = useOutlet()

  useEffect(() => {
    window.scrollTo?.({ top: 0 })
  }, [location.pathname])

  return (
    <AnimatePresence mode="popLayout" initial={false}>
      <motion.div key={location.pathname} variants={page} initial="initial" animate="animate" exit="exit">
        <ErrorBoundary>{outlet}</ErrorBoundary>
      </motion.div>
    </AnimatePresence>
  )
}
