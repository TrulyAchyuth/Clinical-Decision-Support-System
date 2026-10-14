// The one place that talks to the backend. Every other api/*.ts file uses apiRequest.

const configured = import.meta.env.VITE_API_BASE_URL
export const API_BASE_URL = (configured && configured.trim() !== '' ? configured : '/api').replace(/\/+$/, '')

/** An error from the backend (or from failing to reach it), ready to show to the user. */
export class ApiError extends Error {
  /** HTTP status, or 0 when the backend could not be reached. */
  readonly status: number
  /** Field (or rule path) to message, from the backend's validation errors. */
  readonly fieldErrors: Record<string, string>
  /** The whole error body, for responses that carry extra data (e.g. a safety check with a 409). */
  readonly body: Record<string, unknown>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}, body: Record<string, unknown> = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
    this.body = body
  }
}

type Method = 'GET' | 'POST' | 'PUT' | 'DELETE'

interface CsrfToken {
  headerName: string
  token: string
}

// The session's CSRF token, kept in memory only. Every POST, PUT and DELETE sends it in a header.
let csrfToken: CsrfToken | undefined
let onUnauthorized: (() => void) | undefined

/** Forgets the CSRF token. Login and logout replace it on the server, so it is fetched again. */
export function clearCsrfToken() {
  csrfToken = undefined
}

/** Set by AuthProvider: called when the backend says a request needs a login (the session has ended). */
export function setUnauthorizedHandler(handler: (() => void) | undefined) {
  onUnauthorized = handler
}

/**
 * Sends a JSON request and returns the parsed JSON response (undefined for 204).
 * Backend errors are RFC 9457 Problem Details: { status, title, detail, errors?, code? }.
 * If the backend rejects the CSRF token (e.g. the session expired), a new token is
 * fetched and the request is sent once more.
 */
export async function apiRequest<T>(method: Method, path: string, body?: unknown, isRetry = false): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (method !== 'GET') {
    csrfToken ??= await apiRequest<CsrfToken>('GET', '/auth/csrf')
    headers[csrfToken.headerName] = csrfToken.token
  }

  let response: Response
  try {
    response = await fetch(API_BASE_URL + path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw unreachable()
  }

  const text = await response.text()
  const json = parseJson(text)

  if (response.ok) {
    return (text === '' ? undefined : json) as T
  }
  // Login and the "who am I" check answer 401 as part of their normal job; elsewhere it means the session ended.
  if (response.status === 401 && !path.startsWith('/auth/')) {
    onUnauthorized?.()
  }
  if (json && typeof json === 'object') {
    const problem = json as { detail?: unknown; title?: unknown; errors?: unknown; code?: unknown }
    if (response.status === 403 && problem.code === 'CSRF' && !isRetry) {
      clearCsrfToken()
      return apiRequest<T>(method, path, body, true)
    }
    const message = typeof problem.detail === 'string' ? problem.detail
      : typeof problem.title === 'string' ? problem.title : `Request failed (${response.status})`
    return Promise.reject(new ApiError(response.status, message, toFieldErrors(problem.errors), json as Record<string, unknown>))
  }
  if (response.status === 401) {
    throw new ApiError(401, 'You are not logged in or your session has ended.')
  }
  if (response.status >= 500) {
    // The dev-server proxy answers 5xx without a JSON body when the backend is down.
    throw unreachable()
  }
  throw new ApiError(response.status, `Request failed (${response.status})`)
}

function unreachable(): ApiError {
  return new ApiError(0, `Cannot reach the backend (${API_BASE_URL}). Check that it is running: cd backend && ./mvnw spring-boot:run`)
}

function parseJson(text: string): unknown {
  if (text === '') return undefined
  try {
    return JSON.parse(text)
  } catch {
    return undefined
  }
}

function toFieldErrors(errors: unknown): Record<string, string> {
  if (!errors || typeof errors !== 'object' || Array.isArray(errors)) return {}
  const result: Record<string, string> = {}
  for (const [key, value] of Object.entries(errors)) {
    result[key] = String(value)
  }
  return result
}

/** Message for any thrown value. */
export function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'Something went wrong'
}
