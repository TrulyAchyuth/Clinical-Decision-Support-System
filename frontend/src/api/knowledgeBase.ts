import { apiRequest } from './client'
import type { Disease, DiseaseInput, Drug, DrugInput, LabTest, LabTestInput } from './types'

export const drugsApi = {
  list: () => apiRequest<Drug[]>('GET', '/drugs'),
  create: (input: DrugInput) => apiRequest<Drug>('POST', '/drugs', input),
  update: (id: number, input: DrugInput) => apiRequest<Drug>('PUT', `/drugs/${id}`, input),
  remove: (id: number) => apiRequest<void>('DELETE', `/drugs/${id}`),
}

export const diseasesApi = {
  list: () => apiRequest<Disease[]>('GET', '/diseases'),
  create: (input: DiseaseInput) => apiRequest<Disease>('POST', '/diseases', input),
  update: (id: number, input: DiseaseInput) => apiRequest<Disease>('PUT', `/diseases/${id}`, input),
  remove: (id: number) => apiRequest<void>('DELETE', `/diseases/${id}`),
}

export const labTestsApi = {
  list: () => apiRequest<LabTest[]>('GET', '/lab-tests'),
  create: (input: LabTestInput) => apiRequest<LabTest>('POST', '/lab-tests', input),
  update: (id: number, input: LabTestInput) => apiRequest<LabTest>('PUT', `/lab-tests/${id}`, input),
  remove: (id: number) => apiRequest<void>('DELETE', `/lab-tests/${id}`),
}
