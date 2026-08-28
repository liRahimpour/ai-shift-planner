# AI Shift Planner

AI-assisted shift scheduling for gastronomy — restaurants, cafés, bars, bakeries,
quick-service chains. The goal is to cut schedule creation from hours to minutes while
respecting availability, preferences, skills, contract hours, minimum staffing, cost,
fairness, overtime and rest time, across multiple locations and departments.

**The architecture in one sentence:** a deterministic optimization engine (Java +
[Timefold Solver](https://timefold.ai)) makes every actual shift assignment; a local LLM
([Ollama](https://ollama.com)) only interprets language, explains decisions, and answers
questions grounded in tool calls against the real database. The LLM never assigns a shift,
and the solver never depends on the LLM being reachable.

See [`docs/architecture.md`](docs/architecture.md) for diagrams and
[`docs/adr/`](docs/adr/) for the reasoning behind each major decision.

---

## ⚠️ Read this first: what has and has not been verified

This codebase was written in a sandbox with **no network route to Maven Central, no Docker
daemon, and no Kubernetes cluster.** That means:

**Not one line of this has been compiled, and not one test has been run.**

Everything below describes code that is written, internally consistent, and designed with
care — but the first real `./mvnw verify` is the first genuine verification this project has
had. Expect to fix things. The most likely categories, in rough order of probability:

1. **Dependency coordinates and versions** in `pom.xml`. They are the latest stable releases
   known at authoring time but were never resolved against the repository. If one 404s, bump
   it to the latest patch of the same minor line.
2. **Timefold Constraint Streams API details.** The constraint provider uses
   `forEachIncludingUnassigned`, `flattenLast`, `ConstraintCollectors.conditionally` and
   `penalizeLong`. These exist in Timefold 1.x, but exact signatures shift between versions;
   the *logic* is what matters and the `ConstraintVerifier` tests pin it precisely.
3. **Imports and small compile errors** across ~90 source files nobody has fed to a compiler.

What *is* solid: the domain model, the constraint semantics, the security boundaries, the
transaction and concurrency design, and the reasoning captured in the ADRs. Those were the
expensive parts to get right, and they do not depend on a compiler to be correct.

**Recommended first move:**

```bash
./mvnw -B clean verify
```

Then work through what it says. Send me the output and I will fix it.

---

## Quick start

Requires Docker and Docker Compose. No local PostgreSQL, Java or Ollama installation needed.

```bash
cp .env.example .env
docker compose up --build
```

Starts the backend (`:8080`), PostgreSQL (`:5432`) and Ollama (`:11434`).

To populate the demo dataset — one Mainz restaurant, three departments, 34 staff, a planning
period with staffing requirements, availability and comments — set `SEED_DEMO_DATA=true` in
`.env` before the first start.

| Demo login | Password | Roles |
|---|---|---|
| `admin@demo.local` | `demo1234` | `ORG_ADMIN` |
| `manager@demo.local` | `demo1234` | `SHIFT_MANAGER`, `LOCATION_MANAGER` |
| `employee@demo.local` | `demo1234` | `EMPLOYEE` (linked to Anna Becker) |

Pull a model into Ollama once, if you want the AI features:

```bash
docker compose exec ollama ollama pull llama3.1
```

Everything except comment interpretation and chat works without it.

Optional database admin UI at `:8081`:

```bash
docker compose --profile tools up
```

## Building and testing

```bash
./mvnw verify
```

Runs unit tests, Testcontainers-backed integration tests (needs a working Docker daemon) and
produces a JaCoCo report at `target/site/jacoco/index.html`.

CI never needs a running LLM: `FakeLocalAiClient` scripts the interesting cases — a model
demanding a forbidden tool, returning malformed JSON, looping, or being down — as fast
deterministic tests.

## Environment variables

Full list in [`.env.example`](.env.example). The ones that matter:

| Variable | Purpose |
|---|---|
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | PostgreSQL connection |
| `JWT_SECRET` | Token signing key, ≥32 bytes. The app refuses to start with a shorter one |
| `OLLAMA_BASE_URL`, `OLLAMA_MODEL` | Local model endpoint and name |
| `AI_ENABLED` | `false` disables AI features cleanly; everything else keeps working |
| `SCHEDULING_MIN_REST_HOURS` | Minimum rest between shifts. Default 11 (German ArbZG) — **configuration, not a constant**, because this differs by jurisdiction |
| `SCHEDULING_MAX_HOURS_PER_DAY` | Daily working-time ceiling. Default 10 |
| `SCHEDULING_SOLVER_SECONDS` | Time budget per strategy per planning run |
| `SEED_DEMO_DATA` | `true` seeds the demo dataset once |
| `SPRING_PROFILES_ACTIVE` | `dev` \| `prod` \| `test`, plus optional `json-logs` |

## API

REST under `/api/v1`, DTOs only — no JPA entity is ever exposed. Swagger UI at
`/swagger-ui.html` with the `dev` profile.

```
/api/v1/auth                    login, refresh, me
/api/v1/locations               locations and their departments
/api/v1/departments
/api/v1/skills                  extensible qualification catalogue
/api/v1/employees
/api/v1/planning-periods        periods, deadlines, availability, comments
/api/v1/staffing-requirements
/api/v1/schedule-proposals      the three options with their metrics
/api/v1/schedules               selection, manual edits, pinning, publishing
/api/v1/planning-jobs           async solver runs
/api/v1/chat                    grounded question answering
/api/v1/ai                      comment interpretation, replacements, AI status
/api/v1/audit                   append-only trail
```

Errors are uniform:

```json
{
  "code": "AVAILABILITY_DEADLINE_PASSED",
  "message": "The availability deadline for this planning period has passed.",
  "timestamp": "2026-08-28T16:15:30Z",
  "traceId": "a1b2c3d4-…"
}
```

The `traceId` appears on every server log line for that request.

## The demo walkthrough

1. Employee logs in, enters availability, writes *"Samstag bitte erst ab 17 Uhr."*
2. Manager opens the dashboard: **31 of 34 submitted, 3 missing, deadline Wednesday 18:00**.
3. Manager clicks **generate**. Three proposals appear: Fair, Cost-optimized, Balanced.
4. Manager compares: staff cost, wish satisfaction, contract-hours deviation, unfilled
   positions, overtime, fairness score.
5. Manager selects Balanced, asks in chat *"Wer arbeitet Samstagabend an der Bar?"*
6. Asks *"Warum arbeitet Anna Sonntag?"* — the answer comes from solver and database facts,
   phrased by the model, never invented by it.
7. Max calls in sick. The replacement assistant returns ranked candidates with the reasons.
8. Manager publishes. Employees see their shifts.

## Security

Server-side RBAC (`EMPLOYEE`, `SHIFT_MANAGER`, `LOCATION_MANAGER`, `ORG_ADMIN`), stateless
JWT auth, bcrypt hashing, tenant isolation enforced structurally in the repository layer
(`findByIdAndOrganizationId`, never `findById`), audit logging, and prompt-injection-resistant
chat.

Two things worth calling out because they are easy to get wrong:

**Chat authorization does not go through the prompt.** Forbidden tools are never offered to
the model, and are refused again before execution. A model fully persuaded by a malicious
comment still cannot reach data its caller is not entitled to, because the permission check
never consults it. There are tests for exactly this.

**Login failures are indistinguishable.** Unknown email, wrong password and deactivated
account produce the same message, and a constant-work password comparison runs even for
unknown accounts, so neither the wording nor the timing reveals which emails have accounts.

## Feature status

Legend: 🟢 implemented · 🟡 partially implemented · ⚪ not implemented (extension point only)

**Every "Tests" entry below describes tests that are written, not tests that have passed.**
Nothing in this repository has been executed.

| Feature | Status | Tests written | Notes |
|---|---|---|---|
| Multi-tenancy, org → location → department → employee | 🟢 | Integration | Tenant id on every scoped row; repositories take it in every finder |
| Skills as extensible data | 🟢 | — | Per-org catalogue, seeded not hardcoded |
| Employees, contracts, hour limits | 🟢 | Unit | Limits are data, not derived from employment type |
| JWT auth, RBAC | 🟢 | 13 unit | Token-type separation, forgery/tamper tests, enumeration resistance |
| Availability, multiple windows/day | 🟢 | 5 unit | Full containment, not overlap; contradictions rejected at submission |
| Deadlines, lock, manager reopen | 🟢 | 7 unit | Boundary and DST cases covered |
| Employee comments | 🟢 | — | Original text kept verbatim |
| Staffing requirements + skill counts | 🟢 | — | Unsatisfiable skill demands rejected at definition time |
| Shift generation | 🟢 | — | Replaces wholesale; pins preserve manual work |
| Timefold hard constraints | 🟢 | 24 ConstraintVerifier | Exact penalty amounts, midnight-crossing cases |
| Timefold soft constraints | 🟢 | Partial | Weights differ per strategy; behaviour not yet pinned by tests |
| Three strategies | 🟢 | — | One constraint provider, three weight profiles |
| Comparison metrics | 🟢 | — | Derived from the solved plan, not from the score |
| Async planning jobs, idempotency | 🟢 | — | DB partial unique index arbitrates the double-click race |
| Manual editing + re-validation | 🟢 | — | Validates the whole schedule; reports, does not block |
| Pinning | 🟢 | — | Survives regeneration; stale pins dropped safely |
| Publishing | 🟢 | — | Refused while any hard rule is violated |
| Employee schedule view | 🟢 | — | Published plan only |
| LocalAiClient abstraction + fail-soft | 🟢 | 5 unit | `AI_TEMPORARILY_UNAVAILABLE`, never a raw error |
| Comment interpretation | 🟢 | 6 unit | Unparseable fields dropped; hard constraints always reviewed |
| Chat with tool calling | 🟢 | 9 unit | Privilege separation, bounded loop, tool failure, outage |
| Prompt-injection defence | 🟢 | 5 unit | Fencing + the real defence: backend authorization |
| Explainable assignments | 🟢 | — | Evidence from DB/solver; the model only phrases it |
| Replacement assistant | 🟢 | — | Deterministic ranking; works with the AI down |
| Audit logging | 🟢 | — | Append-only, `REQUIRES_NEW`, never fails a request |
| Observability, probes, graceful shutdown | 🟢 | 3 integration | AI health deliberately excluded from readiness |
| Docker, compose, multi-stage, non-root | 🟢 | Built in CI | Never built locally here |
| GitHub Actions CI | 🟢 | — | Unit → integration → verify → Docker build → helm lint |
| Docker Hub publishing | 🟢 | — | Re-runs tests, Trivy-scans before push |
| Kubernetes + Helm | 🟢 | `helm lint`/`template` in CI | Never linted locally here |
| Flyway migration Job | 🟢 | — | Pre-upgrade hook, application's own image |
| Seed data, demo accounts | 🟢 | — | 34 staff, 31 submissions, deliberately awkward |
| **Frontend** | ⚪ | — | Not built. Backend is a complete REST API |
| **End-to-end tests (Playwright)** | ⚪ | — | Requires a frontend |
| **Soft-constraint behaviour tests** | 🟡 | — | Hard constraints tested thoroughly; soft ones are the gap |
| Payroll, time tracking, POS, SSO, forecasting | ⚪ | — | Explicit non-goals; extension points only |

### The honest summary

Roughly **35 of 41** deliverables are implemented in code. Two are genuinely absent (frontend,
E2E tests) and one is thin (soft-constraint tests). And all of it is unverified — see the
warning at the top.

## Known limitations

- **Nothing has been compiled or run.** The single most important thing to know.
- **No frontend.** The backend is complete and self-contained; a React/TypeScript/Vite client
  against this API is the natural next step.
- **JWTs cannot be revoked before expiry.** Bounded by 30-minute access tokens; the refresh
  path re-reads the user so a deactivation takes effect at the next refresh.
- **The validator duplicates the solver's rules.** A deliberate trade-off — re-solving to
  validate one drag-and-drop would take tens of seconds. Both read the same configuration,
  and drift is the risk to watch.
- **Ollama is not used with Spring AI.** See [ADR-010](docs/adr/010-ollama-native-api-instead-of-spring-ai.md)
  for why, and how to reverse it if you prefer the original stack.
- **Solver quality is untuned.** The constraint weights are considered starting values, not
  the result of running them against real rotas.

## Roadmap

Demand forecasting (revenue, weather, reservations, events), POS and time-tracking
integration, shift swapping, push/email/WhatsApp notifications, multi-location staff sharing,
and configurable jurisdiction-specific labour-law rule sets. The domain model and module
boundaries anticipate these; none are implemented.

## Deployment

```bash
# Local
cp .env.example .env && docker compose up --build

# Image
docker pull <your-dockerhub-user>/ai-shift-planner:v1.0.0

# Kubernetes
kubectl create secret generic ai-shift-planner-secrets \
  --from-literal=DB_USERNAME='...' \
  --from-literal=DB_PASSWORD='...' \
  --from-literal=JWT_SECRET="$(openssl rand -base64 48)"

helm upgrade --install ai-shift-planner ./helm/ai-shift-planner \
  --values ./helm/ai-shift-planner/values-prod.yaml \
  --set image.repository=docker.io/<your-dockerhub-user>/ai-shift-planner \
  --set image.tag=v1.0.0
```

Ollama deploys separately — see [`helm/ollama/README.md`](helm/ollama/README.md).

Required GitHub secrets for the pipelines: `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN` (an access
token, never a password), and per-environment `KUBE_CONFIG` for the manual deploy workflow.
