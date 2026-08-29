import { createContext, useCallback, useContext, useMemo, useSyncExternalStore } from 'react'
import type { ReactNode } from 'react'
import { getSession, setSession, subscribeToSession } from '@/api/client'
import type { Session } from '@/api/client'
import { authApi } from '@/api/endpoints'
import type { Role } from '@/api/types'

/**
 * Auth state is owned by the API client (it needs the token for every request anyway) and
 * merely *observed* here through useSyncExternalStore. That avoids the classic bug where a
 * React copy of the session and the copy the fetch layer uses drift apart after a refresh.
 */

export const MANAGER_ROLES: Role[] = [
  'SHIFT_MANAGER',
  'LOCATION_MANAGER',
  'ORG_ADMIN',
  'SUPER_ADMIN',
]

interface AuthValue {
  session: Session | null
  user: Session['user'] | null
  isAuthenticated: boolean
  hasRole: (...roles: Role[]) => boolean
  isManager: boolean
  login: (email: string, password: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const session = useSyncExternalStore(subscribeToSession, getSession, () => null)

  const login = useCallback(async (email: string, password: string) => {
    const response = await authApi.login(email, password)
    setSession({
      accessToken: response.accessToken,
      refreshToken: response.refreshToken,
      user: response.user,
    })
  }, [])

  const logout = useCallback(() => setSession(null), [])

  const value = useMemo<AuthValue>(() => {
    const roles = session?.user.roles ?? []
    const hasRole = (...wanted: Role[]) => wanted.some((r) => roles.includes(r))
    return {
      session,
      user: session?.user ?? null,
      isAuthenticated: Boolean(session),
      hasRole,
      isManager: hasRole(...MANAGER_ROLES),
      login,
      logout,
    }
  }, [session, login, logout])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}
