import { useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { useAiStatus } from '@/api/queries'
import { useAuth } from '@/features/auth/AuthContext'
import ChatDrawer from '@/features/chat/ChatDrawer'
import { Badge, Button } from './primitives'

/**
 * The application shell: navigation, identity, AI status, and the chat entry point.
 *
 * The nav is built from the user's roles, so an employee simply never sees management
 * screens rather than seeing them and being refused.
 */
export default function AppLayout() {
  const { user, isManager, hasRole, logout } = useAuth()
  const [chatOpen, setChatOpen] = useState(false)

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark" aria-hidden="true">
            SP
          </span>
          <span>Schichtplaner</span>
        </div>

        <nav className="nav" aria-label="Hauptnavigation">
          {isManager && (
            <>
              <span className="nav-section">Planung</span>
              <NavItem to="/periods" label="Planungszeiträume" />
              <span className="nav-section">Team</span>
              <NavItem to="/employees" label="Mitarbeitende" />
            </>
          )}

          <span className="nav-section">Mein Bereich</span>
          <NavItem to="/me/availability" label="Meine Verfügbarkeit" />
          <NavItem to="/me/schedule" label="Mein Dienstplan" />

          {hasRole('LOCATION_MANAGER', 'ORG_ADMIN', 'SUPER_ADMIN') && (
            <>
              <span className="nav-section">System</span>
              <NavItem to="/audit" label="Audit-Log" />
            </>
          )}
        </nav>

        <div className="stack" style={{ marginTop: 'auto', gap: 10 }}>
          <AiStatusBadge />
          <hr className="divider" />
          <div className="small">
            <div style={{ fontWeight: 600 }}>
              {user?.firstName} {user?.lastName}
            </div>
            <div className="subtle sidebar-footer-label">{user?.email}</div>
          </div>
          <Button variant="ghost" size="sm" onClick={logout}>
            Abmelden
          </Button>
        </div>
      </aside>

      <main className="main">
        <Outlet />
      </main>

      {isManager && (
        <>
          <Button
            variant="primary"
            className="chat-toggle"
            onClick={() => setChatOpen(true)}
            aria-expanded={chatOpen}
          >
            Fragen stellen
          </Button>
          {chatOpen && <ChatDrawer onClose={() => setChatOpen(false)} />}
        </>
      )}
    </div>
  )
}

function NavItem({ to, label }: { to: string; label: string }) {
  return (
    <NavLink to={to} className={({ isActive }) => `nav-link${isActive ? ' is-active' : ''}`}>
      {label}
    </NavLink>
  )
}

/**
 * Shows whether the local model is reachable. This is deliberately unobtrusive: when it is
 * down, planning, editing and publishing all still work — only interpretation and chat are
 * affected — so it must not read like an outage.
 */
function AiStatusBadge() {
  const { data, isLoading } = useAiStatus()
  if (isLoading || !data) return null
  return data.available ? (
    <Badge tone="success" dot>
      KI verfügbar
    </Badge>
  ) : (
    <Badge tone="warning" dot>
      KI offline
    </Badge>
  )
}
