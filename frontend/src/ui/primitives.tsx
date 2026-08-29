import type { ButtonHTMLAttributes, ReactNode, SelectHTMLAttributes } from 'react'
import { useEffect } from 'react'
import { ApiError } from '@/api/client'

/**
 * The complete set of visual primitives. Feature code composes these; it does not write
 * class names or colours of its own. Keeping the set small is the point: ten primitives that
 * every screen uses look like one product, where fifty bespoke ones drift apart.
 */

type Variant = 'default' | 'primary' | 'danger' | 'ghost'
type Size = 'sm' | 'md' | 'lg'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant
  size?: Size
  loading?: boolean
}

export function Button({
  variant = 'default',
  size = 'md',
  loading = false,
  disabled,
  children,
  className = '',
  ...rest
}: ButtonProps) {
  const classes = [
    'btn',
    variant === 'primary' && 'btn-primary',
    variant === 'danger' && 'btn-danger',
    variant === 'ghost' && 'btn-ghost',
    size === 'lg' && 'btn-lg',
    size === 'sm' && 'btn-sm',
    className,
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <button className={classes} disabled={disabled || loading} {...rest}>
      {loading && <span className="spinner" aria-hidden="true" />}
      {children}
    </button>
  )
}

export function Card({
  children,
  tight = false,
  selected = false,
  className = '',
}: {
  children: ReactNode
  tight?: boolean
  selected?: boolean
  className?: string
}) {
  const classes = ['card', tight && 'card-tight', selected && 'is-selected', className]
    .filter(Boolean)
    .join(' ')
  return <section className={classes}>{children}</section>
}

export function Field({
  label,
  hint,
  htmlFor,
  children,
}: {
  label: string
  hint?: string
  htmlFor?: string
  children: ReactNode
}) {
  return (
    <div className="field">
      <label className="label" htmlFor={htmlFor}>
        {label}
      </label>
      {children}
      {hint && <span className="hint">{hint}</span>}
    </div>
  )
}

export function Select({
  className = '',
  children,
  ...rest
}: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select className={`select ${className}`} {...rest}>
      {children}
    </select>
  )
}

type Tone = 'default' | 'accent' | 'success' | 'warning' | 'danger' | 'info'

export function Badge({
  tone = 'default',
  dot = false,
  children,
}: {
  tone?: Tone
  dot?: boolean
  children: ReactNode
}) {
  const classes = ['badge', tone !== 'default' && `badge-${tone}`].filter(Boolean).join(' ')
  return (
    <span className={classes}>
      {dot && <span className="dot" aria-hidden="true" />}
      {children}
    </span>
  )
}

export function Notice({
  tone = 'default',
  children,
}: {
  tone?: 'default' | 'danger' | 'warning' | 'info' | 'success'
  children: ReactNode
}) {
  const classes = ['notice', tone !== 'default' && `notice-${tone}`].filter(Boolean).join(' ')
  return (
    <div className={classes} role={tone === 'danger' ? 'alert' : 'status'}>
      <div className="grow">{children}</div>
    </div>
  )
}

/**
 * Renders any thrown error, with the two things a user actually needs: what went wrong in
 * their language, and the trace id to quote if they report it. Field violations are listed
 * because "Validation failed" alone is useless.
 */
export function ErrorNotice({ error }: { error: unknown }) {
  if (!error) return null
  if (error instanceof ApiError) {
    return (
      <Notice tone={error.isAiUnavailable ? 'warning' : 'danger'}>
        <div className="stack" style={{ gap: 6 }}>
          <strong>{error.message}</strong>
          {error.violations.length > 0 && (
            <ul style={{ margin: 0, paddingLeft: 18 }}>
              {error.violations.map((v) => (
                <li key={`${v.field}-${v.message}`}>
                  <code>{v.field}</code>: {v.message}
                </li>
              ))}
            </ul>
          )}
          {error.traceId && <span className="trace">Referenz: {error.traceId}</span>}
        </div>
      </Notice>
    )
  }
  return <Notice tone="danger">{(error as Error).message ?? 'Unbekannter Fehler'}</Notice>
}

export function Spinner({ label }: { label?: string }) {
  return (
    <span className="row row-tight muted" role="status">
      <span className="spinner" aria-hidden="true" />
      {label && <span className="small">{label}</span>}
    </span>
  )
}

export function LoadingBlock({ rows = 3 }: { rows?: number }) {
  return (
    <div className="stack" aria-busy="true" aria-label="Wird geladen">
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="skeleton" style={{ width: `${100 - i * 12}%` }} />
      ))}
    </div>
  )
}

export function EmptyState({
  title,
  description,
  action,
}: {
  title: string
  description?: string
  action?: ReactNode
}) {
  return (
    <div className="empty">
      <h3>{title}</h3>
      {description && <p className="muted" style={{ maxWidth: 460 }}>{description}</p>}
      {action}
    </div>
  )
}

export function Tile({
  label,
  value,
  hint,
  tone = 'default',
}: {
  label: string
  value: ReactNode
  hint?: string
  tone?: 'default' | 'danger' | 'warning' | 'success'
}) {
  const valueClass = ['tile-value', tone !== 'default' && `is-${tone}`].filter(Boolean).join(' ')
  return (
    <div className="tile">
      <span className="tile-label">{label}</span>
      <span className={valueClass}>{value}</span>
      {hint && <span className="tile-hint">{hint}</span>}
    </div>
  )
}

export function Modal({
  title,
  onClose,
  children,
  footer,
}: {
  title: string
  onClose: () => void
  children: ReactNode
  footer?: ReactNode
}) {
  // Escape closes: a modal you can only leave with the mouse is a modal people feel trapped in.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div className="modal-backdrop" onClick={onClose} role="presentation">
      <div
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onClick={(e) => e.stopPropagation()}
      >
        <header className="modal-header">
          <h2>{title}</h2>
          <Button variant="ghost" size="sm" onClick={onClose} aria-label="Schließen">
            ✕
          </Button>
        </header>
        <div className="modal-body">{children}</div>
        {footer && (
          <div className="modal-header" style={{ borderBottom: 'none', borderTop: '1px solid var(--border)' }}>
            {footer}
          </div>
        )}
      </div>
    </div>
  )
}

export function Meter({ value, max = 1 }: { value: number; max?: number }) {
  const pct = Math.max(0, Math.min(100, (value / max) * 100))
  return (
    <div className="meter" role="presentation">
      <span style={{ width: `${pct}%` }} />
    </div>
  )
}
