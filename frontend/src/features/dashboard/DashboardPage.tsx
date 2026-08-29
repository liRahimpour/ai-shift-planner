import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  useChangeDeadline,
  useChangeStatus,
  usePeriodSummary,
  useProposals,
} from '@/api/queries'
import type { PlanningPeriodStatus } from '@/api/types'
import {
  formatInstant,
  formatRelative,
  instantToLocalInput,
  jobStatusLabels,
  localInputToInstant,
  periodStatusLabels,
} from '@/lib/format'
import { useGenerationFlow } from '@/features/proposals/useGenerationFlow'
import {
  Badge,
  Button,
  Card,
  ErrorNotice,
  Field,
  LoadingBlock,
  Meter,
  Notice,
  Select,
  Tile,
} from '@/ui/primitives'

/**
 * The screen a shift manager opens first: is the data complete, is the deadline reached, and
 * can we plan? Everything else on the page is subordinate to the one primary action.
 */
export default function DashboardPage() {
  const { periodId } = useParams<{ periodId: string }>()
  const navigate = useNavigate()
  const { data, isLoading, error } = usePeriodSummary(periodId)
  const { data: proposals } = useProposals(periodId)
  const generation = useGenerationFlow(periodId)
  const [editingDeadline, setEditingDeadline] = useState(false)

  if (isLoading) {
    return (
      <Card>
        <LoadingBlock rows={4} />
      </Card>
    )
  }
  if (error) return <ErrorNotice error={error} />
  if (!data || !periodId) return null

  const { period } = data
  const submissionRate =
    data.totalActiveEmployees > 0 ? data.employeesWithSubmissions / data.totalActiveEmployees : 0
  const hasProposals = (proposals?.length ?? 0) > 0
  const canPlan = period.status === 'READY_FOR_PLANNING' || period.status === 'DRAFT'

  return (
    <div className="stack-lg">
      <div className="tiles">
        <Tile label="Mitarbeitende" value={data.totalActiveEmployees} hint="aktiv an diesem Standort" />
        <Tile
          label="Verfügbarkeiten abgegeben"
          value={data.employeesWithSubmissions}
          hint={`von ${data.totalActiveEmployees}`}
          tone={submissionRate >= 0.9 ? 'success' : 'default'}
        />
        <Tile
          label="Fehlend"
          value={data.employeesMissing}
          hint={data.employeesMissing === 0 ? 'vollständig' : 'noch offen'}
          tone={data.employeesMissing === 0 ? 'success' : 'warning'}
        />
        <Tile label="Kommentare" value={data.commentCount} hint="von Mitarbeitenden" />
        <Tile
          label="Zu prüfen"
          value={data.pendingInterpretationReviews}
          hint="KI-Deutungen ohne Freigabe"
          tone={data.pendingInterpretationReviews > 0 ? 'warning' : 'default'}
        />
      </div>

      <Card>
        <div className="card-header">
          <h2>Rückläufe</h2>
          <span className="small muted">
            {data.employeesWithSubmissions} / {data.totalActiveEmployees}
          </span>
        </div>
        <Meter value={submissionRate} />
        {data.employeesMissing > 0 && (
          <p className="small muted" style={{ marginTop: 'var(--space-3)' }}>
            {data.employeesMissing}{' '}
            {data.employeesMissing === 1 ? 'Person hat' : 'Personen haben'} noch nichts eingetragen.
            Planen ist trotzdem möglich — sie werden dann nur dort eingeplant, wo keine
            Verfügbarkeit hinterlegt ist und damit keine harte Sperre besteht.
          </p>
        )}
      </Card>

      <div className="row" style={{ alignItems: 'stretch', gap: 'var(--space-4)' }}>
        <Card className="grow">
          <div className="card-header">
            <h2>Deadline</h2>
            {period.deadlinePassed ? (
              <Badge tone="danger">abgelaufen</Badge>
            ) : (
              <Badge tone="success">offen</Badge>
            )}
          </div>
          <p style={{ fontWeight: 600 }}>{formatInstant(period.availabilityDeadline)}</p>
          <p className="small muted">{formatRelative(period.availabilityDeadline)}</p>

          {editingDeadline ? (
            <DeadlineForm
              periodId={periodId}
              current={period.availabilityDeadline}
              onDone={() => setEditingDeadline(false)}
            />
          ) : (
            <Button
              size="sm"
              variant="ghost"
              style={{ marginTop: 'var(--space-3)' }}
              onClick={() => setEditingDeadline(true)}
            >
              {period.deadlinePassed ? 'Wieder öffnen' : 'Deadline ändern'}
            </Button>
          )}
        </Card>

        <Card className="grow">
          <div className="card-header">
            <h2>Status</h2>
          </div>
          <StatusControl periodId={periodId} current={period.status} />
        </Card>
      </div>

      <Card>
        <div className="card-header">
          <h2>Planung</h2>
          {hasProposals && <Badge tone="accent">{proposals?.length} Vorschläge vorhanden</Badge>}
        </div>

        {!canPlan && (
          <Notice tone="info">
            Der Zeitraum steht auf „{periodStatusLabels[period.status]}“. Setze ihn auf „Bereit zur
            Planung“, sobald genug Verfügbarkeiten vorliegen.
          </Notice>
        )}

        {generation.job && (
          <div style={{ margin: 'var(--space-3) 0' }}>
            {generation.isRunning ? (
              <Notice tone="info">
                <span className="row row-tight">
                  <span className="spinner" aria-hidden="true" />
                  <span>
                    {jobStatusLabels[generation.job.status]}
                    {generation.job.progressNote ? ` — ${generation.job.progressNote}` : ''}
                  </span>
                </span>
              </Notice>
            ) : generation.job.status === 'FAILED' ? (
              <Notice tone="danger">
                Die Planung ist fehlgeschlagen: {generation.job.failureReason ?? 'unbekannter Fehler'}
              </Notice>
            ) : generation.job.status === 'COMPLETED' ? (
              <Notice tone="success">Drei Pläne wurden berechnet.</Notice>
            ) : null}
          </div>
        )}

        <ErrorNotice error={generation.error} />

        <div className="row" style={{ marginTop: 'var(--space-4)' }}>
          <Button
            variant="primary"
            size="lg"
            disabled={!canPlan || generation.isRunning}
            loading={generation.isStarting || generation.isRunning}
            onClick={generation.start}
          >
            Pläne generieren
          </Button>
          {hasProposals && (
            <Button onClick={() => navigate(`/periods/${periodId}/proposals`)}>
              Vorschläge ansehen
            </Button>
          )}
          <span className="small subtle">
            Erzeugt drei Varianten: fair, kostenoptimiert und ausgewogen.
          </span>
        </div>
      </Card>
    </div>
  )
}

function DeadlineForm({
  periodId,
  current,
  onDone,
}: {
  periodId: string
  current: string
  onDone: () => void
}) {
  const [value, setValue] = useState(() => instantToLocalInput(current))
  const change = useChangeDeadline(periodId)

  return (
    <form
      className="stack"
      style={{ marginTop: 'var(--space-3)' }}
      onSubmit={(e) => {
        e.preventDefault()
        change.mutate(localInputToInstant(value), { onSuccess: onDone })
      }}
    >
      <Field label="Neue Deadline" htmlFor="new-deadline">
        <input
          id="new-deadline"
          className="input"
          type="datetime-local"
          required
          value={value}
          onChange={(e) => setValue(e.target.value)}
        />
      </Field>
      <ErrorNotice error={change.error} />
      <div className="row row-tight">
        <Button type="submit" variant="primary" size="sm" loading={change.isPending}>
          Speichern
        </Button>
        <Button type="button" variant="ghost" size="sm" onClick={onDone}>
          Abbrechen
        </Button>
      </div>
      <p className="hint">Jede Änderung wird im Audit-Log festgehalten.</p>
    </form>
  )
}

const STATUS_OPTIONS: PlanningPeriodStatus[] = [
  'OPEN_FOR_AVAILABILITY',
  'READY_FOR_PLANNING',
  'DRAFT',
  'PUBLISHED',
  'ARCHIVED',
]

function StatusControl({
  periodId,
  current,
}: {
  periodId: string
  current: PlanningPeriodStatus
}) {
  const change = useChangeStatus(periodId)
  return (
    <div className="stack">
      <Select
        value={current}
        disabled={change.isPending}
        onChange={(e) => change.mutate(e.target.value as PlanningPeriodStatus)}
        aria-label="Status des Planungszeitraums"
      >
        {STATUS_OPTIONS.map((s) => (
          <option key={s} value={s}>
            {periodStatusLabels[s]}
          </option>
        ))}
      </Select>
      <ErrorNotice error={change.error} />
      <p className="hint">
        „Verfügbarkeiten offen“ erlaubt Mitarbeitenden das Eintragen. „Bereit zur Planung“ gibt
        die Plangenerierung frei.
      </p>
    </div>
  )
}
