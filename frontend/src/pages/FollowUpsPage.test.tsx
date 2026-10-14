import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { mockFetch } from '../test/fetchMock'
import { FollowUpsPage } from './FollowUpsPage'

const followUp = (id: number, timing: string, status: string, reason: string) => ({
  id, patientId: 1, patientName: 'Daniel Okafor', scheduledAt: '2026-10-20T09:00:00Z', reason, notes: null, status, timing,
  rescheduleCount: 0, outcomeNotes: null, statusChangedAt: null, statusChangedBy: null, createdBy: 'clinician', createdAt: '2026-10-14T00:00:00Z',
})

function renderPage() {
  return render(<MemoryRouter><FollowUpsPage /></MemoryRouter>)
}

describe('FollowUpsPage', () => {
  it('groups follow-ups by the timing the backend calculated; finished ones are never overdue', async () => {
    mockFetch({ 'GET /api/follow-ups': { body: [
      followUp(1, 'OVERDUE', 'SCHEDULED', 'BP review'),
      followUp(2, 'UPCOMING', 'SCHEDULED', 'Glucose recheck'),
      followUp(3, 'COMPLETED', 'COMPLETED', 'Past review'),
      followUp(4, 'CANCELLED', 'CANCELLED', 'Cancelled review'),
    ] } })
    renderPage()

    const overdue = (await screen.findByText('Overdue (1)')).closest('section')!
    expect(within(overdue).getByText('BP review')).toBeInTheDocument()
    expect(within(overdue).queryByText('Past review')).not.toBeInTheDocument()
    expect(within(screen.getByText('Upcoming (1)').closest('section')!).getByText('Glucose recheck')).toBeInTheDocument()
    const completed = screen.getByText('Completed (1)').closest('section')!
    expect(within(completed).getByText('Past review')).toBeInTheDocument()
    expect(within(completed).queryByRole('button', { name: 'Cancel' })).not.toBeInTheDocument()
    expect(within(overdue).getByRole('link', { name: 'Daniel Okafor' })).toHaveAttribute('href', '/patients/1?tab=follow-ups')
  })

  it('cancels a follow-up only with a reason', async () => {
    let cancelled = false
    const fetchMock = mockFetch({
      'GET /api/follow-ups': () => ({ body: [cancelled ? followUp(1, 'CANCELLED', 'CANCELLED', 'BP review') : followUp(1, 'OVERDUE', 'SCHEDULED', 'BP review')] }),
      'POST /api/patients/1/follow-ups/1/cancel': () => { cancelled = true; return { body: followUp(1, 'CANCELLED', 'CANCELLED', 'BP review') } },
    })
    renderPage()
    await userEvent.click(await screen.findByRole('button', { name: 'Cancel' }))

    const confirm = screen.getByRole('button', { name: 'Confirm cancel' })
    expect(confirm).toBeDisabled()
    await userEvent.type(screen.getByLabelText(/Reason for cancelling/), 'Patient moved away')
    await userEvent.click(confirm)

    expect(await screen.findByText('Cancelled (1)')).toBeInTheDocument()
    const call = fetchMock.mock.calls.find(([url]) => url === '/api/patients/1/follow-ups/1/cancel')
    expect(JSON.parse(String(call?.[1]?.body))).toEqual({ notes: 'Patient moved away' })
  })

  it('shows empty states when there are no follow-ups', async () => {
    mockFetch({ 'GET /api/follow-ups': { body: [] } })
    renderPage()

    expect(await screen.findByText('No overdue follow-ups.')).toBeInTheDocument()
  })
})
