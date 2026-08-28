# Architecture — AI Shift Planner

## 1. Product summary

AI Shift Planner is a multi-tenant SaaS platform that generates gastronomy shift schedules
in minutes instead of hours, while respecting availability, preferences, skills, contract
hours, minimum staffing, cost, fairness, overtime, rest time, departments and locations.

The system is a **modular monolith** (see ADR-009) split into two clearly separated layers:

- a **deterministic scheduling engine** (Java + Timefold Solver) that owns every actual
  shift assignment, cost calculation and constraint decision, and
- a **local-AI layer** (Spring AI + Ollama) that only interprets language, explains
  decisions made by the solver, and answers chat questions using tool-called facts from
  the database.

The LLM never assigns a shift. The solver never depends on the LLM being available.

## 2. High-level component view

```mermaid
flowchart TB
    Browser["Browser (React SPA)"] -->|HTTPS / REST| API["Spring Boot API (/api/v1)"]

    subgraph Backend["ai-shift-planner backend (modular monolith)"]
        API --> Security["Spring Security<br/>RBAC + Tenant Isolation"]
        Security --> Modules["Feature modules:<br/>organization, employee, availability,<br/>staffing, planning, schedule, chat, audit"]
        Modules --> Solver["Deterministic Scheduling Engine<br/>(Timefold Solver)"]
        Modules --> AiFacade["AiService abstraction<br/>(CommentInterpretation, Chat, Explanation, PlanningInstruction)"]
        Modules --> AuditLog["Audit Logging"]
    end

    Modules --> DB[(PostgreSQL)]
    AiFacade -->|Spring AI| Ollama["Ollama<br/>(local LLM)"]
    AiFacade -. "AI_TEMPORARILY_UNAVAILABLE<br/>if Ollama is down" .-> Modules

    Solver --> DB
```

Key property: every arrow from `Modules`/`Solver` into `DB` continues to work even if the
`Ollama` box is completely gone. Only the `AiFacade` arrow is allowed to fail, and it fails
soft (a typed status), never by throwing the request away.

## 3. AI abstraction

```mermaid
flowchart LR
    subgraph ai["ai module"]
        LocalAiClient["LocalAiClient (interface)"]
        Ollama_Impl["OllamaAiClient"]
        Fake_Impl["DeterministicTestAiClient (test/dev fallback)"]
        LocalAiClient --> Ollama_Impl
        LocalAiClient --> Fake_Impl

        CommentInterpretationService --> LocalAiClient
        ScheduleChatService --> LocalAiClient
        ScheduleExplanationService --> LocalAiClient
        PlanningInstructionService --> LocalAiClient
    end
```

`LocalAiClient` is the single seam between business code and any model provider. Swapping
Ollama for OpenAI, Azure OpenAI, Anthropic or Mistral later means adding one new
implementation class and a Spring profile — no changes to callers.

## 4. Chat / tool-calling flow

```mermaid
sequenceDiagram
    participant User as Shift manager
    participant Chat as ScheduleChatService
    participant Authz as Authorization
    participant Tools as Backend tools (getScheduleForDate, ...)
    participant DB as PostgreSQL
    participant LLM as Ollama

    User->>Chat: "Who works Saturday evening at the bar?"
    Chat->>LLM: user question + tool definitions
    LLM-->>Chat: tool call: getScheduleForDate(date, department)
    Chat->>Authz: can this user call this tool with these args?
    Authz-->>Chat: allow / deny (server-side, same rules as REST API)
    Chat->>Tools: getScheduleForDate(...)
    Tools->>DB: query
    DB-->>Tools: rows
    Tools-->>Chat: structured JSON (source of truth)
    Chat->>LLM: tool result
    LLM-->>Chat: natural-language answer
    Chat-->>User: answer (grounded only in step above)
```

The LLM never receives a full database dump and never receives data the caller is not
authorized to see — authorization is checked **before** a tool executes, not by trusting
the prompt.

## 5. Deployment views

### 5.1 Local development

```mermaid
flowchart LR
    Dev["docker compose up --build"] --> BackendC["backend container"]
    Dev --> PgC["postgres container"]
    Dev --> OllamaC["ollama container"]
    Dev -.optional.-> FrontendC["frontend container"]
    Dev -.optional.-> AdminerC["adminer container"]
```

### 5.2 Kubernetes

```mermaid
flowchart TB
    Ingress --> Service --> Pods["Backend Pods (Deployment, HPA, PDB)"]
    Pods --> PgManaged[("PostgreSQL<br/>(managed or separate service)")]
    Pods --> OllamaSvc["Ollama Service"] --> OllamaPod["Ollama Pod<br/>(ConfigMap: OLLAMA_BASE_URL)"]
    MigrationJob["Flyway Migration Job (Helm hook)"] --> PgManaged
    Pods -. readiness/liveness/startup .-> Actuator["/actuator/health/*"]
```

Flyway migrations run in a dedicated Kubernetes Job (a Helm pre-upgrade hook), not inside
every application pod's startup path, to avoid multiple pods racing to migrate the schema
concurrently during a rolling deployment.

## 6. Domain hierarchy

```
Organization
  └── Location (own IANA timezone, e.g. Europe/Berlin)
        └── Department (data-driven, not hardcoded: Küche, Theke, Bar, ...)
              └── Employee (skills, contract hours, employment type)
```

Every tenant-scoped entity carries `organizationId`; tenant isolation is enforced in the
backend (repository/service layer), never only via the frontend.

## 7. Package structure (package-by-feature)

```
com.aishiftplanner.scheduler
├── auth/
├── organization/
├── employee/
├── availability/
├── staffing/
├── planning/
├── schedule/
├── ai/
├── chat/
├── audit/
└── shared/
```

Within larger modules, code is further split into `api` (controllers/DTOs), `application`
(use cases/services), `domain` (entities/domain logic) and `infrastructure` (repositories,
external clients) — only where that separation earns its keep. No interfaces are created
purely out of habit.

## 8. Non-goals for the MVP

Payroll, time tracking, POS integration, native mobile apps, enterprise SSO, demand
forecasting and event streaming are explicitly out of scope; the domain model and module
boundaries leave room for them (see `docs/adr` and section 92 of the product brief) without
requiring a rewrite.
