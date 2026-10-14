import { describe, expect, it, vi } from 'vitest'
import { mockFetch, TEST_CSRF_TOKEN } from '../test/fetchMock'
import { ApiError, apiRequest, setUnauthorizedHandler } from './client'

describe('apiRequest', () => {
  it('returns parsed JSON and sends JSON bodies', async () => {
    const fetchMock = mockFetch({ 'POST /api/drugs': { status: 201, body: { id: 1, name: 'X' } } })

    const result = await apiRequest<{ id: number }>('POST', '/drugs', { name: 'X' })

    expect(result).toEqual({ id: 1, name: 'X' })
    const [, init] = fetchMock.mock.calls.find(([url]) => url === '/api/drugs')!
    expect(init?.body).toBe('{"name":"X"}')
    expect((init?.headers as Record<string, string>)['Content-Type']).toBe('application/json')
  })

  it('returns undefined for 204 No Content', async () => {
    mockFetch({ 'DELETE /api/drugs/1': { status: 204 } })

    await expect(apiRequest('DELETE', '/drugs/1')).resolves.toBeUndefined()
  })

  it('turns a backend validation error into ApiError with field errors', async () => {
    mockFetch({
      'POST /api/patients': {
        status: 400,
        body: { title: 'Bad Request', status: 400, detail: 'Validation failed', errors: { fullName: 'must not be blank' } },
      },
    })

    const error = await apiRequest('POST', '/patients', {}).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(400)
    expect((error as ApiError).message).toBe('Validation failed')
    expect((error as ApiError).fieldErrors).toEqual({ fullName: 'must not be blank' })
  })

  it('keeps the backend message for 404 and 409', async () => {
    mockFetch({ 'GET /api/patients/9': { status: 404, body: { status: 404, detail: 'Patient with id 9 was not found' } } })

    await expect(apiRequest('GET', '/patients/9')).rejects.toThrow('Patient with id 9 was not found')
  })

  it('explains when the backend cannot be reached', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))

    const error = (await apiRequest('GET', '/patients').catch((e: unknown) => e)) as ApiError

    expect(error.status).toBe(0)
    expect(error.message).toContain('Cannot reach the backend')
  })

  it('treats a proxy 502 without JSON as backend unreachable', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('Bad gateway', { status: 502 })))

    await expect(apiRequest('GET', '/patients')).rejects.toThrow('Cannot reach the backend')
  })

  it('fetches the CSRF token once and sends it on changes but not on reads', async () => {
    const fetchMock = mockFetch({ 'GET /api/drugs': { body: [] }, 'POST /api/drugs': { status: 201, body: {} }, 'DELETE /api/drugs/1': { status: 204 } })

    await apiRequest('GET', '/drugs')
    await apiRequest('POST', '/drugs', {})
    await apiRequest('DELETE', '/drugs/1')

    const calls = fetchMock.mock.calls.map(([url, init]) => ({ url, method: init?.method, headers: init?.headers as Record<string, string> }))
    expect(calls.map((c) => `${c.method} ${c.url}`)).toEqual(['GET /api/drugs', 'GET /api/auth/csrf', 'POST /api/drugs', 'DELETE /api/drugs/1'])
    expect(calls[0].headers['X-CSRF-TOKEN']).toBeUndefined()
    expect(calls[2].headers['X-CSRF-TOKEN']).toBe(TEST_CSRF_TOKEN)
    expect(calls[3].headers['X-CSRF-TOKEN']).toBe(TEST_CSRF_TOKEN)
  })

  it('gets a new CSRF token and retries once when the token is rejected', async () => {
    let tokens = 0
    let posts = 0
    const fetchMock = mockFetch({
      'GET /api/auth/csrf': () => ({ body: { headerName: 'X-CSRF-TOKEN', token: `token-${++tokens}` } }),
      'POST /api/patients': () => (++posts === 1
        ? { status: 403, body: { status: 403, detail: 'Missing or invalid CSRF token', code: 'CSRF' } }
        : { status: 201, body: { id: 4 } }),
    })

    await expect(apiRequest('POST', '/patients', {})).resolves.toEqual({ id: 4 })

    const sent = fetchMock.mock.calls.filter(([url]) => url === '/api/patients').map(([, init]) => (init?.headers as Record<string, string>)['X-CSRF-TOKEN'])
    expect(sent).toEqual(['token-1', 'token-2'])
  })

  it('does not retry more than once when the CSRF token keeps being rejected', async () => {
    const fetchMock = mockFetch({ 'POST /api/patients': { status: 403, body: { status: 403, detail: 'Missing or invalid CSRF token', code: 'CSRF' } } })

    await expect(apiRequest('POST', '/patients', {})).rejects.toThrow('Missing or invalid CSRF token')
    expect(fetchMock.mock.calls.filter(([url]) => url === '/api/patients')).toHaveLength(2)
  })

  it('shows a role refusal (403) without retrying', async () => {
    const fetchMock = mockFetch({ 'DELETE /api/drugs/1': { status: 403, body: { status: 403, detail: 'Your role does not allow this action' } } })

    await expect(apiRequest('DELETE', '/drugs/1')).rejects.toThrow('Your role does not allow this action')
    expect(fetchMock.mock.calls.filter(([url]) => url === '/api/drugs/1')).toHaveLength(1)
  })

  it('reports an ended session (401) to the auth handler, except for the auth endpoints', async () => {
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    mockFetch({
      'GET /api/patients': { status: 401, body: { status: 401, detail: 'You are not logged in or your session has ended' } },
      'GET /api/auth/me': { status: 401, body: { status: 401, detail: 'You are not logged in or your session has ended' } },
    })

    await expect(apiRequest('GET', '/auth/me')).rejects.toThrow()
    expect(handler).not.toHaveBeenCalled()
    await expect(apiRequest('GET', '/patients')).rejects.toThrow('You are not logged in or your session has ended')
    expect(handler).toHaveBeenCalledTimes(1)
  })
})
