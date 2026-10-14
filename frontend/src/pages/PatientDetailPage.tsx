import { useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router'
import { patientsApi } from '../api/patients'
import { ClinicalPanel } from '../components/ClinicalPanel'
import { Alert, Button, Card, ErrorAlert, Loading, PageHeader } from '../components/ui'
import { useAsync } from '../hooks/useAsync'
import { ageInYears, formatDate, formatDateTime, titleCase } from '../utils/format'

export function PatientDetailPage() {
  const patientId = Number(useParams().id)
  const navigate = useNavigate()
  const location = useLocation()
  const patient = useAsync(() => patientsApi.get(patientId), [patientId])
  const [message, setMessage] = useState<string | undefined>((location.state as { message?: string } | null)?.message)
  const [error, setError] = useState<unknown>()

  async function remove() {
    if (!patient.data || !window.confirm(`Delete ${patient.data.fullName}? Their observations and decisions are deleted too.`)) return
    try {
      await patientsApi.remove(patientId)
      navigate('/patients', { state: { message: `${patient.data.fullName} was deleted.` } })
    } catch (err) {
      setError(err)
    }
  }

  if (patient.loading) return <Loading label="Loading patient…" />
  if (patient.error || !patient.data) return <ErrorAlert error={patient.error} onRetry={patient.reload} />
  const p = patient.data

  return (
    <>
      <PageHeader title={p.fullName} description={`Patient #${p.id}`}
        actions={<>
          <Button variant="secondary" onClick={() => navigate(`/patients/${p.id}/edit`)}>Edit</Button>
          <Button variant="danger" onClick={remove}>Delete</Button>
        </>} />
      {message && <Alert kind="success" onDismiss={() => setMessage(undefined)}>{message}</Alert>}
      {error !== undefined && <ErrorAlert error={error} onDismiss={() => setError(undefined)} />}
      <Card title="Details" className="mb-6">
        <dl className="grid gap-4 text-sm sm:grid-cols-2 lg:grid-cols-4">
          <Item label="Date of birth" value={`${formatDate(p.dateOfBirth)} (${ageInYears(p.dateOfBirth)} years)`} />
          <Item label="Gender" value={titleCase(p.gender)} />
          <Item label="Phone" value={p.phone ?? '—'} />
          <Item label="Email" value={p.email ?? '—'} />
          <Item label="Address" value={p.address ?? '—'} />
          <Item label="Last updated" value={formatDateTime(p.updatedAt)} />
        </dl>
      </Card>
      <ClinicalPanel patientId={p.id} />
    </>
  )
}

function Item({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</dt>
      <dd className="mt-0.5 text-slate-900">{value}</dd>
    </div>
  )
}
