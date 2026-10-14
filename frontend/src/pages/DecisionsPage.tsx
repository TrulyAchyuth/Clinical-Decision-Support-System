import { Link, useSearchParams } from 'react-router'
import { patientsApi } from '../api/patients'
import { ClinicalPanel } from '../components/ClinicalPanel'
import { Card, EmptyState, ErrorAlert, Field, inputClass, Loading, PageHeader } from '../components/ui'
import { useAsync } from '../hooks/useAsync'

/** Choose a patient, record observations and request a decision in one place. */
export function DecisionsPage() {
  const [params, setParams] = useSearchParams()
  const selectedId = params.get('patient')
  const patients = useAsync(() => patientsApi.list(), [])

  return (
    <>
      <PageHeader title="Clinical decisions"
        description="Educational rule-based decision support: recorded observations are checked against the stored clinical rules." />
      <Card className="mb-6">
        {patients.loading ? <Loading label="Loading patients…" />
          : patients.error ? <ErrorAlert error={patients.error} onRetry={patients.reload} />
            : !patients.data?.length ? <EmptyState>No patients yet. <Link to="/patients/new" className="text-teal-800 underline">Add a patient</Link> first.</EmptyState> : (
              <div className="max-w-md">
                <Field label="Patient" htmlFor="decision-patient">
                  <select id="decision-patient" className={inputClass} value={selectedId ?? ''}
                    onChange={(e) => setParams(e.target.value ? { patient: e.target.value } : {})}>
                    <option value="">Choose a patient</option>
                    {patients.data.map((p) => <option key={p.id} value={p.id}>{p.fullName} (#{p.id})</option>)}
                  </select>
                </Field>
              </div>
            )}
      </Card>
      {selectedId && <ClinicalPanel key={selectedId} patientId={Number(selectedId)} />}
    </>
  )
}
