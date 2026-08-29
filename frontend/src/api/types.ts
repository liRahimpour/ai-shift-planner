/**
 * Mirrors the backend DTOs under `com.aishiftplanner.scheduler.**.api`.
 *
 * This file is the single place where the wire format is described. Keeping it hand-written
 * (rather than generated) is deliberate for now: it is small, it documents intent, and it
 * makes a backend change surface here as a compile error instead of a runtime surprise. If
 * the API grows past comfortable hand-maintenance, generate it from `/v3/api-docs` into this
 * same module - nothing outside `src/api` imports anything else.
 */

export type UUID = string
/** ISO-8601 date, e.g. "2026-09-12". */
export type IsoDate = string
/** ISO-8601 local time, e.g. "17:00" or "17:00:00". */
export type IsoTime = string
/** ISO-8601 instant in UTC, e.g. "2026-09-02T16:00:00Z". */
export type IsoInstant = string

// --- shared ------------------------------------------------------------------

export const ERROR_CODES = [
  'VALIDATION_FAILED',
  'NOT_FOUND',
  'ALREADY_EXISTS',
  'FORBIDDEN',
  'UNAUTHENTICATED',
  'TENANT_MISMATCH',
  'CONFLICT',
  'OPTIMISTIC_LOCK_CONFLICT',
  'AVAILABILITY_DEADLINE_PASSED',
  'PLANNING_PERIOD_NOT_READY',
  'SCHEDULING_CONFLICT',
  'AI_TEMPORARILY_UNAVAILABLE',
  'INTERNAL_ERROR',
] as const
export type ErrorCode = (typeof ERROR_CODES)[number]

export interface FieldViolation {
  field: string
  message: string
}

export interface ApiErrorBody {
  code: ErrorCode
  message: string
  timestamp: IsoInstant
  traceId: string
  violations: FieldViolation[]
}

// --- auth --------------------------------------------------------------------

export type Role = 'EMPLOYEE' | 'SHIFT_MANAGER' | 'LOCATION_MANAGER' | 'ORG_ADMIN' | 'SUPER_ADMIN'

export interface CurrentUser {
  id: UUID
  organizationId: UUID
  email: string
  firstName: string
  lastName: string
  roles: Role[]
}

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresInSeconds: number
  user: CurrentUser
}

// --- organization ------------------------------------------------------------

export interface Location {
  id: UUID
  organizationId: UUID
  name: string
  timezone: string
  addressLine: string | null
  postalCode: string | null
  city: string | null
  countryCode: string | null
  active: boolean
}

export interface Department {
  id: UUID
  organizationId: UUID
  locationId: UUID
  name: string
  description: string | null
  active: boolean
}

// --- employee ----------------------------------------------------------------

export type EmploymentType =
  | 'FULL_TIME'
  | 'PART_TIME'
  | 'MINIJOB'
  | 'WORKING_STUDENT'
  | 'TEMPORARY'
  | 'OTHER'

export interface Skill {
  id: UUID
  organizationId: UUID
  code: string
  name: string
  description: string | null
  active: boolean
}

export interface Employee {
  id: UUID
  organizationId: UUID
  locationId: UUID
  userId: UUID | null
  firstName: string
  lastName: string
  email: string | null
  employmentType: EmploymentType
  /** Decimals arrive as JSON numbers; kept as `number` for display and arithmetic. */
  hourlyWage: number
  contractHoursPerWeek: number
  minimumHoursPerWeek: number
  maximumHoursPerWeek: number
  skillIds: UUID[]
  departmentIds: UUID[]
  additionalLocationIds: UUID[]
  active: boolean
}

// --- planning periods --------------------------------------------------------

export type PlanningPeriodStatus =
  | 'OPEN_FOR_AVAILABILITY'
  | 'READY_FOR_PLANNING'
  | 'PLANNING'
  | 'DRAFT'
  | 'PUBLISHED'
  | 'ARCHIVED'

export interface PlanningPeriod {
  id: UUID
  organizationId: UUID
  locationId: UUID
  startDate: IsoDate
  endDate: IsoDate
  availabilityDeadline: IsoInstant
  status: PlanningPeriodStatus
  deadlinePassed: boolean
  createdBy: UUID | null
}

export interface PlanningPeriodSummary {
  period: PlanningPeriod
  totalActiveEmployees: number
  employeesWithSubmissions: number
  employeesMissing: number
  commentCount: number
  pendingInterpretationReviews: number
}

// --- availability ------------------------------------------------------------

export type AvailabilityType = 'AVAILABLE' | 'PREFERRED' | 'UNAVAILABLE'

export interface AvailabilityWindowRequest {
  date: IsoDate
  availabilityType: AvailabilityType
  startTime: IsoTime | null
  endTime: IsoTime | null
}

export interface Availability {
  id: UUID
  planningPeriodId: UUID
  employeeId: UUID
  date: IsoDate
  availabilityType: AvailabilityType
  startTime: IsoTime | null
  endTime: IsoTime | null
}

export type InterpretationSource = 'AI' | 'MANUAL' | string
export type ReviewStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | string

export interface Interpretation {
  id: UUID
  commentId: UUID
  interpretedDate: IsoDate | null
  availabilityType: AvailabilityType | null
  preferredStartTime: IsoTime | null
  preferredEndTime: IsoTime | null
  hardConstraint: boolean
  confidence: number
  source: InterpretationSource
  interpretation: string | null
  reviewStatus: ReviewStatus
  needsReview: boolean
}

export interface Comment {
  id: UUID
  planningPeriodId: UUID
  employeeId: UUID
  originalText: string
  createdAt: IsoInstant
  interpretations: Interpretation[]
}

// --- staffing ----------------------------------------------------------------

export interface StaffingRequirement {
  id: UUID
  organizationId: UUID
  locationId: UUID
  departmentId: UUID
  planningPeriodId: UUID
  date: IsoDate
  startTime: IsoTime
  endTime: IsoTime
  crossesMidnight: boolean
  minimumStaff: number
  preferredStaff: number
  maximumStaff: number
  durationHours: number
  requiredSkills: Record<UUID, number>
}

export interface CreateStaffingRequirementRequest {
  departmentId: UUID
  date: IsoDate
  startTime: IsoTime
  endTime: IsoTime
  crossesMidnight: boolean
  minimumStaff: number
  preferredStaff: number
  maximumStaff: number
  requiredSkills: Record<UUID, number>
}

export interface GenerateShiftsResponse {
  requirementsProcessed: number
  shiftsCreated: number
}

// --- schedules ---------------------------------------------------------------

export type PlanningStrategy = 'FAIR' | 'COST_OPTIMIZED' | 'BALANCED' | 'MANUAL'
export type ScheduleStatus = 'DRAFT' | 'PLANNED' | 'PUBLISHED' | 'ARCHIVED'
export type PlanningJobStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'

export interface PlanningJob {
  jobId: UUID
  planningPeriodId: UUID
  status: PlanningJobStatus
  progressNote: string | null
  failureReason: string | null
  startedAt: IsoInstant | null
  finishedAt: IsoInstant | null
}

export interface ScheduleMetrics {
  totalStaffCost: number
  /** 0..1 share of fulfilled PREFERRED wishes. */
  preferenceSatisfaction: number
  /** 0..1 average relative deviation from contract hours. */
  contractHoursDeviation: number
  unfilledPositions: number
  overtimeHours: number
  /** 0..100. */
  fairnessScore: number
  hardScore: number
  softScore: number
  feasible: boolean
}

export interface ScheduleSummary {
  id: UUID
  planningPeriodId: UUID
  strategy: PlanningStrategy
  status: ScheduleStatus
  selected: boolean
  metrics: ScheduleMetrics
}

export interface Assignment {
  assignmentId: UUID
  shiftId: UUID
  slotIndex: number
  employeeId: UUID | null
  employeeName: string | null
  pinned: boolean
}

export interface ShiftWithAssignments {
  shiftId: UUID
  departmentId: UUID
  departmentName: string
  date: IsoDate
  startTime: IsoTime
  endTime: IsoTime
  crossesMidnight: boolean
  requiredEmployees: number
  minimumEmployees: number
  assignments: Assignment[]
}

export interface ScheduleDetail {
  summary: ScheduleSummary
  shifts: ShiftWithAssignments[]
}

export type ValidationSeverity = 'HARD' | 'WARNING'

export interface ValidationIssue {
  severity: ValidationSeverity
  code: string
  message: string
  employeeId: UUID | null
  shiftId: UUID | null
}

export interface ValidationResult {
  feasible: boolean
  issues: ValidationIssue[]
}

export interface ReassignResponse {
  assignment: Assignment
  validation: ValidationResult
}

export interface MySchedule {
  employeeId: UUID
  shifts: ShiftWithAssignments[]
}

// --- AI ----------------------------------------------------------------------

export interface AiStatus {
  available: boolean
  state: 'AVAILABLE' | 'AI_TEMPORARILY_UNAVAILABLE' | string
  note: string
}

export interface ReplacementCandidate {
  employeeId: UUID
  name: string
  availableAtThatTime: boolean
  hasRequiredSkills: boolean
  hoursAfterTakingShift: number
  contractHours: number
  wouldBeOvertime: boolean
  hasEnoughRest: boolean
  conflictsWithAWish: boolean
  estimatedCost: number
  rank: number
  reason: string
}

// --- chat --------------------------------------------------------------------

export interface ToolInvocation {
  tool: string
  arguments: Record<string, string>
  result: string
}

export interface ChatResponse {
  answer: string
  toolsUsed: ToolInvocation[]
  truncated: boolean
}

// --- audit -------------------------------------------------------------------

export interface AuditEntry {
  id: UUID
  actorUserId: UUID | null
  action: string
  entityType: string
  entityId: UUID | null
  metadata: string | null
  correlationId: string | null
  occurredAt: IsoInstant
}
