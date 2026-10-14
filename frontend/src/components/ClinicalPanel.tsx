import { useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import { clinicalRecordsApi } from '../api/clinicalRecords'
import { decisionsApi } from '../api/decisions'
import { diseasesApi, labTestsApi } from '../api/knowledgeBase'
import type { ConditionStatus, Decision, LabFlag } from '../api/types'
import { useAsync } from '../hooks/useAsync'
import {
  dateTimeInputToIso, formatDate, formatDateTime, nowForDateTimeInput, optionalNumber, optionalText, titleCase,
} from '../utils/format'
import { DecisionResult } from './DecisionResult'
import { Alert, Badge, Button, Card, EmptyState, ErrorAlert, Field, inputClass, Loading, Table, td, th } from './ui'

export type ClinicalTab = 'decision' | 'vitals' | 'labs' | 'conditions' | 'history'

const tabs: { id: ClinicalTab; label: string }[] = [
  { id: 'decision', label: 'Request decision' },
  { id: 'vitals', label: 'Vital signs' },
  { id: 'labs', label: 'Lab results' },
  { id: 'conditions', label: 'Conditions' },
  { id: 'history', label: 'Decision history' },
]

/** Everything clinical about one patient: observations, history and decisions. */
export function ClinicalPanel({ patientId, initialTab = 'decision' }: { patientId: number; initialTab?: ClinicalTab }) {
  const [tab, setTab] = useState<ClinicalTab>(initialTab)
  return (
    <Card>
      <div className="-mx-5 -mt-5 mb-5 flex overflow-x-auto border-b border-slate-200 px-3" role="tablist">
        {tabs.map((t) => (
          <button
            key={t.id}
            type="button"
            role="tab"
            aria-selected={tab === t.id}
            onClick={() => setTab(t.id)}
            className={`whitespace-nowrap border-b-2 px-3 py-3 text-sm font-medium ${tab === t.id ? 'border-teal-700 text-teal-800' : 'border-transparent text-slate-500 hover:text-slate-800'}`}
          >
            {t.label}
          </button>
        ))}
      </div>
      {tab === 'decision' && <DecisionTab patientId={patientId} />}
      {tab === 'vitals' && <VitalSignsTab patientId={patientId} />}
      {tab === 'labs' && <LabResultsTab patientId={patientId} />}
      {tab === 'conditions' && <ConditionsTab patientId={patientId} />}
      {tab === 'history' && <HistoryTab patientId={patientId} />}
    </Card>
  )
}

// ---------------------------------------------------------------- decision

function DecisionTab({ patientId }: { patientId: number }) {
  const [decision, setDecision] = useState<Decision>()
  const [error, setError] = useState<unknown>()
  const [running, setRunning] = useState(false)

  async function run() {
    setRunning(true)
    setError(undefined)
    try {
      setDecision(await decisionsApi.request(patientId))
    } catch (err) {
      setError(err)
    } finally {
      setRunning(false)
    }
  }

  return (
    <div>
      <p className="mb-4 text-sm text-slate-600">
        The backend checks the patient's recent vital signs, lab results and active conditions against every
        enabled clinical rule. Each request is saved and appears under <strong>Decision history</strong>.
      </p>
      <Button onClick={run} disabled={running}>{running ? 'Evaluating rules…' : 'Request clinical decision'}</Button>
      <div className="mt-5">
        {error !== undefined && <ErrorAlert error={error} />}
        {decision && <DecisionResult decision={decision} />}
      </div>
    </div>
  )
}

function HistoryTab({ patientId }: { patientId: number }) {
  const history = useAsync(() => decisionsApi.history(patientId), [patientId])
  const [openId, setOpenId] = useState<number>()

  if (history.loading) return <Loading label="Loading decision history…" />
  if (history.error) return <ErrorAlert error={history.error} onRetry={history.reload} />
  if (!history.data?.length) return <EmptyState>No decisions have been requested for this patient yet.</EmptyState>

  return (
    <ul className="space-y-3">
      {history.data.map((decision) => (
        <li key={decision.id} className="rounded-lg border border-slate-200">
          <button
            type="button"
            className="flex w-full flex-wrap items-center gap-2 px-4 py-3 text-left hover:bg-slate-50"
            onClick={() => setOpenId(openId === decision.id ? undefined : decision.id)}
            aria-expanded={openId === decision.id}
          >
            <span className="text-sm font-medium text-slate-900">{formatDateTime(decision.requestedAt)}</span>
            {decision.outcome === 'RULES_MATCHED'
              ? <Badge tone="red">{decision.matchedRules.length} matched</Badge>
              : <Badge>No configured rule matched</Badge>}
            {decision.insufficientData.length > 0 && <Badge tone="amber">{decision.insufficientData.length} insufficient data</Badge>}
            <span className="ml-auto text-xs text-teal-800">{openId === decision.id ? 'Hide' : 'Show details'}</span>
          </button>
          {openId === decision.id && <div className="border-t border-slate-200 p-4"><DecisionResult decision={decision} /></div>}
        </li>
      ))}
    </ul>
  )
}

// ---------------------------------------------------------------- vital signs

const emptyVitals = { systolicBp: '', diastolicBp: '', temperature: '', heartRate: '', respiratoryRate: '', oxygenSaturation: '', notes: '' }

function VitalSignsTab({ patientId }: { patientId: number }) {
  const list = useAsync(() => clinicalRecordsApi.listVitalSigns(patientId), [patientId])
  const [form, setForm] = useState(emptyVitals)
  const [recordedAt, setRecordedAt] = useState(nowForDateTimeInput())
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<unknown>()
  const [message, setMessage] = useState<string>()
  const fieldErrors = error instanceof ApiError ? error.fieldErrors : {}

  const set = (key: keyof typeof emptyVitals) => (e: { target: { value: string } }) => setForm({ ...form, [key]: e.target.value })

  async function submit(e: FormEvent) {
    e.preventDefault()
    setSaving(true); setError(undefined); setMessage(undefined)
    try {
      await clinicalRecordsApi.addVitalSigns(patientId, {
        recordedAt: dateTimeInputToIso(recordedAt),
        systolicBp: optionalNumber(form.systolicBp),
        diastolicBp: optionalNumber(form.diastolicBp),
        temperature: optionalNumber(form.temperature),
        heartRate: optionalNumber(form.heartRate),
        respiratoryRate: optionalNumber(form.respiratoryRate),
        oxygenSaturation: optionalNumber(form.oxygenSaturation),
        notes: optionalText(form.notes),
      })
      setForm(emptyVitals)
      setRecordedAt(nowForDateTimeInput())
      setMessage('Vital signs recorded.')
      list.reload()
    } catch (err) {
      setError(err)
    } finally {
      setSaving(false)
    }
  }

  async function remove(id: number) {
    if (!window.confirm('Delete this set of vital signs?')) return
    setError(undefined); setMessage(undefined)
    try {
      await clinicalRecordsApi.removeVitalSigns(patientId, id)
      setMessage('Vital signs deleted.')
      list.reload()
    } catch (err) {
      setError(err)
    }
  }

  const blood = fieldErrors.bloodPressureComplete ?? fieldErrors.bloodPressureConsistent
  return (
    <div className="space-y-6">
      <form onSubmit={submit} className="rounded-lg border border-slate-200 bg-slate-50 p-4" noValidate>
        <h3 className="mb-3 text-sm font-semibold text-slate-800">Record vital signs</h3>
        {message && <Alert kind="success" onDismiss={() => setMessage(undefined)}>{message}</Alert>}
        {error !== undefined && <ErrorAlert error={error} onDismiss={() => setError(undefined)} />}
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Field label="Recorded at" htmlFor="v-time" required error={fieldErrors.recordedAt}>
            <input id="v-time" type="datetime-local" className={inputClass} value={recordedAt} onChange={(e) => setRecordedAt(e.target.value)} required />
          </Field>
          <Field label="Systolic BP (mmHg)" htmlFor="v-sys" error={fieldErrors.systolicBp ?? blood}>
            <input id="v-sys" type="number" inputMode="numeric" className={inputClass} value={form.systolicBp} onChange={set('systolicBp')} />
          </Field>
          <Field label="Diastolic BP (mmHg)" htmlFor="v-dia" error={fieldErrors.diastolicBp}>
            <input id="v-dia" type="number" inputMode="numeric" className={inputClass} value={form.diastolicBp} onChange={set('diastolicBp')} />
          </Field>
          <Field label="Temperature (°C)" htmlFor="v-temp" error={fieldErrors.temperature}>
            <input id="v-temp" type="number" step="0.1" inputMode="decimal" className={inputClass} value={form.temperature} onChange={set('temperature')} />
          </Field>
          <Field label="Heart rate (beats/min)" htmlFor="v-hr" error={fieldErrors.heartRate}>
            <input id="v-hr" type="number" inputMode="numeric" className={inputClass} value={form.heartRate} onChange={set('heartRate')} />
          </Field>
          <Field label="Respiratory rate (breaths/min)" htmlFor="v-rr" error={fieldErrors.respiratoryRate}>
            <input id="v-rr" type="number" inputMode="numeric" className={inputClass} value={form.respiratoryRate} onChange={set('respiratoryRate')} />
          </Field>
          <Field label="Oxygen saturation (%)" htmlFor="v-spo2" error={fieldErrors.oxygenSaturation}>
            <input id="v-spo2" type="number" inputMode="numeric" className={inputClass} value={form.oxygenSaturation} onChange={set('oxygenSaturation')} />
          </Field>
          <Field label="Notes" htmlFor="v-notes" error={fieldErrors.notes}>
            <input id="v-notes" className={inputClass} value={form.notes} onChange={set('notes')} />
          </Field>
        </div>
        {fieldErrors.anyMeasurementPresent && <p className="mt-2 text-xs text-red-700">{fieldErrors.anyMeasurementPresent}</p>}
        <p className="mt-3 text-xs text-slate-500">
          Fill in only what was measured. Blood pressure needs both values. The backend rejects implausible values,
          and the decision engine only uses recent recordings.
        </p>
        <Button type="submit" className="mt-3" disabled={saving}>{saving ? 'Saving…' : 'Save vital signs'}</Button>
      </form>

      {list.loading ? <Loading /> : list.error ? <ErrorAlert error={list.error} onRetry={list.reload} />
        : !list.data?.length ? <EmptyState>No vital signs recorded yet.</EmptyState> : (
          <Table>
            <thead><tr>
              <th className={th}>Recorded</th><th className={th}>BP (mmHg)</th><th className={th}>Temp (°C)</th>
              <th className={th}>HR</th><th className={th}>RR</th><th className={th}>SpO₂ (%)</th><th className={th}>Notes</th><th className={th}></th>
            </tr></thead>
            <tbody>
              {list.data.map((v) => (
                <tr key={v.id}>
                  <td className={td}>{formatDateTime(v.recordedAt)}</td>
                  <td className={td}>{v.systolicBp != null ? `${v.systolicBp}/${v.diastolicBp}` : '—'}</td>
                  <td className={td}>{v.temperature ?? '—'}</td>
                  <td className={td}>{v.heartRate ?? '—'}</td>
                  <td className={td}>{v.respiratoryRate ?? '—'}</td>
                  <td className={td}>{v.oxygenSaturation ?? '—'}</td>
                  <td className={td}>{v.notes ?? ''}</td>
                  <td className={`${td} text-right`}><Button variant="danger" onClick={() => remove(v.id)}>Delete</Button></td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
    </div>
  )
}

// ---------------------------------------------------------------- lab results

const flagTone: Record<LabFlag, 'red' | 'sky' | 'green'> = { HIGH: 'red', LOW: 'sky', NORMAL: 'green' }

function LabResultsTab({ patientId }: { patientId: number }) {
  const list = useAsync(() => clinicalRecordsApi.listLabResults(patientId), [patientId])
  const labTests = useAsync(() => labTestsApi.list(), [])
  const [labTestId, setLabTestId] = useState('')
  const [value, setValue] = useState('')
  const [measuredAt, setMeasuredAt] = useState(nowForDateTimeInput())
  const [notes, setNotes] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<unknown>()
  const [message, setMessage] = useState<string>()
  const fieldErrors = error instanceof ApiError ? error.fieldErrors : {}
  const selected = labTests.data?.find((t) => String(t.id) === labTestId)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setSaving(true); setError(undefined); setMessage(undefined)
    try {
      await clinicalRecordsApi.addLabResult(patientId, {
        labTestId: Number(labTestId),
        value: Number(value),
        measuredAt: dateTimeInputToIso(measuredAt),
        notes: optionalText(notes),
      })
      setValue(''); setNotes(''); setMeasuredAt(nowForDateTimeInput())
      setMessage('Lab result recorded.')
      list.reload()
    } catch (err) {
      setError(err)
    } finally {
      setSaving(false)
    }
  }

  async function remove(id: number) {
    if (!window.confirm('Delete this lab result?')) return
    setError(undefined); setMessage(undefined)
    try {
      await clinicalRecordsApi.removeLabResult(patientId, id)
      setMessage('Lab result deleted.')
      list.reload()
    } catch (err) {
      setError(err)
    }
  }

  return (
    <div className="space-y-6">
      <form onSubmit={submit} className="rounded-lg border border-slate-200 bg-slate-50 p-4" noValidate>
        <h3 className="mb-3 text-sm font-semibold text-slate-800">Record a lab result</h3>
        {message && <Alert kind="success" onDismiss={() => setMessage(undefined)}>{message}</Alert>}
        {error !== undefined && <ErrorAlert error={error} onDismiss={() => setError(undefined)} />}
        {labTests.error !== undefined && <ErrorAlert error={labTests.error} onRetry={labTests.reload} />}
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Field label="Lab test" htmlFor="l-test" required error={fieldErrors.labTestId}>
            <select id="l-test" className={inputClass} value={labTestId} onChange={(e) => setLabTestId(e.target.value)} required>
              <option value="">{labTests.loading ? 'Loading…' : 'Choose a lab test'}</option>
              {labTests.data?.map((t) => <option key={t.id} value={t.id}>{t.name} ({t.unit})</option>)}
            </select>
          </Field>
          <Field label={`Value${selected ? ` (${selected.unit})` : ''}`} htmlFor="l-value" required error={fieldErrors.value}
            hint={selected ? `Educational reference range ${selected.referenceRangeLow}–${selected.referenceRangeHigh} ${selected.unit}` : undefined}>
            <input id="l-value" type="number" step="0.01" inputMode="decimal" className={inputClass} value={value} onChange={(e) => setValue(e.target.value)} required />
          </Field>
          <Field label="Measured at" htmlFor="l-time" required error={fieldErrors.measuredAt}>
            <input id="l-time" type="datetime-local" className={inputClass} value={measuredAt} onChange={(e) => setMeasuredAt(e.target.value)} required />
          </Field>
          <Field label="Notes" htmlFor="l-notes" error={fieldErrors.notes}>
            <input id="l-notes" className={inputClass} value={notes} onChange={(e) => setNotes(e.target.value)} />
          </Field>
        </div>
        <p className="mt-3 text-xs text-slate-500">Enter the value in the lab test's unit shown above; the backend does not convert units.</p>
        <Button type="submit" className="mt-3" disabled={saving || !labTestId || value === ''}>{saving ? 'Saving…' : 'Save lab result'}</Button>
      </form>

      {list.loading ? <Loading /> : list.error ? <ErrorAlert error={list.error} onRetry={list.reload} />
        : !list.data?.length ? <EmptyState>No lab results recorded yet.</EmptyState> : (
          <Table>
            <thead><tr>
              <th className={th}>Measured</th><th className={th}>Lab test</th><th className={th}>Value</th>
              <th className={th}>Flag</th><th className={th}>Reference range (educational)</th><th className={th}>Notes</th><th className={th}></th>
            </tr></thead>
            <tbody>
              {list.data.map((r) => (
                <tr key={r.id}>
                  <td className={td}>{formatDateTime(r.measuredAt)}</td>
                  <td className={td}>{r.labTestName}</td>
                  <td className={td}>{r.value} {r.unit}</td>
                  <td className={td}><Badge tone={flagTone[r.flag]}>{r.flag}</Badge></td>
                  <td className={td}>{r.referenceRangeLow}–{r.referenceRangeHigh} {r.unit}</td>
                  <td className={td}>{r.notes ?? ''}</td>
                  <td className={`${td} text-right`}><Button variant="danger" onClick={() => remove(r.id)}>Delete</Button></td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
    </div>
  )
}

// ---------------------------------------------------------------- conditions

function ConditionsTab({ patientId }: { patientId: number }) {
  const list = useAsync(() => clinicalRecordsApi.listConditions(patientId), [patientId])
  const diseases = useAsync(() => diseasesApi.list(), [])
  const [diseaseId, setDiseaseId] = useState('')
  const [status, setStatus] = useState<ConditionStatus>('ACTIVE')
  const [onsetDate, setOnsetDate] = useState('')
  const [notes, setNotes] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<unknown>()
  const [message, setMessage] = useState<string>()
  const fieldErrors = error instanceof ApiError ? error.fieldErrors : {}

  async function submit(e: FormEvent) {
    e.preventDefault()
    setSaving(true); setError(undefined); setMessage(undefined)
    try {
      await clinicalRecordsApi.addCondition(patientId, {
        diseaseId: Number(diseaseId), status, onsetDate: optionalText(onsetDate), notes: optionalText(notes),
      })
      setDiseaseId(''); setOnsetDate(''); setNotes(''); setStatus('ACTIVE')
      setMessage('Condition added.')
      list.reload()
    } catch (err) {
      setError(err)
    } finally {
      setSaving(false)
    }
  }

  async function remove(id: number) {
    if (!window.confirm('Remove this condition from the patient history?')) return
    setError(undefined); setMessage(undefined)
    try {
      await clinicalRecordsApi.removeCondition(patientId, id)
      setMessage('Condition removed.')
      list.reload()
    } catch (err) {
      setError(err)
    }
  }

  return (
    <div className="space-y-6">
      <form onSubmit={submit} className="rounded-lg border border-slate-200 bg-slate-50 p-4" noValidate>
        <h3 className="mb-3 text-sm font-semibold text-slate-800">Add a condition (patient history)</h3>
        {message && <Alert kind="success" onDismiss={() => setMessage(undefined)}>{message}</Alert>}
        {error !== undefined && <ErrorAlert error={error} onDismiss={() => setError(undefined)} />}
        {diseases.error !== undefined && <ErrorAlert error={diseases.error} onRetry={diseases.reload} />}
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Field label="Disease" htmlFor="c-disease" required error={fieldErrors.diseaseId}>
            <select id="c-disease" className={inputClass} value={diseaseId} onChange={(e) => setDiseaseId(e.target.value)} required>
              <option value="">{diseases.loading ? 'Loading…' : 'Choose a disease'}</option>
              {diseases.data?.map((d) => <option key={d.id} value={d.id}>{d.icd10Code} — {d.name}</option>)}
            </select>
          </Field>
          <Field label="Status" htmlFor="c-status" error={fieldErrors.status} hint="Only active conditions are used by rules.">
            <select id="c-status" className={inputClass} value={status} onChange={(e) => setStatus(e.target.value as ConditionStatus)}>
              <option value="ACTIVE">Active</option>
              <option value="RESOLVED">Resolved</option>
            </select>
          </Field>
          <Field label="Onset date" htmlFor="c-onset" error={fieldErrors.onsetDate}>
            <input id="c-onset" type="date" className={inputClass} value={onsetDate} onChange={(e) => setOnsetDate(e.target.value)} />
          </Field>
          <Field label="Notes" htmlFor="c-notes" error={fieldErrors.notes}>
            <input id="c-notes" className={inputClass} value={notes} onChange={(e) => setNotes(e.target.value)} />
          </Field>
        </div>
        <Button type="submit" className="mt-3" disabled={saving || !diseaseId}>{saving ? 'Saving…' : 'Add condition'}</Button>
      </form>

      {list.loading ? <Loading /> : list.error ? <ErrorAlert error={list.error} onRetry={list.reload} />
        : !list.data?.length ? <EmptyState>No conditions recorded.</EmptyState> : (
          <Table>
            <thead><tr>
              <th className={th}>ICD-10</th><th className={th}>Disease</th><th className={th}>Status</th>
              <th className={th}>Onset</th><th className={th}>Notes</th><th className={th}></th>
            </tr></thead>
            <tbody>
              {list.data.map((c) => (
                <tr key={c.id}>
                  <td className={`${td} font-mono`}>{c.icd10Code}</td>
                  <td className={td}>{c.diseaseName}</td>
                  <td className={td}><Badge tone={c.status === 'ACTIVE' ? 'teal' : 'slate'}>{titleCase(c.status)}</Badge></td>
                  <td className={td}>{formatDate(c.onsetDate)}</td>
                  <td className={td}>{c.notes ?? ''}</td>
                  <td className={`${td} text-right`}><Button variant="danger" onClick={() => remove(c.id)}>Remove</Button></td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
    </div>
  )
}
