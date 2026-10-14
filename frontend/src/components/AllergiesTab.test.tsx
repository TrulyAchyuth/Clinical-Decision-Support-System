import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { mockFetch } from '../test/fetchMock'
import { AllergiesTab } from './AllergiesTab'

const drugs = [{ id: 1, name: 'Amoxicillin', dosage: '500 mg', category: 'Antibiotic (penicillin)', notes: null }]

const allergy = {
  id: 7, patientId: 1, category: 'DRUG', drugId: 1, allergen: 'Amoxicillin', reaction: 'Hives', severity: 'SEVERE', status: 'ACTIVE',
  recordedDate: '2026-10-01', notes: null, resolutionReason: null, resolvedAt: null, resolvedBy: null, recordedBy: 'clinician', createdAt: '2026-10-01T00:00:00Z',
}

const notAssessed = {
  patientId: 1, assessment: 'NOT_ASSESSED', noKnownAllergiesConfirmedAt: null, noKnownAllergiesConfirmedBy: null, allergies: [],
  assessmentExplanation: "Allergy history not assessed: no active allergy is recorded and 'no known allergies' has not been confirmed. This does not mean the patient has no allergies.",
}

describe('AllergiesTab', () => {
  it('shows an unassessed history as not assessed, never as "no known allergies"', async () => {
    mockFetch({ 'GET /api/patients/1/allergies': { body: notAssessed }, 'GET /api/drugs': { body: drugs } })
    render(<AllergiesTab patientId={1} />)

    expect(await screen.findByText('Allergy history not assessed')).toBeInTheDocument()
    expect(screen.getByText(/does not mean the patient has no allergies/)).toBeInTheDocument()
    expect(screen.queryByText('No known allergies')).not.toBeInTheDocument()
  })

  it('records "no known allergies" only after confirmation', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    let confirmed = false
    mockFetch({
      'GET /api/patients/1/allergies': () => ({ body: confirmed
        ? { ...notAssessed, assessment: 'NO_KNOWN_ALLERGIES', noKnownAllergiesConfirmedAt: '2026-10-14T10:00:00Z', noKnownAllergiesConfirmedBy: 'clinician' }
        : notAssessed }),
      'GET /api/drugs': { body: drugs },
      'POST /api/patients/1/allergies/no-known-allergies': () => { confirmed = true; return { body: notAssessed } },
    })
    render(<AllergiesTab patientId={1} />)
    await userEvent.click(await screen.findByRole('button', { name: 'Confirm no known allergies' }))

    expect(await screen.findByText('No known allergies')).toBeInTheDocument()
    expect(screen.getByText(/Confirmed by clinician/)).toBeInTheDocument()
  })

  it('records a drug allergy linked to the knowledge base and shows the warning banner', async () => {
    let saved = false
    const fetchMock = mockFetch({
      'GET /api/patients/1/allergies': () => ({ body: saved
        ? { ...notAssessed, assessment: 'ALLERGIES_RECORDED', assessmentExplanation: '1 active allergy record(s).', allergies: [allergy] }
        : notAssessed }),
      'GET /api/drugs': { body: drugs },
      'POST /api/patients/1/allergies': () => { saved = true; return { status: 201, body: allergy } },
    })
    render(<AllergiesTab patientId={1} />)
    await userEvent.selectOptions(await screen.findByLabelText(/^Drug/), '1')
    await userEvent.type(screen.getByLabelText('Reaction'), 'Hives')
    await userEvent.selectOptions(screen.getByLabelText(/^Severity/), 'SEVERE')
    await userEvent.click(screen.getByRole('button', { name: 'Save allergy' }))

    expect(await screen.findByText('Allergies recorded')).toBeInTheDocument()
    expect(screen.getByText('Amoxicillin (severe)')).toBeInTheDocument()
    const body = JSON.parse(String(fetchMock.mock.calls.find(([url, init]) => url === '/api/patients/1/allergies' && init?.method === 'POST')?.[1]?.body))
    expect(body).toMatchObject({ category: 'DRUG', drugId: 1, reaction: 'Hives', severity: 'SEVERE' })
    expect(body.allergen).toBeUndefined()
  })

  it('shows the backend validation error for an invalid allergy', async () => {
    mockFetch({
      'GET /api/patients/1/allergies': { body: notAssessed }, 'GET /api/drugs': { body: drugs },
      'POST /api/patients/1/allergies': { status: 400, body: { status: 400, detail: 'Choose the drug from the knowledge base for a drug allergy' } },
    })
    render(<AllergiesTab patientId={1} />)
    await userEvent.click(await screen.findByRole('button', { name: 'Save allergy' }))

    expect(await screen.findByText('Choose the drug from the knowledge base for a drug allergy')).toBeInTheDocument()
  })
})
