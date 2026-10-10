import { Monitor, Moon, Sun } from 'lucide-react'
import { useTheme, type ThemePreference } from '@/lib/themeContext'

const NEXT: Record<ThemePreference, ThemePreference> = { system: 'light', light: 'dark', dark: 'system' }
const ICON = { system: Monitor, light: Sun, dark: Moon } as const

export function ThemeToggle() {
  const { preference, setPreference } = useTheme()
  const Icon = ICON[preference]
  return (
    <button
      type="button"
      onClick={() => setPreference(NEXT[preference])}
      aria-label={`Theme: ${preference}. Switch to ${NEXT[preference]}`}
      title={`Theme: ${preference}`}
      className="inline-flex size-11 items-center justify-center rounded-lg text-fg-muted transition-colors duration-150 hover:bg-surface-muted hover:text-fg focus-visible:outline-2 focus-visible:outline-accent"
    >
      <Icon className="size-5" aria-hidden="true" />
    </button>
  )
}
