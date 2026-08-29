import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { UseQueryOptions } from '@tanstack/react-query'
import {
  aiApi,
  auditApi,
  employeeApi,
  organizationApi,
  planningApi,
  scheduleApi,
  staffingApi,
} from './endpoints'
import type {
  AvailabilityWindowRequest,
  CreateStaffingRequirementRequest,
  PlanningJob,
  PlanningPeriodStatus,
  UUID,
} from './types'

/**
 * Query keys and hooks.
 *
 * All keys live in one tree so that invalidation after a mutation is a single, obvious call
 * instead of a guess. `qk.period(id)` is a prefix of everything belonging to that planning
 * period, so invalidating the prefix refreshes the dashboard, its availability, its comments
 * and its proposals together - which is exactly what "something about this period changed"
 * means.
 */
export const qk = {
  locations: ['locations'] as const,
  departments: (locationId: UUID) => ['locations', locationId, 'departments'] as const,
  employees: ['employees'] as const,
  skills: ['skills'] as const,
  aiStatus: ['ai', 'status'] as const,
  pendingInterpretations: ['ai', 'interpretations', 'pending'] as const,
  audit: (limit: number) => ['audit', limit] as const,

  periods: (locationId: UUID) => ['periods', locationId] as const,
  period: (id: UUID) => ['period', id] as const,
  periodSummary: (id: UUID) => ['period', id, 'summary'] as const,
  myAvailability: (id: UUID) => ['period', id, 'availability', 'me'] as const,
  allAvailability: (id: UUID) => ['period', id, 'availability', 'all'] as const,
  myComments: (id: UUID) => ['period', id, 'comments', 'me'] as const,
  comments: (id: UUID) => ['period', id, 'comments'] as const,
  staffing: (id: UUID) => ['period', id, 'staffing'] as const,
  proposals: (id: UUID) => ['period', id, 'proposals'] as const,
  jobs: (id: UUID) => ['period', id, 'jobs'] as const,
  job: (jobId: UUID) => ['job', jobId] as const,
  mySchedule: (id: UUID) => ['period', id, 'my-schedule'] as const,

  schedule: (scheduleId: UUID) => ['schedule', scheduleId] as const,
  validation: (scheduleId: UUID) => ['schedule', scheduleId, 'validation'] as const,
  replacements: (periodId: UUID, shiftId: UUID) =>
    ['period', periodId, 'replacements', shiftId] as const,
}

type Opts<T> = Omit<UseQueryOptions<T, Error, T, readonly unknown[]>, 'queryKey' | 'queryFn'>

// --- organization / master data ----------------------------------------------

export const useLocations = () =>
  useQuery({ queryKey: qk.locations, queryFn: organizationApi.locations, staleTime: 5 * 60_000 })

export const useDepartments = (locationId: UUID | undefined) =>
  useQuery({
    queryKey: qk.departments(locationId ?? 'none'),
    queryFn: () => organizationApi.departments(locationId!),
    enabled: Boolean(locationId),
    staleTime: 5 * 60_000,
  })

export const useEmployees = (opts?: Opts<Awaited<ReturnType<typeof employeeApi.list>>>) =>
  useQuery({ queryKey: qk.employees, queryFn: employeeApi.list, staleTime: 60_000, ...opts })

export const useSkills = () =>
  useQuery({ queryKey: qk.skills, queryFn: employeeApi.skills, staleTime: 5 * 60_000 })

export const useAiStatus = () =>
  useQuery({
    queryKey: qk.aiStatus,
    queryFn: aiApi.status,
    // The AI can come back without a page reload; a slow poll keeps the badge honest
    // without being chatty.
    refetchInterval: 60_000,
    retry: false,
  })

// --- planning periods ---------------------------------------------------------

export const usePlanningPeriods = (locationId: UUID | undefined) =>
  useQuery({
    queryKey: qk.periods(locationId ?? 'none'),
    queryFn: () => planningApi.list(locationId!),
    enabled: Boolean(locationId),
  })

export const usePeriodSummary = (id: UUID | undefined) =>
  useQuery({
    queryKey: qk.periodSummary(id ?? 'none'),
    queryFn: () => planningApi.summary(id!),
    enabled: Boolean(id),
  })

export const useMyAvailability = (id: UUID | undefined) =>
  useQuery({
    queryKey: qk.myAvailability(id ?? 'none'),
    queryFn: () => planningApi.myAvailability(id!),
    enabled: Boolean(id),
  })

export const useMyComments = (id: UUID | undefined) =>
  useQuery({
    queryKey: qk.myComments(id ?? 'none'),
    queryFn: () => planningApi.myComments(id!),
    enabled: Boolean(id),
  })

export const useComments = (id: UUID | undefined) =>
  useQuery({
    queryKey: qk.comments(id ?? 'none'),
    queryFn: () => planningApi.comments(id!),
    enabled: Boolean(id),
  })

export const useMySchedule = (id: UUID | undefined) =>
  useQuery({
    queryKey: qk.mySchedule(id ?? 'none'),
    queryFn: () => scheduleApi.mySchedule(id!),
    enabled: Boolean(id),
    retry: false,
  })

// --- mutations ----------------------------------------------------------------

/** Invalidates everything scoped to one planning period. */
function useInvalidatePeriod(periodId: UUID | undefined) {
  const qc = useQueryClient()
  return () => {
    if (periodId) void qc.invalidateQueries({ queryKey: qk.period(periodId) })
  }
}

export function useSubmitAvailability(periodId: UUID | undefined) {
  const invalidate = useInvalidatePeriod(periodId)
  return useMutation({
    mutationFn: (windows: AvailabilityWindowRequest[]) =>
      planningApi.submitMyAvailability(periodId!, windows),
    onSuccess: invalidate,
  })
}

export function useSubmitComment(periodId: UUID | undefined) {
  const invalidate = useInvalidatePeriod(periodId)
  return useMutation({
    mutationFn: (text: string) => planningApi.submitMyComment(periodId!, text),
    onSuccess: invalidate,
  })
}

export function useChangeDeadline(periodId: UUID | undefined) {
  const invalidate = useInvalidatePeriod(periodId)
  return useMutation({
    mutationFn: (deadline: string) => planningApi.changeDeadline(periodId!, deadline),
    onSuccess: invalidate,
  })
}

export function useChangeStatus(periodId: UUID | undefined) {
  const invalidate = useInvalidatePeriod(periodId)
  return useMutation({
    mutationFn: (status: PlanningPeriodStatus) => planningApi.changeStatus(periodId!, status),
    onSuccess: invalidate,
  })
}

// --- staffing -----------------------------------------------------------------

export const useStaffingRequirements = (periodId: UUID | undefined) =>
  useQuery({
    queryKey: qk.staffing(periodId ?? 'none'),
    queryFn: () => staffingApi.list(periodId!),
    enabled: Boolean(periodId),
  })

export function useCreateStaffingRequirement(periodId: UUID | undefined) {
  const invalidate = useInvalidatePeriod(periodId)
  return useMutation({
    mutationFn: (body: CreateStaffingRequirementRequest) => staffingApi.create(periodId!, body),
    onSuccess: invalidate,
  })
}

export function useDeleteStaffingRequirement(periodId: UUID | undefined) {
  const invalidate = useInvalidatePeriod(periodId)
  return useMutation({
    mutationFn: (id: UUID) => staffingApi.remove(id),
    onSuccess: invalidate,
  })
}

export function useGenerateShifts(periodId: UUID | undefined) {
  const invalidate = useInvalidatePeriod(periodId)
  return useMutation({
    mutationFn: () => staffingApi.generateShifts(periodId!),
    onSuccess: invalidate,
  })
}

// --- planning jobs & proposals -------------------------------------------------

export const useProposals = (periodId: UUID | undefined) =>
  useQuery({
    queryKey: qk.proposals(periodId ?? 'none'),
    queryFn: () => scheduleApi.proposals(periodId!),
    enabled: Boolean(periodId),
  })

/**
 * Polls a running solver job.
 *
 * Solving is asynchronous by design (an HTTP request must never block for minutes), so the
 * UI polls while the job is live and stops the moment it reaches a terminal state. On
 * completion the period's proposals are invalidated so the results appear without a reload.
 */
export function usePlanningJob(jobId: UUID | undefined, periodId: UUID | undefined) {
  const qc = useQueryClient()
  return useQuery({
    queryKey: qk.job(jobId ?? 'none'),
    queryFn: async () => {
      const job = await scheduleApi.job(jobId!)
      if (job.status === 'COMPLETED' && periodId) {
        void qc.invalidateQueries({ queryKey: qk.period(periodId) })
      }
      return job
    },
    enabled: Boolean(jobId),
    refetchInterval: (query) => {
      const status = (query.state.data as PlanningJob | undefined)?.status
      return status === 'QUEUED' || status === 'RUNNING' ? 1500 : false
    },
  })
}

export function useGeneratePlans(periodId: UUID | undefined) {
  const invalidate = useInvalidatePeriod(periodId)
  return useMutation({
    mutationFn: () => scheduleApi.generate(periodId!),
    onSuccess: invalidate,
  })
}

// --- one schedule ---------------------------------------------------------------

export const useScheduleDetail = (scheduleId: UUID | undefined) =>
  useQuery({
    queryKey: qk.schedule(scheduleId ?? 'none'),
    queryFn: () => scheduleApi.detail(scheduleId!),
    enabled: Boolean(scheduleId),
  })

export const useValidation = (scheduleId: UUID | undefined) =>
  useQuery({
    queryKey: qk.validation(scheduleId ?? 'none'),
    queryFn: () => scheduleApi.validate(scheduleId!),
    enabled: Boolean(scheduleId),
  })

/** Invalidates one schedule and its validation - used after every manual edit. */
function useInvalidateSchedule(scheduleId: UUID | undefined) {
  const qc = useQueryClient()
  return () => {
    if (!scheduleId) return
    void qc.invalidateQueries({ queryKey: qk.schedule(scheduleId) })
    void qc.invalidateQueries({ queryKey: qk.validation(scheduleId) })
  }
}

export function useReassign(scheduleId: UUID | undefined) {
  const invalidate = useInvalidateSchedule(scheduleId)
  return useMutation({
    mutationFn: (vars: { assignmentId: UUID; employeeId: UUID | null }) =>
      scheduleApi.reassign(scheduleId!, vars.assignmentId, vars.employeeId),
    onSuccess: invalidate,
  })
}

export function usePin(scheduleId: UUID | undefined) {
  const invalidate = useInvalidateSchedule(scheduleId)
  return useMutation({
    mutationFn: (vars: { assignmentId: UUID; pinned: boolean }) =>
      scheduleApi.pin(scheduleId!, vars.assignmentId, vars.pinned),
    onSuccess: invalidate,
  })
}

export function useSelectSchedule(periodId: UUID | undefined) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (scheduleId: UUID) => scheduleApi.select(scheduleId),
    onSuccess: (_data, scheduleId) => {
      if (periodId) void qc.invalidateQueries({ queryKey: qk.period(periodId) })
      void qc.invalidateQueries({ queryKey: qk.schedule(scheduleId) })
    },
  })
}

export function usePublishSchedule(periodId: UUID | undefined) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (scheduleId: UUID) => scheduleApi.publish(scheduleId),
    onSuccess: (_data, scheduleId) => {
      if (periodId) void qc.invalidateQueries({ queryKey: qk.period(periodId) })
      void qc.invalidateQueries({ queryKey: qk.schedule(scheduleId) })
    },
  })
}

// --- AI-adjacent ------------------------------------------------------------------

export const useReplacements = (periodId: UUID | undefined, shiftId: UUID | undefined) =>
  useQuery({
    queryKey: qk.replacements(periodId ?? 'none', shiftId ?? 'none'),
    queryFn: () => aiApi.replacements(periodId!, shiftId!),
    enabled: Boolean(periodId && shiftId),
  })

export const usePendingInterpretations = () =>
  useQuery({ queryKey: qk.pendingInterpretations, queryFn: aiApi.pendingInterpretations })

export function useInterpretComments(periodId: UUID | undefined) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => aiApi.interpretComments(periodId!),
    onSuccess: () => {
      if (periodId) void qc.invalidateQueries({ queryKey: qk.period(periodId) })
      void qc.invalidateQueries({ queryKey: qk.pendingInterpretations })
    },
  })
}

export function useReviewInterpretation(periodId: UUID | undefined) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (vars: { id: UUID; accept: boolean }) =>
      aiApi.reviewInterpretation(vars.id, vars.accept),
    onSuccess: () => {
      if (periodId) void qc.invalidateQueries({ queryKey: qk.period(periodId) })
      void qc.invalidateQueries({ queryKey: qk.pendingInterpretations })
    },
  })
}

// --- audit --------------------------------------------------------------------------

export const useAuditLog = (limit = 50) =>
  useQuery({ queryKey: qk.audit(limit), queryFn: () => auditApi.recent(limit) })
