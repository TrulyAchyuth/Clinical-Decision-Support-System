import { describe, expect, it, vi } from 'vitest'
import { mockFetch } from '../test/fetchMock'
import { ApiError, apiRequest } from './client'

describe('apiRequest', () => {
  it('returns parsed JSON and sends JSON bodies', async () => {
    const fetchMock = mockFetch({ 'POST /api/drugs': { status: 201, body: { id: 1, name: 'X' } } })

    const result = await apiRequest<{ id: number }>('POST', '/drugs', { name: 'X' })

    expect(result).toEqual({ id: 1, name: 'X' })
    const [, init] = fetchMock.mock.calls[0]
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
})
