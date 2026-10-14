import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import type { Role } from '../api/types'
import { mockFetch } from '../test/fetchMock'
import { AuthContext, type AuthState } from './AuthProvider'
import { Layout } from './Layout'
import { RequireRole } from './RequireRole'

const health = { body: { status: 'UP', components: { application: 'UP', database: 'UP' }, timestamp: '2026-10-14T00:00:00Z' } }

function renderAt(role: Role, path: string) {
  const auth: AuthState = {
    user: { username: role === 'ADMIN' ? 'admin' : 'clinician', role }, isAdmin: role === 'ADMIN',
    sessionEnded: false, checkError: undefined, login: vi.fn(), logout: vi.fn(),
  }
  return render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route element={<Layout />}>
            <Route path="/" element={<p>Home</p>} />
            <Route path="/admin/users" element={<RequireRole role="ADMIN"><p>User admin content</p></RequireRole>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  )
}

describe('role workspaces', () => {
  it('gives clinicians patient-care navigation without admin items or system health', () => {
    const fetchMock = mockFetch({})
    renderAt('CLINICIAN', '/')

    const nav = screen.getByRole('navigation', { name: 'Main' })
    for (const item of ['Dashboard', 'Patients', 'Clinical decisions', 'Follow-ups', 'Decision rules']) {
      expect(nav).toHaveTextContent(item)
    }
    expect(nav).not.toHaveTextContent('User management')
    expect(nav).not.toHaveTextContent('Clinical rules')
    expect(screen.queryByText(/System healthy|Checking…/)).not.toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('gives administrators the administration navigation and the health indicator', async () => {
    mockFetch({ 'GET /api/health': health })
    renderAt('ADMIN', '/')

    const nav = screen.getByRole('navigation', { name: 'Main' })
    for (const item of ['Admin dashboard', 'User management', 'Clinical rules', 'Drugs', 'Patients']) {
      expect(nav).toHaveTextContent(item)
    }
    expect(await screen.findByText('System healthy')).toBeInTheDocument()
  })

  it('shows access denied when a clinician opens an admin page directly', () => {
    mockFetch({})
    renderAt('CLINICIAN', '/admin/users')

    expect(screen.getByText("You don't have access to this page")).toBeInTheDocument()
    expect(screen.queryByText('User admin content')).not.toBeInTheDocument()
  })

  it('lets an administrator open the admin page', () => {
    mockFetch({ 'GET /api/health': health })
    renderAt('ADMIN', '/admin/users')

    expect(screen.getByText('User admin content')).toBeInTheDocument()
  })
})
