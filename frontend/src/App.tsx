import { Route, Routes } from 'react-router'
import { RequireAuth } from './components/AuthProvider'
import { RequireRole } from './components/RequireRole'
import { Layout } from './components/Layout'
import { DashboardPage } from './pages/DashboardPage'
import { DecisionsPage } from './pages/DecisionsPage'
import { DiseasesPage } from './pages/DiseasesPage'
import { DrugsPage } from './pages/DrugsPage'
import { FollowUpsPage } from './pages/FollowUpsPage'
import { LabTestsPage } from './pages/LabTestsPage'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { PatientDetailPage } from './pages/PatientDetailPage'
import { PatientFormPage } from './pages/PatientFormPage'
import { PatientsPage } from './pages/PatientsPage'
import { ProfilePage } from './pages/ProfilePage'
import { RuleSummariesPage } from './pages/RuleSummariesPage'
import { RulesPage } from './pages/RulesPage'
import { UsersPage } from './pages/UsersPage'

export default function App() {
  return (
    <Routes>
      <Route path="login" element={<LoginPage />} />
      <Route element={<RequireAuth><Layout /></RequireAuth>}>
        <Route index element={<DashboardPage />} />
        <Route path="patients" element={<PatientsPage />} />
        <Route path="patients/new" element={<PatientFormPage />} />
        <Route path="patients/:id" element={<PatientDetailPage />} />
        <Route path="patients/:id/edit" element={<PatientFormPage />} />
        <Route path="decisions" element={<DecisionsPage />} />
        <Route path="follow-ups" element={<FollowUpsPage />} />
        <Route path="drugs" element={<DrugsPage />} />
        <Route path="diseases" element={<DiseasesPage />} />
        <Route path="lab-tests" element={<LabTestsPage />} />
        <Route path="reference/rules" element={<RuleSummariesPage />} />
        <Route path="profile" element={<ProfilePage />} />
        <Route path="rules" element={<RequireRole role="ADMIN"><RulesPage /></RequireRole>} />
        <Route path="admin/users" element={<RequireRole role="ADMIN"><UsersPage /></RequireRole>} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
