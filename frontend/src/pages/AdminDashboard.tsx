import { Link } from 'react-router'
import { getHealth } from '../api/health'
import { diseasesApi, drugsApi, labTestsApi } from '../api/knowledgeBase'
import { rulesApi } from '../api/rules'
import type { Health } from '../api/types'
import { usersApi } from '../api/users'
import { Badge, Button, Card, ErrorAlert, Loading, PageHeader, QuickAction, StatCard } from '../components/ui'
import { useAsync } from '../hooks/useAsync'
import { formatDateTime } from '../utils/format'

type Status = 'checking' | 'healthy' | 'degraded' | 'unavailable'

const statusBadge: Record<Status, { label: string; tone: 'slate' | 'green' | 'amber' | 'red' }> = {
  checking: { label: 'Checking…', tone: 'slate' },
  healthy: { label: 'Healthy', tone: 'green' },
  degraded: { label: 'Degraded', tone: 'amber' },
  unavailable: { label: 'Unavailable', tone: 'red' },
}

/** Health from the real check: unavailable when the backend cannot be reached, never "healthy" by default. */
export function healthStatus(loading: boolean, health: Health | null | undefined) {
  if (loading || health === undefined) return { overall: 'checking', application: 'checking', database: 'checking' } as const
  if (health === null) return { overall: 'unavailable', application: 'unavailable', database: 'unavailable' } as const
  return {
    overall: health.status === 'UP' ? 'healthy' : 'degraded',
    application: health.components.application === 'UP' ? 'healthy' : 'unavailable',
    database: health.components.database === 'UP' ? 'healthy' : 'unavailable',
  } as const
}

/** Administration overview: system health, users, rules and catalogues, all from live data. */
export function AdminDashboard() {
  const health = useAsync(getHealth, [])
  const users = useAsync(() => usersApi.list(), [])
  const rules = useAsync(() => rulesApi.list(), [])
  const catalogues = useAsync(async () => {
    const [drugs, diseases, labTests] = await Promise.all([drugsApi.list(), diseasesApi.list(), labTestsApi.list()])
    return { drugs: drugs.length, diseases: diseases.length, labTests: labTests.length }
  }, [])
  const status = healthStatus(health.loading, health.data)

  return (
    <>
      <PageHeader title="Administration overview" description="System health, user accounts, clinical rules and reference catalogues." />

      <div className="mb-6 grid gap-6 lg:grid-cols-3">
        <Card title="System health" actions={<Button variant="ghost" onClick={health.reload} disabled={health.loading}>Re-check</Button>}>
          <dl className="space-y-3 text-sm">
            <HealthRow label="Overall" status={status.overall} />
            <HealthRow label="Backend application" status={status.application} />
            <HealthRow label="MySQL database" status={status.database} />
          </dl>
          <p className="mt-4 text-xs text-slate-500">
            {health.data ? `Checked ${formatDateTime(health.data.timestamp)} via GET /api/health.` : health.loading ? 'Checking…' : 'The backend did not answer the health check.'}
          </p>
        </Card>

        <Card title="User accounts" actions={<Link to="/admin/users" className="text-sm font-medium text-teal-800 hover:underline">Manage</Link>}>
          {users.loading ? <Loading /> : users.error ? <ErrorAlert error={users.error} onRetry={users.reload} /> : users.data && (
            <dl className="grid grid-cols-2 gap-4 text-sm">
              <Metric label="Registered" value={users.data.length} />
              <Metric label="Active" value={users.data.filter((u) => u.enabled).length} />
              <Metric label="Administrators" value={users.data.filter((u) => u.role === 'ADMIN').length} />
              <Metric label="Clinicians" value={users.data.filter((u) => u.role === 'CLINICIAN').length} />
              <Metric label="Deactivated" value={users.data.filter((u) => !u.enabled).length} />
            </dl>
          )}
        </Card>

        <Card title="Clinical rules" actions={<Link to="/rules" className="text-sm font-medium text-teal-800 hover:underline">Manage</Link>}>
          {rules.loading ? <Loading /> : rules.error ? <ErrorAlert error={rules.error} onRetry={rules.reload} /> : rules.data && (
            <>
              <dl className="grid grid-cols-2 gap-4 text-sm">
                <Metric label="Total" value={rules.data.length} />
                <Metric label="Active" value={rules.data.filter((r) => r.enabled).length} />
                <Metric label="Inactive" value={rules.data.filter((r) => !r.enabled).length} />
                <Metric label="Failing validation" value={rules.data.filter((r) => !r.valid).length} warn={rules.data.some((r) => !r.valid)} />
              </dl>
              {rules.data.some((r) => !r.valid) && (
                <p className="mt-3 text-xs text-amber-800">Rules failing validation are skipped in decisions; open Clinical rules to fix them.</p>
              )}
            </>
          )}
        </Card>
      </div>

      {catalogues.error !== undefined && <ErrorAlert error={catalogues.error} onRetry={catalogues.reload} />}
      <div className="mb-6 grid gap-4 sm:grid-cols-3">
        <StatCard label="Drugs" icon="drug" to="/drugs" value={catalogues.loading ? '…' : catalogues.data?.drugs ?? '—'} />
        <StatCard label="Diseases" icon="disease" tone="sky" to="/diseases" value={catalogues.loading ? '…' : catalogues.data?.diseases ?? '—'} />
        <StatCard label="Lab tests" icon="lab" tone="green" to="/lab-tests" value={catalogues.loading ? '…' : catalogues.data?.labTests ?? '—'} />
      </div>

      <section aria-label="Administrative actions">
        <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-500">Administrative actions</h2>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <QuickAction to="/admin/users" icon="users" title="Manage users" description="Create accounts, change roles, activate or deactivate" />
          <QuickAction to="/rules" icon="rules" title="Clinical rules" description="Create, test, activate and prioritise rules" />
          <QuickAction to="/drugs" icon="drug" title="Drug catalogue" description="Maintain drugs used for medications and allergies" />
          <QuickAction to="/diseases" icon="disease" title="Disease catalogue" description="Maintain ICD-10 coded diseases" />
          <QuickAction to="/lab-tests" icon="lab" title="Lab-test catalogue" description="Maintain tests and reference ranges" />
        </div>
      </section>
    </>
  )
}

function HealthRow({ label, status }: { label: string; status: Status }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <dt className="text-slate-600">{label}</dt>
      <dd><Badge tone={statusBadge[status].tone}>{statusBadge[status].label}</Badge></dd>
    </div>
  )
}

function Metric({ label, value, warn }: { label: string; value: number; warn?: boolean }) {
  return (
    <div>
      <dt className="text-slate-500">{label}</dt>
      <dd className={`text-xl font-semibold ${warn ? 'text-amber-700' : 'text-slate-900'}`}>{value}</dd>
    </div>
  )
}
