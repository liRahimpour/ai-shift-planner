# ADR-008: Use Docker Hub as the initial container registry

## Status
Accepted

## Context
The team needs a container registry reachable both from CI and from arbitrary Kubernetes
clusters, without standing up private registry infrastructure for the MVP.

## Decision
Publish backend images to Docker Hub (`docker.io/<dockerhub-user>/ai-shift-planner`) from
GitHub Actions, authenticated with a Docker Hub **access token** (never a password) stored
as the `DOCKERHUB_USERNAME`/`DOCKERHUB_TOKEN` GitHub secrets. Images are only pushed after
`mvn verify` and the Docker build succeed. Tags: `main`, `sha-<commit>` for development
builds; `v<semver>`, `<semver>`, `latest` for tagged releases.

## Consequences
- Zero extra infrastructure to publish images for the MVP.
- Migrating to a private registry (GHCR, ECR, GCR, ACR) later only touches the
  `docker-publish.yml` workflow and `image.repository` Helm value — not the application.
