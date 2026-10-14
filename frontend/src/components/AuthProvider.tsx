// Who is logged in, shared with every page through React context.
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { authApi } from '../api/auth'
import { ApiError, clearCsrfToken, setUnauthorizedHandler } from '../api/client'
import type { CurrentUser } from '../api/types'
import { Loading } from './ui'

export interface AuthState {
  /** undefined while the first check is running; null when nobody is logged in. */
  user: CurrentUser | null | undefined
  isAdmin: boolean
  /** True when a request found the session had ended (shown on the login page). */
  sessionEnded: boolean
  /** Set when the first check failed for a reason other than "not logged in", e.g. backend down. */
  checkError: unknown
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthState | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>()
  const [sessionEnded, setSessionEnded] = useState(false)
  const [checkError, setCheckError] = useState<unknown>()

  useEffect(() => {
    let current = true
    authApi.me().then(
      (me) => { if (current) setUser(me) },
      (err: unknown) => {
        if (!current) return
        if (!(err instanceof ApiError && err.status === 401)) setCheckError(err)
        setUser(null)
      },
    )
    return () => { current = false }
  }, [])

  useEffect(() => {
    setUnauthorizedHandler(() => {
      clearCsrfToken()
      setUser(null)
      setSessionEnded(true)
    })
    return () => setUnauthorizedHandler(undefined)
  }, [])

  const login = useCallback(async (username: string, password: string) => {
    const me = await authApi.login(username, password)
    clearCsrfToken()
    setUser(me)
    setSessionEnded(false)
    setCheckError(undefined)
  }, [])

  const logout = useCallback(async () => {
    await authApi.logout()
    clearCsrfToken()
    setUser(null)
  }, [])

  const value = useMemo<AuthState>(
    () => ({ user, isAdmin: user?.role === 'ADMIN', sessionEnded, checkError, login, logout }),
    [user, sessionEnded, checkError, login, logout],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const auth = useContext(AuthContext)
  if (!auth) throw new Error('useAuth must be used inside <AuthProvider>')
  return auth
}

/**
 * Shows its children only to a logged-in user; anyone else is sent to the
 * login page, which returns them here afterwards. This only shapes the UI:
 * the backend checks the login and role on every request.
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const location = useLocation()
  if (user === undefined) return <Loading label="Checking login…" />
  if (user === null) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  return children
}
