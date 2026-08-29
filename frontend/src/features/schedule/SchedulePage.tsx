import { useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import {
  useEmployees,
  usePin,
  usePublishSchedule,
  useReassign,
  useScheduleDetail,
  useSelectSchedule,
  useValidation,
} from '@/api/queries'
import type {
  Assignment,
  Employee,
  ShiftWithAssignments,
  UUID,
  ValidationIssue,
  ValidationResult,
} from '@/api/types'
import {
  formatCurrency,
  formatDate,
  formatHours,
  formatPercent,
  formatTime,
  formatWeekday,
  scheduleStatusLabels,
  strategyLabels,
  validationCodeLabels,
} from '@/lib/format'
import {
  Badge,
  Button,
  Card,
  ErrorNotice,
  LoadingBlock,
  Notice,
  Select,
  Tile,
} from '@/ui/primitives'
import { groupByDate, indexIssues } from './model'
import ReplacementDialog from './ReplacementDialog'

/**
 * The plan, and everything a shift manager does to it before publishing.
 *
 * Two principles drive the layout:
 *
 * - **The manager stays in charge.** Every assignment can be changed by hand, and any
 *   assignment can be pinned so a re-optimization leaves it alone.
 * - **Consequences are immediate.** Each manual change re-runs validation server-side, and
 *   the result appears right away — a rest-time violation you find at publishing time is a
 *   rest-time violation you found too late.
 */
export default function SchedulePage() {
  const { periodId, scheduleId } = useParams<{ periodId: string; scheduleId: string }>()
  const { data, isLoading, error } = useScheduleDetail(scheduleId)
  const { data: validation } = useValidation(scheduleId)
  const { data: employees = [] } = useEmployees()

  const reassign = useReassign(scheduleId)
  const pin = usePin(scheduleId)
  const select = useSelectSchedule(periodId)
  const publish = usePublishSchedule(periodId)

  const [lastValidation, setLastValidation] = useState<ValidationResult | null>(null)
  const [replacementFor, setReplacementFor] = useState<{
    shiftId: UUID
    assignmentId: UUID
    name: string | null
  } | null>(null)

  const byDate = useMemo(() => groupByDate(data?.shifts ?? []), [data])
  const issuesByShift = useMemo(() => indexIssues(validation?.issues ?? []), [validation])

  if (isLoading) {
    return (
      <Card>
        <LoadingBlock rows={5} />
      </Card>
    )
  }
  if (error) return <ErrorNotice error={error} />
  if (!data || !scheduleId || !periodId) return null

  const { summary } = data
  const m = summary.metrics
  const isPublished = summary.status === 'PUBLISHED'

  const doReassign = (assignmentId: UUID, employeeId: UUID | null) => {
    reassign.mutate(
      { assignmentId, employeeId },
      {
        onSuccess: (response) => {
          setLastValidation(response.validation)
          setReplacementFor(null)
        },
      },
    )
  }

  return (
    <div className="stack-lg">
      <Card>
        <div className="card-header">
          <div className="row">
            <h2>{strategyLabels[summary.strategy]}</h2>
            <Badge tone={isPublished ? 'success' : 'info'}>
              {scheduleStatusLabels[summary.status]}
            </Badge>
            {summary.selected && <Badge tone="accent">gewählt</Badge>}
            {!m.feasible && <Badge tone="danger">verletzt harte Regeln</Badge>}
          </div>
          <div className="row row-tight">
            {!summary.selected && !isPublished && (
              <Button loading={select.isPending} onClick={() => select.mutate(scheduleId)}>
                Diesen Plan wählen
              </Button>
            )}
            <Button
              variant="primary"
              disabled={isPublished || !m.feasible}
              loading={publish.isPending}
              onClick={() => publish.mutate(scheduleId)}
            >
              {isPublished ? 'Veröffentlicht' : 'Plan veröffentlichen'}
            </Button>
          </div>
        </div>

        <div className="tiles">
          <Tile label="Personalkosten" value={formatCurrency(m.totalStaffCost)} />
          <Tile label="Wunscherfüllung" value={formatPercent(m.preferenceSatisfaction)} />
          <Tile
            label="Unbesetzt"
            value={m.unfilledPositions}
            tone={m.unfilledPositions > 0 ? 'danger' : 'success'}
          />
          <Tile label="Überstunden" value={formatHours(m.overtimeHours)} />
          <Tile label="Fairness" value={`${Math.round(m.fairnessScore)}/100`} />
        </div>

        <ErrorNotice error={publish.error} />
        <ErrorNotice error={select.error} />

        {!m.feasible && (
          <Notice tone="danger">
            Solange harte Regeln verletzt sind, lässt sich der Plan nicht veröffentlichen. Die
            betroffenen Schichten sind unten markiert.
          </Notice>
        )}
        {isPublished && (
          <Notice tone="success">
            Der Plan ist veröffentlicht — Mitarbeitende sehen ihre Schichten unter „Mein
            Dienstplan“.
          </Notice>
        )}
      </Card>

      <ValidationPanel validation={lastValidation ?? validation ?? null} />
      <ErrorNotice error={reassign.error} />
      <ErrorNotice error={pin.error} />

      {byDate.map(([date, shifts]) => (
        <div className="day-group" key={date}>
          <div className="day-heading">
            <h2>{formatWeekday(date)}</h2>
            <span className="muted">{formatDate(date)}</span>
          </div>
          {shifts.map((shift) => (
            <ShiftCard
              key={shift.shiftId}
              shift={shift}
              employees={employees}
              issues={issuesByShift.get(shift.shiftId) ?? []}
              readOnly={isPublished}
              busy={reassign.isPending || pin.isPending}
              onReassign={doReassign}
              onPin={(assignmentId, pinned) => pin.mutate({ assignmentId, pinned })}
              onFindReplacement={(assignmentId, name) =>
                setReplacementFor({ shiftId: shift.shiftId, assignmentId, name })
              }
            />
          ))}
        </div>
      ))}

      {replacementFor && (
        <ReplacementDialog
          periodId={periodId}
          shiftId={replacementFor.shiftId}
          assignmentId={replacementFor.assignmentId}
          currentName={replacementFor.name}
          assigning={reassign.isPending}
          onAssign={(employeeId) => doReassign(replacementFor.assignmentId, employeeId)}
          onClose={() => setReplacementFor(null)}
        />
      )}
    </div>
  )
}

function ShiftCard({
  shift,
  employees,
  issues,
  readOnly,
  busy,
  onReassign,
  onPin,
  onFindReplacement,
}: {
  shift: ShiftWithAssignments
  employees: Employee[]
  issues: ValidationIssue[]
  readOnly: boolean
  busy: boolean
  onReassign: (assignmentId: UUID, employeeId: UUID | null) => void
  onPin: (assignmentId: UUID, pinned: boolean) => void
  onFindReplacement: (assignmentId: UUID, name: string | null) => void
}) {
  const filled = shift.assignments.filter((a) => a.employeeId).length
  const understaffed = filled < shift.minimumEmployees

  // Employees who belong to this department come first: in a real shop that is almost always
  // who you want, and scrolling past 34 names to find one of the four bartenders is friction
  // with no upside.
  const [matching, others] = useMemo(() => {
    const inDept = employees.filter((e) => e.active && e.departmentIds.includes(shift.departmentId))
    const rest = employees.filter((e) => e.active && !e.departmentIds.includes(shift.departmentId))
    return [inDept, rest]
  }, [employees, shift.departmentId])

  return (
    <div className={`shift-card${understaffed || issues.length > 0 ? ' is-understaffed' : ''}`}>
      <div className="shift-head">
        <div className="row row-tight">
          <span className="shift-time">
            {formatTime(shift.startTime)} – {formatTime(shift.endTime)}
          </span>
          <Badge tone="accent">{shift.departmentName}</Badge>
          {shift.crossesMidnight && <Badge>+1 Tag</Badge>}
        </div>
        <Badge tone={understaffed ? 'danger' : filled < shift.requiredEmployees ? 'warning' : 'success'}>
          {filled} / {shift.requiredEmployees} besetzt
          {shift.minimumEmployees > 0 && ` · min. ${shift.minimumEmployees}`}
        </Badge>
      </div>

      {issues.length > 0 && (
        <div className="stack" style={{ gap: 4 }}>
          {issues.map((issue, i) => (
            <span
              key={`${issue.code}-${i}`}
              className="small"
              style={{ color: issue.severity === 'HARD' ? 'var(--danger)' : 'var(--warning)' }}
            >
              {issue.severity === 'HARD' ? '⛔' : '⚠️'}{' '}
              {validationCodeLabels[issue.code] ?? issue.code}: {issue.message}
            </span>
          ))}
        </div>
      )}

      <div className="slots">
        {shift.assignments.map((assignment) => (
          <Slot
            key={assignment.assignmentId}
            assignment={assignment}
            matching={matching}
            others={others}
            readOnly={readOnly}
            busy={busy}
            onReassign={onReassign}
            onPin={onPin}
            onFindReplacement={onFindReplacement}
          />
        ))}
      </div>
    </div>
  )
}

function Slot({
  assignment,
  matching,
  others,
  readOnly,
  busy,
  onReassign,
  onPin,
  onFindReplacement,
}: {
  assignment: Assignment
  matching: Employee[]
  others: Employee[]
  readOnly: boolean
  busy: boolean
  onReassign: (assignmentId: UUID, employeeId: UUID | null) => void
  onPin: (assignmentId: UUID, pinned: boolean) => void
  onFindReplacement: (assignmentId: UUID, name: string | null) => void
}) {
  const empty = !assignment.employeeId

  return (
    <div className={`slot${empty ? ' is-empty' : ''}`}>
      <Select
        className="input-sm"
        disabled={readOnly || busy}
        value={assignment.employeeId ?? ''}
        aria-label="Zugewiesene Person"
        onChange={(e) => onReassign(assignment.assignmentId, e.target.value || null)}
      >
        <option value="">— unbesetzt —</option>
        <optgroup label="Abteilung">
          {matching.map((e) => (
            <option key={e.id} value={e.id}>
              {e.firstName} {e.lastName}
            </option>
          ))}
        </optgroup>
        <optgroup label="Weitere">
          {others.map((e) => (
            <option key={e.id} value={e.id}>
              {e.firstName} {e.lastName}
            </option>
          ))}
        </optgroup>
      </Select>

      {!readOnly && (
        <>
          <button
            type="button"
            className={`pin-btn${assignment.pinned ? ' is-on' : ''}`}
            title={
              assignment.pinned
                ? 'Fixiert — bleibt bei einer Neuberechnung unverändert'
                : 'Fixieren'
            }
            aria-pressed={assignment.pinned}
            disabled={busy}
            onClick={() => onPin(assignment.assignmentId, !assignment.pinned)}
          >
            📌
          </button>
          <button
            type="button"
            className="pin-btn"
            title="Ersatz suchen"
            disabled={busy}
            onClick={() => onFindReplacement(assignment.assignmentId, assignment.employeeName)}
          >
            ⇄
          </button>
        </>
      )}
    </div>
  )
}

function ValidationPanel({ validation }: { validation: ValidationResult | null }) {
  if (!validation) return null
  const hard = validation.issues.filter((i) => i.severity === 'HARD')
  const warnings = validation.issues.filter((i) => i.severity === 'WARNING')

  if (validation.feasible && warnings.length === 0) {
    return <Notice tone="success">Keine Regelverletzungen. Der Plan ist veröffentlichbar.</Notice>
  }

  return (
    <Card>
      <div className="card-header">
        <h2>Prüfung</h2>
        <div className="row row-tight">
          {hard.length > 0 && <Badge tone="danger">{hard.length} Verletzung(en)</Badge>}
          {warnings.length > 0 && <Badge tone="warning">{warnings.length} Hinweis(e)</Badge>}
        </div>
      </div>
      <div className="stack" style={{ gap: 6 }}>
        {[...hard, ...warnings].map((issue, i) => (
          <div key={`${issue.code}-${i}`} className="row row-tight">
            <Badge tone={issue.severity === 'HARD' ? 'danger' : 'warning'}>
              {validationCodeLabels[issue.code] ?? issue.code}
            </Badge>
            <span className="small">{issue.message}</span>
          </div>
        ))}
      </div>
    </Card>
  )
}

