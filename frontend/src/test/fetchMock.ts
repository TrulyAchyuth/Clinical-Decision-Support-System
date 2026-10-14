import { vi } from 'vitest'

export interface MockResponse {
  status?: number
  body?: unknown
}

export const TEST_CSRF_TOKEN = 'test-csrf-token'

/**
 * Replaces fetch with fake backend responses keyed by "METHOD /api/path",
 * e.g. { 'GET /api/patients': { body: [] } }. Unknown requests fail the test.
 * GET /api/auth/csrf answers with TEST_CSRF_TOKEN unless a test sets its own.
 * Returns the mock so tests can inspect the calls that were made.
 */
export function mockFetch(routes: Record<string, MockResponse | (() => MockResponse)>) {
  const allRoutes: Record<string, MockResponse | (() => MockResponse)> = {
    'GET /api/auth/csrf': { body: { headerName: 'X-CSRF-TOKEN', token: TEST_CSRF_TOKEN } },
    ...routes,
  }
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const key = `${init?.method ?? 'GET'} ${String(input)}`
    const route = allRoutes[key]
    if (!route) throw new Error(`Unexpected request in test: ${key}`)
    const { status = 200, body } = typeof route === 'function' ? route() : route
    return new Response(body === undefined ? null : JSON.stringify(body), {
      status,
      headers: { 'Content-Type': 'application/json' },
    })
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}
