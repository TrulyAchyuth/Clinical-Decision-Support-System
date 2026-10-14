import { apiRequest } from './client'
import type { DrugInteraction, Medication, MedicationInput, MedicationUpdate, SafetyCheck } from './types'

const base = (patientId: number) => `/patients/${patientId}/medications`

export const medicationsApi = {
  list: (patientId: number) => apiRequest<Medication[]>('GET', base(patientId)),
  safetyCheck: (patientId: number, drugId: number) => apiRequest<SafetyCheck>('POST', `${base(patientId)}/safety-check`, { drugId }),
  add: (patientId: number, input: MedicationInput) => apiRequest<Medication>('POST', base(patientId), input),
  update: (patientId: number, id: number, input: MedicationUpdate) => apiRequest<Medication>('PUT', `${base(patientId)}/${id}`, input),
  discontinue: (patientId: number, id: number, reason: string) =>
    apiRequest<Medication>('POST', `${base(patientId)}/${id}/discontinue`, { reason }),
  complete: (patientId: number, id: number, reason?: string) =>
    apiRequest<Medication>('POST', `${base(patientId)}/${id}/complete`, { reason }),
  interactions: () => apiRequest<DrugInteraction[]>('GET', '/drug-interactions'),
}
