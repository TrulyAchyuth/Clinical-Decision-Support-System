import type { Decision, RuleResult } from '../api/types'
import { formatDateTime } from '../utils/format'
import { Badge, SeverityBadge } from './ui'

/**
 * Shows one decision exactly as the backend returned it: outcome, summary,
 * matched rules with their reasons and suggested actions, rules that could not
 * be evaluated, and the backend's disclaimer.
 */
export function DecisionResult({ decision }: { decision: Decision }) {
  const matched = decision.matchedRules.length
  return (
    <div className="space-y-4">
      <div className="rounded-lg border-2 border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-950" role="note">
        <p className="font-semibold">Rule-based decision support — not a diagnosis</p>
        <p className="mt-1">{decision.disclaimer}</p>
      </div>

      {decision.outcome === 'RULES_MATCHED' ? (
        <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3">
          <p className="font-semibold text-red-900">{matched} {matched === 1 ? 'rule' : 'rules'} matched — clinician review suggested</p>
          <p className="mt-1 text-sm text-red-900">{decision.summary}</p>
        </div>
      ) : (
        <div className="rounded-lg border border-slate-300 bg-slate-100 px-4 py-3">
          <p className="font-semibold text-slate-900">No configured rule matched</p>
          <p className="mt-1 text-sm text-slate-800">{decision.summary}</p>
          <p className="mt-2 text-sm text-slate-700">
            This only means that none of the active rules' conditions were met by the recorded data. It does not show
            that the patient is healthy.
          </p>
        </div>
      )}

      <dl className="grid grid-cols-2 gap-2 text-sm sm:grid-cols-5">
        <Count label="Rules evaluated" value={decision.rulesEvaluated} />
        <Count label="Matched" value={matched} />
        <Count label="Not matched" value={decision.rulesNotMatched} />
        <Count label="Insufficient data" value={decision.insufficientData.length} />
        <Count label="Invalid rules" value={decision.invalidRules.length} />
      </dl>
      <p className="text-xs text-slate-500">Decision #{decision.id}, requested {formatDateTime(decision.requestedAt)}</p>

      {matched > 0 && (
        <section>
          <h3 className="mb-2 text-sm font-semibold text-slate-800">Matched rules (highest priority first)</h3>
          <div className="space-y-3">
            {decision.matchedRules.map((rule) => <MatchedRule key={rule.ruleId} rule={rule} />)}
          </div>
        </section>
      )}

      {decision.insufficientData.length > 0 && (
        <RuleList
          title="Could not be evaluated — data missing or too old"
          note="These rules were not checked. Record the missing observations and request a new decision."
          rules={decision.insufficientData}
        />
      )}

      {decision.invalidRules.length > 0 && (
        <RuleList
          title="Skipped — rule definition is invalid"
          note="Fix these rules on the Clinical rules page."
          rules={decision.invalidRules}
        />
      )}
    </div>
  )
}

function Count({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white px-3 py-2">
      <dt className="text-xs text-slate-500">{label}</dt>
      <dd className="text-lg font-semibold text-slate-900">{value}</dd>
    </div>
  )
}

function MatchedRule({ rule }: { rule: RuleResult }) {
  return (
    <article className="rounded-lg border border-slate-200 bg-white p-4">
      <div className="flex flex-wrap items-center gap-2">
        <h4 className="font-semibold text-slate-900">{rule.name}</h4>
        <SeverityBadge severity={rule.severity} />
        <Badge>Priority {rule.priority}</Badge>
        <span className="font-mono text-xs text-slate-500">{rule.ruleId}</span>
      </div>
      <div className="mt-3 grid gap-4 md:grid-cols-2">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Why it matched (recorded observations)</p>
          <ul className="mt-1 list-disc space-y-1 pl-5 text-sm text-slate-800">
            {rule.reasons.map((reason) => <li key={reason}>{reason}</li>)}
          </ul>
        </div>
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Suggested next steps (from the rule)</p>
          <ul className="mt-1 list-disc space-y-1 pl-5 text-sm text-slate-800">
            {rule.recommendedActions.map((action) => <li key={action}>{action}</li>)}
          </ul>
        </div>
      </div>
    </article>
  )
}

function RuleList({ title, note, rules }: { title: string; note: string; rules: RuleResult[] }) {
  return (
    <section className="rounded-lg border border-amber-200 bg-amber-50/50 p-4">
      <h3 className="text-sm font-semibold text-amber-950">{title}</h3>
      <p className="mb-2 text-xs text-amber-900">{note}</p>
      <ul className="space-y-2 text-sm">
        {rules.map((rule) => (
          <li key={rule.ruleId}>
            <span className="font-medium text-slate-900">{rule.name}</span>{' '}
            <span className="font-mono text-xs text-slate-500">{rule.ruleId}</span>
            <ul className="list-disc pl-5 text-slate-700">
              {rule.reasons.map((reason) => <li key={reason}>{reason}</li>)}
            </ul>
          </li>
        ))}
      </ul>
    </section>
  )
}
