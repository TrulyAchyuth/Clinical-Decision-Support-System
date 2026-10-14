import { apiRequest } from './client'
import type { FollowUp, FollowUpInput } from './types'

const base = (patientId: number) => `/patients/${patientId}/follow-ups`

export const followUpsApi = {
  listAll: () => apiRequest<FollowUp[]>('GET', '/follow-ups'),
  list: (patientId: number) => apiRequest<FollowUp[]>('GET', base(patientId)),
  schedule: (patientId: number, input: FollowUpInput) => apiRequest<FollowUp>('POST', base(patientId), input),
  reschedule: (patientId: number, id: number, scheduledAt: string | null) =>
    apiRequest<FollowUp>('POST', `${base(patientId)}/${id}/reschedule`, { scheduledAt }),
  complete: (patientId: number, id: number, notes?: string) => apiRequest<FollowUp>('POST', `${base(patientId)}/${id}/complete`, { notes }),
  cancel: (patientId: number, id: number, notes: string) => apiRequest<FollowUp>('POST', `${base(patientId)}/${id}/cancel`, { notes }),
}
