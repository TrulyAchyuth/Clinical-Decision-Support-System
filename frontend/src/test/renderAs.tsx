import { render } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router'
import { vi } from 'vitest'
import type { Role } from '../api/types'
import { AuthContext, type AuthState } from '../components/AuthProvider'

/** Renders a page as a logged-in user with the given role, without calling the auth endpoints. */
export function renderAs(role: Role, ui: ReactNode) {
  const auth: AuthState = {
    user: { username: role === 'ADMIN' ? 'admin' : 'clinician', role },
    isAdmin: role === 'ADMIN',
    sessionEnded: false,
    checkError: undefined,
    login: vi.fn(),
    logout: vi.fn(),
  }
  return render(<AuthContext.Provider value={auth}><MemoryRouter>{ui}</MemoryRouter></AuthContext.Provider>)
}
