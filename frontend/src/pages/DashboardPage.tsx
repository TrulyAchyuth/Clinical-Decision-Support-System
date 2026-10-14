import { Link } from 'react-router'
import { getHealth } from '../api/health'
import { diseasesApi, drugsApi, labTestsApi } from '../api/knowledgeBase'
import { patientsApi } from '../api/patients'
import { rulesApi } from '../api/rules'
import { Card, EmptyState, ErrorAlert, Loading, PageHeader, SeverityBadge, Table, td, th } from '../components/ui'
import { useAsync } from '../hooks/useAsync'

/** Overview built from the list endpoints; there is no separate dashboard API. */
export function DashboardPage() {
  const health = useAsync(getHealth, [])
  const counts = useAsync(async () => {
    const [patients, drugs, diseases, labTests, rules] = await Promise.all([
      patientsApi.list(), drugsApi.list(), diseasesApi.list(), labTestsApi.list(), rulesApi.list(),
    ])
    return { patients: patients.length, drugs: drugs.length, diseases: diseases.length, labTests: labTests.length, rules }
  }, [])

  return (
    <>
      <PageHeader title="Dashboard" description="Overview of the synthetic data and the active clinical rules." />

      <div className="mb-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        <Card title="System status">
          {health.loading ? <Loading label="Checking…" /> : health.data == null ? (
            <p className="text-sm text-red-700">The backend cannot be reached. Start it with <code className="rounded bg-slate-100 px-1">./mvnw spring-boot:run</code>.</p>
          ) : (
            <ul className="space-y-1 text-sm">
              <StatusLine label="Backend application" up={health.data.components.application === 'UP'} />
              <StatusLine label="MySQL database" up={health.data.components.database === 'UP'} />
            </ul>
          )}
        </Card>
        <Card title="Using the prototype" className="xl:col-span-2">
          <ol className="list-decimal space-y-1 pl-5 text-sm text-slate-700">
            <li>Add or choose a <Link className="text-teal-800 underline" to="/patients">patient</Link>.</li>
            <li>Record vital signs, lab results and conditions on the patient's page.</li>
            <li>Request a <Link className="text-teal-800 underline" to="/decisions">clinical decision</Link>: the backend checks the data against the stored rules.</li>
            <li>Review matched rules, their reasons and suggested next steps; earlier decisions are kept in the history.</li>
          </ol>
        </Card>
      </div>

      {counts.error !== undefined && <ErrorAlert error={counts.error} onRetry={counts.reload} />}
      {counts.loading ? <Loading /> : counts.data && (
        <>
          <div className="mb-6 grid grid-cols-2 gap-4 md:grid-cols-5">
            <StatCard to="/patients" label="Patients" value={counts.data.patients} />
            <StatCard to="/drugs" label="Drugs" value={counts.data.drugs} />
            <StatCard to="/diseases" label="Diseases" value={counts.data.diseases} />
            <StatCard to="/lab-tests" label="Lab tests" value={counts.data.labTests} />
            <StatCard to="/rules" label="Active rules" value={counts.data.rules.filter((r) => r.enabled).length}
              note={`of ${counts.data.rules.length}`} />
          </div>
          <Card title="Active clinical rules" actions={<Link to="/rules" className="text-sm font-medium text-teal-800 hover:underline">Manage rules</Link>}>
            {counts.data.rules.filter((r) => r.enabled).length === 0 ? <EmptyState>No rules are enabled.</EmptyState> : (
              <Table>
                <thead><tr><th className={th}>Priority</th><th className={th}>Rule</th><th className={th}>Severity</th></tr></thead>
                <tbody>
                  {counts.data.rules.filter((r) => r.enabled).map((rule) => (
                    <tr key={rule.id}>
                      <td className={td}>{rule.priority}</td>
                      <td className={td}><span className="font-medium text-slate-900">{rule.name}</span> <span className="font-mono text-xs text-slate-500">{rule.ruleId}</span></td>
                      <td className={td}><SeverityBadge severity={rule.severity} /></td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            )}
            <p className="mt-3 text-xs text-slate-500">Illustrative example rules; not clinically validated. Decision history is shown per patient.</p>
          </Card>
        </>
      )}
    </>
  )
}

function StatusLine({ label, up }: { label: string; up: boolean }) {
  return (
    <li className="flex items-center gap-2">
      <span className={`h-2.5 w-2.5 rounded-full ${up ? 'bg-green-500' : 'bg-red-500'}`} aria-hidden="true" />
      {label}: <span className="font-medium">{up ? 'up' : 'down'}</span>
    </li>
  )
}

function StatCard({ to, label, value, note }: { to: string; label: string; value: number; note?: string }) {
  return (
    <Link to={to} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm hover:border-teal-600">
      <p className="text-sm text-slate-500">{label}</p>
      <p className="mt-1 text-2xl font-semibold text-slate-900">{value} {note && <span className="text-sm font-normal text-slate-500">{note}</span>}</p>
    </Link>
  )
}
