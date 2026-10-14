import { API_BASE_URL } from './client'
import type { Health } from './types'

/** GET /api/health — returns the body for both 200 (UP) and 503 (DEGRADED); null if unreachable. */
export async function getHealth(): Promise<Health | null> {
  try {
    const response = await fetch(`${API_BASE_URL}/health`, { headers: { Accept: 'application/json' } })
    if (response.status !== 200 && response.status !== 503) return null
    return (await response.json()) as Health
  } catch {
    return null
  }
}
