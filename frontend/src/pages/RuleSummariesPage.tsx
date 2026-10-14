import { useState } from 'react'
import { rulesApi } from '../api/rules'
import { Badge, Card, EmptyState, ErrorAlert, Field, inputClass, Loading, PageHeader, SeverityBadge } from '../components/ui'
import { useAsync } from '../hooks/useAsync'

/** The decision rules in plain English for clinicians (read-only; no rule definitions). */
export function RuleSummariesPage() {
  const rules = useAsync(() => rulesApi.summaries(), [])
  const [search, setSearch] = useState('')
  const term = search.trim().toLowerCase()
  const visible = (rules.data ?? []).filter((r) => term === ''
    || [r.name, r.ruleId, r.description ?? '', r.conditionSummary].some((t) => t.toLowerCase().includes(term)))

  return (
    <>
      <PageHeader title="Decision rules"
        description="What the clinical decision check looks for, in priority order. Rules are illustrative and not clinically validated; a match is a prompt for review, not a diagnosis." />
      <div className="mb-4 max-w-sm">
        <Field label="Search rules" htmlFor="rs-search">
          <input id="rs-search" className={inputClass} value={search} onChange={(e) => setSearch(e.target.value)} />
        </Field>
      </div>
      {rules.loading ? <Loading /> : rules.error ? <ErrorAlert error={rules.error} onRetry={rules.reload} />
        : visible.length === 0 ? <EmptyState>No rules to show.</EmptyState> : (
          <div className="grid gap-4 lg:grid-cols-2">
            {visible.map((r) => (
              <Card key={r.id}>
                <div className="flex flex-wrap items-center gap-2">
                  <h2 className="text-base font-semibold text-slate-900">{r.name}</h2>
                  <SeverityBadge severity={r.severity} />
                  {r.enabled ? <Badge tone="green">Active</Badge> : <Badge>Inactive</Badge>}
                  {!r.valid && <Badge tone="amber">Needs administrator review</Badge>}
                </div>
                <p className="mt-1 text-xs text-slate-500">Priority {r.priority}</p>
                {r.description && <p className="mt-2 text-sm text-slate-600">{r.description}</p>}
                <p className="mt-3 text-sm text-slate-800"><span className="font-medium">Checks:</span> {r.conditionSummary}</p>
                <p className="mt-3 text-sm font-medium text-slate-800">Suggested actions</p>
                <ul className="mt-1 list-disc pl-5 text-sm text-slate-700">{r.recommendedActions.map((a) => <li key={a}>{a}</li>)}</ul>
              </Card>
            ))}
          </div>
        )}
    </>
  )
}
