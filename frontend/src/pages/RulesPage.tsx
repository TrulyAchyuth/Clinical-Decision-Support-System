import { useState, type FormEvent } from 'react'
import { ApiError, errorMessage } from '../api/client'
import { patientsApi } from '../api/patients'
import { rulesApi } from '../api/rules'
import type { Rule, RuleDryRun, RuleInput, RuleValidation, Severity } from '../api/types'
import {
  Alert, Badge, Button, Card, EmptyState, ErrorAlert, Field, inputClass, Loading, PageHeader, SeverityBadge, Table, td, th,
} from '../components/ui'
import { useAuth } from '../components/AuthProvider'
import { ConditionBuilder, describeCondition, RuleErrorList } from '../components/RuleConditionTools'
import { useAsync } from '../hooks/useAsync'
import { optionalText } from '../utils/format'

const TEMPLATE_CONDITION = `{
  "fact": "vitals.heartRate",
  "operator": "gt",
  "value": 100
}`

interface RuleForm {
  ruleId: string
  name: string
  description: string
  severity: Severity | ''
  priority: string
  enabled: boolean
  condition: string
  actions: string
}

const emptyForm: RuleForm = {
  ruleId: '', name: '', description: '', severity: 'MEDIUM', priority: '100', enabled: true,
  condition: TEMPLATE_CONDITION, actions: '',
}

function toForm(rule: Rule): RuleForm {
  return {
    ruleId: rule.ruleId, name: rule.name, description: rule.description ?? '', severity: rule.severity,
    priority: String(rule.priority), enabled: rule.enabled, condition: JSON.stringify(rule.condition, null, 2),
    actions: rule.recommendedActions.join('\n'),
  }
}

/** Turns the form into the backend's rule JSON. Only checks that the condition is JSON; the backend validates the rule. */
function toInput(form: RuleForm): RuleInput {
  let condition: unknown
  try {
    condition = JSON.parse(form.condition)
  } catch (err) {
    throw new ApiError(400, 'The condition is not valid JSON.', { condition: errorMessage(err) })
  }
  return {
    ruleId: form.ruleId,
    name: form.name,
    description: optionalText(form.description),
    severity: form.severity as Severity,
    priority: form.priority === '' ? null : Number(form.priority),
    enabled: form.enabled,
    condition,
    recommendedActions: form.actions.split('\n').map((line) => line.trim()).filter((line) => line !== ''),
  }
}

function parseCondition(text: string): unknown {
  try {
    return JSON.parse(text)
  } catch {
    return undefined
  }
}

function visibleRules(rules: Rule[], search: string, status: 'all' | 'active' | 'inactive'): Rule[] {
  const term = search.trim().toLowerCase()
  return rules.filter((r) => (status === 'all' || (status === 'active') === r.enabled)
    && (term === '' || [r.name, r.ruleId, r.description ?? ''].some((t) => t.toLowerCase().includes(term))))
}

export function RulesPage() {
  const { isAdmin } = useAuth()
  const rules = useAsync(() => rulesApi.list(), [])
  const facts = useAsync(() => rulesApi.facts(), [])
  const [editing, setEditing] = useState<Rule | 'new' | null>(null)
  const [form, setForm] = useState<RuleForm>(emptyForm)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()
  const [validation, setValidation] = useState<RuleValidation>()
  const [message, setMessage] = useState<string>()
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState<'all' | 'active' | 'inactive'>('all')
  const [viewing, setViewing] = useState<number>()
  const [dryRunPatient, setDryRunPatient] = useState('')
  const [dryRun, setDryRun] = useState<RuleDryRun>()
  const patients = useAsync(() => (isAdmin ? patientsApi.list() : Promise.resolve([])), [isAdmin])
  const fieldErrors = error instanceof ApiError ? error.fieldErrors : {}
  const parsedCondition = parseCondition(form.condition)

  function open(rule: Rule | 'new') {
    setEditing(rule); setError(undefined); setValidation(undefined); setMessage(undefined); setDryRun(undefined)
    setForm(rule === 'new' ? emptyForm : toForm(rule))
  }

  function update<K extends keyof RuleForm>(key: K, value: RuleForm[K]) {
    setForm({ ...form, [key]: value })
    setValidation(undefined); setDryRun(undefined)
  }

  async function check() {
    setBusy(true); setError(undefined); setValidation(undefined)
    try {
      setValidation(await rulesApi.validate(toInput(form)))
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  async function save(e: FormEvent) {
    e.preventDefault()
    setBusy(true); setError(undefined); setValidation(undefined)
    try {
      const input = toInput(form)
      const saved = editing === 'new' ? await rulesApi.create(input) : await rulesApi.update((editing as Rule).id, input)
      setMessage(`Rule ${saved.ruleId} saved.`)
      setEditing(null)
      rules.reload()
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  /** Activation re-validates the stored rule on the server; deactivation always works. */
  async function toggle(rule: Rule) {
    const question = rule.enabled
      ? `Deactivate ${rule.ruleId}? It will no longer be evaluated in new decisions. Past decisions are unchanged.`
      : `Activate ${rule.ruleId}? The backend validates it first; it will then be evaluated in every new decision.`
    if (!window.confirm(question)) return
    setError(undefined); setMessage(undefined)
    try {
      if (rule.enabled) await rulesApi.deactivate(rule.id)
      else await rulesApi.activate(rule.id)
      setMessage(`Rule ${rule.ruleId} ${rule.enabled ? 'deactivated' : 'activated'}.`)
      rules.reload()
    } catch (err) {
      setError(err)
    }
  }

  async function runDryRun() {
    setBusy(true); setError(undefined); setDryRun(undefined)
    try {
      const input = toInput(form)
      setDryRun(await rulesApi.dryRun(input.condition, Number(dryRunPatient)))
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  async function remove(rule: Rule) {
    if (!window.confirm(`Delete rule ${rule.ruleId}? Past decisions keep their copy of it.`)) return
    setError(undefined); setMessage(undefined)
    try {
      await rulesApi.remove(rule.id)
      setMessage(`Rule ${rule.ruleId} deleted.`)
      rules.reload()
    } catch (err) {
      setError(err)
    }
  }

  return (
    <>
      <PageHeader title="Clinical rules"
        description="Configure the rules evaluated in clinical decisions. Every rule is validated by the backend before it is saved or activated. Sample rules are illustrative and not clinically validated."
        actions={isAdmin && <Button onClick={() => open('new')}>Add rule</Button>} />
      {!isAdmin && <Alert kind="info">Read-only: only administrators can change clinical rules.</Alert>}
      {message && <Alert kind="success" onDismiss={() => setMessage(undefined)}>{message}</Alert>}
      {error !== undefined && !editing && <ErrorAlert error={error} onDismiss={() => setError(undefined)} />}

      {editing && (
        <Card title={editing === 'new' ? 'Add rule' : `Edit ${editing.ruleId}`} className="mb-6">
          <form onSubmit={save} noValidate>
            {error !== undefined && <ErrorAlert error={error} />}
            {validation && (validation.valid
              ? <Alert kind="success">The backend accepted this rule. It has not been saved yet.</Alert>
              : (
                <Alert kind="error">
                  <p className="font-medium">The backend found problems in this rule:</p>
                  <RuleErrorList errors={validation.errors} />
                </Alert>
              ))}
            <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
              <Field label="Rule ID" htmlFor="r-id" required error={fieldErrors.ruleId} hint="Letters, digits, - and _ (e.g. HR-LOW)">
                <input id="r-id" className={`${inputClass} font-mono`} value={form.ruleId} onChange={(e) => update('ruleId', e.target.value)} />
              </Field>
              <Field label="Name" htmlFor="r-name" required error={fieldErrors.name}>
                <input id="r-name" className={inputClass} value={form.name} onChange={(e) => update('name', e.target.value)} />
              </Field>
              <Field label="Severity" htmlFor="r-severity" required error={fieldErrors.severity}>
                <select id="r-severity" className={inputClass} value={form.severity} onChange={(e) => update('severity', e.target.value as Severity)}>
                  <option value="LOW">Low</option><option value="MEDIUM">Medium</option><option value="HIGH">High</option>
                </select>
              </Field>
              <Field label="Priority" htmlFor="r-priority" required error={fieldErrors.priority} hint="1–1000; 1 is the most important">
                <input id="r-priority" type="number" className={inputClass} value={form.priority} onChange={(e) => update('priority', e.target.value)} />
              </Field>
              <div className="md:col-span-2 lg:col-span-4">
                <Field label="Description" htmlFor="r-desc" error={fieldErrors.description} hint="Describe what the rule checks; say so if it is illustrative.">
                  <textarea id="r-desc" rows={2} className={inputClass} value={form.description} onChange={(e) => update('description', e.target.value)} />
                </Field>
              </div>
              <div className="md:col-span-2 lg:col-span-4 rounded-lg border border-slate-200 bg-slate-50 p-3">
                <p className="mb-2 text-sm font-medium text-slate-700">Condition builder</p>
                {parsedCondition === undefined
                  ? <p className="text-sm text-slate-600">The JSON below is not valid yet, so the builder cannot show it.</p>
                  : <ConditionBuilder condition={parsedCondition} facts={facts.data ?? []}
                      onChange={(c) => update('condition', JSON.stringify(c, null, 2))} />}
                <p className="mt-3 text-sm text-slate-700"><span className="font-medium">In plain English:</span>{' '}
                  {parsedCondition === undefined ? '—' : describeCondition(parsedCondition, facts.data ?? [])}</p>
              </div>
              <div className="md:col-span-2">
                <Field label="Condition (JSON)" htmlFor="r-condition" required error={fieldErrors.condition}
                  hint="Advanced: the builder above writes this for you.">
                  <textarea id="r-condition" rows={12} spellCheck={false} className={`${inputClass} font-mono text-xs`} value={form.condition} onChange={(e) => update('condition', e.target.value)} />
                </Field>
              </div>
              <div className="md:col-span-2">
                <Field label="Recommended actions" htmlFor="r-actions" required error={fieldErrors.recommendedActions} hint="One action per line (1–10).">
                  <textarea id="r-actions" rows={6} className={inputClass} value={form.actions} onChange={(e) => update('actions', e.target.value)} />
                </Field>
                <label className="mt-4 flex items-center gap-2 text-sm text-slate-700">
                  <input type="checkbox" checked={form.enabled} onChange={(e) => update('enabled', e.target.checked)} className="h-4 w-4 accent-teal-700" />
                  Enabled (evaluated in decisions)
                </label>
              </div>
            </div>
            <div className="mt-4 rounded-lg border border-slate-200 p-3">
              <p className="text-sm font-medium text-slate-700">Dry run: try the condition on a patient (nothing is saved)</p>
              <div className="mt-2 flex flex-wrap items-center gap-2">
                <select aria-label="Patient for dry run" className={`${inputClass} w-auto`} value={dryRunPatient} onChange={(e) => setDryRunPatient(e.target.value)}>
                  <option value="">Choose a patient</option>
                  {patients.data?.map((p) => <option key={p.id} value={p.id}>{p.fullName}</option>)}
                </select>
                <Button variant="secondary" onClick={runDryRun} disabled={busy || !dryRunPatient}>Test on patient</Button>
              </div>
              {dryRun && (dryRun.valid ? (
                <Alert kind={dryRun.result === 'MATCHED' ? 'warning' : 'info'}>
                  <p className="font-medium">{dryRun.result === 'MATCHED' ? 'The condition matches this patient now'
                    : dryRun.result === 'NOT_MATCHED' ? 'The condition does not match this patient now'
                      : 'Insufficient data: the condition cannot be evaluated for this patient'}</p>
                  <ul className="mt-1 list-disc pl-5">{dryRun.reasons.map((r) => <li key={r}>{r}</li>)}</ul>
                  <p className="mt-1 text-xs">{dryRun.note}</p>
                </Alert>
              ) : (
                <Alert kind="error"><p className="font-medium">The condition is not valid:</p><RuleErrorList errors={dryRun.errors} /></Alert>
              ))}
            </div>
            <div className="mt-4 flex flex-wrap gap-2">
              <Button variant="secondary" onClick={check} disabled={busy}>Check rule</Button>
              <Button type="submit" disabled={busy}>{busy ? 'Working…' : 'Save rule'}</Button>
              <Button variant="ghost" onClick={() => { setEditing(null); setError(undefined) }}>Cancel</Button>
            </div>
          </form>
        </Card>
      )}

      <Card className="mb-6">
        <div className="mb-4 flex flex-wrap items-end gap-3">
          <Field label="Search rules" htmlFor="r-search">
            <input id="r-search" className={inputClass} placeholder="Name, rule ID or description" value={search} onChange={(e) => setSearch(e.target.value)} />
          </Field>
          <Field label="Status" htmlFor="r-status">
            <select id="r-status" className={inputClass} value={statusFilter} onChange={(e) => setStatusFilter(e.target.value as typeof statusFilter)}>
              <option value="all">All rules</option><option value="active">Active only</option><option value="inactive">Inactive only</option>
            </select>
          </Field>
        </div>
        {rules.loading ? <Loading /> : rules.error ? <ErrorAlert error={rules.error} onRetry={rules.reload} />
          : !rules.data?.length ? <EmptyState>No rules yet.</EmptyState> : visibleRules(rules.data, search, statusFilter).length === 0
            ? <EmptyState>No rules match the search.</EmptyState> : (
            <Table>
              <thead><tr><th className={th}>Priority</th><th className={th}>Rule</th><th className={th}>Severity</th><th className={th}>Status</th><th className={th}></th></tr></thead>
              <tbody>
                {visibleRules(rules.data, search, statusFilter).map((rule) => (
                  <tr key={rule.id}>
                    <td className={td}>{rule.priority}</td>
                    <td className={td}>
                      <p className="font-medium text-slate-900">{rule.name} <span className="font-mono text-xs font-normal text-slate-500">{rule.ruleId}</span></p>
                      {rule.description && <p className="mt-0.5 max-w-xl text-xs text-slate-500">{rule.description}</p>}
                      {viewing === rule.id && (
                        <div className="mt-2 max-w-xl rounded-lg border border-slate-200 bg-slate-50 p-3 text-xs text-slate-700">
                          <p><span className="font-semibold">Condition:</span> {describeCondition(rule.condition, facts.data ?? [])}</p>
                          <p className="mt-1 font-semibold">Suggested actions (prompts for review, not orders):</p>
                          <ul className="list-disc pl-4">{rule.recommendedActions.map((a) => <li key={a}>{a}</li>)}</ul>
                          <pre className="mt-2 overflow-x-auto rounded bg-white p-2 font-mono">{JSON.stringify(rule.condition, null, 2)}</pre>
                          <p className="mt-1 text-slate-500">Last changed {new Date(rule.updatedAt).toLocaleString()}</p>
                        </div>
                      )}
                    </td>
                    <td className={td}><SeverityBadge severity={rule.severity} /></td>
                    <td className={td}>{rule.enabled ? <Badge tone="green">Enabled</Badge> : <Badge>Disabled</Badge>}</td>
                    <td className={`${td} whitespace-nowrap text-right`}>
                      <Button variant="ghost" onClick={() => setViewing(viewing === rule.id ? undefined : rule.id)}>{viewing === rule.id ? 'Hide' : 'View'}</Button>
                      {isAdmin && <>
                        <Button variant="ghost" onClick={() => toggle(rule)}>{rule.enabled ? 'Deactivate' : 'Activate'}</Button>
                        <Button variant="ghost" onClick={() => open(rule)}>Edit</Button>
                        <Button variant="danger" className="ml-1" onClick={() => remove(rule)}>Delete</Button>
                      </>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </Table>
          )}
        <p className="mt-3 text-xs text-slate-500">Rules are evaluated in priority order (1 first). Activation is validated by the backend; an invalid rule cannot be activated. All sample rules are illustrative and not clinically validated.</p>
      </Card>

      <Card title="Writing conditions">
        <div className="grid gap-6 lg:grid-cols-2">
          <div className="text-sm text-slate-700">
            <p>A condition is one of:</p>
            <ul className="mt-2 list-disc space-y-1 pl-5">
              <li><code className="text-xs">{'{"all": [ ... ]}'}</code> — every condition must be true (AND)</li>
              <li><code className="text-xs">{'{"any": [ ... ]}'}</code> — at least one must be true (OR)</li>
              <li><code className="text-xs">{'{"not": { ... }}'}</code> — the condition must be false (NOT)</li>
              <li><code className="text-xs">{'{"fact": "...", "operator": "...", "value": ...}'}</code> — a comparison</li>
            </ul>
            <p className="mt-3">If a fact has no recent value, the rule is reported as <strong>insufficient data</strong>; it is never treated as normal.</p>
            <pre className="mt-3 overflow-x-auto rounded-lg bg-slate-900 p-3 text-xs text-slate-100">{`{"any": [
  {"fact": "vitals.systolicBp", "operator": "gte", "value": 140},
  {"fact": "vitals.diastolicBp", "operator": "gte", "value": 90}
]}`}</pre>
          </div>
          <div>
            <p className="mb-2 text-sm font-medium text-slate-700">Available facts (from the backend)</p>
            {facts.loading ? <Loading /> : facts.error ? <ErrorAlert error={facts.error} onRetry={facts.reload} /> : (
              <Table>
                <thead><tr><th className={th}>Fact</th><th className={th}>Unit</th><th className={th}>Operators</th></tr></thead>
                <tbody>
                  {facts.data?.map((f) => (
                    <tr key={f.name}>
                      <td className={td}><code className="text-xs">{f.name}</code><p className="text-xs text-slate-500">{f.label}</p></td>
                      <td className={td}>{f.unit ?? (f.type === 'CODE_LIST' ? 'ICD-10 codes' : '')}</td>
                      <td className={`${td} font-mono text-xs`}>{f.operators.join(', ')}</td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            )}
          </div>
        </div>
      </Card>
    </>
  )
}
