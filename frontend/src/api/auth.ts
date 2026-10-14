import { apiRequest } from './client'
import type { CurrentUser } from './types'

export const authApi = {
  me: () => apiRequest<CurrentUser>('GET', '/auth/me'),
  login: (username: string, password: string) => apiRequest<CurrentUser>('POST', '/auth/login', { username, password }),
  logout: () => apiRequest<void>('POST', '/auth/logout'),
}
