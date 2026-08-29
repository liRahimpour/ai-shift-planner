import { Navigate, Route, Routes } from 'react-router-dom'
import { MANAGER_ROLES, useAuth } from '@/features/auth/AuthContext'
import { RequireAuth, RequireRole } from '@/features/auth/guards'
import LoginPage from '@/features/auth/LoginPage'
import AuditPage from '@/features/audit/AuditPage'
import AvailabilityPage from '@/features/availability/AvailabilityPage'
import CommentsPage from '@/features/availability/CommentsPage'
import DashboardPage from '@/features/dashboard/DashboardPage'
import PeriodLayout from '@/features/dashboard/PeriodLayout'
import PeriodsPage from '@/features/dashboard/PeriodsPage'
import EmployeesPage from '@/features/employees/EmployeesPage'
import ProposalsPage from '@/features/proposals/ProposalsPage'
import MySchedulePage from '@/features/schedule/MySchedulePage'
import SchedulePage from '@/features/schedule/SchedulePage'
import StaffingPage from '@/features/staffing/StaffingPage'
import AppLayout from '@/ui/AppLayout'
import { EmptyState } from '@/ui/primitives'

/**
 * Route table.
 *
 * Period-scoped screens nest under `/periods/:periodId/...` so the id is always in the URL:
 * links are shareable, a reload lands where you were, and context-aware components (the
 * chat) can read the current period without any prop plumbing.
 */
export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      <Route element={<RequireAuth />}>
        <Route element={<AppLayout />}>
          <Route index element={<HomeRedirect />} />

          <Route path="me/availability" element={<AvailabilityPage />} />
          <Route path="me/schedule" element={<MySchedulePage />} />

          <Route element={<RequireRole roles={MANAGER_ROLES} />}>
            <Route path="periods" element={<PeriodsPage />} />
            <Route path="periods/:periodId" element={<PeriodLayout />}>
              <Route index element={<DashboardPage />} />
              <Route path="staffing" element={<StaffingPage />} />
              <Route path="comments" element={<CommentsPage />} />
              <Route path="proposals" element={<ProposalsPage />} />
              <Route path="schedules/:scheduleId" element={<SchedulePage />} />
            </Route>
            <Route path="employees" element={<EmployeesPage />} />
          </Route>

          <Route element={<RequireRole roles={['LOCATION_MANAGER', 'ORG_ADMIN', 'SUPER_ADMIN']} />}>
            <Route path="audit" element={<AuditPage />} />
          </Route>

          <Route path="*" element={<NotFound />} />
        </Route>
      </Route>
    </Routes>
  )
}

/** Managers start at planning; everyone else starts at their own week. */
function HomeRedirect() {
  const { isManager } = useAuth()
  return <Navigate to={isManager ? '/periods' : '/me/availability'} replace />
}

function NotFound() {
  return (
    <div className="page">
      <EmptyState title="Seite nicht gefunden" description="Diese Adresse gibt es nicht." />
    </div>
  )
}
