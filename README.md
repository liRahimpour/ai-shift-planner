# AI Shift Planner

AI-assisted shift scheduling for gastronomy businesses (restaurants, cafés, bars,
bakeries, quick-service chains). Goal: cut the time to build a shift schedule from hours to
minutes while respecting availability, preferences, skills, contract hours, minimum
staffing, cost, fairness, overtime and rest time — across multiple locations and
departments.

**Architecture in one sentence:** a deterministic optimization engine (Java + [Timefold
Solver](https://timefold.ai)) makes every actual shift assignment; a local LLM (Spring AI +
[Ollama](https://ollama.com)) only interprets language, explains decisions, and answers
chat questions grounded in real data via tool calls. The LLM never assigns a shift, and the
solver never depends on the LLM being reachable. See [`docs/architecture.md`](docs/architecture.md)
and [`docs/adr/`](docs/adr/) for the reasoning behind every major technical decision.

## Project status

This repository is being built incrementally, phase by phase (see the task list in the
originating conversation / commit history). Each phase is expected to compile and pass its
tests before the next one starts. Current state:

| Phase | Feature | Status |
|---|---|---|
| 0 | Architecture docs + ADRs | ✅ done |
| 1 | Project skeleton (Spring Boot, Maven, Docker, Flyway, Actuator) | ✅ done |
| 2 | Core domain (Organization/Location/Department/User/Employee/Skill) | 🚧 in progress |
| 3–18 | Auth, availability, staffing, Timefold solver, chat, CI/CD, Kubernetes, Helm | ⏳ not started |

A full, kept-up-to-date status table (Feature / Status / Tests / Notes) will replace this
one at the end of Phase 18.

## Quick start (local development)

Requires Docker and Docker Compose. No local PostgreSQL or Ollama installation needed.

```bash
cp .env.example .env
docker compose up --build
```

This starts the backend (`:8080`), PostgreSQL (`:5432`) and Ollama (`:11434`). Add an
optional DB admin UI with:

```bash
docker compose --profile tools up
```

## Building & testing without Docker

```bash
./mvnw verify
```

Runs unit tests, Testcontainers-backed integration tests (needs a working Docker daemon for
Testcontainers itself), and produces a JaCoCo coverage report under
`target/site/jacoco/index.html`.

> **Note for anyone continuing this build in a constrained sandbox:** the environment this
> project was originally scaffolded in has no route to Maven Central or Docker Hub and no
> running Docker daemon, so `./mvnw verify` could not be executed there. Versions in `pom.xml`
> are the latest known-stable releases at authoring time but were not live-verified against
> the Maven repository — treat the first real `./mvnw verify` (locally or in CI) as the
> actual verification step, and bump any coordinate that 404s to the latest patch of the
> same minor line.

## Environment variables

See [`.env.example`](.env.example) for the full list. Key ones:

| Variable | Purpose |
|---|---|
| `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` | PostgreSQL connection |
| `JWT_SECRET` | Signing key for access/refresh tokens — **must** be overridden outside local dev |
| `OLLAMA_BASE_URL` / `OLLAMA_MODEL` | Local LLM endpoint and model name; the app degrades AI features to `AI_TEMPORARILY_UNAVAILABLE` if unreachable, core scheduling keeps working |
| `APP_DEFAULT_TIMEZONE` | Fallback IANA timezone; each location also stores its own |
| `SPRING_PROFILES_ACTIVE` | `dev` \| `prod` \| `test` |

## API

REST API under `/api/v1`. OpenAPI/Swagger UI is available at `/swagger-ui.html` when the
`dev` profile is active. No JPA entities are exposed directly — every endpoint uses DTOs.

## Security

Server-side RBAC (`EMPLOYEE`, `SHIFT_MANAGER`, `LOCATION_MANAGER`, `ORG_ADMIN`), JWT-based
stateless authentication, bcrypt password hashing, tenant isolation enforced in the backend,
audit logging of sensitive actions, and prompt-injection resistant AI tool-calling (employee
comments are treated as untrusted data, never as instructions — see ADR-003 and
`docs/architecture.md` §4).

## Known limitations (MVP)

Payroll, time tracking, POS integration, native mobile apps and enterprise SSO are
explicitly out of scope for the MVP; see the product brief's "Nicht-Ziele" section. A
frontend is not part of the current build — the backend is a complete, independently usable
REST API, and a React/TypeScript/Vite frontend is a natural next addition against it.

## Roadmap

Revenue/demand forecasting, shift swapping, push/email/WhatsApp notifications,
multi-location staff sharing, payroll/time-tracking integration and configurable
jurisdiction-specific labor-law rule sets are architecturally anticipated (see
`docs/architecture.md` and the ADRs) but not implemented in the MVP.
