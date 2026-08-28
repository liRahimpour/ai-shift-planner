# ADR-006: Target Kubernetes for production deployment

## Status
Accepted

## Context
The product must eventually run as a commercial SaaS with horizontal scalability, rolling
updates without downtime, and standard health/readiness semantics.

## Decision
Design the backend to be stateless (ADR from section 50 of the product brief) and target
Kubernetes as the production runtime, using standard primitives (Deployment, Service,
Ingress, HPA, PDB, ConfigMap, Secret references) rather than a custom orchestration layer.

## Consequences
- Requires health/readiness/liveness endpoints wired to Spring Boot Actuator from day one.
- Requires a safe database migration strategy under multiple concurrently starting pods
  (see ADR-004 consequences and the migration Job).
- No local persistent application files; all state lives in PostgreSQL (and, later, object
  storage for uploads if needed).
