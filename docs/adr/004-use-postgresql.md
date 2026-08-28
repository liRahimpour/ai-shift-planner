# ADR-004: Use PostgreSQL with Flyway-managed schema

## Status
Accepted

## Context
The domain has strong relational structure (organizations, locations, departments,
employees, availabilities, shifts, assignments) with many foreign keys, uniqueness and
range constraints that are best enforced by the database itself, not only by application
code. Multi-tenancy and audit requirements also favor a mature relational engine with solid
transactional guarantees.

## Decision
Use PostgreSQL as the only supported production database. All schema changes are
versioned, forward-only Flyway migrations (`V1__initial_schema.sql`, `V2__availability.sql`,
...). `spring.jpa.hibernate.ddl-auto` is `validate` (never `update`/`create`) outside of
throwaway local experiments.

## Consequences
- Schema history is explicit, reviewable and reproducible across dev/CI/staging/prod.
- Kubernetes deployments need a safe migration strategy for concurrently starting pods
  (see the dedicated Flyway migration Job, section 64 of the product brief).
- Testcontainers (real PostgreSQL, not H2) are used in integration tests to catch
  Postgres-specific behavior early.
