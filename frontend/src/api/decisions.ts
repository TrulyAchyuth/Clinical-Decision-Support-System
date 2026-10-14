import { apiRequest } from './client'
import type { Decision } from './types'

export const decisionsApi = {
  /** Evaluates all enabled rules now; the backend stores the decision. */
  request: (patientId: number) => apiRequest<Decision>('POST', `/patients/${patientId}/decisions`),
  history: (patientId: number) => apiRequest<Decision[]>('GET', `/patients/${patientId}/decisions`),
}
