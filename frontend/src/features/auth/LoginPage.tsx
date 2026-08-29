import { useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { Button, Card, ErrorNotice, Field } from '@/ui/primitives'
import { useAuth } from './AuthContext'

const DEMO_ACCOUNTS = [
  { email: 'manager@demo.local', label: 'Schichtleitung' },
  { email: 'employee@demo.local', label: 'Mitarbeiter:in' },
  { email: 'admin@demo.local', label: 'Administration' },
]

export default function LoginPage() {
  const { isAuthenticated, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  const from = (location.state as { from?: string } | null)?.from ?? '/'
  if (isAuthenticated) return <Navigate to={from} replace />

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await login(email, password)
      navigate(from, { replace: true })
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="login-shell">
      <div className="login-card">
        <div className="row">
          <span className="brand-mark">SP</span>
          <div>
            <h1 style={{ fontSize: 18 }}>Schichtplaner</h1>
            <p className="small muted">Planung für die Gastronomie</p>
          </div>
        </div>

        <Card>
          <form className="stack" onSubmit={onSubmit}>
            <Field label="E-Mail" htmlFor="email">
              <input
                id="email"
                className="input"
                type="email"
                autoComplete="username"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </Field>
            <Field label="Passwort" htmlFor="password">
              <input
                id="password"
                className="input"
                type="password"
                autoComplete="current-password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </Field>

            <ErrorNotice error={error} />

            <Button type="submit" variant="primary" size="lg" loading={busy}>
              Anmelden
            </Button>
          </form>
        </Card>

        <Card tight>
          <p className="small muted" style={{ marginBottom: 8 }}>
            Demo-Zugänge (Passwort <code>demo1234</code>), sofern die Demodaten geladen sind:
          </p>
          <div className="suggestions">
            {DEMO_ACCOUNTS.map((account) => (
              <button
                key={account.email}
                type="button"
                className="chip"
                onClick={() => {
                  setEmail(account.email)
                  setPassword('demo1234')
                }}
              >
                {account.label}
              </button>
            ))}
          </div>
        </Card>
      </div>
    </div>
  )
}
