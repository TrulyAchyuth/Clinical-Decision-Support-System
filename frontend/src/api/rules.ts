import { apiRequest } from './client'
import type { Fact, Rule, RuleInput, RuleValidation } from './types'

export const rulesApi = {
  list: () => apiRequest<Rule[]>('GET', '/rules'),
  facts: () => apiRequest<Fact[]>('GET', '/rules/facts'),
  validate: (input: RuleInput) => apiRequest<RuleValidation>('POST', '/rules/validate', input),
  create: (input: RuleInput) => apiRequest<Rule>('POST', '/rules', input),
  update: (id: number, input: RuleInput) => apiRequest<Rule>('PUT', `/rules/${id}`, input),
  remove: (id: number) => apiRequest<void>('DELETE', `/rules/${id}`),
}

/** The editable fields of a stored rule, e.g. to switch it on or off with PUT. */
export function toRuleInput(rule: Rule): RuleInput {
  return {
    ruleId: rule.ruleId,
    name: rule.name,
    description: rule.description ?? undefined,
    severity: rule.severity,
    priority: rule.priority,
    enabled: rule.enabled,
    condition: rule.condition,
    recommendedActions: rule.recommendedActions,
  }
}
