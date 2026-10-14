// Small, reusable building blocks used by every page.
import type { ButtonHTMLAttributes, ReactNode } from 'react'
import { Link } from 'react-router'
import { ApiError, errorMessage } from '../api/client'
import { Icon, type IconName } from './Icon'

export function PageHeader({ title, description, actions }: { title: string; description?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-slate-900">{title}</h1>
        {description && <p className="mt-1 text-sm text-slate-600">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  )
}

export function Card({ title, actions, children, className = '' }: { title?: ReactNode; actions?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <section className={`rounded-2xl border border-slate-200 bg-white shadow-sm ${className}`}>
      {(title || actions) && (
        <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 px-5 py-3">
          {title && <h2 className="text-base font-semibold text-slate-800">{title}</h2>}
          {actions}
        </div>
      )}
      <div className="p-5">{children}</div>
    </section>
  )
}

type Variant = 'primary' | 'secondary' | 'danger' | 'ghost'

const variants: Record<Variant, string> = {
  primary: 'bg-teal-700 text-white hover:bg-teal-800 focus-visible:outline-teal-700',
  secondary: 'border border-slate-300 bg-white text-slate-700 hover:bg-slate-50 focus-visible:outline-slate-400',
  danger: 'border border-red-200 bg-white text-red-700 hover:bg-red-50 focus-visible:outline-red-600',
  ghost: 'text-teal-800 hover:bg-teal-50 focus-visible:outline-teal-700',
}

export function Button({ variant = 'primary', className = '', ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant }) {
  return (
    <button
      type="button"
      className={`inline-flex items-center justify-center gap-1.5 rounded-lg px-3.5 py-2 text-sm font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 disabled:cursor-not-allowed disabled:opacity-50 ${variants[variant]} ${className}`}
      {...props}
    />
  )
}

type AlertKind = 'error' | 'success' | 'info' | 'warning'

const alertStyles: Record<AlertKind, string> = {
  error: 'border-red-200 bg-red-50 text-red-800',
  success: 'border-green-200 bg-green-50 text-green-800',
  info: 'border-sky-200 bg-sky-50 text-sky-900',
  warning: 'border-amber-300 bg-amber-50 text-amber-900',
}

export function Alert({ kind, children, onDismiss }: { kind: AlertKind; children: ReactNode; onDismiss?: () => void }) {
  return (
    <div role={kind === 'error' ? 'alert' : 'status'} className={`mb-4 flex items-start justify-between gap-3 rounded-lg border px-4 py-3 text-sm ${alertStyles[kind]}`}>
      <div className="min-w-0">{children}</div>
      {onDismiss && (
        <button type="button" onClick={onDismiss} className="shrink-0 font-semibold opacity-70 hover:opacity-100" aria-label="Dismiss">×</button>
      )}
    </div>
  )
}

/** Shows an API (or any) error, including each field error the backend returned. */
export function ErrorAlert({ error, onRetry, onDismiss }: { error: unknown; onRetry?: () => void; onDismiss?: () => void }) {
  const fieldErrors = error instanceof ApiError ? Object.entries(error.fieldErrors) : []
  return (
    <Alert kind="error" onDismiss={onDismiss}>
      <p className="font-medium">{errorMessage(error)}</p>
      {fieldErrors.length > 0 && (
        <ul className="mt-1 list-disc pl-5">
          {fieldErrors.map(([field, message]) => <li key={field}><span className="font-mono text-xs">{field}</span>: {message}</li>)}
        </ul>
      )}
      {onRetry && <button type="button" onClick={onRetry} className="mt-2 font-medium underline">Try again</button>}
    </Alert>
  )
}

export function Loading({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="flex items-center gap-2 py-6 text-sm text-slate-500" role="status">
      <span className="h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-teal-700" aria-hidden="true" />
      {label}
    </div>
  )
}

export function EmptyState({ children }: { children: ReactNode }) {
  return <p className="rounded-lg border border-dashed border-slate-300 px-4 py-6 text-center text-sm text-slate-500">{children}</p>
}

export const inputClass =
  'w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-teal-600 focus:outline-none focus:ring-2 focus:ring-teal-600/20 aria-[invalid=true]:border-red-400'

/** A labelled form field. `error` comes from the backend's validation response. */
export function Field({ label, htmlFor, error, hint, required, children }: {
  label: string; htmlFor: string; error?: string; hint?: ReactNode; required?: boolean; children: ReactNode
}) {
  return (
    <div>
      <label htmlFor={htmlFor} className="mb-1 block text-sm font-medium text-slate-700">
        {label}{required && <span className="text-red-600"> *</span>}
      </label>
      {children}
      {hint && !error && <p className="mt-1 text-xs text-slate-500">{hint}</p>}
      {error && <p className="mt-1 text-xs text-red-700">{error}</p>}
    </div>
  )
}

type Tone = 'red' | 'amber' | 'green' | 'slate' | 'sky' | 'teal'

const tones: Record<Tone, string> = {
  red: 'bg-red-100 text-red-800 ring-red-200',
  amber: 'bg-amber-100 text-amber-900 ring-amber-200',
  green: 'bg-green-100 text-green-800 ring-green-200',
  slate: 'bg-slate-100 text-slate-700 ring-slate-200',
  sky: 'bg-sky-100 text-sky-800 ring-sky-200',
  teal: 'bg-teal-100 text-teal-800 ring-teal-200',
}

export function Badge({ tone = 'slate', children }: { tone?: Tone; children: ReactNode }) {
  return <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset ${tones[tone]}`}>{children}</span>
}

export function SeverityBadge({ severity }: { severity: 'LOW' | 'MEDIUM' | 'HIGH' }) {
  const tone: Tone = severity === 'HIGH' ? 'red' : severity === 'MEDIUM' ? 'amber' : 'sky'
  return <Badge tone={tone}>{severity} severity</Badge>
}

/** Wraps a table so it scrolls sideways on small screens instead of breaking the layout. */
export function Table({ children }: { children: ReactNode }) {
  return (
    <div className="-mx-5 overflow-x-auto">
      <table className="min-w-full text-left text-sm">{children}</table>
    </div>
  )
}

export const th = 'whitespace-nowrap border-b border-slate-200 bg-slate-50 px-5 py-2.5 text-xs font-semibold uppercase tracking-wide text-slate-500'
export const td = 'border-b border-slate-100 px-5 py-3 align-top text-slate-700'

const statTones = {
  teal: 'bg-teal-50 text-teal-700',
  amber: 'bg-amber-50 text-amber-700',
  red: 'bg-red-50 text-red-700',
  sky: 'bg-sky-50 text-sky-700',
  slate: 'bg-slate-100 text-slate-600',
  green: 'bg-green-50 text-green-700',
} as const

/** A dashboard metric from real data: label, value, icon and optional link. */
export function StatCard({ label, value, icon, tone = 'teal', to, note }: {
  label: string; value: ReactNode; icon: IconName; tone?: keyof typeof statTones; to?: string; note?: ReactNode
}) {
  const content = (
    <>
      <span className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl ${statTones[tone]}`}><Icon name={icon} /></span>
      <span className="min-w-0">
        <span className="block text-sm text-slate-500">{label}</span>
        <span className="mt-0.5 block text-2xl font-semibold tracking-tight text-slate-900">{value}</span>
        {note && <span className="block text-xs text-slate-500">{note}</span>}
      </span>
    </>
  )
  const className = 'flex items-start gap-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm'
  return to
    ? <Link to={to} className={`${className} transition-colors hover:border-teal-600 focus-visible:outline-2 focus-visible:outline-teal-700`}>{content}</Link>
    : <div className={className}>{content}</div>
}

/** A shortcut to a workflow, with a short explanation of where it leads. */
export function QuickAction({ to, icon, title, description }: { to: string; icon: IconName; title: string; description: string }) {
  return (
    <Link to={to} className="group flex items-start gap-3 rounded-xl border border-slate-200 bg-white p-4 transition-colors hover:border-teal-600 hover:bg-teal-50/40 focus-visible:outline-2 focus-visible:outline-teal-700">
      <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-teal-50 text-teal-700 group-hover:bg-teal-100"><Icon name={icon} className="h-5 w-5" /></span>
      <span>
        <span className="block text-sm font-semibold text-slate-900">{title}</span>
        <span className="mt-0.5 block text-xs text-slate-500">{description}</span>
      </span>
    </Link>
  )
}
