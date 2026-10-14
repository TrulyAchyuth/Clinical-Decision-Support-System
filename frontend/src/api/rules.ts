import { apiRequest } from './client'
import type { Fact, Rule, RuleDryRun, RuleInput, RuleSummary, RuleValidation } from './types'

export const rulesApi = {
  /** Plain-English rules for any logged-in user; everything else here is ADMIN only. */
  summaries: () => apiRequest<RuleSummary[]>('GET', '/rules/summaries'),
  list: () => apiRequest<Rule[]>('GET', '/rules'),
  facts: () => apiRequest<Fact[]>('GET', '/rules/facts'),
  validate: (input: RuleInput) => apiRequest<RuleValidation>('POST', '/rules/validate', input),
  create: (input: RuleInput) => apiRequest<Rule>('POST', '/rules', input),
  update: (id: number, input: RuleInput) => apiRequest<Rule>('PUT', `/rules/${id}`, input),
  remove: (id: number) => apiRequest<void>('DELETE', `/rules/${id}`),
  /** Validates the stored rule again before switching it on (400 with problems if invalid). */
  activate: (id: number) => apiRequest<Rule>('POST', `/rules/${id}/activate`),
  deactivate: (id: number) => apiRequest<Rule>('POST', `/rules/${id}/deactivate`),
  /** Evaluates an unsaved condition against one patient; nothing is stored. */
  dryRun: (condition: unknown, patientId: number) => apiRequest<RuleDryRun>('POST', '/rules/dry-run', { condition, patientId }),
}
