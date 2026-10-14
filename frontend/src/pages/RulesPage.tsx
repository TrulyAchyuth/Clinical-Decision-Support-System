import { useState, type FormEvent } from 'react'
import { ApiError, errorMessage } from '../api/client'
import { rulesApi, toRuleInput } from '../api/rules'
import type { Rule, RuleInput, RuleValidation, Severity } from '../api/types'
import {
  Alert, Badge, Button, Card, EmptyState, ErrorAlert, Field, inputClass, Loading, PageHeader, SeverityBadge, Table, td, th,
} from '../components/ui'
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

export function RulesPage() {
  const rules = useAsync(() => rulesApi.list(), [])
  const facts = useAsync(() => rulesApi.facts(), [])
  const [editing, setEditing] = useState<Rule | 'new' | null>(null)
  const [form, setForm] = useState<RuleForm>(emptyForm)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()
  const [validation, setValidation] = useState<RuleValidation>()
  const [message, setMessage] = useState<string>()
  const fieldErrors = error instanceof ApiError ? error.fieldErrors : {}

  function open(rule: Rule | 'new') {
    setEditing(rule); setError(undefined); setValidation(undefined); setMessage(undefined)
    setForm(rule === 'new' ? emptyForm : toForm(rule))
  }

  function update<K extends keyof RuleForm>(key: K, value: RuleForm[K]) {
    setForm({ ...form, [key]: value })
    setValidation(undefined)
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

  async function toggle(rule: Rule) {
    setError(undefined); setMessage(undefined)
    try {
      await rulesApi.update(rule.id, { ...toRuleInput(rule), enabled: !rule.enabled })
      setMessage(`Rule ${rule.ruleId} ${rule.enabled ? 'disabled' : 'enabled'}.`)
      rules.reload()
    } catch (err) {
      setError(err)
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
        description="Rules are stored as JSON in the database and checked by the backend before they are saved. All sample rules are illustrative and not clinically validated."
        actions={<Button onClick={() => open('new')}>Add rule</Button>} />
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
                  <ul className="mt-1 list-disc pl-5">
                    {validation.errors.map((e) => <li key={e.path + e.message}><span className="font-mono text-xs">{e.path}</span>: {e.message}</li>)}
                  </ul>
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
                <Field label="Description" htmlFor="r-desc" error={fieldErrors.description} hint="Say what the rule checks and that it is an educational example.">
                  <textarea id="r-desc" rows={2} className={inputClass} value={form.description} onChange={(e) => update('description', e.target.value)} />
                </Field>
              </div>
              <div className="md:col-span-2">
                <Field label="Condition (JSON)" htmlFor="r-condition" required error={fieldErrors.condition}>
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
            <div className="mt-4 flex flex-wrap gap-2">
              <Button variant="secondary" onClick={check} disabled={busy}>Check rule</Button>
              <Button type="submit" disabled={busy}>{busy ? 'Working…' : 'Save rule'}</Button>
              <Button variant="ghost" onClick={() => { setEditing(null); setError(undefined) }}>Cancel</Button>
            </div>
          </form>
        </Card>
      )}

      <Card className="mb-6">
        {rules.loading ? <Loading /> : rules.error ? <ErrorAlert error={rules.error} onRetry={rules.reload} />
          : !rules.data?.length ? <EmptyState>No rules yet.</EmptyState> : (
            <Table>
              <thead><tr><th className={th}>Priority</th><th className={th}>Rule</th><th className={th}>Severity</th><th className={th}>Status</th><th className={th}></th></tr></thead>
              <tbody>
                {rules.data.map((rule) => (
                  <tr key={rule.id}>
                    <td className={td}>{rule.priority}</td>
                    <td className={td}>
                      <p className="font-medium text-slate-900">{rule.name} <span className="font-mono text-xs font-normal text-slate-500">{rule.ruleId}</span></p>
                      {rule.description && <p className="mt-0.5 max-w-xl text-xs text-slate-500">{rule.description}</p>}
                    </td>
                    <td className={td}><SeverityBadge severity={rule.severity} /></td>
                    <td className={td}>{rule.enabled ? <Badge tone="green">Enabled</Badge> : <Badge>Disabled</Badge>}</td>
                    <td className={`${td} whitespace-nowrap text-right`}>
                      <Button variant="ghost" onClick={() => toggle(rule)}>{rule.enabled ? 'Disable' : 'Enable'}</Button>
                      <Button variant="ghost" onClick={() => open(rule)}>Edit</Button>
                      <Button variant="danger" className="ml-1" onClick={() => remove(rule)}>Delete</Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </Table>
          )}
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
