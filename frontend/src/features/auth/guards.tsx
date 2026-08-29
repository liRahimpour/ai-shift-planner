import { Navigate, Outlet, useLocation } from 'react-router-dom'
import type { Role } from '@/api/types'
import { EmptyState } from '@/ui/primitives'
import { useAuth } from './AuthContext'

/**
 * Route guards.
 *
 * These are a usability feature, not a security control: they keep people out of screens
 * that would only show them errors. Every rule they express is enforced again server-side —
 * see SecurityConfig and the @PreAuthorize annotations on the controllers.
 */

export function RequireAuth() {
  const { isAuthenticated } = useAuth()
  const location = useLocation()
  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  }
  return <Outlet />
}

export function RequireRole({ roles }: { roles: Role[] }) {
  const { hasRole } = useAuth()
  if (!hasRole(...roles)) {
    return (
      <div className="page">
        <EmptyState
          title="Kein Zugriff"
          description="Für diesen Bereich fehlen dir die nötigen Rechte. Wende dich an eine Administratorin oder einen Administrator deiner Organisation."
        />
      </div>
    )
  }
  return <Outlet />
}
