import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router'
import { ApiError } from '../api/client'
import { patientsApi } from '../api/patients'
import type { Gender } from '../api/types'
import { Button, Card, ErrorAlert, Field, inputClass, Loading, PageHeader } from '../components/ui'
import { optionalText } from '../utils/format'

const empty = { fullName: '', dateOfBirth: '', gender: '' as Gender | '', phone: '', email: '', address: '' }

/** Create (/patients/new) or edit (/patients/:id/edit) a patient. */
export function PatientFormPage() {
  const { id } = useParams()
  const editing = id !== undefined
  const navigate = useNavigate()
  const [form, setForm] = useState(empty)
  const [loading, setLoading] = useState(editing)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<unknown>()
  const fieldErrors = error instanceof ApiError ? error.fieldErrors : {}

  useEffect(() => {
    if (!editing) return
    patientsApi.get(Number(id)).then(
      (p) => { setForm({ fullName: p.fullName, dateOfBirth: p.dateOfBirth, gender: p.gender, phone: p.phone ?? '', email: p.email ?? '', address: p.address ?? '' }); setLoading(false) },
      (err: unknown) => { setError(err); setLoading(false) },
    )
  }, [editing, id])

  const set = (key: keyof typeof empty) => (e: { target: { value: string } }) => setForm({ ...form, [key]: e.target.value })

  async function submit(e: FormEvent) {
    e.preventDefault()
    setSaving(true)
    setError(undefined)
    const input = {
      fullName: form.fullName,
      dateOfBirth: form.dateOfBirth || null,
      gender: form.gender || null,
      phone: optionalText(form.phone),
      email: optionalText(form.email),
      address: optionalText(form.address),
    }
    try {
      const saved = editing ? await patientsApi.update(Number(id), input) : await patientsApi.create(input)
      navigate(`/patients/${saved.id}`, { state: { message: editing ? 'Patient updated.' : 'Patient created.' } })
    } catch (err) {
      setError(err)
      setSaving(false)
    }
  }

  return (
    <>
      <PageHeader title={editing ? 'Edit patient' : 'Add patient'} description="Use synthetic (made-up) details only." />
      <Card>
        {loading ? <Loading /> : (
          <form onSubmit={submit} noValidate>
            {error !== undefined && <ErrorAlert error={error} />}
            <div className="grid gap-4 md:grid-cols-2">
              <Field label="Full name" htmlFor="p-name" required error={fieldErrors.fullName}>
                <input id="p-name" className={inputClass} value={form.fullName} onChange={set('fullName')} aria-invalid={!!fieldErrors.fullName} required />
              </Field>
              <Field label="Date of birth" htmlFor="p-dob" required error={fieldErrors.dateOfBirth}>
                <input id="p-dob" type="date" className={inputClass} value={form.dateOfBirth} onChange={set('dateOfBirth')} aria-invalid={!!fieldErrors.dateOfBirth} required />
              </Field>
              <Field label="Gender" htmlFor="p-gender" required error={fieldErrors.gender}>
                <select id="p-gender" className={inputClass} value={form.gender} onChange={set('gender')} aria-invalid={!!fieldErrors.gender} required>
                  <option value="">Choose…</option>
                  <option value="FEMALE">Female</option>
                  <option value="MALE">Male</option>
                  <option value="OTHER">Other</option>
                  <option value="UNKNOWN">Unknown</option>
                </select>
              </Field>
              <Field label="Phone" htmlFor="p-phone" error={fieldErrors.phone}>
                <input id="p-phone" type="tel" className={inputClass} value={form.phone} onChange={set('phone')} aria-invalid={!!fieldErrors.phone} />
              </Field>
              <Field label="Email" htmlFor="p-email" error={fieldErrors.email}>
                <input id="p-email" type="email" className={inputClass} value={form.email} onChange={set('email')} aria-invalid={!!fieldErrors.email} />
              </Field>
              <Field label="Address" htmlFor="p-address" error={fieldErrors.address}>
                <input id="p-address" className={inputClass} value={form.address} onChange={set('address')} aria-invalid={!!fieldErrors.address} />
              </Field>
            </div>
            <div className="mt-6 flex gap-2">
              <Button type="submit" disabled={saving}>{saving ? 'Saving…' : 'Save patient'}</Button>
              <Button variant="secondary" onClick={() => navigate(editing ? `/patients/${id}` : '/patients')}>Cancel</Button>
            </div>
          </form>
        )}
      </Card>
    </>
  )
}
