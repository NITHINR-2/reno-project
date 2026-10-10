import { createContext, useContext } from 'react'

export type ThemePreference = 'system' | 'light' | 'dark'

export const ThemeContext = createContext<{ preference: ThemePreference; setPreference: (p: ThemePreference) => void } | null>(null)

export function useTheme() {
  const context = useContext(ThemeContext)
  if (!context) throw new Error('useTheme must be used inside ThemeProvider')
  return context
}
