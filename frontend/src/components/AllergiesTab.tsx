import { useState, type FormEvent, type ReactNode } from 'react'
import { allergiesApi } from '../api/allergies'
import { ApiError } from '../api/client'
import { drugsApi } from '../api/knowledgeBase'
import type { Allergy, AllergyCategory, AllergySeverity, PatientAllergies } from '../api/types'
import { useAsync } from '../hooks/useAsync'
import { formatDate, formatDateTime, optionalText, titleCase, todayForDateInput } from '../utils/format'
import { Alert, Badge, Button, EmptyState, ErrorAlert, Field, inputClass, Loading, Table, td, th } from './ui'

const categories: { value: AllergyCategory; label: string }[] = [
  { value: 'DRUG', label: 'Drug (from the knowledge base)' },
  { value: 'DRUG_CATEGORY', label: 'Drug category (from the knowledge base)' },
  { value: 'FOOD', label: 'Food' },
  { value: 'ENVIRONMENTAL', label: 'Environmental' },
  { value: 'OTHER', label: 'Other (free text, not matched automatically)' },
]
const severities: AllergySeverity[] = ['MILD', 'MODERATE', 'SEVERE', 'UNKNOWN']

/** The allergy-history status in words; "not assessed" is never shown as "no allergies". */
export function AllergyStatus({ data }: { data: PatientAllergies }) {
  const active = data.allergies.filter((a) => a.status === 'ACTIVE')
  if (data.assessment === 'ALLERGIES_RECORDED') {
    return (
      <div className="rounded-lg border border-red-300 bg-red-50 px-4 py-3 text-sm text-red-900" role="status">
        <p className="font-semibold">Allergies recorded</p>
        <p className="mt-1">{active.map((a) => `${a.allergen}${a.severity !== 'UNKNOWN' ? ` (${a.severity.toLowerCase()})` : ''}`).join(', ')}</p>
      </div>
    )
  }
  if (data.assessment === 'NO_KNOWN_ALLERGIES') {
    return (
      <div className="rounded-lg border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-900" role="status">
        <p className="font-semibold">No known allergies</p>
        <p className="mt-1">Confirmed by {data.noKnownAllergiesConfirmedBy} on {formatDateTime(data.noKnownAllergiesConfirmedAt)}.</p>
      </div>
    )
  }
  return (
    <div className="rounded-lg border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-900" role="status">
      <p className="font-semibold">Allergy history not assessed</p>
      <p className="mt-1">{data.assessmentExplanation}</p>
    </div>
  )
}

/** Compact allergy status for the top of the patient page. */
export function AllergyBanner({ patientId }: { patientId: number }) {
  const allergies = useAsync(() => allergiesApi.list(patientId), [patientId])
  if (allergies.loading) return null
  if (allergies.error || !allergies.data) return <ErrorAlert error={allergies.error} onRetry={allergies.reload} />
  return <div className="mb-6"><AllergyStatus data={allergies.data} /></div>
}

/** Record, edit and resolve allergies, and confirm "no known allergies". Records are never deleted. */
export function AllergiesTab({ patientId }: { patientId: number }) {
  const list = useAsync(() => allergiesApi.list(patientId), [patientId])
  const drugs = useAsync(() => drugsApi.list(), [])
  const [category, setCategory] = useState<AllergyCategory>('DRUG')
  const [drugId, setDrugId] = useState('')
  const [allergen, setAllergen] = useState('')
  const [reaction, setReaction] = useState('')
  const [severity, setSeverity] = useState<AllergySeverity>('UNKNOWN')
  const [recordedDate, setRecordedDate] = useState(todayForDateInput())
  const [notes, setNotes] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()
  const [message, setMessage] = useState<string>()
  const [editing, setEditing] = useState<Allergy>()
  const [edit, setEdit] = useState({ reaction: '', severity: 'UNKNOWN' as AllergySeverity, recordedDate: '', notes: '' })
  const [resolving, setResolving] = useState<Allergy>()
  const [resolveReason, setResolveReason] = useState('')
  const fieldErrors = error instanceof ApiError ? error.fieldErrors : {}
  const drugCategories = [...new Set(drugs.data?.map((d) => d.category) ?? [])].sort()

  async function run(action: () => Promise<unknown>, success: string, after?: () => void) {
    setBusy(true); setError(undefined); setMessage(undefined)
    try {
      await action()
      setMessage(success)
      after?.()
      list.reload()
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  function submit(e: FormEvent) {
    e.preventDefault()
    void run(() => allergiesApi.add(patientId, {
      category,
      drugId: category === 'DRUG' && drugId ? Number(drugId) : undefined,
      allergen: category === 'DRUG' ? undefined : optionalText(allergen),
      reaction: optionalText(reaction), severity,
      recordedDate: recordedDate === '' ? null : recordedDate,
      notes: optionalText(notes),
    }), 'Allergy recorded.', () => { setDrugId(''); setAllergen(''); setReaction(''); setNotes(''); setSeverity('UNKNOWN') })
  }

  function confirmNka() {
    if (!window.confirm('Record that this patient was asked and has no known allergies?')) return
    void run(() => allergiesApi.confirmNoKnownAllergies(patientId), '"No known allergies" recorded.')
  }

  const active = list.data?.allergies.filter((a) => a.status === 'ACTIVE') ?? []
  const resolved = list.data?.allergies.filter((a) => a.status === 'RESOLVED') ?? []

  return (
    <div className="space-y-6">
      {message && <Alert kind="success" onDismiss={() => setMessage(undefined)}>{message}</Alert>}
      {error !== undefined && <ErrorAlert error={error} onDismiss={() => setError(undefined)} />}
      {list.loading ? <Loading /> : list.error ? <ErrorAlert error={list.error} onRetry={list.reload} /> : list.data && (
        <>
          <AllergyStatus data={list.data} />
          {active.length === 0 && list.data.assessment !== 'NO_KNOWN_ALLERGIES' && (
            <Button variant="secondary" onClick={confirmNka} disabled={busy}>Confirm no known allergies</Button>
          )}
        </>
      )}

      <form onSubmit={submit} className="rounded-lg border border-slate-200 bg-slate-50 p-4" noValidate>
        <h3 className="mb-3 text-sm font-semibold text-slate-800">Record an allergy</h3>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <Field label="Allergen type" htmlFor="a-category" required error={fieldErrors.category}>
            <select id="a-category" className={inputClass} value={category} onChange={(e) => setCategory(e.target.value as AllergyCategory)}>
              {categories.map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
            </select>
          </Field>
          {category === 'DRUG' ? (
            <Field label="Drug" htmlFor="a-drug" required error={fieldErrors.drugId} hint="Matches every strength of this drug in safety checks.">
              <select id="a-drug" className={inputClass} value={drugId} onChange={(e) => setDrugId(e.target.value)}>
                <option value="">{drugs.loading ? 'Loading…' : 'Choose a drug'}</option>
                {drugs.data?.map((d) => <option key={d.id} value={d.id}>{d.name} {d.dosage}</option>)}
              </select>
            </Field>
          ) : category === 'DRUG_CATEGORY' ? (
            <Field label="Drug category" htmlFor="a-drug-category" required error={fieldErrors.allergen}>
              <select id="a-drug-category" className={inputClass} value={allergen} onChange={(e) => setAllergen(e.target.value)}>
                <option value="">Choose a category</option>
                {drugCategories.map((c) => <option key={c} value={c}>{c}</option>)}
              </select>
            </Field>
          ) : (
            <Field label="Allergen" htmlFor="a-allergen" required error={fieldErrors.allergen}>
              <input id="a-allergen" className={inputClass} value={allergen} onChange={(e) => setAllergen(e.target.value)} />
            </Field>
          )}
          <Field label="Reaction" htmlFor="a-reaction" error={fieldErrors.reaction}>
            <input id="a-reaction" className={inputClass} value={reaction} onChange={(e) => setReaction(e.target.value)} />
          </Field>
          <Field label="Severity" htmlFor="a-severity" required error={fieldErrors.severity}>
            <select id="a-severity" className={inputClass} value={severity} onChange={(e) => setSeverity(e.target.value as AllergySeverity)}>
              {severities.map((s) => <option key={s} value={s}>{titleCase(s)}</option>)}
            </select>
          </Field>
          <Field label="Recorded on" htmlFor="a-date" required error={fieldErrors.recordedDate}>
            <input id="a-date" type="date" className={inputClass} value={recordedDate} onChange={(e) => setRecordedDate(e.target.value)} />
          </Field>
          <Field label="Notes" htmlFor="a-notes" error={fieldErrors.notes}>
            <input id="a-notes" className={inputClass} value={notes} onChange={(e) => setNotes(e.target.value)} />
          </Field>
        </div>
        <Button type="submit" className="mt-3" disabled={busy}>{busy ? 'Saving…' : 'Save allergy'}</Button>
      </form>

      {editing && (
        <form className="rounded-lg border border-teal-200 bg-teal-50/40 p-4" noValidate onSubmit={(e) => {
          e.preventDefault()
          void run(() => allergiesApi.update(patientId, editing.id, {
            reaction: optionalText(edit.reaction), severity: edit.severity,
            recordedDate: edit.recordedDate === '' ? null : edit.recordedDate, notes: optionalText(edit.notes),
          }), 'Allergy updated.', () => setEditing(undefined))
        }}>
          <h3 className="mb-3 text-sm font-semibold text-slate-800">Edit allergy: {editing.allergen}</h3>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <Field label="Reaction" htmlFor="ae-reaction"><input id="ae-reaction" className={inputClass} value={edit.reaction} onChange={(e) => setEdit({ ...edit, reaction: e.target.value })} /></Field>
            <Field label="Severity" htmlFor="ae-severity">
              <select id="ae-severity" className={inputClass} value={edit.severity} onChange={(e) => setEdit({ ...edit, severity: e.target.value as AllergySeverity })}>
                {severities.map((s) => <option key={s} value={s}>{titleCase(s)}</option>)}
              </select>
            </Field>
            <Field label="Recorded on" htmlFor="ae-date"><input id="ae-date" type="date" className={inputClass} value={edit.recordedDate} onChange={(e) => setEdit({ ...edit, recordedDate: e.target.value })} /></Field>
            <Field label="Notes" htmlFor="ae-notes"><input id="ae-notes" className={inputClass} value={edit.notes} onChange={(e) => setEdit({ ...edit, notes: e.target.value })} /></Field>
          </div>
          <div className="mt-3 flex gap-2">
            <Button type="submit" disabled={busy}>Save changes</Button>
            <Button variant="secondary" onClick={() => setEditing(undefined)}>Cancel</Button>
          </div>
        </form>
      )}

      {resolving && (
        <form className="rounded-lg border border-amber-200 bg-amber-50 p-4" noValidate onSubmit={(e) => {
          e.preventDefault()
          void run(() => allergiesApi.resolve(patientId, resolving.id, resolveReason), `Allergy to ${resolving.allergen} resolved; it is kept in the history.`,
            () => { setResolving(undefined); setResolveReason('') })
        }}>
          <Field label={`Reason for resolving the allergy to ${resolving.allergen}`} htmlFor="a-resolve" required>
            <input id="a-resolve" className={inputClass} value={resolveReason} onChange={(e) => setResolveReason(e.target.value)} />
          </Field>
          <div className="mt-3 flex gap-2">
            <Button type="submit" variant="danger" disabled={busy || resolveReason.trim() === ''}>Confirm resolve</Button>
            <Button variant="secondary" onClick={() => setResolving(undefined)}>Cancel</Button>
          </div>
        </form>
      )}

      {list.data && (
        <>
          <section>
            <h3 className="mb-2 text-sm font-semibold text-slate-800">Active allergies</h3>
            {active.length === 0 ? <EmptyState>No active allergy records. See the status above: this is not the same as "no allergies".</EmptyState> : (
              <AllergyTable rows={active} actions={(a) => (
                <>
                  <Button variant="ghost" onClick={() => { setEditing(a); setEdit({ reaction: a.reaction ?? '', severity: a.severity, recordedDate: a.recordedDate, notes: a.notes ?? '' }) }}>Edit</Button>
                  <Button variant="danger" className="ml-1" onClick={() => { setResolving(a); setResolveReason('') }}>Resolve</Button>
                </>
              )} />
            )}
          </section>
          <section>
            <h3 className="mb-2 text-sm font-semibold text-slate-800">Resolved allergies</h3>
            {resolved.length === 0 ? <EmptyState>No resolved allergies.</EmptyState> : <AllergyTable rows={resolved} />}
          </section>
        </>
      )}
    </div>
  )
}

function AllergyTable({ rows, actions }: { rows: Allergy[]; actions?: (a: Allergy) => ReactNode }) {
  return (
    <Table>
      <thead><tr>
        <th className={th}>Allergen</th><th className={th}>Type</th><th className={th}>Reaction</th><th className={th}>Severity</th>
        <th className={th}>Recorded</th><th className={th}>Status</th>{actions && <th className={th}></th>}
      </tr></thead>
      <tbody>
        {rows.map((a) => (
          <tr key={a.id}>
            <td className={`${td} font-medium text-slate-900`}>{a.allergen}{a.notes && <p className="text-xs font-normal text-slate-500">{a.notes}</p>}</td>
            <td className={td}>{titleCase(a.category.replace('_', ' '))}</td>
            <td className={td}>{a.reaction ?? '—'}</td>
            <td className={td}><Badge tone={a.severity === 'SEVERE' ? 'red' : a.severity === 'MODERATE' ? 'amber' : 'slate'}>{titleCase(a.severity)}</Badge></td>
            <td className={td}>{formatDate(a.recordedDate)} <span className="text-xs text-slate-500">by {a.recordedBy}</span></td>
            <td className={td}>{a.status === 'ACTIVE' ? <Badge tone="red">Active</Badge> : (
              <><Badge>Resolved</Badge><p className="mt-1 text-xs text-slate-500">{a.resolutionReason} ({a.resolvedBy}, {formatDateTime(a.resolvedAt)})</p></>
            )}</td>
            {actions && <td className={`${td} whitespace-nowrap text-right`}>{actions(a)}</td>}
          </tr>
        ))}
      </tbody>
    </Table>
  )
}
