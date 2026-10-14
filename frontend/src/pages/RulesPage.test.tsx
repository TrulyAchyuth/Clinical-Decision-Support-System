import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { mockFetch } from '../test/fetchMock'
import { renderAs } from '../test/renderAs'
import { RulesPage } from './RulesPage'

const rule = {
  id: 3, ruleId: 'HR-HIGH', name: 'Tachycardia', description: 'Educational example', severity: 'MEDIUM', priority: 30,
  enabled: true, condition: { fact: 'vitals.heartRate', operator: 'gt', value: 100 },
  recommendedActions: ['Re-check the heart rate'], createdAt: '2026-10-13T00:00:00Z', updatedAt: '2026-10-13T00:00:00Z',
}

const facts = [{ name: 'vitals.heartRate', label: 'Heart rate', unit: 'beats/min', type: 'NUMBER', operators: ['gt', 'lt'] }]

function renderPage() {
  return renderAs('ADMIN', <RulesPage />)
}

describe('RulesPage', () => {
  it('lists stored rules and the facts reported by the backend', async () => {
    mockFetch({ 'GET /api/rules': { body: [rule] }, 'GET /api/rules/facts': { body: facts }, 'GET /api/patients': { body: [] } })
    renderPage()

    expect(await screen.findByText('Tachycardia')).toBeInTheDocument()
    expect(screen.getByText('Enabled')).toBeInTheDocument()
    expect(await screen.findByText('vitals.heartRate')).toBeInTheDocument()
  })

  it('shows the problems the backend finds when checking a rule', async () => {
    const fetchMock = mockFetch({
      'GET /api/rules': { body: [rule] },
      'GET /api/rules/facts': { body: facts }, 'GET /api/patients': { body: [] },
      'POST /api/rules/validate': { body: { valid: false, errors: [{ path: 'condition.fact', message: 'unknown fact "vitals.pulse"' }] } },
    })
    renderPage()
    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }))
    await userEvent.click(screen.getByRole('button', { name: 'Check rule' }))

    expect(await screen.findByText('The backend found problems in this rule:')).toBeInTheDocument()
    expect(screen.getByText('condition.fact')).toBeInTheDocument()
    expect(screen.getByText(/unknown fact "vitals.pulse"/)).toBeInTheDocument()
    const body = JSON.parse(String(fetchMock.mock.calls.find(([, init]) => init?.method === 'POST')?.[1]?.body))
    expect(body.ruleId).toBe('HR-HIGH')
    expect(body.condition).toEqual(rule.condition)
  })

  it('shows readable errors when saving an invalid rule', async () => {
    mockFetch({
      'GET /api/rules': { body: [] },
      'GET /api/rules/facts': { body: facts }, 'GET /api/patients': { body: [] },
      'POST /api/rules': { status: 400, body: { status: 400, detail: 'Invalid rule', errors: { 'condition.operator': 'must be one of gt, gte, lt, lte, eq, neq, contains' } } },
    })
    renderPage()
    await userEvent.click(await screen.findByRole('button', { name: 'Add rule' }))
    await userEvent.click(screen.getByRole('button', { name: 'Save rule' }))

    expect(await screen.findByText('Invalid rule')).toBeInTheDocument()
    expect(screen.getByText('condition.operator')).toBeInTheDocument()
  })

  it('reports condition text that is not JSON without calling the backend', async () => {
    const fetchMock = mockFetch({ 'GET /api/rules': { body: [] }, 'GET /api/rules/facts': { body: facts }, 'GET /api/patients': { body: [] } })
    renderPage()
    await userEvent.click(await screen.findByRole('button', { name: 'Add rule' }))
    const condition = screen.getByLabelText(/Condition \(JSON\)/)
    await userEvent.clear(condition)
    await userEvent.type(condition, 'not json')
    await userEvent.click(screen.getByRole('button', { name: 'Check rule' }))

    expect(await screen.findByText('The condition is not valid JSON.')).toBeInTheDocument()
    expect(fetchMock.mock.calls.every(([, init]) => (init?.method ?? 'GET') === 'GET')).toBe(true)
  })

  it('activates a rule only after confirmation, through the activate endpoint', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)
    const fetchMock = mockFetch({
      'GET /api/rules': { body: [{ ...rule, enabled: false }] }, 'GET /api/rules/facts': { body: facts }, 'GET /api/patients': { body: [] },
      'POST /api/rules/3/activate': { body: rule },
    })
    renderPage()
    await userEvent.click(await screen.findByRole('button', { name: 'Activate' }))

    expect(confirm).toHaveBeenCalled()
    expect(await screen.findByText('Rule HR-HIGH activated.')).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url, init]) => url === '/api/rules/3/activate' && init?.method === 'POST')).toBe(true)
  })

  it('shows why a rule cannot be activated in readable language', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    mockFetch({
      'GET /api/rules': { body: [{ ...rule, enabled: false }] }, 'GET /api/rules/facts': { body: facts }, 'GET /api/patients': { body: [] },
      'POST /api/rules/3/activate': { status: 400, body: { status: 400, detail: 'Invalid rule', errors: { 'condition.fact': 'unknown fact "lab.Old"' } } },
    })
    renderPage()
    await userEvent.click(await screen.findByRole('button', { name: 'Activate' }))

    expect(await screen.findByText('Invalid rule')).toBeInTheDocument()
    expect(screen.queryByText('Rule HR-HIGH activated.')).not.toBeInTheDocument()
  })

  it('builds the condition from lists, describes it in plain English and dry-runs it on a patient', async () => {
    const fetchMock = mockFetch({
      'GET /api/rules': { body: [] }, 'GET /api/rules/facts': { body: facts },
      'GET /api/patients': { body: [{ id: 1, fullName: 'Daniel Okafor' }] },
      'POST /api/rules/dry-run': { body: { patientId: 1, valid: true, errors: [], result: 'MATCHED', reasons: ['Heart rate is 118 beats/min'], evaluatedAt: '2026-10-14T10:00:00Z', note: 'Dry run only: nothing was saved.' } },
    })
    renderPage()
    await userEvent.click(await screen.findByRole('button', { name: 'Add rule' }))
    await screen.findByRole('option', { name: 'Heart rate (beats/min)' })

    const value = screen.getByLabelText('Value 1')
    await userEvent.clear(value)
    await userEvent.type(value, '120')
    expect(screen.getByText(/Heart rate is greater than 120 beats\/min/)).toBeInTheDocument()
    expect(JSON.parse((screen.getByLabelText(/Condition \(JSON\)/) as HTMLTextAreaElement).value))
      .toEqual({ fact: 'vitals.heartRate', operator: 'gt', value: 120 })

    await userEvent.selectOptions(screen.getByLabelText('Patient for dry run'), '1')
    await userEvent.click(screen.getByRole('button', { name: 'Test on patient' }))
    expect(await screen.findByText('The condition matches this patient now')).toBeInTheDocument()
    const body = JSON.parse(String(fetchMock.mock.calls.find(([url]) => url === '/api/rules/dry-run')?.[1]?.body))
    expect(body).toEqual({ patientId: 1, condition: { fact: 'vitals.heartRate', operator: 'gt', value: 120 } })
  })

  it('filters the list by search text and status', async () => {
    mockFetch({
      'GET /api/rules': { body: [rule, { ...rule, id: 4, ruleId: 'BP-HIGH', name: 'High blood pressure', enabled: false }] },
      'GET /api/rules/facts': { body: facts }, 'GET /api/patients': { body: [] },
    })
    renderPage()
    await screen.findByText('Tachycardia')

    await userEvent.selectOptions(screen.getByLabelText('Status'), 'inactive')
    expect(screen.queryByText('Tachycardia')).not.toBeInTheDocument()
    expect(screen.getByText('High blood pressure')).toBeInTheDocument()
    await userEvent.selectOptions(screen.getByLabelText('Status'), 'all')
    await userEvent.type(screen.getByLabelText('Search rules'), 'tachy')
    expect(screen.getByText('Tachycardia')).toBeInTheDocument()
    expect(screen.queryByText('High blood pressure')).not.toBeInTheDocument()
  })
})
