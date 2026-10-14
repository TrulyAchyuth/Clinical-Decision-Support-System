import { vi } from 'vitest'

export interface MockResponse {
  status?: number
  body?: unknown
}

/**
 * Replaces fetch with fake backend responses keyed by "METHOD /api/path",
 * e.g. { 'GET /api/patients': { body: [] } }. Unknown requests fail the test.
 * Returns the mock so tests can inspect the calls that were made.
 */
export function mockFetch(routes: Record<string, MockResponse | (() => MockResponse)>) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const key = `${init?.method ?? 'GET'} ${String(input)}`
    const route = routes[key]
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
