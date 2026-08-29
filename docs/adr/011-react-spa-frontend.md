# ADR-011: A React/TypeScript/Vite single-page app, served same-origin by nginx

## Status
Accepted

## Context
The backend is a complete REST API, but several items in the definition of done are only
demonstrable through a user interface: employees entering availability and seeing their
published plan, the manager dashboard, comparing the three proposals, editing and publishing
a plan, and asking the chat a question. A UI was therefore not optional for the MVP.

Three shapes were considered:

1. **Server-rendered templates (Thymeleaf).** Fewer moving parts and no second build, but the
   schedule editor is a genuinely interactive screen — reassign, pin, re-validate, poll a
   running solver job — and doing that with full page reloads would be worse for the exact
   workflow the product is sold on.
2. **A separate SPA on its own origin.** The most common setup, but it requires CORS
   configuration that must stay in sync across dev, compose, and Kubernetes, and every
   mismatch shows up as an opaque browser error.
3. **A separate SPA served same-origin behind a proxy.** Chosen.

## Decision
Build a React 18 + TypeScript + Vite SPA under `frontend/`, and serve it from an nginx
container that proxies `/api` and `/actuator` to the backend Service. In development, Vite's
dev server proxies the same paths. In Kubernetes, the Ingress routes `/api` and `/actuator`
to the backend and everything else to the frontend Service.

Consequently **the browser only ever talks to one origin**, in every environment, and there
is no CORS configuration anywhere in the system.

Supporting decisions, and why:

- **TanStack Query for server state.** Nearly all state in this app *is* server state.
  Query gives caching, request de-duplication, invalidation after mutations, and the polling
  the async solver job needs, instead of a hand-rolled `useEffect` per screen. All query keys
  live in one module so that "something about this planning period changed" is a single
  invalidation call rather than a guess.
- **No component library.** The surface is small and the visual language is specific. A
  handful of primitives in `src/ui/primitives.tsx` plus CSS custom-property design tokens is
  less code than overriding a framework's defaults, and re-skinning for a customer's brand
  means editing one block of tokens.
- **Feature folders mirroring the backend modules.** `src/features/{auth, availability,
  staffing, dashboard, proposals, schedule, chat, employees, audit}` line up one-to-one with
  the backend's package-by-feature layout, so a change to one domain concept is a change in
  two directories with the same name.
- **One hand-written types module.** `src/api/types.ts` mirrors the backend DTOs and is the
  only description of the wire format; a backend change surfaces as a compile error rather
  than a runtime surprise. If the API outgrows hand-maintenance, generate it from
  `/v3/api-docs` into that same module — nothing outside `src/api` imports anything else.

## Consequences
- A second toolchain (Node) in the repo, a second image to build, publish and scan, and a
  second Dependabot ecosystem. CI runs frontend type checks, unit tests and a Docker build in
  its own job; the publish workflow builds and scans both images with the same tags, so one
  version number describes the whole system.
- Route guards in the SPA are a **usability** feature only. Every rule they express is
  enforced again by `@PreAuthorize` and tenant checks on the server — the frontend is never
  the security boundary, and the e2e suite asserts both halves.
- The chat is context-aware because period-scoped routes carry the id in the path
  (`/periods/:periodId/...`), which also makes links shareable and reloads land where the
  user was.
- Playwright e2e tests exist but are deliberately opt-in (`npm run e2e` against a running
  stack) rather than part of the default CI job, so a failing e2e run always means "the
  system is broken" and never "the environment was not up".
