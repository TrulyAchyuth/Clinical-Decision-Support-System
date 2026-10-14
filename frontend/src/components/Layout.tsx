import { useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router'
import { getHealth } from '../api/health'
import type { Health, Role } from '../api/types'
import { useAuth } from './AuthProvider'
import { Icon, type IconName } from './Icon'
import { ProfileMenu } from './ProfileMenu'
import { ErrorAlert } from './ui'

interface NavItem { to: string; label: string; icon: IconName }
interface NavGroup { title: string; items: NavItem[] }

/** Each role gets its own workspace; the backend enforces the same split. */
const navigation: Record<Role, NavGroup[]> = {
  CLINICIAN: [
    { title: 'Overview', items: [{ to: '/', label: 'Dashboard', icon: 'dashboard' }] },
    { title: 'Patient care', items: [
      { to: '/patients', label: 'Patients', icon: 'patients' },
      { to: '/decisions', label: 'Clinical decisions', icon: 'decision' },
      { to: '/follow-ups', label: 'Follow-ups', icon: 'calendar' },
    ] },
    { title: 'Clinical reference', items: [
      { to: '/drugs', label: 'Drugs', icon: 'drug' },
      { to: '/diseases', label: 'Diseases', icon: 'disease' },
      { to: '/lab-tests', label: 'Lab tests', icon: 'lab' },
      { to: '/reference/rules', label: 'Decision rules', icon: 'book' },
    ] },
  ],
  ADMIN: [
    { title: 'Administration', items: [
      { to: '/', label: 'Admin dashboard', icon: 'dashboard' },
      { to: '/admin/users', label: 'User management', icon: 'users' },
      { to: '/rules', label: 'Clinical rules', icon: 'rules' },
    ] },
    { title: 'Catalogues', items: [
      { to: '/drugs', label: 'Drugs', icon: 'drug' },
      { to: '/diseases', label: 'Diseases', icon: 'disease' },
      { to: '/lab-tests', label: 'Lab tests', icon: 'lab' },
    ] },
    { title: 'Clinical workflows', items: [
      { to: '/patients', label: 'Patients', icon: 'patients' },
      { to: '/decisions', label: 'Clinical decisions', icon: 'decision' },
      { to: '/follow-ups', label: 'Follow-ups', icon: 'calendar' },
    ] },
  ],
}

/** Sidebar, top bar with the account menu, and the page. */
export function Layout() {
  const { user } = useAuth()
  const [menuOpen, setMenuOpen] = useState(false)
  const [logoutError, setLogoutError] = useState<unknown>()
  const location = useLocation()

  useEffect(() => setMenuOpen(false), [location.pathname])

  const groups = user ? navigation[user.role] : []

  return (
    <div className="min-h-screen bg-slate-50">
      {menuOpen && <div className="fixed inset-0 z-30 bg-slate-900/40 lg:hidden" onClick={() => setMenuOpen(false)} aria-hidden="true" />}

      <aside className={`fixed inset-y-0 left-0 z-40 flex w-64 transform flex-col bg-slate-900 text-slate-200 transition-transform lg:translate-x-0 ${menuOpen ? 'translate-x-0' : '-translate-x-full'}`}>
        <div className="flex h-16 items-center gap-3 border-b border-slate-800 px-5">
          <img src="/favicon.svg" alt="" className="h-8 w-8" />
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold text-white">Clinical Decision Support System</p>
            <p className="truncate text-xs text-slate-400">Patient Insights &amp; Clinical Decision Support</p>
          </div>
        </div>
        <nav className="flex-1 space-y-5 overflow-y-auto p-3" aria-label="Main">
          {groups.map((group) => (
            <div key={group.title}>
              <p className="px-3 pb-1 text-[11px] font-semibold uppercase tracking-wider text-slate-500">{group.title}</p>
              <div className="space-y-0.5">
                {group.items.map((item) => (
                  <NavLink key={item.to} to={item.to} end={item.to === '/'}
                    className={({ isActive }) =>
                      `flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${isActive ? 'bg-teal-700 text-white' : 'text-slate-300 hover:bg-slate-800 hover:text-white'}`}>
                    <Icon name={item.icon} />
                    {item.label}
                  </NavLink>
                ))}
              </div>
            </div>
          ))}
        </nav>
      </aside>

      <div className="flex min-h-screen flex-col lg:pl-64">
        <header className="sticky top-0 z-20 flex h-16 items-center gap-3 border-b border-slate-200 bg-white/95 px-4 backdrop-blur sm:px-6">
          <button type="button" className="rounded-lg p-2 text-slate-600 hover:bg-slate-100 lg:hidden" onClick={() => setMenuOpen(true)} aria-label="Open menu">
            <Icon name="menu" />
          </button>
          <p className="truncate text-sm font-semibold text-slate-800 lg:hidden">Clinical Decision Support System</p>
          <div className="ml-auto flex items-center gap-3">
            {user?.role === 'ADMIN' && <BackendStatus />}
            <ProfileMenu onError={setLogoutError} />
          </div>
        </header>
        {logoutError !== undefined && (
          <div className="px-4 pt-4 sm:px-6"><ErrorAlert error={logoutError} onDismiss={() => setLogoutError(undefined)} /></div>
        )}

        <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-6 sm:px-6 lg:py-8">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

/** Backend and database status for administrators, from GET /api/health. */
function BackendStatus() {
  const [health, setHealth] = useState<Health | null | undefined>()

  useEffect(() => {
    let current = true
    getHealth().then((result) => { if (current) setHealth(result) })
    return () => { current = false }
  }, [])

  const [label, color] = health === undefined ? ['Checking…', 'bg-slate-400']
    : health === null ? ['Backend unreachable', 'bg-red-500']
      : health.status === 'UP' ? ['System healthy', 'bg-green-500']
        : ['Database unavailable', 'bg-amber-500']

  return (
    <span className="hidden items-center gap-2 rounded-full border border-slate-200 px-2.5 py-1 text-xs text-slate-600 md:inline-flex" title="From GET /api/health">
      <span className={`h-2 w-2 rounded-full ${color}`} aria-hidden="true" />
      {label}
    </span>
  )
}
