import { apiRequest } from './client'
import type {
  LabResult, LabResultInput, PatientCondition, PatientConditionInput, VitalSigns, VitalSignsInput,
} from './types'

/** A patient's vital signs, lab results and conditions: /api/patients/{id}/... */
export const clinicalRecordsApi = {
  listVitalSigns: (patientId: number) => apiRequest<VitalSigns[]>('GET', `/patients/${patientId}/vital-signs`),
  addVitalSigns: (patientId: number, input: VitalSignsInput) =>
    apiRequest<VitalSigns>('POST', `/patients/${patientId}/vital-signs`, input),
  removeVitalSigns: (patientId: number, id: number) => apiRequest<void>('DELETE', `/patients/${patientId}/vital-signs/${id}`),

  listLabResults: (patientId: number) => apiRequest<LabResult[]>('GET', `/patients/${patientId}/lab-results`),
  addLabResult: (patientId: number, input: LabResultInput) =>
    apiRequest<LabResult>('POST', `/patients/${patientId}/lab-results`, input),
  removeLabResult: (patientId: number, id: number) => apiRequest<void>('DELETE', `/patients/${patientId}/lab-results/${id}`),

  listConditions: (patientId: number) => apiRequest<PatientCondition[]>('GET', `/patients/${patientId}/conditions`),
  addCondition: (patientId: number, input: PatientConditionInput) =>
    apiRequest<PatientCondition>('POST', `/patients/${patientId}/conditions`, input),
  removeCondition: (patientId: number, id: number) => apiRequest<void>('DELETE', `/patients/${patientId}/conditions/${id}`),
}
