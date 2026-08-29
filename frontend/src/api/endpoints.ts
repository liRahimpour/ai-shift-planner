import { api, request } from './client'
import type {
  AiStatus,
  Assignment,
  AuditEntry,
  Availability,
  AvailabilityWindowRequest,
  ChatResponse,
  Comment,
  CreateStaffingRequirementRequest,
  Department,
  Employee,
  GenerateShiftsResponse,
  Interpretation,
  IsoDate,
  IsoInstant,
  Location,
  MySchedule,
  PlanningJob,
  PlanningPeriod,
  PlanningPeriodStatus,
  PlanningPeriodSummary,
  ReassignResponse,
  ReplacementCandidate,
  ScheduleDetail,
  ScheduleSummary,
  Skill,
  StaffingRequirement,
  TokenResponse,
  UUID,
  ValidationResult,
} from './types'

/**
 * One function per backend endpoint, grouped the same way the backend groups its modules.
 *
 * Components never build URLs. When an endpoint changes, it changes here and every caller
 * that needs updating fails to compile.
 */

export const authApi = {
  login: (email: string, password: string) =>
    request<TokenResponse>('/api/v1/auth/login', {
      method: 'POST',
      body: { email, password },
      anonymous: true,
    }),
  me: () => api.get<TokenResponse['user']>('/api/v1/auth/me'),
}

export const organizationApi = {
  locations: () => api.get<Location[]>('/api/v1/locations'),
  location: (id: UUID) => api.get<Location>(`/api/v1/locations/${id}`),
  departments: (locationId: UUID) =>
    api.get<Department[]>(`/api/v1/locations/${locationId}/departments`),
  allDepartments: () => api.get<Department[]>('/api/v1/departments'),
}

export const employeeApi = {
  list: () => api.get<Employee[]>('/api/v1/employees'),
  get: (id: UUID) => api.get<Employee>(`/api/v1/employees/${id}`),
  skills: () => api.get<Skill[]>('/api/v1/skills'),
}

export const planningApi = {
  list: (locationId: UUID) =>
    api.get<PlanningPeriod[]>(`/api/v1/planning-periods?locationId=${locationId}`),
  create: (body: {
    locationId: UUID
    startDate: IsoDate
    endDate: IsoDate
    availabilityDeadline: IsoInstant
  }) => api.post<PlanningPeriod>('/api/v1/planning-periods', body),
  summary: (id: UUID) => api.get<PlanningPeriodSummary>(`/api/v1/planning-periods/${id}/summary`),
  changeDeadline: (id: UUID, availabilityDeadline: IsoInstant) =>
    api.put<PlanningPeriod>(`/api/v1/planning-periods/${id}/deadline`, { availabilityDeadline }),
  changeStatus: (id: UUID, status: PlanningPeriodStatus) =>
    api.put<PlanningPeriod>(`/api/v1/planning-periods/${id}/status`, { status }),

  myAvailability: (id: UUID) =>
    api.get<Availability[]>(`/api/v1/planning-periods/${id}/availability/me`),
  submitMyAvailability: (id: UUID, windows: AvailabilityWindowRequest[]) =>
    api.put<Availability[]>(`/api/v1/planning-periods/${id}/availability/me`, { windows }),
  allAvailability: (id: UUID) =>
    api.get<Availability[]>(`/api/v1/planning-periods/${id}/availability`),
  submitForEmployee: (id: UUID, employeeId: UUID, windows: AvailabilityWindowRequest[]) =>
    api.put<Availability[]>(
      `/api/v1/planning-periods/${id}/availability/employees/${employeeId}`,
      { windows },
    ),

  submitMyComment: (id: UUID, text: string) =>
    api.post<Comment>(`/api/v1/planning-periods/${id}/comments/me`, { text }),
  myComments: (id: UUID) => api.get<Comment[]>(`/api/v1/planning-periods/${id}/comments/me`),
  comments: (id: UUID) => api.get<Comment[]>(`/api/v1/planning-periods/${id}/comments`),
}

export const staffingApi = {
  list: (planningPeriodId: UUID) =>
    api.get<StaffingRequirement[]>(
      `/api/v1/planning-periods/${planningPeriodId}/staffing-requirements`,
    ),
  create: (planningPeriodId: UUID, body: CreateStaffingRequirementRequest) =>
    api.post<StaffingRequirement>(
      `/api/v1/planning-periods/${planningPeriodId}/staffing-requirements`,
      body,
    ),
  remove: (id: UUID) => api.del<void>(`/api/v1/staffing-requirements/${id}`),
  generateShifts: (planningPeriodId: UUID) =>
    api.post<GenerateShiftsResponse>(
      `/api/v1/planning-periods/${planningPeriodId}/shifts/generate`,
    ),
}

export const scheduleApi = {
  generate: (planningPeriodId: UUID) =>
    api.post<PlanningJob>(`/api/v1/planning-periods/${planningPeriodId}/generate`),
  job: (jobId: UUID) => api.get<PlanningJob>(`/api/v1/planning-jobs/${jobId}`),
  jobs: (planningPeriodId: UUID) =>
    api.get<PlanningJob[]>(`/api/v1/planning-periods/${planningPeriodId}/planning-jobs`),
  cancelJob: (jobId: UUID) => api.post<PlanningJob>(`/api/v1/planning-jobs/${jobId}/cancel`),

  proposals: (planningPeriodId: UUID) =>
    api.get<ScheduleSummary[]>(
      `/api/v1/planning-periods/${planningPeriodId}/schedule-proposals`,
    ),
  detail: (scheduleId: UUID) => api.get<ScheduleDetail>(`/api/v1/schedules/${scheduleId}`),
  select: (scheduleId: UUID) =>
    api.post<ScheduleSummary>(`/api/v1/schedules/${scheduleId}/select`),
  reassign: (scheduleId: UUID, assignmentId: UUID, employeeId: UUID | null) =>
    api.put<ReassignResponse>(
      `/api/v1/schedules/${scheduleId}/assignments/${assignmentId}`,
      { employeeId },
    ),
  pin: (scheduleId: UUID, assignmentId: UUID, pinned: boolean) =>
    api.put<Assignment>(
      `/api/v1/schedules/${scheduleId}/assignments/${assignmentId}/pin`,
      { pinned },
    ),
  validate: (scheduleId: UUID) =>
    api.get<ValidationResult>(`/api/v1/schedules/${scheduleId}/validation`),
  publish: (scheduleId: UUID) =>
    api.post<ScheduleSummary>(`/api/v1/schedules/${scheduleId}/publish`),
  mySchedule: (planningPeriodId: UUID) =>
    api.get<MySchedule>(`/api/v1/planning-periods/${planningPeriodId}/my-schedule`),
}

export const aiApi = {
  status: () => api.get<AiStatus>('/api/v1/ai/status'),
  interpretComments: (planningPeriodId: UUID) =>
    api.post<Interpretation[]>(
      `/api/v1/ai/planning-periods/${planningPeriodId}/interpret-comments`,
    ),
  replacements: (planningPeriodId: UUID, shiftId: UUID) =>
    api.get<ReplacementCandidate[]>(
      `/api/v1/ai/planning-periods/${planningPeriodId}/shifts/${shiftId}/replacements`,
    ),
  pendingInterpretations: () =>
    api.get<Interpretation[]>('/api/v1/comment-interpretations/pending'),
  reviewInterpretation: (id: UUID, accept: boolean) =>
    api.post<Interpretation>(`/api/v1/comment-interpretations/${id}/review`, { accept }),
}

export const chatApi = {
  ask: (question: string, planningPeriodId?: UUID) =>
    api.post<ChatResponse>('/api/v1/chat', {
      question,
      planningPeriodId: planningPeriodId ?? null,
    }),
}

export const auditApi = {
  recent: (limit = 50) => api.get<AuditEntry[]>(`/api/v1/audit?limit=${limit}`),
  forEntity: (entityType: string, entityId: UUID) =>
    api.get<AuditEntry[]>(
      `/api/v1/audit/entity?entityType=${encodeURIComponent(entityType)}&entityId=${entityId}`,
    ),
}
