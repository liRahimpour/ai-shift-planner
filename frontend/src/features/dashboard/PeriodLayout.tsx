import { NavLink, Outlet, useParams } from 'react-router-dom'
import { usePeriodSummary } from '@/api/queries'
import { formatDate, periodStatusLabels } from '@/lib/format'
import { Badge, Card, ErrorNotice, LoadingBlock } from '@/ui/primitives'
import { statusTone } from './PeriodsPage'

/**
 * Frame shared by every screen belonging to one planning period: the period's identity stays
 * visible, and the four work areas are one click apart in a fixed order that follows the
 * actual workflow — availability, then staffing needs, then plans.
 */
export default function PeriodLayout() {
  const { periodId } = useParams<{ periodId: string }>()
  const { data, isLoading, error } = usePeriodSummary(periodId)

  return (
    <div className="page">
      <ErrorNotice error={error} />

      {isLoading && (
        <Card>
          <LoadingBlock />
        </Card>
      )}

      {data && (
        <header className="page-header">
          <div>
            <h1>
              {formatDate(data.period.startDate)} – {formatDate(data.period.endDate)}
            </h1>
            <p className="subtitle">Planungszeitraum</p>
          </div>
          <Badge tone={statusTone(data.period.status)}>
            {periodStatusLabels[data.period.status]}
          </Badge>
        </header>
      )}

      {periodId && (
        <nav className="row row-tight" aria-label="Bereiche des Planungszeitraums">
          <Tab to={`/periods/${periodId}`} end label="Übersicht" />
          <Tab to={`/periods/${periodId}/staffing`} label="Personalbedarf" />
          <Tab
            to={`/periods/${periodId}/comments`}
            label="Kommentare"
            count={data?.pendingInterpretationReviews}
          />
          <Tab to={`/periods/${periodId}/proposals`} label="Planvorschläge" />
        </nav>
      )}

      <Outlet />
    </div>
  )
}

function Tab({
  to,
  label,
  end = false,
  count,
}: {
  to: string
  label: string
  end?: boolean
  count?: number
}) {
  return (
    <NavLink to={to} end={end} className={({ isActive }) => `btn${isActive ? ' btn-primary' : ''}`}>
      {label}
      {count !== undefined && count > 0 && <Badge tone="warning">{count}</Badge>}
    </NavLink>
  )
}
