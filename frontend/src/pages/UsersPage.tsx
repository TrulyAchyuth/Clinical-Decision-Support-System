import { useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import type { Role, UserSummary } from '../api/types'
import { usersApi } from '../api/users'
import { useAuth } from '../components/AuthProvider'
import { roleLabel } from '../components/ProfileMenu'
import { Alert, Badge, Button, Card, EmptyState, ErrorAlert, Field, inputClass, Loading, PageHeader, Table, td, th } from '../components/ui'
import { useAsync } from '../hooks/useAsync'
import { formatDateTime } from '../utils/format'

/**
 * User management for administrators. Passwords are never shown; accounts are
 * deactivated, not deleted. The backend refuses unsafe changes (your own
 * account, the last active administrator) and ends the sessions of users it
 * deactivates or whose role changes.
 */
export function UsersPage() {
  const { user: me } = useAuth()
  const users = useAsync(() => usersApi.list(), [])
  const [search, setSearch] = useState('')
  const [roleFilter, setRoleFilter] = useState<Role | ''>('')
  const [statusFilter, setStatusFilter] = useState<'all' | 'active' | 'inactive'>('all')
  const [form, setForm] = useState({ username: '', password: '', role: 'CLINICIAN' as Role })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()
  const [message, setMessage] = useState<string>()
  const fieldErrors = error instanceof ApiError ? error.fieldErrors : {}

  async function run(action: () => Promise<unknown>, success: string) {
    setBusy(true); setError(undefined); setMessage(undefined)
    try {
      await action()
      setMessage(success)
      users.reload()
      return true
    } catch (err) {
      setError(err)
      return false
    } finally {
      setBusy(false)
    }
  }

  async function create(e: FormEvent) {
    e.preventDefault()
    if (form.role === 'ADMIN' && !window.confirm(`Create ${form.username} as an administrator? Administrators can manage users, rules and catalogues.`)) return
    if (await run(() => usersApi.create(form), `Account "${form.username.trim()}" created as ${roleLabel(form.role).toLowerCase()}.`)) {
      setForm({ username: '', password: '', role: 'CLINICIAN' })
    }
  }

  function toggle(u: UserSummary) {
    const question = u.enabled
      ? `Deactivate ${u.username}? They will be signed out and cannot log in until reactivated.`
      : `Reactivate ${u.username}?`
    if (!window.confirm(question)) return
    void run(() => (u.enabled ? usersApi.deactivate(u.id) : usersApi.activate(u.id)),
      `${u.username} ${u.enabled ? 'deactivated' : 'reactivated'}.`)
  }

  function changeRole(u: UserSummary, role: Role) {
    if (!window.confirm(`Change ${u.username} to ${roleLabel(role).toLowerCase()}? They will be signed out and must log in again.`)) return
    void run(() => usersApi.changeRole(u.id, role), `${u.username} is now ${roleLabel(role).toLowerCase()}.`)
  }

  const term = search.trim().toLowerCase()
  const visible = (users.data ?? []).filter((u) => (term === '' || u.username.toLowerCase().includes(term))
    && (roleFilter === '' || u.role === roleFilter)
    && (statusFilter === 'all' || (statusFilter === 'active') === u.enabled))

  return (
    <>
      <PageHeader title="User management" description="Accounts that can sign in. Passwords are stored only as secure hashes and are never displayed." />
      {message && <Alert kind="success" onDismiss={() => setMessage(undefined)}>{message}</Alert>}
      {error !== undefined && <ErrorAlert error={error} onDismiss={() => setError(undefined)} />}

      <Card title="Create an account" className="mb-6">
        <form onSubmit={create} noValidate className="grid gap-4 md:grid-cols-4 md:items-end">
          <Field label="Username" htmlFor="u-name" required error={fieldErrors.username} hint="3–50 letters, digits, '.', '_' or '-'">
            <input id="u-name" className={inputClass} autoComplete="off" value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} />
          </Field>
          <Field label="Initial password" htmlFor="u-password" required error={fieldErrors.password} hint="8–72 characters; share it securely">
            <input id="u-password" type="password" autoComplete="new-password" className={inputClass} value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} />
          </Field>
          <Field label="Role" htmlFor="u-role" required error={fieldErrors.role}>
            <select id="u-role" className={inputClass} value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value as Role })}>
              <option value="CLINICIAN">Clinician</option>
              <option value="ADMIN">Administrator</option>
            </select>
          </Field>
          <Button type="submit" disabled={busy}>{busy ? 'Saving…' : 'Create account'}</Button>
        </form>
      </Card>

      <Card>
        <div className="mb-4 flex flex-wrap items-end gap-3">
          <Field label="Search users" htmlFor="u-search">
            <input id="u-search" className={inputClass} placeholder="Username" value={search} onChange={(e) => setSearch(e.target.value)} />
          </Field>
          <Field label="Role" htmlFor="u-role-filter">
            <select id="u-role-filter" className={inputClass} value={roleFilter} onChange={(e) => setRoleFilter(e.target.value as Role | '')}>
              <option value="">All roles</option><option value="ADMIN">Administrators</option><option value="CLINICIAN">Clinicians</option>
            </select>
          </Field>
          <Field label="Status" htmlFor="u-status-filter">
            <select id="u-status-filter" className={inputClass} value={statusFilter} onChange={(e) => setStatusFilter(e.target.value as typeof statusFilter)}>
              <option value="all">All</option><option value="active">Active</option><option value="inactive">Deactivated</option>
            </select>
          </Field>
        </div>
        {users.loading ? <Loading label="Loading users…" /> : users.error ? <ErrorAlert error={users.error} onRetry={users.reload} />
          : visible.length === 0 ? <EmptyState>No users match the filters.</EmptyState> : (
            <Table>
              <thead><tr><th className={th}>Username</th><th className={th}>Role</th><th className={th}>Status</th><th className={th}>Created</th><th className={th}></th></tr></thead>
              <tbody>
                {visible.map((u) => {
                  const isMe = u.username === me?.username
                  return (
                    <tr key={u.id}>
                      <td className={`${td} font-medium text-slate-900`}>{u.username} {isMe && <Badge tone="teal">You</Badge>}</td>
                      <td className={td}>
                        {isMe ? roleLabel(u.role) : (
                          <select aria-label={`Role for ${u.username}`} className={`${inputClass} w-auto py-1`} value={u.role} disabled={busy}
                            onChange={(e) => changeRole(u, e.target.value as Role)}>
                            <option value="CLINICIAN">Clinician</option>
                            <option value="ADMIN">Administrator</option>
                          </select>
                        )}
                      </td>
                      <td className={td}>{u.enabled ? <Badge tone="green">Active</Badge> : <Badge>Deactivated</Badge>}</td>
                      <td className={td}>{formatDateTime(u.createdAt)}</td>
                      <td className={`${td} whitespace-nowrap text-right`}>
                        {!isMe && (
                          <Button variant={u.enabled ? 'danger' : 'secondary'} disabled={busy} onClick={() => toggle(u)}>
                            {u.enabled ? 'Deactivate' : 'Reactivate'}
                          </Button>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </Table>
          )}
        <p className="mt-3 text-xs text-slate-500">Accounts are never deleted. You cannot change your own role or deactivate yourself, and at least one active administrator is always kept.</p>
      </Card>
    </>
  )
}
