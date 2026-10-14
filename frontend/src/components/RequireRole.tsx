import type { ReactNode } from 'react'
import { Link } from 'react-router'
import type { Role } from '../api/types'
import { useAuth } from './AuthProvider'
import { Icon } from './Icon'

/**
 * Shows its children only to users with the given role; others see an
 * access-denied message. This only shapes the UI: the backend refuses the
 * same data with 403.
 */
export function RequireRole({ role, children }: { role: Role; children: ReactNode }) {
  const { user } = useAuth()
  if (user?.role !== role) return <AccessDenied />
  return children
}

export function AccessDenied() {
  return (
    <div className="mx-auto max-w-lg rounded-2xl border border-slate-200 bg-white p-8 text-center shadow-sm" role="alert">
      <span className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-amber-50 text-amber-700">
        <Icon name="shield" className="h-6 w-6" />
      </span>
      <h1 className="mt-4 text-lg font-semibold text-slate-900">You don't have access to this page</h1>
      <p className="mt-2 text-sm text-slate-600">This area is for administrators. Ask an administrator if you need access.</p>
      <Link to="/" className="mt-5 inline-flex items-center gap-2 rounded-lg bg-teal-700 px-4 py-2 text-sm font-medium text-white hover:bg-teal-800">
        Back to the dashboard
      </Link>
    </div>
  )
}
