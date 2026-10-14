import { apiRequest } from './client'
import type { Patient, PatientInput } from './types'

export const patientsApi = {
  list: (search?: string) =>
    apiRequest<Patient[]>('GET', search ? `/patients?search=${encodeURIComponent(search)}` : '/patients'),
  get: (id: number) => apiRequest<Patient>('GET', `/patients/${id}`),
  create: (input: PatientInput) => apiRequest<Patient>('POST', '/patients', input),
  update: (id: number, input: PatientInput) => apiRequest<Patient>('PUT', `/patients/${id}`, input),
  remove: (id: number) => apiRequest<void>('DELETE', `/patients/${id}`),
}
