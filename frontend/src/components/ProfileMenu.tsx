import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { useNavigate } from 'react-router'
import { useAuth } from './AuthProvider'
import { Icon } from './Icon'

export const roleLabel = (role: 'ADMIN' | 'CLINICIAN') => (role === 'ADMIN' ? 'Administrator' : 'Clinician')

/** Up to two initials from the username, e.g. "clinician" -> "CL", "jane.doe" -> "JD". */
export function initials(username: string): string {
  const parts = username.split(/[._\-\s]+/).filter(Boolean)
  return (parts.length > 1 ? parts[0][0] + parts[1][0] : username.slice(0, 2)).toUpperCase()
}

/**
 * Top-right account menu: the logged-in username and role (from the
 * backend session), My profile and Log out. Keyboard: Enter/Space/Arrow Down
 * opens it, arrows move between items, Escape closes and returns focus.
 * Clicking outside closes it.
 */
export function ProfileMenu({ onError }: { onError: (error: unknown) => void }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const container = useRef<HTMLDivElement>(null)
  const button = useRef<HTMLButtonElement>(null)
  const items = useRef<(HTMLButtonElement | null)[]>([])

  useEffect(() => {
    if (!open) return
    items.current[0]?.focus()
    function onPointerDown(event: MouseEvent) {
      if (container.current && !container.current.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onPointerDown)
    return () => document.removeEventListener('mousedown', onPointerDown)
  }, [open])

  if (!user) return null

  function close() {
    setOpen(false)
    button.current?.focus()
  }

  function onMenuKeyDown(event: KeyboardEvent) {
    const list = items.current.filter((item): item is HTMLButtonElement => item !== null)
    const index = list.indexOf(document.activeElement as HTMLButtonElement)
    if (event.key === 'Escape') { event.preventDefault(); close() }
    else if (event.key === 'ArrowDown') { event.preventDefault(); list[(index + 1) % list.length]?.focus() }
    else if (event.key === 'ArrowUp') { event.preventDefault(); list[(index - 1 + list.length) % list.length]?.focus() }
    else if (event.key === 'Tab') setOpen(false)
  }

  async function logOut() {
    setBusy(true)
    try {
      await logout()
    } catch (err) {
      onError(err)
    } finally {
      setBusy(false)
      setOpen(false)
    }
  }

  return (
    <div className="relative" ref={container}>
      <button ref={button} type="button" aria-haspopup="menu" aria-expanded={open} aria-label={`Account menu for ${user.username}`}
        onClick={() => setOpen((value) => !value)}
        onKeyDown={(e) => { if (e.key === 'ArrowDown') { e.preventDefault(); setOpen(true) } }}
        className="flex items-center gap-2 rounded-full border border-slate-200 bg-white py-1 pl-1 pr-2 text-left hover:bg-slate-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-teal-700">
        <span className="flex h-8 w-8 items-center justify-center rounded-full bg-teal-700 text-xs font-semibold text-white" aria-hidden="true">{initials(user.username)}</span>
        <span className="hidden leading-tight sm:block">
          <span className="block text-sm font-medium text-slate-900">{user.username}</span>
          <span className="block text-xs text-slate-500">{roleLabel(user.role)}</span>
        </span>
        <Icon name="chevron" className="h-4 w-4 text-slate-400" />
      </button>
      {open && (
        <div role="menu" aria-label="Account" onKeyDown={onMenuKeyDown}
          className="absolute right-0 z-40 mt-2 w-60 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-lg">
          <div className="border-b border-slate-100 px-4 py-3">
            <p className="text-sm font-semibold text-slate-900">{user.username}</p>
            <p className="mt-0.5 inline-flex rounded-full bg-teal-50 px-2 py-0.5 text-xs font-medium text-teal-800 ring-1 ring-inset ring-teal-200">{user.role}</p>
          </div>
          <button ref={(el) => { items.current[0] = el }} role="menuitem" type="button"
            onClick={() => { setOpen(false); navigate('/profile') }}
            className="flex w-full items-center gap-3 px-4 py-2.5 text-left text-sm text-slate-700 hover:bg-slate-50 focus:bg-slate-100 focus:outline-none">
            <Icon name="user" className="h-4 w-4 text-slate-500" /> My profile
          </button>
          <button ref={(el) => { items.current[1] = el }} role="menuitem" type="button" onClick={logOut} disabled={busy}
            className="flex w-full items-center gap-3 border-t border-slate-100 px-4 py-2.5 text-left text-sm text-red-700 hover:bg-red-50 focus:bg-red-50 focus:outline-none disabled:opacity-50">
            <Icon name="logout" className="h-4 w-4" /> {busy ? 'Logging out…' : 'Log out'}
          </button>
        </div>
      )}
    </div>
  )
}
