# ADR-009: Build a modular monolith, not microservices

## Status
Accepted

## Context
"Cloud-native" is often conflated with microservices. For an MVP with a small team,
distributed systems (Kafka/RabbitMQ, a service mesh, Kubernetes operators, per-feature
services) would add operational and cognitive overhead without a concrete need — network
partitions, distributed transactions and cross-service versioning would slow the team down
without a proven scaling bottleneck to justify them.

## Decision
Build one deployable Spring Boot application, organized by package-by-feature
(`auth`, `organization`, `employee`, `availability`, `staffing`, `planning`, `schedule`,
`ai`, `chat`, `audit`, `shared`), each internally layered into `api`/`application`/`domain`/
`infrastructure` where that separation earns its keep. The backend remains stateless and
horizontally scalable (multiple pods behind one Service), which gives most of the practical
benefit people reach for microservices for, without the distributed-systems tax.

## Consequences
- Faster iteration, simpler local development (`docker compose up --build`), simpler CI.
- Module boundaries are enforced by code review and package structure, not by network
  boundaries — this requires discipline (no reaching into another feature's `domain`
  package from outside its `api`).
- Splitting out a service later (e.g. the solver, if its resource profile diverges sharply
  from the rest of the app) remains possible because modules already don't share internal
  state, only published DTOs/interfaces.
