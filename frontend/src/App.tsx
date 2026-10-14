import { Route, Routes } from 'react-router'
import { Layout } from './components/Layout'
import { DashboardPage } from './pages/DashboardPage'
import { DecisionsPage } from './pages/DecisionsPage'
import { DiseasesPage } from './pages/DiseasesPage'
import { DrugsPage } from './pages/DrugsPage'
import { LabTestsPage } from './pages/LabTestsPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { PatientDetailPage } from './pages/PatientDetailPage'
import { PatientFormPage } from './pages/PatientFormPage'
import { PatientsPage } from './pages/PatientsPage'
import { RulesPage } from './pages/RulesPage'

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<DashboardPage />} />
        <Route path="patients" element={<PatientsPage />} />
        <Route path="patients/new" element={<PatientFormPage />} />
        <Route path="patients/:id" element={<PatientDetailPage />} />
        <Route path="patients/:id/edit" element={<PatientFormPage />} />
        <Route path="decisions" element={<DecisionsPage />} />
        <Route path="drugs" element={<DrugsPage />} />
        <Route path="diseases" element={<DiseasesPage />} />
        <Route path="lab-tests" element={<LabTestsPage />} />
        <Route path="rules" element={<RulesPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
