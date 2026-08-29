import { useLocation } from 'react-router-dom'
import type { UUID } from '@/api/types'

const PERIOD_PATH = /\/periods\/([0-9a-fA-F-]{36})/

/**
 * The planning period the user is currently looking at, read from the URL.
 *
 * Every period-scoped route nests the id in its path (`/periods/:periodId/...`), which keeps
 * links shareable and means context-aware components — the chat, above all — can find the
 * period without prop-drilling it through the whole tree.
 */
export function useCurrentPeriodId(): UUID | undefined {
  const { pathname } = useLocation()
  return PERIOD_PATH.exec(pathname)?.[1]
}
