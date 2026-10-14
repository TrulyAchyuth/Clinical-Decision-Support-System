import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { mockFetch } from '../test/fetchMock'
import { MedicationsTab } from './MedicationsTab'

const drugs = [
  { id: 4, name: 'Warfarin', dosage: '5 mg', category: 'Anticoagulant', notes: null },
  { id: 5, name: 'Ibuprofen', dosage: '400 mg', category: 'NSAID', notes: null },
]

const medication = (id: number, status: string, drugName: string) => ({
  id, patientId: 1, drugId: 4, drugName, drugStrength: '5 mg', drugCategory: 'Anticoagulant', dose: 5, doseUnit: 'mg',
  route: 'ORAL', frequency: 'once daily', startDate: '2026-10-01', endDate: status === 'ACTIVE' ? null : '2026-10-10',
  status, notes: null, statusReason: status === 'DISCONTINUED' ? 'Side effects' : null, statusChangedAt: null, statusChangedBy: null,
  safetyWarnings: [], safetyOverrideReason: null, recordedBy: 'clinician', createdAt: '2026-10-01T00:00:00Z', updatedAt: '2026-10-01T00:00:00Z',
})

const interactionCheck = {
  patientId: 1, drugId: 5, drugName: 'Ibuprofen 400 mg', overallStatus: 'WARNINGS', requiresAcknowledgement: true,
  checkedAt: '2026-10-14T10:00:00Z', disclaimer: 'Educational prototype. Finding nothing does not mean the medication is safe.',
  findings: [
    { code: 'INTERACTION-10-30-aa11bb22', checkType: 'INTERACTION', resultStatus: 'MATCH_FOUND', priority: 'HIGH',
      title: 'Interaction listed: Ibuprofen 400 mg with Warfarin 5 mg', severity: 'MAJOR', medications: ['Ibuprofen 400 mg', 'Warfarin 5 mg'], allergens: [],
      explanation: 'ILLUSTRATIVE SAMPLE: bleeding-risk demonstration.', mechanism: null, recommendedAction: null,
      source: 'Illustrative teaching sample', dataClassification: 'ILLUSTRATIVE_SAMPLE', sourceVersion: null, sourceReviewedOn: null,
      reviewStatus: 'NOT_CLINICALLY_REVIEWED', requiresReview: true },
    { code: 'ALLERGY-NONE-FOUND', checkType: 'ALLERGY', resultStatus: 'NO_MATCH_FOUND', priority: 'LOW', title: 'No recorded allergy matches',
      severity: null, medications: ['Ibuprofen 400 mg'], allergens: [], explanation: 'No active allergy matches.', mechanism: null, recommendedAction: null,
      source: 'Patient allergy record', dataClassification: 'PATIENT_RECORD', sourceVersion: null, sourceReviewedOn: null, reviewStatus: null, requiresReview: false },
  ],
}

const allergyFinding = {
  code: 'ALLERGY-21-cc33dd44', checkType: 'ALLERGY', resultStatus: 'MATCH_FOUND', priority: 'HIGH', title: 'Recorded drug allergy matches Ibuprofen 400 mg',
  severity: 'SEVERE', medications: ['Ibuprofen 400 mg'], allergens: ['Ibuprofen'], explanation: 'The patient has an active recorded allergy to Ibuprofen.',
  mechanism: null, recommendedAction: 'Confirm the allergy history and recorded reaction with the patient before deciding whether to proceed.',
  source: 'Patient allergy record', dataClassification: 'PATIENT_RECORD', sourceVersion: null, sourceReviewedOn: null, reviewStatus: null, requiresReview: true,
}

async function fillForm() {
  await userEvent.selectOptions(await screen.findByLabelText(/Drug \(knowledge base\)/), '5')
  await userEvent.type(screen.getByLabelText(/^Dose \*$/), '400')
  await userEvent.type(screen.getByLabelText(/Dose unit/), 'mg')
  await userEvent.type(screen.getByLabelText(/Frequency/), 'three times daily')
}

describe('MedicationsTab', () => {
  it('shows active medications separately from the history', async () => {
    mockFetch({
      'GET /api/patients/1/medications': { body: [medication(1, 'ACTIVE', 'Warfarin'), medication(2, 'DISCONTINUED', 'Aspirin')] },
      'GET /api/drugs': { body: drugs },
    })
    render(<MedicationsTab patientId={1} />)

    const active = (await screen.findByText('Active medications')).closest('section')!
    const history = screen.getByText('Medication history').closest('section')!
    expect(within(active).getByText('Warfarin')).toBeInTheDocument()
    expect(within(active).queryByText('Aspirin')).not.toBeInTheDocument()
    expect(within(history).getByText('Aspirin')).toBeInTheDocument()
    expect(within(history).getByText('Side effects')).toBeInTheDocument()
  })

  it('requires the safety check, acknowledgement and a reason before saving, and sends them', async () => {
    const fetchMock = mockFetch({
      'GET /api/patients/1/medications': { body: [medication(1, 'ACTIVE', 'Warfarin')] },
      'GET /api/drugs': { body: drugs },
      'POST /api/patients/1/medications/safety-check': { body: interactionCheck },
      'POST /api/patients/1/medications': { status: 201, body: { ...medication(3, 'ACTIVE', 'Ibuprofen'), drugStrength: '400 mg' } },
    })
    render(<MedicationsTab patientId={1} />)
    await fillForm()

    const save = screen.getByRole('button', { name: '2. Save medication' })
    expect(save).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: '1. Run safety check' }))

    expect(await screen.findByText(/ILLUSTRATIVE SAMPLE DATA/)).toBeInTheDocument()
    expect(screen.getByText('Warnings found — clinician review required')).toBeInTheDocument()
    expect(save).toBeDisabled()
    await userEvent.click(screen.getByLabelText('I have reviewed this warning'))
    expect(save).toBeDisabled()
    await userEvent.type(screen.getByLabelText(/Reason for continuing/), 'Short course, monitored')
    expect(save).toBeEnabled()
    await userEvent.click(save)

    expect(await screen.findByText(/added to the active medications/)).toBeInTheDocument()
    const body = JSON.parse(String(fetchMock.mock.calls.find(([url, init]) => url === '/api/patients/1/medications' && init?.method === 'POST')?.[1]?.body))
    expect(body).toMatchObject({ drugId: 5, dose: 400, doseUnit: 'mg', route: 'ORAL', frequency: 'three times daily',
      acknowledgedWarnings: ['INTERACTION-10-30-aa11bb22'], overrideReason: 'Short course, monitored' })
  })

  it('presents "nothing found" as limited data, never as safe', async () => {
    mockFetch({
      'GET /api/patients/1/medications': { body: [] },
      'GET /api/drugs': { body: drugs },
      'POST /api/patients/1/medications/safety-check': { body: { ...interactionCheck, overallStatus: 'NO_ISSUES_FOUND_IN_AVAILABLE_DATA', requiresAcknowledgement: false,
        findings: [interactionCheck.findings[1]] } },
    })
    render(<MedicationsTab patientId={1} />)
    await fillForm()
    await userEvent.click(screen.getByRole('button', { name: '1. Run safety check' }))

    expect(await screen.findByText('No issues found in the available data (this is not a guarantee of safety)')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '2. Save medication' })).toBeEnabled()
    expect(screen.getByText(/does not mean the medication is safe/)).toBeInTheDocument()
  })

  it('shows the finding details and says when the source gives no mechanism or recommended action', async () => {
    mockFetch({
      'GET /api/patients/1/medications': { body: [medication(1, 'ACTIVE', 'Warfarin')] },
      'GET /api/drugs': { body: drugs },
      'POST /api/patients/1/medications/safety-check': { body: { ...interactionCheck, findings: [allergyFinding, ...interactionCheck.findings] } },
    })
    render(<MedicationsTab patientId={1} />)
    await fillForm()
    await userEvent.click(screen.getByRole('button', { name: '1. Run safety check' }))

    const interaction = (await screen.findByText('Interaction listed: Ibuprofen 400 mg with Warfarin 5 mg')).closest('li')!
    expect(within(interaction).getByText('Priority: High')).toBeInTheDocument()
    expect(within(interaction).getByText('Severity: Major')).toBeInTheDocument()
    expect(within(interaction).getByText('Illustrative sample data')).toBeInTheDocument()
    expect(within(interaction).getAllByText('Not provided by the source')).toHaveLength(2)
    expect(within(interaction).getByText(/Not clinically reviewed/)).toBeInTheDocument()

    const allergy = screen.getByText('Recorded drug allergy matches Ibuprofen 400 mg').closest('li')!
    expect(within(allergy).getByText('Patient record')).toBeInTheDocument()
    expect(within(allergy).getByText('Ibuprofen')).toBeInTheDocument()
    expect(within(allergy).getByText(/Confirm the allergy history/)).toBeInTheDocument()
    expect(screen.queryByText(/\bsafe to\b/i)).not.toBeInTheDocument()
  })

  it('re-runs the check when medications change, keeping acknowledgements only for unchanged findings', async () => {
    let discontinued = false
    mockFetch({
      'GET /api/patients/1/medications': () => ({ body: discontinued
        ? [medication(1, 'ACTIVE', 'Warfarin'), medication(2, 'DISCONTINUED', 'Aspirin')]
        : [medication(1, 'ACTIVE', 'Warfarin'), medication(2, 'ACTIVE', 'Aspirin')] }),
      'GET /api/drugs': { body: drugs },
      'POST /api/patients/1/medications/safety-check': () => ({ body: discontinued
        ? { ...interactionCheck, findings: [allergyFinding, ...interactionCheck.findings] }
        : interactionCheck }),
      'POST /api/patients/1/medications/2/discontinue': () => { discontinued = true; return { body: medication(2, 'DISCONTINUED', 'Aspirin') } },
    })
    render(<MedicationsTab patientId={1} />)
    await fillForm()
    await userEvent.click(screen.getByRole('button', { name: '1. Run safety check' }))
    await userEvent.click(await screen.findByLabelText('I have reviewed this warning'))

    await userEvent.click(screen.getAllByRole('button', { name: 'Discontinue' })[1])
    await userEvent.type(screen.getByLabelText(/Reason for discontinuing/), 'No longer needed')
    await userEvent.click(screen.getByRole('button', { name: 'Confirm discontinue' }))

    expect(await screen.findByText(/safety check was re-run\. 1 new or changed finding/)).toBeInTheDocument()
    const allergy = screen.getByText('Recorded drug allergy matches Ibuprofen 400 mg').closest('li')!
    expect(within(allergy).getByText('New or changed since last check')).toBeInTheDocument()
    expect(within(allergy).getByLabelText('I have reviewed this warning')).not.toBeChecked()
    const interaction = screen.getByText('Interaction listed: Ibuprofen 400 mg with Warfarin 5 mg').closest('li')!
    expect(within(interaction).queryByText('New or changed since last check')).not.toBeInTheDocument()
    expect(within(interaction).getByLabelText('I have reviewed this warning')).toBeChecked()
  })

  it('discontinues only with a reason and keeps the record in history', async () => {
    const fetchMock = mockFetch({
      'GET /api/patients/1/medications': { body: [medication(1, 'ACTIVE', 'Warfarin')] },
      'GET /api/drugs': { body: drugs },
      'POST /api/patients/1/medications/1/discontinue': { body: medication(1, 'DISCONTINUED', 'Warfarin') },
    })
    render(<MedicationsTab patientId={1} />)
    await userEvent.click(await screen.findByRole('button', { name: 'Discontinue' }))

    const confirm = screen.getByRole('button', { name: 'Confirm discontinue' })
    expect(confirm).toBeDisabled()
    await userEvent.type(screen.getByLabelText(/Reason for discontinuing/), 'Bleeding risk')
    await userEvent.click(confirm)

    expect(await screen.findByText(/kept in the medication history/)).toBeInTheDocument()
    const call = fetchMock.mock.calls.find(([url]) => url === '/api/patients/1/medications/1/discontinue')
    expect(JSON.parse(String(call?.[1]?.body))).toEqual({ reason: 'Bleeding risk' })
  })
})
