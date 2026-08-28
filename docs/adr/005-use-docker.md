# ADR-005: Containerize with Docker (multi-stage builds)

## Status
Accepted

## Context
The application must run identically for developers, in CI, and in production, and must be
deployable to Kubernetes without a separate packaging step.

## Decision
Provide a multi-stage `Dockerfile` (Maven+JDK build stage → minimal JRE runtime stage,
non-root user, no build tools or Maven cache in the final image) plus a `Dockerfile.dev`
for fast local iteration, and a `docker-compose.yml` that runs backend + PostgreSQL +
Ollama with a single `docker compose up --build`.

## Consequences
- No local PostgreSQL/Ollama installation is required to develop the app.
- The same image built in CI is what gets deployed via Helm/Kubernetes — no "works on my
  machine" drift between environments.
