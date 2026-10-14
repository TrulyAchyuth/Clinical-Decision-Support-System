import { useAuth } from '../components/AuthProvider'
import { initials, roleLabel } from '../components/ProfileMenu'
import { Badge, Card, PageHeader } from '../components/ui'

const permissions = {
  CLINICIAN: [
    'View and update patient records (patients cannot be deleted)',
    'Record vital signs, lab results, conditions, medications, allergies and follow-ups',
    'Run the medication safety check and request clinical decisions',
    'Read the drug, disease and lab-test catalogues and plain-English decision rules',
  ],
  ADMIN: [
    'Everything a clinician can do, plus deleting patients',
    'Manage user accounts and roles',
    'Create, edit, test, activate and deactivate clinical rules',
    'Maintain the drug, disease and lab-test catalogues',
  ],
} as const

/** The signed-in account as the backend reports it. Nothing here can be edited. */
export function ProfilePage() {
  const { user } = useAuth()
  if (!user) return null
  return (
    <>
      <PageHeader title="My profile" />
      <div className="grid gap-6 lg:grid-cols-3">
        <Card>
          <div className="flex items-center gap-4">
            <span className="flex h-14 w-14 items-center justify-center rounded-full bg-teal-700 text-lg font-semibold text-white" aria-hidden="true">{initials(user.username)}</span>
            <div>
              <p className="text-lg font-semibold text-slate-900">{user.username}</p>
              <p className="text-sm text-slate-500">{roleLabel(user.role)}</p>
            </div>
          </div>
          <dl className="mt-5 space-y-3 text-sm">
            <div className="flex justify-between"><dt className="text-slate-500">Username</dt><dd className="font-medium">{user.username}</dd></div>
            <div className="flex justify-between"><dt className="text-slate-500">Role</dt><dd><Badge tone="teal">{user.role}</Badge></dd></div>
            <div className="flex justify-between"><dt className="text-slate-500">Account status</dt><dd><Badge tone="green">Active — signed in</Badge></dd></div>
          </dl>
          <p className="mt-5 text-xs text-slate-500">Your role and account are managed by an administrator.</p>
        </Card>
        <Card title="What your role allows" className="lg:col-span-2">
          <ul className="list-disc space-y-1.5 pl-5 text-sm text-slate-700">
            {permissions[user.role].map((p) => <li key={p}>{p}</li>)}
          </ul>
          <p className="mt-4 text-xs text-slate-500">The server checks these permissions on every request.</p>
        </Card>
      </div>
    </>
  )
}
