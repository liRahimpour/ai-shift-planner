import { useReplacements } from '@/api/queries'
import type { ReplacementCandidate, UUID } from '@/api/types'
import { formatCurrency, formatHours } from '@/lib/format'
import { Badge, Button, EmptyState, ErrorNotice, LoadingBlock, Modal } from '@/ui/primitives'

/**
 * "X hat sich krank gemeldet — wer kann einspringen?"
 *
 * The ranking comes from the backend and is deterministic: same inputs, same order, every
 * time. Each candidate is shown with the facts behind their position rather than only a
 * score, because a shift manager overruling the ranking needs to see *why* it ranked that
 * way — and they are the ones who know that Sarah hates closing shifts.
 */
export default function ReplacementDialog({
  periodId,
  shiftId,
  assignmentId,
  currentName,
  onAssign,
  onClose,
  assigning,
}: {
  periodId: UUID
  shiftId: UUID
  assignmentId: UUID
  currentName: string | null
  onAssign: (employeeId: UUID) => void
  onClose: () => void
  assigning: boolean
}) {
  const { data: candidates, isLoading, error } = useReplacements(periodId, shiftId)

  return (
    <Modal title={`Ersatz für ${currentName ?? 'offenen Platz'}`} onClose={onClose}>
      <ErrorNotice error={error} />
      {isLoading && <LoadingBlock rows={4} />}

      {candidates?.length === 0 && (
        <EmptyState
          title="Keine passenden Kandidat:innen"
          description="Niemand erfüllt gleichzeitig Verfügbarkeit, Qualifikation und Ruhezeit für diese Schicht. Ändere den Bedarf, die Zeiten oder frage jemanden direkt an."
        />
      )}

      {candidates && candidates.length > 0 && (
        <div className="stack">
          {candidates.map((c) => (
            <CandidateRow
              key={c.employeeId}
              candidate={c}
              assigning={assigning}
              onAssign={() => onAssign(c.employeeId)}
            />
          ))}
        </div>
      )}

      <p className="hint" style={{ marginTop: 'var(--space-4)' }}>
        Das Ranking berücksichtigt Verfügbarkeit, Qualifikation, bestehende Stunden, Ruhezeit,
        Überstunden, Kosten und Wünsche. Die Auswahl bleibt bei dir — Platz {assignmentId.slice(0, 8)}.
      </p>
    </Modal>
  )
}

function CandidateRow({
  candidate: c,
  onAssign,
  assigning,
}: {
  candidate: ReplacementCandidate
  onAssign: () => void
  assigning: boolean
}) {
  const blocked = !c.availableAtThatTime || !c.hasRequiredSkills || !c.hasEnoughRest

  return (
    <div className="card card-tight">
      <div className="row spread">
        <div className="row row-tight">
          <Badge tone={c.rank === 1 ? 'accent' : 'default'}>#{c.rank}</Badge>
          <strong>{c.name}</strong>
        </div>
        <Button size="sm" variant="primary" onClick={onAssign} loading={assigning}>
          Einsetzen
        </Button>
      </div>

      <div className="row row-tight" style={{ marginTop: 'var(--space-3)' }}>
        <Fact ok={c.availableAtThatTime} label="verfügbar" />
        <Fact ok={c.hasRequiredSkills} label="qualifiziert" />
        <Fact ok={c.hasEnoughRest} label="Ruhezeit" />
        <Fact ok={!c.wouldBeOvertime} label={c.wouldBeOvertime ? 'Überstunden' : 'keine Überstunden'} />
        <Fact ok={!c.conflictsWithAWish} label={c.conflictsWithAWish ? 'Wunschkonflikt' : 'kein Wunschkonflikt'} />
      </div>

      <div className="row row-tight small muted" style={{ marginTop: 'var(--space-2)' }}>
        <span>
          Danach {formatHours(c.hoursAfterTakingShift)} von {formatHours(c.contractHours)}
        </span>
        <span>·</span>
        <span>Kosten {formatCurrency(c.estimatedCost)}</span>
      </div>

      {c.reason && (
        <p className="small" style={{ marginTop: 'var(--space-2)' }}>
          {c.reason}
        </p>
      )}

      {blocked && (
        <p className="hint" style={{ color: 'var(--warning)', marginTop: 6 }}>
          Diese Person erfüllt nicht alle harten Bedingungen — der Plan wird danach als
          regelverletzend markiert.
        </p>
      )}
    </div>
  )
}

function Fact({ ok, label }: { ok: boolean; label: string }) {
  return <Badge tone={ok ? 'success' : 'danger'}>{ok ? '✓' : '✕'} {label}</Badge>
}
