import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { patientsApi } from '../api/patients'
import { Alert, Button, Card, EmptyState, ErrorAlert, inputClass, Loading, PageHeader, Table, td, th } from '../components/ui'
import { useAsync } from '../hooks/useAsync'
import { ageInYears, formatDate, titleCase } from '../utils/format'

export function PatientsPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch] = useState('')
  const patients = useAsync(() => patientsApi.list(search), [search])
  const [message, setMessage] = useState<string | undefined>((location.state as { message?: string } | null)?.message)
  const [error, setError] = useState<unknown>()

  function submitSearch(e: FormEvent) {
    e.preventDefault()
    setSearch(searchInput.trim())
  }

  async function remove(id: number, name: string) {
    if (!window.confirm(`Delete ${name}? Their observations and decisions are deleted too.`)) return
    setError(undefined)
    try {
      await patientsApi.remove(id)
      setMessage(`${name} was deleted.`)
      patients.reload()
    } catch (err) {
      setError(err)
    }
  }

  return (
    <>
      <PageHeader title="Patients" description="Synthetic patient records."
        actions={<Button onClick={() => navigate('/patients/new')}>Add patient</Button>} />
      {message && <Alert kind="success" onDismiss={() => setMessage(undefined)}>{message}</Alert>}
      {error !== undefined && <ErrorAlert error={error} onDismiss={() => setError(undefined)} />}
      <Card>
        <form onSubmit={submitSearch} className="mb-4 flex flex-col gap-2 sm:flex-row" role="search">
          <label htmlFor="patient-search" className="sr-only">Search by name</label>
          <input id="patient-search" className={`${inputClass} sm:max-w-xs`} placeholder="Search by name" value={searchInput} onChange={(e) => setSearchInput(e.target.value)} />
          <div className="flex gap-2">
            <Button type="submit" variant="secondary">Search</Button>
            {search && <Button variant="ghost" onClick={() => { setSearch(''); setSearchInput('') }}>Clear</Button>}
          </div>
        </form>
        {patients.loading ? <Loading label="Loading patients…" />
          : patients.error ? <ErrorAlert error={patients.error} onRetry={patients.reload} />
            : !patients.data?.length ? <EmptyState>{search ? `No patients match "${search}".` : 'No patients yet. Add the first one.'}</EmptyState> : (
              <Table>
                <thead><tr>
                  <th className={th}>Name</th><th className={th}>Date of birth</th><th className={th}>Gender</th>
                  <th className={th}>Phone</th><th className={th}>Email</th><th className={th}></th>
                </tr></thead>
                <tbody>
                  {patients.data.map((p) => (
                    <tr key={p.id}>
                      <td className={td}><Link to={`/patients/${p.id}`} className="font-medium text-teal-800 hover:underline">{p.fullName}</Link></td>
                      <td className={td}>{formatDate(p.dateOfBirth)} <span className="text-slate-500">({ageInYears(p.dateOfBirth)} y)</span></td>
                      <td className={td}>{titleCase(p.gender)}</td>
                      <td className={td}>{p.phone ?? '—'}</td>
                      <td className={td}>{p.email ?? '—'}</td>
                      <td className={`${td} whitespace-nowrap text-right`}>
                        <Button variant="ghost" onClick={() => navigate(`/patients/${p.id}/edit`)}>Edit</Button>
                        <Button variant="danger" className="ml-1" onClick={() => remove(p.id, p.fullName)}>Delete</Button>
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
