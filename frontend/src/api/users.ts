import { apiRequest } from './client'
import type { Role, UserInput, UserSummary } from './types'

/** User management (ADMIN only on the backend). */
export const usersApi = {
  list: () => apiRequest<UserSummary[]>('GET', '/admin/users'),
  create: (input: UserInput) => apiRequest<UserSummary>('POST', '/admin/users', input),
  activate: (id: number) => apiRequest<UserSummary>('POST', `/admin/users/${id}/activate`),
  deactivate: (id: number) => apiRequest<UserSummary>('POST', `/admin/users/${id}/deactivate`),
  changeRole: (id: number, role: Role) => apiRequest<UserSummary>('PUT', `/admin/users/${id}/role`, { role }),
}
