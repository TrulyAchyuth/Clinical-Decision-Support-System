import { useAuth } from '../components/AuthProvider'
import { AdminDashboard } from './AdminDashboard'
import { ClinicianDashboard } from './ClinicianDashboard'

/** Each role has its own dashboard. */
export function DashboardPage() {
  const { user } = useAuth()
  return user?.role === 'ADMIN' ? <AdminDashboard /> : <ClinicianDashboard />
}
