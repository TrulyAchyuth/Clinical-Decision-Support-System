import { useState, type FormEvent, type ReactNode } from 'react'
import { ApiError } from '../api/client'
import { drugsApi } from '../api/knowledgeBase'
import { medicationsApi } from '../api/medications'
import type { Medication, MedicationRoute, SafetyCheck } from '../api/types'
import { useAsync } from '../hooks/useAsync'
import { formatDate, optionalNumber, optionalText, titleCase, todayForDateInput } from '../utils/format'
import { SafetyCheckPanel } from './SafetyCheckPanel'
import { Alert, Badge, Button, EmptyState, ErrorAlert, Field, inputClass, Loading, Table, td, th } from './ui'

const routes: MedicationRoute[] = ['ORAL', 'INTRAVENOUS', 'INTRAMUSCULAR', 'SUBCUTANEOUS', 'TOPICAL', 'INHALED', 'SUBLINGUAL', 'RECTAL', 'OTHER']

interface DoseForm { dose: string; doseUnit: string; route: MedicationRoute | ''; frequency: string; startDate: string; endDate: string; notes: string }

const emptyDose = (): DoseForm => ({ dose: '', doseUnit: '', route: 'ORAL', frequency: '', startDate: todayForDateInput(), endDate: '', notes: '' })

function doseInput(form: DoseForm) {
  return {
    dose: optionalNumber(form.dose) ?? null,
    doseUnit: form.doseUnit,
    route: form.route === '' ? null : form.route,
    frequency: form.frequency,
    startDate: form.startDate === '' ? null : form.startDate,
    endDate: optionalText(form.endDate),
    notes: optionalText(form.notes),
  }
}

/** Review codes in `next` that were not in `previous`: new findings, or findings whose content changed. */
function changedReviewCodes(previous: SafetyCheck | undefined, next: SafetyCheck): Set<string> {
  if (!previous) return new Set()
  const before = new Set(previous.findings.map((f) => f.code))
  return new Set(next.findings.filter((f) => f.requiresReview && !before.has(f.code)).map((f) => f.code))
}

/**
 * A patient's medications. Adding one is two steps: run the safety check,
 * review (and acknowledge) any warnings, then save. When the medication
 * records change, or the saved data differs from the check, the check is
 * re-run: acknowledgements are kept only for findings that are unchanged, and
 * new or changed findings are marked. Nothing is deleted: medications are
 * discontinued or completed and kept as history.
 */
export function MedicationsTab({ patientId }: { patientId: number }) {
  const list = useAsync(() => medicationsApi.list(patientId), [patientId])
  const drugs = useAsync(() => drugsApi.list(), [])
  const [drugId, setDrugId] = useState('')
  const [form, setForm] = useState<DoseForm>(emptyDose)
  const [check, setCheck] = useState<SafetyCheck>()
  const [lastCheck, setLastCheck] = useState<SafetyCheck>()
  const [acknowledged, setAcknowledged] = useState<Set<string>>(new Set())
  const [changedCodes, setChangedCodes] = useState<Set<string>>(new Set())
  const [recheckNote, setRecheckNote] = useState<string>()
  const [overrideReason, setOverrideReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()
  const [message, setMessage] = useState<string>()
  const [editing, setEditing] = useState<Medication>()
  const [editForm, setEditForm] = useState<DoseForm>(emptyDose)
  const [stopping, setStopping] = useState<{ medication: Medication; action: 'discontinue' | 'complete' }>()
  const [stopReason, setStopReason] = useState('')
  const fieldErrors = error instanceof ApiError ? error.fieldErrors : {}

  const toReview = check?.findings.filter((f) => f.requiresReview) ?? []
  const allAcknowledged = toReview.every((f) => acknowledged.has(f.code))
  const canSave = check !== undefined && allAcknowledged && (toReview.length === 0 || overrideReason.trim() !== '')

  function chooseDrug(value: string) {
    setDrugId(value); setCheck(undefined); setAcknowledged(new Set()); setOverrideReason('')
    setChangedCodes(new Set()); setRecheckNote(undefined)
  }

  /** Shows a newer check, keeping acknowledgements only for findings that are exactly the same as before. */
  function showUpdatedCheck(next: SafetyCheck, note: string) {
    const changed = changedReviewCodes(check, next)
    const stillPresent = new Set(next.findings.map((f) => f.code))
    setCheck(next); setLastCheck(next); setChangedCodes(changed)
    setAcknowledged((prev) => new Set([...prev].filter((code) => stillPresent.has(code))))
    setRecheckNote(changed.size > 0 ? `${note} ${changed.size} new or changed finding(s) need review.` : `${note} No new or changed findings.`)
  }

  /** Re-runs an open safety check after the patient's medication records changed. */
  async function recheckAfterChange() {
    if (!check || !drugId) return
    try {
      showUpdatedCheck(await medicationsApi.safetyCheck(patientId, Number(drugId)), 'Medication records changed, so the safety check was re-run.')
    } catch (err) {
      setCheck(undefined); setError(err)
    }
  }

  async function runCheck() {
    setBusy(true); setError(undefined); setMessage(undefined)
    try {
      const result = await medicationsApi.safetyCheck(patientId, Number(drugId))
      setCheck(result); setLastCheck(result); setAcknowledged(new Set()); setChangedCodes(new Set()); setRecheckNote(undefined)
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  async function save(e: FormEvent) {
    e.preventDefault()
    setBusy(true); setError(undefined); setMessage(undefined)
    try {
      const saved = await medicationsApi.add(patientId, {
        drugId: Number(drugId), ...doseInput(form),
        acknowledgedWarnings: [...acknowledged], overrideReason: optionalText(overrideReason),
      })
      setMessage(`${saved.drugName} ${saved.drugStrength} added to the active medications.`)
      setDrugId(''); setForm(emptyDose()); setCheck(undefined); setAcknowledged(new Set()); setOverrideReason('')
      setChangedCodes(new Set()); setRecheckNote(undefined)
      list.reload()
    } catch (err) {
      // The data changed since the check (e.g. a new allergy): show the new check to review.
      const fresh = err instanceof ApiError ? (err.body.safetyCheck as SafetyCheck | undefined) : undefined
      if (fresh) showUpdatedCheck(fresh, 'The patient data changed since the check.')
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  function startEdit(m: Medication) {
    setEditing(m); setError(undefined); setMessage(undefined)
    setEditForm({ dose: String(m.dose), doseUnit: m.doseUnit, route: m.route, frequency: m.frequency, startDate: m.startDate, endDate: m.endDate ?? '', notes: m.notes ?? '' })
  }

  async function saveEdit(e: FormEvent) {
    e.preventDefault()
    if (!editing) return
    setBusy(true); setError(undefined)
    try {
      await medicationsApi.update(patientId, editing.id, doseInput(editForm))
      setMessage(`${editing.drugName} updated.`); setEditing(undefined)
      list.reload()
      await recheckAfterChange()
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  async function confirmStop(e: FormEvent) {
    e.preventDefault()
    if (!stopping) return
    setBusy(true); setError(undefined)
    try {
      const { medication, action } = stopping
      if (action === 'discontinue') await medicationsApi.discontinue(patientId, medication.id, stopReason)
      else await medicationsApi.complete(patientId, medication.id, optionalText(stopReason))
      setMessage(`${medication.drugName} ${action === 'discontinue' ? 'discontinued' : 'completed'}; it is kept in the medication history.`)
      setStopping(undefined); setStopReason('')
      list.reload()
      await recheckAfterChange()
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  const active = list.data?.filter((m) => m.status === 'ACTIVE') ?? []
  const history = list.data?.filter((m) => m.status !== 'ACTIVE') ?? []

  return (
    <div className="space-y-6">
      {message && <Alert kind="success" onDismiss={() => setMessage(undefined)}>{message}</Alert>}
      {error !== undefined && !editing && !stopping && <ErrorAlert error={error} onDismiss={() => setError(undefined)} />}

      <form onSubmit={save} className="rounded-lg border border-slate-200 bg-slate-50 p-4" noValidate>
        <h3 className="mb-3 text-sm font-semibold text-slate-800">Add a medication</h3>
        {drugs.error !== undefined && <ErrorAlert error={drugs.error} onRetry={drugs.reload} />}
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Field label="Drug (knowledge base)" htmlFor="m-drug" required error={fieldErrors.drugId}>
            <select id="m-drug" className={inputClass} value={drugId} onChange={(e) => chooseDrug(e.target.value)}>
              <option value="">{drugs.loading ? 'Loading…' : 'Choose a drug'}</option>
              {drugs.data?.map((d) => <option key={d.id} value={d.id}>{d.name} {d.dosage}</option>)}
            </select>
          </Field>
          <DoseFields form={form} setForm={setForm} errors={fieldErrors} prefix="m" />
        </div>
        <div className="mt-3 flex flex-wrap gap-2">
          <Button variant="secondary" onClick={runCheck} disabled={busy || !drugId}>1. Run safety check</Button>
          <Button type="submit" disabled={busy || !canSave}>{busy ? 'Working…' : '2. Save medication'}</Button>
        </div>
        {!check && drugId && <p className="mt-2 text-xs text-slate-600">Run the safety check before saving. It compares the drug with this patient's allergies and active medications.</p>}
        {check && (
          <div className="mt-4 space-y-3">
            {recheckNote && <Alert kind="info" onDismiss={() => setRecheckNote(undefined)}>{recheckNote}</Alert>}
            <SafetyCheckPanel check={check} acknowledged={acknowledged} changedCodes={changedCodes}
              onAcknowledge={(code, checked) => setAcknowledged((prev) => {
                const next = new Set(prev)
                if (checked) next.add(code); else next.delete(code)
                return next
              })} />
            {toReview.length > 0 && (
              <Field label="Reason for continuing despite the warnings" htmlFor="m-override" required error={fieldErrors.overrideReason}
                hint="Stored with the medication together with the acknowledged warnings.">
                <textarea id="m-override" rows={2} className={inputClass} value={overrideReason} onChange={(e) => setOverrideReason(e.target.value)} />
              </Field>
            )}
          </div>
        )}
      </form>

      {editing && (
        <form onSubmit={saveEdit} className="rounded-lg border border-teal-200 bg-teal-50/40 p-4" noValidate>
          <h3 className="mb-3 text-sm font-semibold text-slate-800">Edit {editing.drugName} {editing.drugStrength}</h3>
          {error !== undefined && <ErrorAlert error={error} />}
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <DoseFields form={editForm} setForm={setEditForm} errors={fieldErrors} prefix="e" />
          </div>
          <div className="mt-3 flex gap-2">
            <Button type="submit" disabled={busy}>Save changes</Button>
            <Button variant="secondary" onClick={() => { setEditing(undefined); setError(undefined) }}>Cancel</Button>
          </div>
        </form>
      )}

      {stopping && (
        <form onSubmit={confirmStop} className="rounded-lg border border-amber-200 bg-amber-50 p-4" noValidate>
          <h3 className="mb-3 text-sm font-semibold text-slate-800">
            {stopping.action === 'discontinue' ? 'Discontinue' : 'Complete'} {stopping.medication.drugName} {stopping.medication.drugStrength}
          </h3>
          {error !== undefined && <ErrorAlert error={error} />}
          <Field label={stopping.action === 'discontinue' ? 'Reason for discontinuing' : 'Note (optional)'} htmlFor="m-stop" required={stopping.action === 'discontinue'}>
            <input id="m-stop" className={inputClass} value={stopReason} onChange={(e) => setStopReason(e.target.value)} />
          </Field>
          <p className="mt-2 text-xs text-slate-600">The end date is set to today. The record stays in the medication history.</p>
          <div className="mt-3 flex gap-2">
            <Button type="submit" variant="danger" disabled={busy || (stopping.action === 'discontinue' && stopReason.trim() === '')}>
              Confirm {stopping.action === 'discontinue' ? 'discontinue' : 'complete'}
            </Button>
            <Button variant="secondary" onClick={() => { setStopping(undefined); setError(undefined) }}>Cancel</Button>
          </div>
        </form>
      )}

      {list.loading ? <Loading /> : list.error ? <ErrorAlert error={list.error} onRetry={list.reload} /> : (
        <>
          <section>
            <h3 className="mb-2 text-sm font-semibold text-slate-800">Active medications</h3>
            {active.length === 0 ? <EmptyState>No active medications recorded. The medication list may be incomplete.</EmptyState> : (
              <MedicationTable rows={active} actions={(m) => (
                <>
                  <Button variant="ghost" onClick={() => startEdit(m)}>Edit</Button>
                  <Button variant="ghost" onClick={() => { setStopping({ medication: m, action: 'complete' }); setStopReason(''); setError(undefined) }}>Complete</Button>
                  <Button variant="danger" className="ml-1" onClick={() => { setStopping({ medication: m, action: 'discontinue' }); setStopReason(''); setError(undefined) }}>Discontinue</Button>
                </>
              )} />
            )}
          </section>
          <section>
            <h3 className="mb-2 text-sm font-semibold text-slate-800">Medication history</h3>
            {history.length === 0 ? <EmptyState>No discontinued or completed medications.</EmptyState> : <MedicationTable rows={history} />}
          </section>
        </>
      )}

      {lastCheck && !check && (
        <section>
          <h3 className="mb-2 text-sm font-semibold text-slate-800">Last safety check</h3>
          <p className="mb-2 text-xs text-slate-500">As at the time shown; run a new check before relying on it.</p>
          <SafetyCheckPanel check={lastCheck} />
        </section>
      )}
    </div>
  )
}

function DoseFields({ form, setForm, errors, prefix }: {
  form: DoseForm; setForm: (f: DoseForm) => void; errors: Record<string, string>; prefix: string
}) {
  const set = (key: keyof DoseForm) => (e: { target: { value: string } }) => setForm({ ...form, [key]: e.target.value })
  return (
    <>
      <Field label="Dose" htmlFor={`${prefix}-dose`} required error={errors.dose}>
        <input id={`${prefix}-dose`} type="number" step="0.01" min="0" className={inputClass} value={form.dose} onChange={set('dose')} />
      </Field>
      <Field label="Dose unit" htmlFor={`${prefix}-unit`} required error={errors.doseUnit} hint='e.g. "mg" or "tablet"'>
        <input id={`${prefix}-unit`} className={inputClass} value={form.doseUnit} onChange={set('doseUnit')} />
      </Field>
      <Field label="Route" htmlFor={`${prefix}-route`} required error={errors.route}>
        <select id={`${prefix}-route`} className={inputClass} value={form.route} onChange={set('route')}>
          {routes.map((r) => <option key={r} value={r}>{titleCase(r)}</option>)}
        </select>
      </Field>
      <Field label="Frequency" htmlFor={`${prefix}-freq`} required error={errors.frequency} hint='e.g. "twice daily"'>
        <input id={`${prefix}-freq`} className={inputClass} value={form.frequency} onChange={set('frequency')} />
      </Field>
      <Field label="Start date" htmlFor={`${prefix}-start`} required error={errors.startDate}>
        <input id={`${prefix}-start`} type="date" className={inputClass} value={form.startDate} onChange={set('startDate')} />
      </Field>
      <Field label="End date" htmlFor={`${prefix}-end`} error={errors.endDate}>
        <input id={`${prefix}-end`} type="date" className={inputClass} value={form.endDate} onChange={set('endDate')} />
      </Field>
      <Field label="Notes" htmlFor={`${prefix}-notes`} error={errors.notes}>
        <input id={`${prefix}-notes`} className={inputClass} value={form.notes} onChange={set('notes')} />
      </Field>
    </>
  )
}

function MedicationTable({ rows, actions }: { rows: Medication[]; actions?: (m: Medication) => ReactNode }) {
  return (
    <Table>
      <thead><tr>
        <th className={th}>Medication</th><th className={th}>Dose</th><th className={th}>Dates</th><th className={th}>Status</th>
        <th className={th}>Safety warnings acknowledged</th>{actions && <th className={th}></th>}
      </tr></thead>
      <tbody>
        {rows.map((m) => (
          <tr key={m.id}>
            <td className={td}><span className="font-medium text-slate-900">{m.drugName}</span> <span className="text-slate-500">{m.drugStrength}</span>
              {m.notes && <p className="text-xs text-slate-500">{m.notes}</p>}</td>
            <td className={td}>{m.dose} {m.doseUnit}, {titleCase(m.route)}, {m.frequency}</td>
            <td className={td}>{formatDate(m.startDate)} – {m.endDate ? formatDate(m.endDate) : 'ongoing'}</td>
            <td className={td}>
              <Badge tone={m.status === 'ACTIVE' ? 'green' : m.status === 'DISCONTINUED' ? 'red' : 'slate'}>{titleCase(m.status)}</Badge>
              {m.statusReason && <p className="mt-1 text-xs text-slate-500">{m.statusReason}</p>}
            </td>
            <td className={td}>
              {m.safetyWarnings.length === 0 ? <span className="text-xs text-slate-500">None</span> : (
                <details className="text-xs">
                  <summary className="cursor-pointer font-medium text-red-800">{m.safetyWarnings.length} warning(s) acknowledged by {m.recordedBy}</summary>
                  <ul className="mt-1 list-disc pl-4 text-slate-700">{m.safetyWarnings.map((w) => <li key={w}>{w}</li>)}</ul>
                  {m.safetyOverrideReason && <p className="mt-1 text-slate-600">Reason: {m.safetyOverrideReason}</p>}
                </details>
              )}
            </td>
            {actions && <td className={`${td} whitespace-nowrap text-right`}>{actions(m)}</td>}
          </tr>
        ))}
      </tbody>
    </Table>
  )
}
