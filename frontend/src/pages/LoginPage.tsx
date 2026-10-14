import { useState, type FormEvent } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { ApiError } from '../api/client'
import { useAuth } from '../components/AuthProvider'
import { Alert, Button, ErrorAlert, Field, inputClass, Loading } from '../components/ui'

export function LoginPage() {
  const { user, sessionEnded, checkError, login } = useAuth()
  const navigate = useNavigate()
  const from = (useLocation().state as { from?: string } | null)?.from ?? '/'
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()
  const fieldErrors = error instanceof ApiError ? error.fieldErrors : {}

  if (user === undefined) return <Loading label="Checking login…" />
  if (user) return <Navigate to={from} replace />

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true); setError(undefined)
    try {
      await login(username, password)
      navigate(from, { replace: true })
    } catch (err) {
      setError(err)
      setPassword('')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4 py-10">
      <div className="w-full max-w-sm">
        <div className="mb-6 flex items-center gap-3">
          <img src="/favicon.svg" alt="" className="h-10 w-10" />
          <div>
            <h1 className="text-xl font-semibold text-slate-900">Clinical Decision Support System</h1>
            <p className="text-sm text-slate-500">Patient Insights &amp; Clinical Decision Support</p>
          </div>
        </div>
        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <h2 className="mb-4 text-base font-semibold text-slate-900">Sign in</h2>
          {sessionEnded && error === undefined && <Alert kind="info">Your session has ended. Please log in again.</Alert>}
          {checkError !== undefined && error === undefined && <ErrorAlert error={checkError} />}
          {error !== undefined && <ErrorAlert error={error} />}
          <form onSubmit={submit} noValidate className="space-y-4">
            <Field label="Username" htmlFor="login-username" required error={fieldErrors.username}>
              <input id="login-username" className={inputClass} autoComplete="username" autoFocus
                value={username} onChange={(e) => setUsername(e.target.value)} />
            </Field>
            <Field label="Password" htmlFor="login-password" required error={fieldErrors.password}>
              <input id="login-password" type="password" className={inputClass} autoComplete="current-password"
                value={password} onChange={(e) => setPassword(e.target.value)} />
            </Field>
            <Button type="submit" className="w-full" disabled={busy}>{busy ? 'Logging in…' : 'Log in'}</Button>
          </form>
        </div>
        <p className="mt-4 text-center text-xs text-slate-500">
          Educational prototype — synthetic data only — not for clinical use.
        </p>
      </div>
    </div>
  )
}
