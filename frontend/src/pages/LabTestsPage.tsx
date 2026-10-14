import { useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import { labTestsApi } from '../api/knowledgeBase'
import type { LabTest } from '../api/types'
import { Alert, Button, Card, EmptyState, ErrorAlert, Field, inputClass, Loading, PageHeader, Table, td, th } from '../components/ui'
import { useAuth } from '../components/AuthProvider'
import { useAsync } from '../hooks/useAsync'
import { optionalText } from '../utils/format'

const empty = { name: '', referenceRangeLow: '', referenceRangeHigh: '', unit: '', notes: '' }

export function LabTestsPage() {
  const { isAdmin } = useAuth()
  const labTests = useAsync(() => labTestsApi.list(), [])
  const [editing, setEditing] = useState<LabTest | 'new' | null>(null)
  const [form, setForm] = useState(empty)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<unknown>()
  const [message, setMessage] = useState<string>()
  const fieldErrors = error instanceof ApiError ? error.fieldErrors : {}

  function open(test: LabTest | 'new') {
    setEditing(test); setError(undefined); setMessage(undefined)
    setForm(test === 'new' ? empty : {
      name: test.name, referenceRangeLow: String(test.referenceRangeLow), referenceRangeHigh: String(test.referenceRangeHigh),
      unit: test.unit, notes: test.notes ?? '',
    })
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    setSaving(true); setError(undefined)
    const input = {
      name: form.name,
      referenceRangeLow: form.referenceRangeLow === '' ? null : Number(form.referenceRangeLow),
      referenceRangeHigh: form.referenceRangeHigh === '' ? null : Number(form.referenceRangeHigh),
      unit: form.unit,
      notes: optionalText(form.notes) ?? null,
    }
    try {
      if (editing === 'new') await labTestsApi.create(input)
      else if (editing) await labTestsApi.update(editing.id, input)
      setMessage(`Lab test "${form.name.trim()}" saved.`)
      setEditing(null)
      labTests.reload()
    } catch (err) {
      setError(err)
    } finally {
      setSaving(false)
    }
  }

  async function remove(test: LabTest) {
    if (!window.confirm(`Delete lab test ${test.name}?`)) return
    setError(undefined); setMessage(undefined)
    try {
      await labTestsApi.remove(test.id)
      setMessage(`Lab test "${test.name}" deleted.`)
      labTests.reload()
    } catch (err) {
      setError(err)
    }
  }

  const set = (key: keyof typeof empty) => (e: { target: { value: string } }) => setForm({ ...form, [key]: e.target.value })

  return (
    <>
      <PageHeader title="Lab tests"
        description="Laboratory tests and reference ranges used to flag results. Verify ranges against your local laboratory."
        actions={isAdmin && <Button onClick={() => open('new')}>Add lab test</Button>} />
      {!isAdmin && <Alert kind="info">Read-only: only administrators can change the knowledge base.</Alert>}
      {message && <Alert kind="success" onDismiss={() => setMessage(undefined)}>{message}</Alert>}
      {error !== undefined && !editing && <ErrorAlert error={error} onDismiss={() => setError(undefined)} />}

      {editing && (
        <Card title={editing === 'new' ? 'Add lab test' : `Edit ${editing.name}`} className="mb-6">
          <form onSubmit={submit} noValidate>
            {error !== undefined && <ErrorAlert error={error} />}
            <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
              <Field label="Name" htmlFor="t-name" required error={fieldErrors.name}
                hint={editing !== 'new' ? 'Rules refer to lab tests by name; renaming can make a rule invalid.' : undefined}>
                <input id="t-name" className={inputClass} value={form.name} onChange={set('name')} required />
              </Field>
              <Field label="Reference range low" htmlFor="t-low" required error={fieldErrors.referenceRangeLow ?? fieldErrors.referenceRangeValid}>
                <input id="t-low" type="number" step="0.01" className={inputClass} value={form.referenceRangeLow} onChange={set('referenceRangeLow')} required />
              </Field>
              <Field label="Reference range high" htmlFor="t-high" required error={fieldErrors.referenceRangeHigh}>
                <input id="t-high" type="number" step="0.01" className={inputClass} value={form.referenceRangeHigh} onChange={set('referenceRangeHigh')} required />
              </Field>
              <Field label="Unit" htmlFor="t-unit" required error={fieldErrors.unit}>
                <input id="t-unit" className={inputClass} value={form.unit} onChange={set('unit')} required />
              </Field>
              <div className="md:col-span-2 lg:col-span-4">
                <Field label="Notes" htmlFor="t-notes" error={fieldErrors.notes}>
                  <input id="t-notes" className={inputClass} value={form.notes} onChange={set('notes')} />
                </Field>
              </div>
            </div>
            <div className="mt-4 flex gap-2">
              <Button type="submit" disabled={saving}>{saving ? 'Saving…' : 'Save'}</Button>
              <Button variant="secondary" onClick={() => { setEditing(null); setError(undefined) }}>Cancel</Button>
            </div>
          </form>
        </Card>
      )}

      <Card>
        {labTests.loading ? <Loading /> : labTests.error ? <ErrorAlert error={labTests.error} onRetry={labTests.reload} />
          : !labTests.data?.length ? <EmptyState>No lab tests yet.</EmptyState> : (
            <Table>
              <thead><tr><th className={th}>Name</th><th className={th}>Reference range</th><th className={th}>Notes</th><th className={th}></th></tr></thead>
              <tbody>
                {labTests.data.map((test) => (
                  <tr key={test.id}>
                    <td className={`${td} font-medium text-slate-900`}>{test.name}</td>
                    <td className={td}>{test.referenceRangeLow}–{test.referenceRangeHigh} {test.unit}</td>
                    <td className={`${td} text-slate-500`}>{test.notes ?? ''}</td>
                    <td className={`${td} whitespace-nowrap text-right`}>
                      {isAdmin && <>
                        <Button variant="ghost" onClick={() => open(test)}>Edit</Button>
                        <Button variant="danger" className="ml-1" onClick={() => remove(test)}>Delete</Button>
                      </>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </Table>
          )}
      </Card>
    </>
  )
}
