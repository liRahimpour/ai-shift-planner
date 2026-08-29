import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, getSession, request, setSession } from './client'

/**
 * The HTTP layer carries rules that are invisible in normal use and painful when wrong:
 * refresh-once semantics, what counts as retryable, and how a backend error becomes
 * something a user can act on. These are the tests for those rules.
 */

/** Reads the headers of the nth fetch call, with the index checks TS rightly demands. */
function headersOfCall(fetchMock: { mock: { calls: unknown[][] } }, index: number): Record<string, string> {
  const call = fetchMock.mock.calls[index]
  if (!call) throw new Error(`no fetch call at index ${index}`)
  const init = call[1] as RequestInit | undefined
  return (init?.headers ?? {}) as Record<string, string>
}

const json = (status: number, body: unknown): Response =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })

const session = { accessToken: 'old-token', refreshToken: 'refresh-1', user: user() }

function user() {
  return {
    id: 'u1',
    organizationId: 'o1',
    email: 'manager@demo.local',
    firstName: 'Mia',
    lastName: 'Berger',
    roles: ['SHIFT_MANAGER' as const],
  }
}

beforeEach(() => {
  setSession(null)
  vi.restoreAllMocks()
})

afterEach(() => {
  setSession(null)
})

describe('error mapping', () => {
  it('turns the backend error body into a typed ApiError', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        json(409, {
          code: 'AVAILABILITY_DEADLINE_PASSED',
          message: 'Die Verfügbarkeit kann nicht mehr verändert werden.',
          traceId: 'trace-42',
          violations: [],
        }),
      ),
    )

    const error = await request('/api/v1/x').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).code).toBe('AVAILABILITY_DEADLINE_PASSED')
    expect((error as ApiError).traceId).toBe('trace-42')
    expect((error as ApiError).status).toBe(409)
  })

  it('still produces a usable error when the response is not JSON at all', async () => {
    // A proxy returning an HTML 502 must not surface as "cannot read property of undefined".
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response('<html>gateway</html>', { status: 502 })),
    )

    const error = (await request('/api/v1/x').catch((e: unknown) => e)) as ApiError
    expect(error).toBeInstanceOf(ApiError)
    expect(error.code).toBe('INTERNAL_ERROR')
    expect(error.message).toContain('502')
  })

  it('reports an unreachable server rather than throwing a raw network error', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))

    const error = (await request('/api/v1/x').catch((e: unknown) => e)) as ApiError
    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(0)
    expect(error.message).toMatch(/nicht erreichbar/i)
  })

  it('flags AI unavailability as its own soft state', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        json(503, { code: 'AI_TEMPORARILY_UNAVAILABLE', message: 'KI offline', traceId: 't' }),
      ),
    )

    const error = (await request('/api/v1/chat').catch((e: unknown) => e)) as ApiError
    expect(error.isAiUnavailable).toBe(true)
  })
})

describe('authentication', () => {
  it('attaches the bearer token', async () => {
    setSession(session)
    const fetchMock = vi.fn().mockResolvedValue(json(200, { ok: true }))
    vi.stubGlobal('fetch', fetchMock)

    await request('/api/v1/employees')

    expect(headersOfCall(fetchMock, 0)['Authorization']).toBe('Bearer old-token')
  })

  it('sends no token for an anonymous call', async () => {
    setSession(session)
    const fetchMock = vi.fn().mockResolvedValue(json(200, {}))
    vi.stubGlobal('fetch', fetchMock)

    await request('/api/v1/auth/login', { method: 'POST', body: {}, anonymous: true })

    expect(headersOfCall(fetchMock, 0)['Authorization']).toBeUndefined()
  })

  it('refreshes once on 401 and replays the original request', async () => {
    setSession(session)
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(json(401, { code: 'UNAUTHENTICATED', message: 'expired' }))
      .mockResolvedValueOnce(
        json(200, {
          accessToken: 'new-token',
          refreshToken: 'refresh-2',
          tokenType: 'Bearer',
          expiresInSeconds: 1800,
          user: user(),
        }),
      )
      .mockResolvedValueOnce(json(200, { ok: true }))
    vi.stubGlobal('fetch', fetchMock)

    const result = await request<{ ok: boolean }>('/api/v1/employees')

    expect(result).toEqual({ ok: true })
    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(getSession()?.accessToken).toBe('new-token')
    expect(headersOfCall(fetchMock, 2)['Authorization']).toBe('Bearer new-token')
  })

  it('refreshes only once when several requests hit 401 together', async () => {
    // Without collapsing, a dashboard firing six queries would send six refresh calls and
    // five of them would fail against a rotated refresh token, logging the user out.
    setSession(session)
    let refreshCalls = 0
    const fetchMock = vi.fn().mockImplementation((url: string) => {
      if (String(url).endsWith('/auth/refresh')) {
        refreshCalls += 1
        return Promise.resolve(
          json(200, {
            accessToken: 'new-token',
            refreshToken: 'refresh-2',
            tokenType: 'Bearer',
            expiresInSeconds: 1800,
            user: user(),
          }),
        )
      }
      return Promise.resolve(
        getSession()?.accessToken === 'new-token'
          ? json(200, { ok: true })
          : json(401, { code: 'UNAUTHENTICATED', message: 'expired' }),
      )
    })
    vi.stubGlobal('fetch', fetchMock)

    await Promise.all([
      request('/api/v1/a'),
      request('/api/v1/b'),
      request('/api/v1/c'),
    ])

    expect(refreshCalls).toBe(1)
  })

  it('clears the session when the refresh token is no longer valid', async () => {
    setSession(session)
    const fetchMock = vi.fn().mockImplementation((url: string) =>
      Promise.resolve(
        String(url).endsWith('/auth/refresh')
          ? json(401, { code: 'UNAUTHENTICATED', message: 'refresh expired' })
          : json(401, { code: 'UNAUTHENTICATED', message: 'expired' }),
      ),
    )
    vi.stubGlobal('fetch', fetchMock)

    await request('/api/v1/employees').catch(() => undefined)

    expect(getSession()).toBeNull()
  })

  it('does not refresh on 403, which a new token would not fix', async () => {
    setSession(session)
    const fetchMock = vi
      .fn()
      .mockResolvedValue(json(403, { code: 'FORBIDDEN', message: 'nope', traceId: 't' }))
    vi.stubGlobal('fetch', fetchMock)

    await request('/api/v1/audit').catch(() => undefined)

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(getSession()).not.toBeNull()
  })
})

describe('responses', () => {
  it('returns undefined for 204 instead of failing to parse an empty body', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })))
    await expect(request('/api/v1/staffing-requirements/x')).resolves.toBeUndefined()
  })
})
