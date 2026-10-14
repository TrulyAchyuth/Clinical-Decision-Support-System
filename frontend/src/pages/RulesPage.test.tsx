import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { mockFetch } from '../test/fetchMock'
import { RulesPage } from './RulesPage'

const rule = {
  id: 3, ruleId: 'HR-HIGH', name: 'Tachycardia', description: 'Educational example', severity: 'MEDIUM', priority: 30,
  enabled: true, condition: { fact: 'vitals.heartRate', operator: 'gt', value: 100 },
  recommendedActions: ['Re-check the heart rate'], createdAt: '2026-10-13T00:00:00Z', updatedAt: '2026-10-13T00:00:00Z',
}

const facts = [{ name: 'vitals.heartRate', label: 'Heart rate', unit: 'beats/min', type: 'NUMBER', operators: ['gt', 'lt'] }]

function renderPage() {
  return render(<MemoryRouter><RulesPage /></MemoryRouter>)
}

describe('RulesPage', () => {
  it('lists stored rules and the facts reported by the backend', async () => {
    mockFetch({ 'GET /api/rules': { body: [rule] }, 'GET /api/rules/facts': { body: facts } })
    renderPage()

    expect(await screen.findByText('Tachycardia')).toBeInTheDocument()
    expect(screen.getByText('Enabled')).toBeInTheDocument()
    expect(await screen.findByText('vitals.heartRate')).toBeInTheDocument()
  })

  it('shows the problems the backend finds when checking a rule', async () => {
    const fetchMock = mockFetch({
      'GET /api/rules': { body: [rule] },
      'GET /api/rules/facts': { body: facts },
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
      'GET /api/rules/facts': { body: facts },
      'POST /api/rules': { status: 400, body: { status: 400, detail: 'Invalid rule', errors: { 'condition.operator': 'must be one of gt, gte, lt, lte, eq, neq, contains' } } },
    })
    renderPage()
    await userEvent.click(await screen.findByRole('button', { name: 'Add rule' }))
    await userEvent.click(screen.getByRole('button', { name: 'Save rule' }))

    expect(await screen.findByText('Invalid rule')).toBeInTheDocument()
    expect(screen.getByText('condition.operator')).toBeInTheDocument()
  })

  it('reports condition text that is not JSON without calling the backend', async () => {
    const fetchMock = mockFetch({ 'GET /api/rules': { body: [] }, 'GET /api/rules/facts': { body: facts } })
    renderPage()
    await userEvent.click(await screen.findByRole('button', { name: 'Add rule' }))
    const condition = screen.getByLabelText(/Condition \(JSON\)/)
    await userEvent.clear(condition)
    await userEvent.type(condition, 'not json')
    await userEvent.click(screen.getByRole('button', { name: 'Check rule' }))

    expect(await screen.findByText('The condition is not valid JSON.')).toBeInTheDocument()
    expect(fetchMock.mock.calls.every(([, init]) => (init?.method ?? 'GET') === 'GET')).toBe(true)
  })
})
