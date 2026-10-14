import { useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router'
import { getHealth } from '../api/health'
import type { Health } from '../api/types'
import { Icon, type IconName } from './Icon'

const navigation: { to: string; label: string; icon: IconName }[] = [
  { to: '/', label: 'Dashboard', icon: 'dashboard' },
  { to: '/patients', label: 'Patients', icon: 'patients' },
  { to: '/decisions', label: 'Clinical decisions', icon: 'decision' },
  { to: '/drugs', label: 'Drugs', icon: 'drug' },
  { to: '/diseases', label: 'Diseases', icon: 'disease' },
  { to: '/lab-tests', label: 'Lab tests', icon: 'lab' },
  { to: '/rules', label: 'Clinical rules', icon: 'rules' },
]

/** Sidebar + top bar + disclaimer around every page. */
export function Layout() {
  const [menuOpen, setMenuOpen] = useState(false)
  const location = useLocation()

  useEffect(() => setMenuOpen(false), [location.pathname])

  return (
    <div className="min-h-screen bg-slate-50">
      {menuOpen && <div className="fixed inset-0 z-30 bg-slate-900/40 lg:hidden" onClick={() => setMenuOpen(false)} aria-hidden="true" />}

      <aside className={`fixed inset-y-0 left-0 z-40 w-64 transform bg-slate-900 text-slate-200 transition-transform lg:translate-x-0 ${menuOpen ? 'translate-x-0' : '-translate-x-full'}`}>
        <div className="flex h-16 items-center gap-3 border-b border-slate-800 px-5">
          <img src="/favicon.svg" alt="" className="h-8 w-8" />
          <div>
            <p className="text-sm font-semibold text-white">CDSS</p>
            <p className="text-xs text-slate-400">Decision support prototype</p>
          </div>
        </div>
        <nav className="space-y-1 p-3" aria-label="Main">
          {navigation.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) =>
                `flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium ${isActive ? 'bg-teal-700 text-white' : 'text-slate-300 hover:bg-slate-800 hover:text-white'}`}
            >
              <Icon name={item.icon} />
              {item.label}
            </NavLink>
          ))}
        </nav>
        <p className="absolute inset-x-0 bottom-0 border-t border-slate-800 p-4 text-xs leading-relaxed text-slate-400">
          Educational prototype using synthetic data. Not for clinical use.
        </p>
      </aside>

      <div className="lg:pl-64">
        <header className="sticky top-0 z-20 flex h-16 items-center gap-3 border-b border-slate-200 bg-white px-4 sm:px-6">
          <button type="button" className="rounded-lg p-2 text-slate-600 hover:bg-slate-100 lg:hidden" onClick={() => setMenuOpen(true)} aria-label="Open menu">
            <Icon name="menu" />
          </button>
          <p className="truncate text-sm font-semibold text-slate-800 sm:text-base">Clinical Decision Support System</p>
          <div className="ml-auto flex items-center gap-2">
            <span className="hidden rounded-full bg-amber-100 px-2.5 py-1 text-xs font-medium text-amber-900 sm:inline" title="Login is added in a later step. Anyone who can reach the backend can use these screens.">
              No login (development mode)
            </span>
            <BackendStatus />
          </div>
        </header>

        <div className="border-b border-amber-200 bg-amber-50 px-4 py-2 text-center text-xs font-medium text-amber-900 sm:px-6 sm:text-sm">
          Educational prototype — synthetic data only — not for clinical use. Rule results are prompts for review, not diagnoses.
        </div>

        <main className="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:py-8">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

/** Small indicator in the top bar based on GET /api/health. */
function BackendStatus() {
  const [health, setHealth] = useState<Health | null | undefined>()

  useEffect(() => {
    let current = true
    getHealth().then((result) => { if (current) setHealth(result) })
    return () => { current = false }
  }, [])

  const [label, color] = health === undefined ? ['Checking backend…', 'bg-slate-400']
    : health === null ? ['Backend unreachable', 'bg-red-500']
      : health.status === 'UP' ? ['Backend and database up', 'bg-green-500']
        : ['Database unavailable', 'bg-amber-500']

  return (
    <span className="inline-flex items-center gap-2 rounded-full border border-slate-200 px-2.5 py-1 text-xs text-slate-600" title="From GET /api/health">
      <span className={`h-2 w-2 rounded-full ${color}`} aria-hidden="true" />
      <span className="hidden sm:inline">{label}</span>
      <span className="sm:hidden">{health?.status === 'UP' ? 'Online' : 'Status'}</span>
    </span>
  )
}
