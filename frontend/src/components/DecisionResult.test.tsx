import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Decision } from '../api/types'
import { DecisionResult } from './DecisionResult'

const DISCLAIMER = 'Educational prototype using synthetic data. Matched rules are prompts for clinician review, not diagnoses. The rules are illustrative and not clinically validated.'

function decision(overrides: Partial<Decision>): Decision {
  return {
    id: 7, patientId: 1, requestedAt: '2026-10-13T10:00:00Z', outcome: 'NO_RULES_MATCHED',
    summary: 'No active rule matched the recorded data. This does not rule out any condition.',
    rulesEvaluated: 5, rulesNotMatched: 5, matchedRules: [], insufficientData: [], invalidRules: [],
    disclaimer: DISCLAIMER, ...overrides,
  }
}

describe('DecisionResult', () => {
  it('shows matched rules with their reasons, suggested actions and the disclaimer', () => {
    render(<DecisionResult decision={decision({
      outcome: 'RULES_MATCHED',
      summary: '1 of 5 active rules matched. Review the reasons and suggested actions; a match is not a diagnosis.',
      rulesNotMatched: 4,
      matchedRules: [{
        ruleId: 'HR-HIGH', name: 'Tachycardia', severity: 'MEDIUM', priority: 30,
        reasons: ['Heart rate is 118 beats/min (recorded 2026-10-13 09:30 UTC), which is greater than 100 beats/min.'],
        recommendedActions: ['Re-check the heart rate with the patient at rest'],
      }],
    })} />)

    expect(screen.getByText(DISCLAIMER)).toBeInTheDocument()
    expect(screen.getByText('1 rule matched — clinician review suggested')).toBeInTheDocument()
    expect(screen.getByText('Tachycardia')).toBeInTheDocument()
    expect(screen.getByText('MEDIUM severity')).toBeInTheDocument()
    expect(screen.getByText(/Heart rate is 118 beats\/min/)).toBeInTheDocument()
    expect(screen.getByText('Re-check the heart rate with the patient at rest')).toBeInTheDocument()
  })

  it('never presents "no match" as the patient being healthy', () => {
    const { container } = render(<DecisionResult decision={decision({})} />)

    expect(screen.getByText('No configured rule matched')).toBeInTheDocument()
    expect(screen.getByText(/does not rule out any condition/)).toBeInTheDocument()
    expect(screen.getByText(/does not show that the patient is healthy/)).toBeInTheDocument()
    const text = container.textContent ?? ''
    expect(text).not.toMatch(/\b(all clear|patient is normal|no problems)\b/i)
    // "healthy" may only appear inside the negative statement above.
    expect(text.replace('does not show that the patient is healthy', '')).not.toMatch(/healthy/i)
  })

  it('lists rules that could not be evaluated because data was missing', () => {
    render(<DecisionResult decision={decision({
      rulesNotMatched: 4,
      insufficientData: [{ ruleId: 'GLUCOSE-HIGH', name: 'Elevated fasting blood glucose', severity: 'MEDIUM', priority: 50,
        reasons: ['No Fasting plasma glucose result in the last 72 hours'], recommendedActions: [] }],
    })} />)

    expect(screen.getByText('Could not be evaluated — data missing or too old')).toBeInTheDocument()
    expect(screen.getByText('No Fasting plasma glucose result in the last 72 hours')).toBeInTheDocument()
  })
})
