# ADR-007: Package Kubernetes manifests as a Helm chart

## Status
Accepted

## Context
Raw Kubernetes YAML duplicates itself badly across dev/staging/prod and makes
per-environment configuration (replica counts, resource limits, ingress hosts, autoscaling)
error-prone to keep in sync.

## Decision
Ship a single Helm chart (`helm/ai-shift-planner`) with environment-specific value files
(`values-dev.yaml`, `values-prod.yaml`) instead of separate manifest trees. `helm lint`
(and `helm template`) run in CI to catch chart errors before merge.

## Consequences
- One chart, many environments; environment differences are data (values), not code.
- No production secrets are stored in `values.yaml`; the chart references an
  `existingSecret` created out-of-band.
