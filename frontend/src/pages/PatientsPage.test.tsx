import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Role } from '../api/types'
import { mockFetch } from '../test/fetchMock'
import { renderAs } from '../test/renderAs'
import { PatientsPage } from './PatientsPage'

const patient = {
  id: 1, fullName: 'Daniel Okafor', dateOfBirth: '1958-04-12', gender: 'MALE', phone: '+1 555 0101',
  email: 'daniel.okafor@example.com', address: null, createdAt: '2026-10-13T00:00:00Z', updatedAt: '2026-10-13T00:00:00Z',
}

function renderPage(role: Role = 'CLINICIAN') {
  return renderAs(role, <PatientsPage />)
}

describe('PatientsPage', () => {
  it('shows patients loaded from the backend', async () => {
    mockFetch({ 'GET /api/patients': { body: [patient] } })
    renderPage()

    expect(screen.getByText('Loading patients…')).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: 'Daniel Okafor' })).toHaveAttribute('href', '/patients/1')
    expect(screen.getByText('daniel.okafor@example.com')).toBeInTheDocument()
  })

  it('shows an empty state when there are no patients', async () => {
    mockFetch({ 'GET /api/patients': { body: [] } })
    renderPage()

    expect(await screen.findByText('No patients yet. Add the first one.')).toBeInTheDocument()
  })

  it('searches using the backend search parameter', async () => {
    const fetchMock = mockFetch({
      'GET /api/patients': { body: [patient] },
      'GET /api/patients?search=jor': { body: [] },
    })
    renderPage()
    await screen.findByText('Daniel Okafor')

    await userEvent.type(screen.getByLabelText('Search by name'), 'jor')
    await userEvent.click(screen.getByRole('button', { name: 'Search' }))

    expect(await screen.findByText('No patients match "jor".')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/patients?search=jor', expect.anything())
  })

  it('shows an error with a retry button when the backend fails', async () => {
    let calls = 0
    mockFetch({ 'GET /api/patients': () => (++calls === 1 ? { status: 500, body: { status: 500, detail: 'An unexpected error occurred' } } : { body: [patient] }) })
    renderPage()

    expect(await screen.findByText('An unexpected error occurred')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByText('Daniel Okafor')).toBeInTheDocument()
  })

  it('hides Delete from a clinician', async () => {
    mockFetch({ 'GET /api/patients': { body: [patient] } })
    renderPage('CLINICIAN')

    await screen.findByText('Daniel Okafor')
    expect(screen.getByRole('button', { name: 'Edit' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument()
  })

  it('shows Delete to an admin', async () => {
    mockFetch({ 'GET /api/patients': { body: [patient] } })
    renderPage('ADMIN')

    await screen.findByText('Daniel Okafor')
    expect(screen.getByRole('button', { name: 'Delete' })).toBeInTheDocument()
  })
})
