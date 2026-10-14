import { apiRequest } from './client'
import type { Allergy, AllergyInput, AllergyUpdate, PatientAllergies } from './types'

const base = (patientId: number) => `/patients/${patientId}/allergies`

export const allergiesApi = {
  list: (patientId: number) => apiRequest<PatientAllergies>('GET', base(patientId)),
  add: (patientId: number, input: AllergyInput) => apiRequest<Allergy>('POST', base(patientId), input),
  update: (patientId: number, id: number, input: AllergyUpdate) => apiRequest<Allergy>('PUT', `${base(patientId)}/${id}`, input),
  resolve: (patientId: number, id: number, reason: string) => apiRequest<Allergy>('POST', `${base(patientId)}/${id}/resolve`, { reason }),
  confirmNoKnownAllergies: (patientId: number) => apiRequest<PatientAllergies>('POST', `${base(patientId)}/no-known-allergies`),
}
