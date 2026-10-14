import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { describe, expect, it } from 'vitest'
import { patientsApi } from '../api/patients'
import { useAsync } from '../hooks/useAsync'
import { LoginPage } from '../pages/LoginPage'
import { mockFetch, TEST_CSRF_TOKEN } from '../test/fetchMock'
import { AuthProvider, RequireAuth } from './AuthProvider'
import { Layout } from './Layout'

const health = { body: { status: 'UP', components: { application: 'UP', database: 'UP' }, timestamp: '2026-10-14T00:00:00Z' } }
const notLoggedIn = { status: 401, body: { status: 401, detail: 'You are not logged in or your session has ended' } }
const clinician = { body: { username: 'clinician', role: 'CLINICIAN' } }

function PatientCount() {
  const patients = useAsync(() => patientsApi.list(), [])
  return <p>{patients.data ? `${patients.data.length} patients` : 'Loading'}</p>
}

function renderApp(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <Routes>
          <Route path="login" element={<LoginPage />} />
          <Route element={<RequireAuth><Layout /></RequireAuth>}>
            <Route path="patients" element={<PatientCount />} />
          </Route>
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}

async function logIn(username: string, password: string) {
  await userEvent.type(await screen.findByLabelText(/Username/), username)
  await userEvent.type(screen.getByLabelText(/Password/), password)
  await userEvent.click(screen.getByRole('button', { name: 'Log in' }))
}

describe('login', () => {
  it('sends a logged-out user to the login page and back to the page they wanted after login', async () => {
    const fetchMock = mockFetch({
      'GET /api/auth/me': notLoggedIn,
      'POST /api/auth/login': clinician,
      'GET /api/patients': { body: [] },
      'GET /api/health': health,
    })
    renderApp('/patients')

    expect(await screen.findByRole('button', { name: 'Log in' })).toBeInTheDocument()
    expect(screen.queryByText('Your session has ended. Please log in again.')).not.toBeInTheDocument()
    await logIn('clinician', 'demo-password')

    expect(await screen.findByText('0 patients')).toBeInTheDocument()
    expect(screen.getByText('clinician')).toBeInTheDocument()
    expect(screen.getByText('Clinician')).toBeInTheDocument()
    const [, init] = fetchMock.mock.calls.find(([url, i]) => url === '/api/auth/login' && i?.method === 'POST')!
    expect(JSON.parse(String(init?.body))).toEqual({ username: 'clinician', password: 'demo-password' })
    expect((init?.headers as Record<string, string>)['X-CSRF-TOKEN']).toBe(TEST_CSRF_TOKEN)
  })

  it('shows the protected page straight away when the session is still valid', async () => {
    mockFetch({ 'GET /api/auth/me': clinician, 'GET /api/patients': { body: [] }, 'GET /api/health': health })
    renderApp('/patients')

    expect(await screen.findByText('0 patients')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Log in' })).not.toBeInTheDocument()
  })

  it('shows the backend message for wrong credentials and clears the password', async () => {
    mockFetch({
      'GET /api/auth/me': notLoggedIn,
      'POST /api/auth/login': { status: 401, body: { status: 401, detail: 'Invalid username or password' } },
    })
    renderApp('/login')

    await logIn('clinician', 'wrong-password')

    expect(await screen.findByText('Invalid username or password')).toBeInTheDocument()
    expect(screen.getByLabelText(/Password/)).toHaveValue('')
    expect(screen.getByLabelText(/Username/)).toHaveValue('clinician')
  })

  it('returns to the login page with a message when the session ends', async () => {
    mockFetch({ 'GET /api/auth/me': clinician, 'GET /api/patients': notLoggedIn, 'GET /api/health': health })
    renderApp('/patients')

    expect(await screen.findByText('Your session has ended. Please log in again.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Log in' })).toBeInTheDocument()
  })

  it('logs out with the CSRF token and returns to the login page', async () => {
    const fetchMock = mockFetch({
      'GET /api/auth/me': clinician,
      'GET /api/patients': { body: [] },
      'GET /api/health': health,
      'POST /api/auth/logout': { status: 204 },
    })
    renderApp('/patients')
    await screen.findByText('0 patients')

    await userEvent.click(screen.getByRole('button', { name: 'Account menu for clinician' }))
    await userEvent.click(screen.getByRole('menuitem', { name: 'Log out' }))

    expect(await screen.findByRole('button', { name: 'Log in' })).toBeInTheDocument()
    const [, init] = fetchMock.mock.calls.find(([url]) => url === '/api/auth/logout')!
    expect((init?.headers as Record<string, string>)['X-CSRF-TOKEN']).toBe(TEST_CSRF_TOKEN)
  })

  it('explains when the backend cannot be reached', async () => {
    mockFetch({ 'GET /api/auth/me': { status: 502 } })
    renderApp('/patients')

    expect(await screen.findByText(/Cannot reach the backend/)).toBeInTheDocument()
  })
})
