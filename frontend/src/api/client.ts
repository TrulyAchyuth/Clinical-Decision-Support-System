// The one place that talks to the backend. Every other api/*.ts file uses apiRequest.

const configured = import.meta.env.VITE_API_BASE_URL
export const API_BASE_URL = (configured && configured.trim() !== '' ? configured : '/api').replace(/\/+$/, '')

/** An error from the backend (or from failing to reach it), ready to show to the user. */
export class ApiError extends Error {
  /** HTTP status, or 0 when the backend could not be reached. */
  readonly status: number
  /** Field (or rule path) to message, from the backend's validation errors. */
  readonly fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

type Method = 'GET' | 'POST' | 'PUT' | 'DELETE'

/**
 * Sends a JSON request and returns the parsed JSON response (undefined for 204).
 * Backend errors are RFC 9457 Problem Details: { status, title, detail, errors? }.
 */
export async function apiRequest<T>(method: Method, path: string, body?: unknown): Promise<T> {
  let response: Response
  try {
    response = await fetch(API_BASE_URL + path, {
      method,
      headers: body === undefined ? { Accept: 'application/json' } : { Accept: 'application/json', 'Content-Type': 'application/json' },
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
  if (json && typeof json === 'object') {
    const problem = json as { detail?: unknown; title?: unknown; errors?: unknown }
    const message = typeof problem.detail === 'string' ? problem.detail
      : typeof problem.title === 'string' ? problem.title : `Request failed (${response.status})`
    return Promise.reject(new ApiError(response.status, message, toFieldErrors(problem.errors)))
  }
  if (response.status === 401) {
    throw new ApiError(401, 'The backend refused the request (401). Start it with the dev profile (`./mvnw spring-boot:run`); login is not implemented yet.')
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
