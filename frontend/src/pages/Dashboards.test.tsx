import { screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { mockFetch } from '../test/fetchMock'
import { renderAs } from '../test/renderAs'
import { DashboardPage } from './DashboardPage'

const patient = (id: number, name: string, updatedAt: string) => ({
  id, fullName: name, dateOfBirth: '1970-01-01', gender: 'UNKNOWN', phone: null, email: null, address: null,
  createdAt: '2026-10-01T00:00:00Z', updatedAt,
})
const followUp = (id: number, timing: string, reason: string) => ({
  id, patientId: 1, patientName: 'Patient One', scheduledAt: '2026-10-20T09:00:00Z', reason, notes: null,
  status: timing === 'COMPLETED' ? 'COMPLETED' : 'SCHEDULED', timing, rescheduleCount: 0, outcomeNotes: null,
  statusChangedAt: null, statusChangedBy: null, createdBy: 'clinician', createdAt: '2026-10-14T00:00:00Z',
})
const rule = (id: number, enabled: boolean, valid: boolean) => ({
  id, ruleId: `R-${id}`, name: `Rule ${id}`, description: null, severity: 'LOW', priority: id, enabled,
  condition: {}, recommendedActions: [], createdAt: '', updatedAt: '', valid, validationErrors: [],
})

describe('clinician dashboard', () => {
  it('shows counts from stored data, due follow-ups and recent patients, and never calls admin or health APIs', async () => {
    mockFetch({
      'GET /api/patients': { body: [patient(1, 'Patient One', '2026-10-10T00:00:00Z'), patient(2, 'Patient Two', '2026-10-12T00:00:00Z')] },
      'GET /api/follow-ups': { body: [followUp(1, 'OVERDUE', 'BP review'), followUp(2, 'UPCOMING', 'Lab recheck'), followUp(3, 'COMPLETED', 'Done review')] },
    })
    renderAs('CLINICIAN', <DashboardPage />)

    expect(await screen.findByText('BP review')).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('clinician')
    expect(within(screen.getByText('Patients').closest('a')!).getByText('2')).toBeInTheDocument()
    expect(within(screen.getByText('Overdue follow-ups').closest('a')!).getByText('1')).toBeInTheDocument()
    expect(within(screen.getByText('Upcoming follow-ups').closest('a')!).getByText('1')).toBeInTheDocument()
    expect(screen.queryByText('Done review')).not.toBeInTheDocument()
    const recent = screen.getByText('Recently updated patients').closest('section')!
    expect(within(recent).getAllByRole('link').map((a) => a.textContent)).toEqual(['Patient Two', 'Patient One'])
    expect(screen.queryByText(/database/i)).not.toBeInTheDocument()
  })

  it('shows empty states when there is no data', async () => {
    mockFetch({ 'GET /api/patients': { body: [] }, 'GET /api/follow-ups': { body: [] } })
    renderAs('CLINICIAN', <DashboardPage />)

    expect(await screen.findByText('No overdue or upcoming follow-ups.')).toBeInTheDocument()
    expect(screen.getByText('No patients yet.')).toBeInTheDocument()
  })
})

describe('admin dashboard', () => {
  const lists = {
    'GET /api/admin/users': { body: [
      { id: 1, username: 'admin', role: 'ADMIN', enabled: true, createdAt: null },
      { id: 2, username: 'clinician', role: 'CLINICIAN', enabled: true, createdAt: null },
      { id: 3, username: 'old', role: 'CLINICIAN', enabled: false, createdAt: null },
    ] },
    'GET /api/rules': { body: [rule(1, true, true), rule(2, false, true), rule(3, true, false)] },
    'GET /api/drugs': { body: [{}, {}] }, 'GET /api/diseases': { body: [{}] }, 'GET /api/lab-tests': { body: [{}, {}, {}] },
  }

  it('shows real health, user, rule and catalogue figures', async () => {
    mockFetch({ ...lists, 'GET /api/health': { body: { status: 'UP', components: { application: 'UP', database: 'UP' }, timestamp: '2026-10-14T10:00:00Z' } } })
    renderAs('ADMIN', <DashboardPage />)

    const health = (await screen.findByText('System health')).closest('section')!
    expect(await within(health).findAllByText('Healthy')).toHaveLength(3)
    const users = (await screen.findByText('Registered')).closest('dl')!
    expect(within(users).getByText('Registered').nextSibling).toHaveTextContent('3')
    expect(within(users).getByText('Deactivated').nextSibling).toHaveTextContent('1')
    expect(within(users).getByText('Administrators').nextSibling).toHaveTextContent('1')
    const rules = (await screen.findByText('Failing validation')).closest('dl')!
    expect(within(rules).getByText('Failing validation').nextSibling).toHaveTextContent('1')
    expect(within(rules).getByText('Inactive').nextSibling).toHaveTextContent('1')
  })

  it('reports a degraded database and an unreachable backend honestly', async () => {
    mockFetch({ ...lists, 'GET /api/health': { status: 503, body: { status: 'DEGRADED', components: { application: 'UP', database: 'DOWN' }, timestamp: '2026-10-14T10:00:00Z' } } })
    const { unmount } = renderAs('ADMIN', <DashboardPage />)
    const health = (await screen.findByText('System health')).closest('section')!
    expect(await within(health).findByText('Degraded')).toBeInTheDocument()
    expect(within(health).getByText('MySQL database').nextSibling).toHaveTextContent('Unavailable')
    unmount()

    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    renderAs('ADMIN', <DashboardPage />)
    const down = (await screen.findByText('System health')).closest('section')!
    expect(await within(down).findAllByText('Unavailable')).toHaveLength(3)
    expect(within(down).queryByText('Healthy')).not.toBeInTheDocument()
  })
})
