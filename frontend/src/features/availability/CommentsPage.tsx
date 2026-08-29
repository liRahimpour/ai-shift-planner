import { useParams } from 'react-router-dom'
import {
  useAiStatus,
  useComments,
  useEmployees,
  useInterpretComments,
  useReviewInterpretation,
} from '@/api/queries'
import type { Interpretation, UUID } from '@/api/types'
import { availabilityLabels, formatDate, formatInstant, formatPercent, formatTime } from '@/lib/format'
import { Badge, Button, Card, EmptyState, ErrorNotice, LoadingBlock, Notice } from '@/ui/primitives'

/**
 * Comments and what the model made of them.
 *
 * The rule this screen exists to enforce: an AI reading of a comment never becomes a hard
 * planning constraint on its own. A person accepts or rejects it, with the original text
 * right there to compare against — which is also the only honest way to use a confidence
 * score, since a number is only useful next to the thing it is a claim about.
 */
export default function CommentsPage() {
  const { periodId } = useParams<{ periodId: string }>()
  const { data: comments, isLoading, error } = useComments(periodId)
  const { data: employees } = useEmployees()
  const { data: aiStatus } = useAiStatus()
  const interpret = useInterpretComments(periodId)

  const nameOf = (employeeId: UUID) => {
    const e = employees?.find((x) => x.id === employeeId)
    return e ? `${e.firstName} ${e.lastName}` : 'Unbekannt'
  }

  const pendingCount =
    comments?.reduce((n, c) => n + c.interpretations.filter((i) => i.needsReview).length, 0) ?? 0
  const uninterpreted = comments?.filter((c) => c.interpretations.length === 0).length ?? 0

  return (
    <div className="stack-lg">
      <Card>
        <div className="card-header">
          <h2>KI-Deutung der Kommentare</h2>
          {pendingCount > 0 && <Badge tone="warning">{pendingCount} zu prüfen</Badge>}
        </div>

        {aiStatus && !aiStatus.available ? (
          <Notice tone="warning">
            Die lokale KI ist nicht erreichbar. Kommentare bleiben im Originaltext erhalten und
            können jederzeit später gedeutet werden — Planung, Bearbeitung und Veröffentlichung
            sind davon nicht betroffen.
          </Notice>
        ) : (
          <p className="small muted">
            {uninterpreted > 0
              ? `${uninterpreted} ${uninterpreted === 1 ? 'Kommentar wurde' : 'Kommentare wurden'} noch nicht gedeutet.`
              : 'Alle Kommentare sind gedeutet.'}
          </p>
        )}

        <ErrorNotice error={interpret.error} />

        <div className="row" style={{ marginTop: 'var(--space-4)' }}>
          <Button
            variant="primary"
            loading={interpret.isPending}
            disabled={!aiStatus?.available || uninterpreted === 0}
            onClick={() => interpret.mutate()}
          >
            Kommentare interpretieren
          </Button>
          <span className="small subtle">
            Ergebnisse landen in der Prüfliste, nicht direkt im Plan.
          </span>
        </div>
      </Card>

      <ErrorNotice error={error} />
      {isLoading && (
        <Card>
          <LoadingBlock rows={4} />
        </Card>
      )}

      {comments?.length === 0 && (
        <Card>
          <EmptyState
            title="Noch keine Kommentare"
            description="Mitarbeitende können zu ihrer Verfügbarkeit eine Anmerkung schreiben — sie erscheint hier."
          />
        </Card>
      )}

      {comments && comments.length > 0 && (
        <div className="stack">
          {comments.map((comment) => (
            <Card key={comment.id}>
              <div className="card-header">
                <h3>{nameOf(comment.employeeId)}</h3>
                <span className="small subtle">{formatInstant(comment.createdAt)}</span>
              </div>

              <blockquote
                style={{
                  margin: 0,
                  paddingLeft: 'var(--space-4)',
                  borderLeft: '3px solid var(--border-strong)',
                  color: 'var(--text)',
                }}
              >
                {comment.originalText}
              </blockquote>

              <div className="stack" style={{ marginTop: 'var(--space-4)', gap: 'var(--space-2)' }}>
                {comment.interpretations.length === 0 ? (
                  <p className="hint">Noch nicht gedeutet.</p>
                ) : (
                  comment.interpretations.map((i) => (
                    <InterpretationRow key={i.id} interpretation={i} periodId={periodId} />
                  ))
                )}
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}

function InterpretationRow({
  interpretation,
  periodId,
}: {
  interpretation: Interpretation
  periodId: string | undefined
}) {
  const review = useReviewInterpretation(periodId)
  const i = interpretation
  const confidence = Number(i.confidence ?? 0)
  const lowConfidence = confidence < 0.7

  return (
    <div className="card card-tight" style={{ background: 'var(--surface-2)' }}>
      <div className="row spread">
        <div className="row row-tight">
          <Badge tone={i.hardConstraint ? 'danger' : 'info'}>
            {i.hardConstraint ? 'harte Regel' : 'Wunsch'}
          </Badge>
          <span>
            {i.interpretedDate ? formatDate(i.interpretedDate) : 'ohne Datum'}
            {i.availabilityType ? ` · ${availabilityLabels[i.availabilityType]}` : ''}
            {i.preferredStartTime ? ` ab ${formatTime(i.preferredStartTime)}` : ''}
            {i.preferredEndTime ? ` bis ${formatTime(i.preferredEndTime)}` : ''}
          </span>
        </div>
        <Badge tone={lowConfidence ? 'warning' : 'default'}>
          Sicherheit {formatPercent(confidence)}
        </Badge>
      </div>

      {i.interpretation && (
        <p className="small muted" style={{ marginTop: 6 }}>
          {i.interpretation}
        </p>
      )}

      {lowConfidence && i.needsReview && (
        <p className="hint" style={{ marginTop: 6, color: 'var(--warning)' }}>
          Niedrige Sicherheit — bitte gegen den Originaltext prüfen.
        </p>
      )}

      <ErrorNotice error={review.error} />

      {i.needsReview ? (
        <div className="row row-tight" style={{ marginTop: 'var(--space-3)' }}>
          <Button
            size="sm"
            variant="primary"
            loading={review.isPending}
            onClick={() => review.mutate({ id: i.id, accept: true })}
          >
            Übernehmen
          </Button>
          <Button
            size="sm"
            variant="danger"
            loading={review.isPending}
            onClick={() => review.mutate({ id: i.id, accept: false })}
          >
            Verwerfen
          </Button>
        </div>
      ) : (
        <div style={{ marginTop: 'var(--space-3)' }}>
          <Badge tone={i.reviewStatus === 'ACCEPTED' ? 'success' : 'default'}>
            {i.reviewStatus === 'ACCEPTED' ? 'übernommen' : 'verworfen'}
          </Badge>
        </div>
      )}
    </div>
  )
}
