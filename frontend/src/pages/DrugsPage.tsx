import { useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import { drugsApi } from '../api/knowledgeBase'
import type { Drug } from '../api/types'
import { Alert, Button, Card, EmptyState, ErrorAlert, Field, inputClass, Loading, PageHeader, Table, td, th } from '../components/ui'
import { useAsync } from '../hooks/useAsync'
import { optionalText } from '../utils/format'

const empty = { name: '', dosage: '', category: '', notes: '' }

export function DrugsPage() {
  const drugs = useAsync(() => drugsApi.list(), [])
  const [editing, setEditing] = useState<Drug | 'new' | null>(null)
  const [form, setForm] = useState(empty)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<unknown>()
  const [message, setMessage] = useState<string>()
  const fieldErrors = error instanceof ApiError ? error.fieldErrors : {}

  function open(drug: Drug | 'new') {
    setEditing(drug); setError(undefined); setMessage(undefined)
    setForm(drug === 'new' ? empty : { name: drug.name, dosage: drug.dosage, category: drug.category, notes: drug.notes ?? '' })
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    setSaving(true); setError(undefined)
    const input = { name: form.name, dosage: form.dosage, category: form.category, notes: optionalText(form.notes) ?? null }
    try {
      if (editing === 'new') await drugsApi.create(input)
      else if (editing) await drugsApi.update(editing.id, input)
      setMessage(`Drug "${form.name.trim()}" saved.`)
      setEditing(null)
      drugs.reload()
    } catch (err) {
      setError(err)
    } finally {
      setSaving(false)
    }
  }

  async function remove(drug: Drug) {
    if (!window.confirm(`Delete ${drug.name} ${drug.dosage}?`)) return
    setError(undefined); setMessage(undefined)
    try {
      await drugsApi.remove(drug.id)
      setMessage(`Drug "${drug.name}" deleted.`)
      drugs.reload()
    } catch (err) {
      setError(err)
    }
  }

  const set = (key: keyof typeof empty) => (e: { target: { value: string } }) => setForm({ ...form, [key]: e.target.value })

  return (
    <>
      <PageHeader title="Drugs" description="Knowledge base. Dosages are educational examples, not prescribing guidance."
        actions={<Button onClick={() => open('new')}>Add drug</Button>} />
      {message && <Alert kind="success" onDismiss={() => setMessage(undefined)}>{message}</Alert>}
      {error !== undefined && !editing && <ErrorAlert error={error} onDismiss={() => setError(undefined)} />}

      {editing && (
        <Card title={editing === 'new' ? 'Add drug' : `Edit ${editing.name}`} className="mb-6">
          <form onSubmit={submit} noValidate>
            {error !== undefined && <ErrorAlert error={error} />}
            <div className="grid gap-4 md:grid-cols-2">
              <Field label="Name" htmlFor="d-name" required error={fieldErrors.name}>
                <input id="d-name" className={inputClass} value={form.name} onChange={set('name')} required />
              </Field>
              <Field label="Dosage" htmlFor="d-dosage" required error={fieldErrors.dosage} hint='For example "500 mg". Name + dosage must be unique.'>
                <input id="d-dosage" className={inputClass} value={form.dosage} onChange={set('dosage')} required />
              </Field>
              <Field label="Category" htmlFor="d-category" required error={fieldErrors.category}>
                <input id="d-category" className={inputClass} value={form.category} onChange={set('category')} required />
              </Field>
              <Field label="Notes" htmlFor="d-notes" error={fieldErrors.notes}>
                <input id="d-notes" className={inputClass} value={form.notes} onChange={set('notes')} />
              </Field>
            </div>
            <div className="mt-4 flex gap-2">
              <Button type="submit" disabled={saving}>{saving ? 'Saving…' : 'Save'}</Button>
              <Button variant="secondary" onClick={() => { setEditing(null); setError(undefined) }}>Cancel</Button>
            </div>
          </form>
        </Card>
      )}

      <Card>
        {drugs.loading ? <Loading /> : drugs.error ? <ErrorAlert error={drugs.error} onRetry={drugs.reload} />
          : !drugs.data?.length ? <EmptyState>No drugs yet.</EmptyState> : (
            <Table>
              <thead><tr><th className={th}>Name</th><th className={th}>Dosage</th><th className={th}>Category</th><th className={th}>Notes</th><th className={th}></th></tr></thead>
              <tbody>
                {drugs.data.map((drug) => (
                  <tr key={drug.id}>
                    <td className={`${td} font-medium text-slate-900`}>{drug.name}</td>
                    <td className={td}>{drug.dosage}</td>
                    <td className={td}>{drug.category}</td>
                    <td className={`${td} text-slate-500`}>{drug.notes ?? ''}</td>
                    <td className={`${td} whitespace-nowrap text-right`}>
                      <Button variant="ghost" onClick={() => open(drug)}>Edit</Button>
                      <Button variant="danger" className="ml-1" onClick={() => remove(drug)}>Delete</Button>
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
