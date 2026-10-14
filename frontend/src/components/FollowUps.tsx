import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { ApiError } from '../api/client'
import { followUpsApi } from '../api/followUps'
import type { FollowUp, FollowUpTiming } from '../api/types'
import { useAsync } from '../hooks/useAsync'
import { dateTimeInputToIso, formatDateTime, optionalText } from '../utils/format'
import { Alert, Badge, Button, EmptyState, ErrorAlert, Field, inputClass, Loading, Table, td, th } from './ui'

const timingBadge: Record<FollowUpTiming, { label: string; tone: 'red' | 'amber' | 'sky' | 'green' | 'slate' }> = {
  OVERDUE: { label: 'Overdue', tone: 'red' },
  UPCOMING: { label: 'Upcoming', tone: 'amber' },
  LATER: { label: 'Scheduled', tone: 'sky' },
  COMPLETED: { label: 'Completed', tone: 'green' },
  CANCELLED: { label: 'Cancelled', tone: 'slate' },
}

export function TimingBadge({ timing }: { timing: FollowUpTiming }) {
  return <Badge tone={timingBadge[timing].tone}>{timingBadge[timing].label}</Badge>
}

type Action = { followUp: FollowUp; kind: 'reschedule' | 'complete' | 'cancel' }

/**
 * Follow-ups with reschedule / complete / cancel for SCHEDULED ones. Timing
 * (overdue, upcoming) comes from the backend, calculated when the list loads.
 */
export function FollowUpList({ rows, showPatient, onChanged }: { rows: FollowUp[]; showPatient?: boolean; onChanged: () => void }) {
  const [action, setAction] = useState<Action>()
  const [value, setValue] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()
  const [message, setMessage] = useState<string>()

  function start(followUp: FollowUp, kind: Action['kind']) {
    setAction({ followUp, kind }); setValue(''); setError(undefined); setMessage(undefined)
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (!action) return
    const { followUp, kind } = action
    setBusy(true); setError(undefined)
    try {
      if (kind === 'reschedule') await followUpsApi.reschedule(followUp.patientId, followUp.id, dateTimeInputToIso(value))
      else if (kind === 'complete') await followUpsApi.complete(followUp.patientId, followUp.id, optionalText(value))
      else await followUpsApi.cancel(followUp.patientId, followUp.id, value)
      setMessage(`Follow-up "${followUp.reason}" ${kind === 'reschedule' ? 'rescheduled' : kind === 'complete' ? 'completed' : 'cancelled'}.`)
      setAction(undefined)
      onChanged()
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div>
      {message && <Alert kind="success" onDismiss={() => setMessage(undefined)}>{message}</Alert>}
      {action && (
        <form onSubmit={submit} className="mb-4 rounded-lg border border-amber-200 bg-amber-50 p-4" noValidate>
          {error !== undefined && <ErrorAlert error={error} />}
          {action.kind === 'reschedule' ? (
            <Field label={`New date and time for "${action.followUp.reason}"`} htmlFor="f-new-time" required>
              <input id="f-new-time" type="datetime-local" className={inputClass} value={value} onChange={(e) => setValue(e.target.value)} />
            </Field>
          ) : (
            <Field label={action.kind === 'cancel' ? 'Reason for cancelling' : 'Outcome notes (optional)'} htmlFor="f-outcome" required={action.kind === 'cancel'}>
              <input id="f-outcome" className={inputClass} value={value} onChange={(e) => setValue(e.target.value)} />
            </Field>
          )}
          <div className="mt-3 flex gap-2">
            <Button type="submit" disabled={busy || ((action.kind === 'cancel' || action.kind === 'reschedule') && value.trim() === '')}>
              Confirm {action.kind}
            </Button>
            <Button variant="secondary" onClick={() => setAction(undefined)}>Back</Button>
          </div>
        </form>
      )}
      <Table>
        <thead><tr>
          <th className={th}>When</th>{showPatient && <th className={th}>Patient</th>}<th className={th}>Reason</th>
          <th className={th}>Status</th><th className={th}></th>
        </tr></thead>
        <tbody>
          {rows.map((f) => (
            <tr key={f.id}>
              <td className={`${td} whitespace-nowrap`}>{formatDateTime(f.scheduledAt)}
                {f.rescheduleCount > 0 && <p className="text-xs text-slate-500">Rescheduled {f.rescheduleCount}×</p>}</td>
              {showPatient && <td className={td}><Link className="font-medium text-teal-800 hover:underline" to={`/patients/${f.patientId}?tab=follow-ups`}>{f.patientName}</Link></td>}
              <td className={td}>{f.reason}{f.notes && <p className="text-xs text-slate-500">{f.notes}</p>}</td>
              <td className={td}><TimingBadge timing={f.timing} />
                {f.outcomeNotes && <p className="mt-1 text-xs text-slate-500">{f.outcomeNotes}</p>}</td>
              <td className={`${td} whitespace-nowrap text-right`}>
                {f.status === 'SCHEDULED' && (
                  <>
                    <Button variant="ghost" onClick={() => start(f, 'reschedule')}>Reschedule</Button>
                    <Button variant="ghost" onClick={() => start(f, 'complete')}>Complete</Button>
                    <Button variant="danger" className="ml-1" onClick={() => start(f, 'cancel')}>Cancel</Button>
                  </>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </Table>
    </div>
  )
}

/** A patient's follow-ups: schedule a new one and manage existing ones. */
export function FollowUpsTab({ patientId }: { patientId: number }) {
  const list = useAsync(() => followUpsApi.list(patientId), [patientId])
  const [when, setWhen] = useState('')
  const [reason, setReason] = useState('')
  const [notes, setNotes] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()
  const [message, setMessage] = useState<string>()
  const fieldErrors = error instanceof ApiError ? error.fieldErrors : {}

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true); setError(undefined); setMessage(undefined)
    try {
      await followUpsApi.schedule(patientId, { scheduledAt: dateTimeInputToIso(when), reason, notes: optionalText(notes) })
      setMessage('Follow-up scheduled.'); setWhen(''); setReason(''); setNotes('')
      list.reload()
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="space-y-6">
      <form onSubmit={submit} className="rounded-lg border border-slate-200 bg-slate-50 p-4" noValidate>
        <h3 className="mb-3 text-sm font-semibold text-slate-800">Schedule a follow-up</h3>
        {message && <Alert kind="success" onDismiss={() => setMessage(undefined)}>{message}</Alert>}
        {error !== undefined && <ErrorAlert error={error} onDismiss={() => setError(undefined)} />}
        <div className="grid gap-4 sm:grid-cols-3">
          <Field label="Date and time" htmlFor="f-when" required error={fieldErrors.scheduledAt} hint="Your local time; must be in the future.">
            <input id="f-when" type="datetime-local" className={inputClass} value={when} onChange={(e) => setWhen(e.target.value)} />
          </Field>
          <Field label="Reason" htmlFor="f-reason" required error={fieldErrors.reason}>
            <input id="f-reason" className={inputClass} value={reason} onChange={(e) => setReason(e.target.value)} />
          </Field>
          <Field label="Notes" htmlFor="f-notes" error={fieldErrors.notes}>
            <input id="f-notes" className={inputClass} value={notes} onChange={(e) => setNotes(e.target.value)} />
          </Field>
        </div>
        <Button type="submit" className="mt-3" disabled={busy}>{busy ? 'Saving…' : 'Schedule follow-up'}</Button>
      </form>
      {list.loading ? <Loading /> : list.error ? <ErrorAlert error={list.error} onRetry={list.reload} />
        : !list.data?.length ? <EmptyState>No follow-ups for this patient yet.</EmptyState>
          : <FollowUpList rows={list.data} onChanged={list.reload} />}
    </div>
  )
}
