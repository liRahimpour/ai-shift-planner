import type { ApiErrorBody, ErrorCode, FieldViolation, TokenResponse } from './types'

/**
 * The single HTTP entry point for the whole app.
 *
 * Everything that talks to the backend goes through {@link request}, which owns four things
 * that are easy to get subtly wrong if they are scattered across components:
 *
 * 1. attaching the bearer token,
 * 2. refreshing an expired token exactly once even if ten requests fail at the same moment,
 * 3. turning the backend's uniform error body into a typed {@link ApiError}, and
 * 4. never letting an HTML error page or a network failure surface as `undefined`.
 */

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''
const STORAGE_KEY = 'asp.session'

export interface Session {
  accessToken: string
  refreshToken: string
  user: TokenResponse['user']
}

/** Raised for every non-2xx response and for network failures. */
export class ApiError extends Error {
  readonly code: ErrorCode
  readonly status: number
  readonly traceId: string | null
  readonly violations: FieldViolation[]

  constructor(
    code: ErrorCode,
    message: string,
    status: number,
    traceId: string | null = null,
    violations: FieldViolation[] = [],
  ) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.traceId = traceId
    this.violations = violations
  }

  /** True when the failure is "the AI is down", which the UI treats as a soft state. */
  get isAiUnavailable(): boolean {
    return this.code === 'AI_TEMPORARILY_UNAVAILABLE'
  }
}

// --- session storage ---------------------------------------------------------

let session: Session | null = readStoredSession()
const listeners = new Set<(s: Session | null) => void>()

function readStoredSession(): Session | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as Session) : null
  } catch {
    // Private mode, disabled site data, or a corrupt entry - all mean "not logged in".
    return null
  }
}

export function getSession(): Session | null {
  return session
}

export function setSession(next: Session | null): void {
  session = next
  try {
    if (next) localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
    else localStorage.removeItem(STORAGE_KEY)
  } catch {
    // Storage is a convenience: the session still works for this tab without it.
  }
  listeners.forEach((l) => l(next))
}

export function subscribeToSession(listener: (s: Session | null) => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

// --- token refresh -----------------------------------------------------------

let refreshInFlight: Promise<Session | null> | null = null

async function refreshSession(): Promise<Session | null> {
  const current = session
  if (!current) return null

  // Collapse concurrent refreshes: without this, a dashboard that fires six queries on
  // mount would send six refresh calls and five of them would fail on a rotated token.
  refreshInFlight ??= (async () => {
    try {
      const response = await fetch(`${API_BASE}/api/v1/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: current.refreshToken }),
      })
      if (!response.ok) {
        setSession(null)
        return null
      }
      const body = (await response.json()) as TokenResponse
      const next: Session = {
        accessToken: body.accessToken,
        refreshToken: body.refreshToken,
        user: body.user,
      }
      setSession(next)
      return next
    } catch {
      setSession(null)
      return null
    } finally {
      refreshInFlight = null
    }
  })()

  return refreshInFlight
}

// --- request -----------------------------------------------------------------

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  /** Set for the login call, which must not attach or refresh a token. */
  anonymous?: boolean
  signal?: AbortSignal
}

async function toApiError(response: Response): Promise<ApiError> {
  let body: Partial<ApiErrorBody> | null = null
  try {
    body = (await response.json()) as Partial<ApiErrorBody>
  } catch {
    body = null
  }
  const fallbackCode: ErrorCode =
    response.status === 401
      ? 'UNAUTHENTICATED'
      : response.status === 403
        ? 'FORBIDDEN'
        : response.status === 404
          ? 'NOT_FOUND'
          : 'INTERNAL_ERROR'
  return new ApiError(
    body?.code ?? fallbackCode,
    body?.message ?? `Die Anfrage ist fehlgeschlagen (HTTP ${response.status}).`,
    response.status,
    body?.traceId ?? null,
    body?.violations ?? [],
  )
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, anonymous = false, signal } = options

  const send = async (token: string | null): Promise<Response> => {
    const headers: Record<string, string> = { Accept: 'application/json' }
    if (body !== undefined) headers['Content-Type'] = 'application/json'
    if (token) headers['Authorization'] = `Bearer ${token}`

    const init: RequestInit = { method, headers }
    if (body !== undefined) init.body = JSON.stringify(body)
    if (signal) init.signal = signal

    return fetch(`${API_BASE}${path}`, init)
  }

  let response: Response
  try {
    response = await send(anonymous ? null : (session?.accessToken ?? null))
  } catch {
    throw new ApiError(
      'INTERNAL_ERROR',
      'Der Server ist nicht erreichbar. Läuft das Backend?',
      0,
    )
  }

  // One retry after a refresh, and only for an expired token - never for a 403, which is a
  // genuine permission decision and would not change with a fresh token.
  if (response.status === 401 && !anonymous && session) {
    const refreshed = await refreshSession()
    if (refreshed) {
      try {
        response = await send(refreshed.accessToken)
      } catch {
        throw new ApiError('INTERNAL_ERROR', 'Der Server ist nicht erreichbar.', 0)
      }
    }
  }

  if (!response.ok) throw await toApiError(response)

  if (response.status === 204) return undefined as T
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

/** Convenience wrappers - identical semantics, less noise at the call site. */
export const api = {
  get: <T>(path: string, signal?: AbortSignal) => request<T>(path, { method: 'GET', signal }),
  post: <T>(path: string, body?: unknown) => request<T>(path, { method: 'POST', body }),
  put: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PUT', body }),
  del: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
}
