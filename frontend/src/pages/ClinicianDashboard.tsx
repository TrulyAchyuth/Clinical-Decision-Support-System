import { Link } from 'react-router'
import { followUpsApi } from '../api/followUps'
import { patientsApi } from '../api/patients'
import { useAuth } from '../components/AuthProvider'
import { TimingBadge } from '../components/FollowUps'
import { Card, EmptyState, ErrorAlert, Loading, PageHeader, QuickAction, StatCard, Table, td, th } from '../components/ui'
import { useAsync } from '../hooks/useAsync'
import { formatDateTime } from '../utils/format'

export function greeting(now = new Date()): string {
  const hour = now.getHours()
  return hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening'
}

/** Patient-care overview for clinicians, built only from stored data. */
export function ClinicianDashboard() {
  const { user } = useAuth()
  const patients = useAsync(() => patientsApi.list(), [])
  const followUps = useAsync(() => followUpsApi.listAll(), [])

  const overdue = followUps.data?.filter((f) => f.timing === 'OVERDUE') ?? []
  const upcoming = followUps.data?.filter((f) => f.timing === 'UPCOMING') ?? []
  const due = [...overdue, ...upcoming].slice(0, 6)
  const recent = [...(patients.data ?? [])].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)).slice(0, 5)
  const count = (loading: boolean, failed: boolean, value: number) => (loading ? '…' : failed ? '—' : value)

  return (
    <>
      <PageHeader title={`${greeting()}, ${user?.username ?? ''}`} description="Clinical overview" />

      <div className="mb-6 grid gap-4 sm:grid-cols-3">
        <StatCard label="Patients" icon="patients" to="/patients"
          value={count(patients.loading, patients.error !== undefined, patients.data?.length ?? 0)} />
        <StatCard label="Upcoming follow-ups" icon="calendar" tone="amber" to="/follow-ups" note="Due within 7 days"
          value={count(followUps.loading, followUps.error !== undefined, upcoming.length)} />
        <StatCard label="Overdue follow-ups" icon="alert" tone={overdue.length > 0 ? 'red' : 'slate'} to="/follow-ups"
          value={count(followUps.loading, followUps.error !== undefined, overdue.length)} />
      </div>

      <section className="mb-6" aria-label="Quick actions">
        <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-500">Quick actions</h2>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <QuickAction to="/patients" icon="patients" title="View patients" description="Search and open patient records" />
          <QuickAction to="/patients" icon="heart" title="Record observations" description="Open a patient, then Vital signs" />
          <QuickAction to="/patients" icon="lab" title="Review laboratory results" description="Open a patient, then Lab results" />
          <QuickAction to="/decisions" icon="decision" title="Request a clinical decision" description="Check a patient's data against the active rules" />
          <QuickAction to="/patients" icon="drug" title="Medications and allergies" description="Open a patient, then Medications or Allergies" />
          <QuickAction to="/follow-ups" icon="calendar" title="Follow-ups" description="Review due follow-ups; schedule from a patient record" />
        </div>
      </section>

      <div className="grid gap-6 xl:grid-cols-5">
        <Card title="Follow-ups due" className="xl:col-span-3"
          actions={<Link to="/follow-ups" className="text-sm font-medium text-teal-800 hover:underline">All follow-ups</Link>}>
          {followUps.loading ? <Loading /> : followUps.error ? <ErrorAlert error={followUps.error} onRetry={followUps.reload} />
            : due.length === 0 ? <EmptyState>No overdue or upcoming follow-ups.</EmptyState> : (
              <Table>
                <thead><tr><th className={th}>When</th><th className={th}>Patient</th><th className={th}>Reason</th><th className={th}>Status</th></tr></thead>
                <tbody>
                  {due.map((f) => (
                    <tr key={f.id}>
                      <td className={`${td} whitespace-nowrap`}>{formatDateTime(f.scheduledAt)}</td>
                      <td className={td}><Link className="font-medium text-teal-800 hover:underline" to={`/patients/${f.patientId}?tab=follow-ups`}>{f.patientName}</Link></td>
                      <td className={td}>{f.reason}</td>
                      <td className={td}><TimingBadge timing={f.timing} /></td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            )}
        </Card>
        <Card title="Recently updated patients" className="xl:col-span-2">
          {patients.loading ? <Loading /> : patients.error ? <ErrorAlert error={patients.error} onRetry={patients.reload} />
            : recent.length === 0 ? <EmptyState>No patients yet.</EmptyState> : (
              <ul className="divide-y divide-slate-100">
                {recent.map((p) => (
                  <li key={p.id} className="flex items-center justify-between gap-3 py-2.5">
                    <Link to={`/patients/${p.id}`} className="font-medium text-teal-800 hover:underline">{p.fullName}</Link>
                    <span className="text-xs text-slate-500">Updated {formatDateTime(p.updatedAt)}</span>
                  </li>
                ))}
              </ul>
            )}
        </Card>
      </div>
    </>
  )
}
