import { useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import { diseasesApi } from '../api/knowledgeBase'
import type { Disease } from '../api/types'
import { Alert, Button, Card, EmptyState, ErrorAlert, Field, inputClass, Loading, PageHeader, Table, td, th } from '../components/ui'
import { useAuth } from '../components/AuthProvider'
import { useAsync } from '../hooks/useAsync'

const empty = { name: '', icd10Code: '' }

export function DiseasesPage() {
  const { isAdmin } = useAuth()
  const diseases = useAsync(() => diseasesApi.list(), [])
  const [editing, setEditing] = useState<Disease | 'new' | null>(null)
  const [form, setForm] = useState(empty)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<unknown>()
  const [message, setMessage] = useState<string>()
  const fieldErrors = error instanceof ApiError ? error.fieldErrors : {}

  function open(disease: Disease | 'new') {
    setEditing(disease); setError(undefined); setMessage(undefined)
    setForm(disease === 'new' ? empty : { name: disease.name, icd10Code: disease.icd10Code })
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    setSaving(true); setError(undefined)
    try {
      if (editing === 'new') await diseasesApi.create(form)
      else if (editing) await diseasesApi.update(editing.id, form)
      setMessage(`Disease "${form.name.trim()}" saved.`)
      setEditing(null)
      diseases.reload()
    } catch (err) {
      setError(err)
    } finally {
      setSaving(false)
    }
  }

  async function remove(disease: Disease) {
    if (!window.confirm(`Delete ${disease.icd10Code} ${disease.name}?`)) return
    setError(undefined); setMessage(undefined)
    try {
      await diseasesApi.remove(disease.id)
      setMessage(`Disease ${disease.icd10Code} deleted.`)
      diseases.reload()
    } catch (err) {
      setError(err)
    }
  }

  const set = (key: keyof typeof empty) => (e: { target: { value: string } }) => setForm({ ...form, [key]: e.target.value })

  return (
    <>
      <PageHeader title="Diseases" description="Knowledge base of diseases with ICD-10 codes. Used for patient conditions and rules."
        actions={isAdmin && <Button onClick={() => open('new')}>Add disease</Button>} />
      {!isAdmin && <Alert kind="info">Read-only: only administrators can change the knowledge base.</Alert>}
      {message && <Alert kind="success" onDismiss={() => setMessage(undefined)}>{message}</Alert>}
      {error !== undefined && !editing && <ErrorAlert error={error} onDismiss={() => setError(undefined)} />}

      {editing && (
        <Card title={editing === 'new' ? 'Add disease' : `Edit ${editing.icd10Code}`} className="mb-6">
          <form onSubmit={submit} noValidate>
            {error !== undefined && <ErrorAlert error={error} />}
            <div className="grid gap-4 md:grid-cols-2">
              <Field label="Name" htmlFor="s-name" required error={fieldErrors.name}>
                <input id="s-name" className={inputClass} value={form.name} onChange={set('name')} required />
              </Field>
              <Field label="ICD-10 code" htmlFor="s-code" required error={fieldErrors.icd10Code} hint="For example I10 or E11.9. Must be unique.">
                <input id="s-code" className={`${inputClass} font-mono`} value={form.icd10Code} onChange={set('icd10Code')} required />
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
        {diseases.loading ? <Loading /> : diseases.error ? <ErrorAlert error={diseases.error} onRetry={diseases.reload} />
          : !diseases.data?.length ? <EmptyState>No diseases yet.</EmptyState> : (
            <Table>
              <thead><tr><th className={th}>ICD-10</th><th className={th}>Name</th><th className={th}></th></tr></thead>
              <tbody>
                {diseases.data.map((disease) => (
                  <tr key={disease.id}>
                    <td className={`${td} font-mono`}>{disease.icd10Code}</td>
                    <td className={`${td} font-medium text-slate-900`}>{disease.name}</td>
                    <td className={`${td} whitespace-nowrap text-right`}>
                      {isAdmin && <>
                        <Button variant="ghost" onClick={() => open(disease)}>Edit</Button>
                        <Button variant="danger" className="ml-1" onClick={() => remove(disease)}>Delete</Button>
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
