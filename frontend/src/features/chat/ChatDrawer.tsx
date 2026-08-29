import { useEffect, useRef, useState } from 'react'
import { ApiError } from '@/api/client'
import { chatApi } from '@/api/endpoints'
import type { ToolInvocation } from '@/api/types'
import { useCurrentPeriodId } from '@/lib/useCurrentPeriodId'
import { Badge, Button, Notice } from '@/ui/primitives'

/**
 * The manager's question box.
 *
 * Three deliberate choices make this useful rather than decorative:
 *
 * 1. It is **context-aware**: whichever planning period the user is looking at is sent along,
 *    so "Wer arbeitet Samstag?" resolves to a concrete Saturday instead of being ambiguous.
 * 2. It is **transparent**: every answer can be expanded to show which backend tools were
 *    called and what they returned. The model phrases answers, the database supplies facts —
 *    and the user can check that for themselves rather than taking it on trust.
 * 3. It **degrades honestly**: when the local model is unreachable the failure says so, and
 *    says that planning is unaffected, instead of showing a generic error.
 */

const SUGGESTIONS = [
  'Wer arbeitet Samstagabend an der Bar?',
  'Welche Schichten sind unterbesetzt?',
  'Welche Mitarbeitenden liegen unter ihren Vertragsstunden?',
  'Wer arbeitet diese Woche am meisten?',
  'Wer hat Samstag frei gewünscht?',
]

interface Turn {
  id: number
  role: 'user' | 'assistant' | 'error'
  text: string
  tools?: ToolInvocation[]
  truncated?: boolean
}

export default function ChatDrawer({ onClose }: { onClose: () => void }) {
  const periodId = useCurrentPeriodId()
  const [turns, setTurns] = useState<Turn[]>([])
  const [question, setQuestion] = useState('')
  const [busy, setBusy] = useState(false)
  const bodyRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLTextAreaElement>(null)
  const nextId = useRef(1)

  useEffect(() => {
    inputRef.current?.focus()
  }, [])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  useEffect(() => {
    const body = bodyRef.current
    if (!body) return
    // Element.scrollTo is missing in a few older browsers (and in jsdom); assigning
    // scrollTop is universally supported and does the same job without the animation.
    if (typeof body.scrollTo === 'function') {
      body.scrollTo({ top: body.scrollHeight, behavior: 'smooth' })
    } else {
      body.scrollTop = body.scrollHeight
    }
  }, [turns, busy])

  const ask = async (text: string) => {
    const trimmed = text.trim()
    if (!trimmed || busy) return

    setTurns((prev) => [...prev, { id: nextId.current++, role: 'user', text: trimmed }])
    setQuestion('')
    setBusy(true)
    try {
      const response = await chatApi.ask(trimmed, periodId)
      setTurns((prev) => [
        ...prev,
        {
          id: nextId.current++,
          role: 'assistant',
          text: response.answer,
          tools: response.toolsUsed,
          truncated: response.truncated,
        },
      ])
    } catch (err) {
      const message =
        err instanceof ApiError && err.isAiUnavailable
          ? 'Die lokale KI ist gerade nicht erreichbar. Planung, Bearbeitung und Veröffentlichung funktionieren davon unabhängig weiter.'
          : err instanceof ApiError
            ? err.message
            : 'Die Frage konnte nicht beantwortet werden.'
      setTurns((prev) => [...prev, { id: nextId.current++, role: 'error', text: message }])
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <div className="drawer-backdrop" onClick={onClose} role="presentation" />
      <aside className="drawer" role="dialog" aria-modal="true" aria-label="Fragen zum Dienstplan">
        <header className="drawer-header">
          <div>
            <h2>Fragen zum Dienstplan</h2>
            <p className="small subtle">
              {periodId ? 'Bezieht sich auf den geöffneten Zeitraum' : 'Kein Zeitraum geöffnet'}
            </p>
          </div>
          <Button variant="ghost" size="sm" onClick={onClose} aria-label="Schließen">
            ✕
          </Button>
        </header>

        <div className="drawer-body" ref={bodyRef}>
          {turns.length === 0 && (
            <div className="stack">
              <Notice tone="info">
                Antworten stammen ausschließlich aus den Daten dieses Systems. Die KI formuliert
                sie nur — sie erfindet keine Schichten und sieht nur, was du auch sehen darfst.
              </Notice>
              <p className="small muted">Zum Beispiel:</p>
              <div className="suggestions">
                {SUGGESTIONS.map((s) => (
                  <button key={s} type="button" className="chip" onClick={() => void ask(s)}>
                    {s}
                  </button>
                ))}
              </div>
            </div>
          )}

          {turns.map((turn) => (
            <ChatTurn key={turn.id} turn={turn} />
          ))}

          {busy && (
            <div className="bubble bubble-ai">
              <span className="row row-tight">
                <span className="spinner" aria-hidden="true" />
                <span className="muted">Daten werden abgefragt …</span>
              </span>
            </div>
          )}
        </div>

        <div className="drawer-footer">
          <textarea
            ref={inputRef}
            className="textarea"
            rows={2}
            placeholder="Frage eingeben … (Enter zum Senden)"
            value={question}
            disabled={busy}
            onChange={(e) => setQuestion(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault()
                void ask(question)
              }
            }}
          />
          <div className="row spread">
            <span className="small subtle">Shift + Enter für eine neue Zeile</span>
            <Button variant="primary" onClick={() => void ask(question)} loading={busy}>
              Senden
            </Button>
          </div>
        </div>
      </aside>
    </>
  )
}

function ChatTurn({ turn }: { turn: Turn }) {
  if (turn.role === 'user') return <div className="bubble bubble-user">{turn.text}</div>
  if (turn.role === 'error') return <div className="bubble bubble-error">{turn.text}</div>

  return (
    <>
      <div className="bubble bubble-ai">{turn.text}</div>
      {turn.truncated && (
        <span className="tool-trace">
          <Badge tone="warning">Antwort gekürzt</Badge>
        </span>
      )}
      {turn.tools && turn.tools.length > 0 && (
        <details className="tool-trace">
          <summary>
            Datengrundlage · {turn.tools.length} {turn.tools.length === 1 ? 'Abfrage' : 'Abfragen'}
          </summary>
          {turn.tools.map((tool, i) => (
            <pre key={`${tool.tool}-${i}`}>
              {tool.tool}({formatArgs(tool.arguments)}){'\n'}→ {tool.result}
            </pre>
          ))}
        </details>
      )}
    </>
  )
}

function formatArgs(args: Record<string, string>): string {
  return Object.entries(args)
    .map(([k, v]) => `${k}: ${v}`)
    .join(', ')
}
