import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { mockFetch } from '../test/fetchMock'
import { renderAs } from '../test/renderAs'
import { RuleSummariesPage } from './RuleSummariesPage'

describe('RuleSummariesPage', () => {
  it('shows clinicians rules in plain English from the summaries endpoint only', async () => {
    const fetchMock = mockFetch({ 'GET /api/rules/summaries': { body: [{
      id: 1, ruleId: 'HR-HIGH', name: 'Tachycardia', description: 'Illustrative example', severity: 'MEDIUM', priority: 30,
      enabled: true, valid: true, conditionSummary: 'Heart rate is greater than 100 beats/min', recommendedActions: ['Re-check the heart rate'],
    }] } })
    renderAs('CLINICIAN', <RuleSummariesPage />)

    expect(await screen.findByText('Tachycardia')).toBeInTheDocument()
    expect(screen.getByText('Heart rate is greater than 100 beats/min')).toBeInTheDocument()
    expect(screen.queryByText(/"fact"/)).not.toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: /JSON/ })).not.toBeInTheDocument()
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual(['/api/rules/summaries'])
  })
})
