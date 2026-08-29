import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { scheduleApi } from '@/api/endpoints'
import { qk, useGeneratePlans, usePlanningJob } from '@/api/queries'
import type { UUID } from '@/api/types'

/**
 * Drives "generate the three plans" from the button press to the finished proposals.
 *
 * Solving runs asynchronously on the server, so the UI has to follow a job rather than wait
 * on a request. Two details matter:
 *
 * - On mount it adopts an already-running job. Reload the page mid-solve and you still see
 *   progress instead of an idle button that tempts you into starting a second run.
 * - The button is disabled while a job is live. The backend rejects duplicate runs anyway,
 *   but a UI that invites a rejected click is a UI that looks broken.
 */
export function useGenerationFlow(periodId: UUID | undefined) {
  const [jobId, setJobId] = useState<UUID | undefined>()

  const { data: existingJobs } = useQuery({
    queryKey: qk.jobs(periodId ?? 'none'),
    queryFn: () => scheduleApi.jobs(periodId!),
    enabled: Boolean(periodId),
  })

  useEffect(() => {
    if (jobId || !existingJobs) return
    const live = existingJobs.find((j) => j.status === 'QUEUED' || j.status === 'RUNNING')
    if (live) setJobId(live.jobId)
  }, [existingJobs, jobId])

  const { data: job } = usePlanningJob(jobId, periodId)
  const generate = useGeneratePlans(periodId)

  const isRunning = job?.status === 'QUEUED' || job?.status === 'RUNNING'

  return {
    job,
    isRunning,
    isStarting: generate.isPending,
    error: generate.error,
    start: () => generate.mutate(undefined, { onSuccess: (created) => setJobId(created.jobId) }),
    /** Clears a finished job so its banner disappears without hiding a live one. */
    dismiss: () => {
      if (!isRunning) setJobId(undefined)
    },
  }
}
