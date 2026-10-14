import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { mockFetch } from '../test/fetchMock'
import { renderAs } from '../test/renderAs'
import { UsersPage } from './UsersPage'

const users = [
  { id: 1, username: 'admin', role: 'ADMIN', enabled: true, createdAt: '2026-10-14T00:00:00Z' },
  { id: 2, username: 'clinician', role: 'CLINICIAN', enabled: true, createdAt: '2026-10-14T00:00:00Z' },
  { id: 3, username: 'retired', role: 'CLINICIAN', enabled: false, createdAt: '2026-10-14T00:00:00Z' },
]

describe('UsersPage', () => {
  it('lists accounts without passwords and offers no actions on your own account', async () => {
    mockFetch({ 'GET /api/admin/users': { body: users } })
    renderAs('ADMIN', <UsersPage />)

    const me = (await screen.findByText('You')).closest('tr')!
    expect(within(me).queryByRole('button')).not.toBeInTheDocument()
    expect(within(me).queryByRole('combobox')).not.toBeInTheDocument()
    const clinician = screen.getByText('clinician').closest('tr')!
    expect(within(clinician).getByRole('button', { name: 'Deactivate' })).toBeInTheDocument()
    expect(within(screen.getByText('retired').closest('tr')!).getByText('Deactivated')).toBeInTheDocument()
    expect(document.body.textContent).not.toMatch(/\$2[aby]\$/)
  })

  it('filters by search text, role and status', async () => {
    mockFetch({ 'GET /api/admin/users': { body: users } })
    renderAs('ADMIN', <UsersPage />)
    await screen.findByText('retired')

    await userEvent.selectOptions(screen.getByLabelText('Status'), 'inactive')
    expect(screen.queryByText('clinician')).not.toBeInTheDocument()
    expect(screen.getByText('retired')).toBeInTheDocument()
    await userEvent.selectOptions(screen.getByLabelText('Status'), 'all')
    await userEvent.selectOptions(screen.getByLabelText('Role', { selector: '#u-role-filter' }), 'ADMIN')
    expect(screen.queryByText('retired')).not.toBeInTheDocument()
  })

  it('creates a clinician account with the chosen role', async () => {
    const fetchMock = mockFetch({
      'GET /api/admin/users': { body: users },
      'POST /api/admin/users': { status: 201, body: { id: 4, username: 'newdoc', role: 'CLINICIAN', enabled: true, createdAt: null } },
    })
    renderAs('ADMIN', <UsersPage />)
    await userEvent.type(await screen.findByLabelText(/^Username/), 'newdoc')
    await userEvent.type(screen.getByLabelText(/Initial password/), 'a-long-password')
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByText('Account "newdoc" created as clinician.')).toBeInTheDocument()
    const body = JSON.parse(String(fetchMock.mock.calls.find(([url, init]) => url === '/api/admin/users' && init?.method === 'POST')?.[1]?.body))
    expect(body).toEqual({ username: 'newdoc', password: 'a-long-password', role: 'CLINICIAN' })
  })

  it('deactivates after confirmation and shows the backend refusal for unsafe changes', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    mockFetch({
      'GET /api/admin/users': { body: users },
      'POST /api/admin/users/2/deactivate': { status: 409, body: { status: 409, detail: 'At least one active administrator is required' } },
    })
    renderAs('ADMIN', <UsersPage />)
    await userEvent.click(within((await screen.findByText('clinician')).closest('tr')!).getByRole('button', { name: 'Deactivate' }))

    expect(await screen.findByText('At least one active administrator is required')).toBeInTheDocument()
  })
})
