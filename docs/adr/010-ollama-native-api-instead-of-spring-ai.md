# ADR-010: Talk to Ollama over its native HTTP API rather than through Spring AI

## Status
Accepted — but flagged as the most reversible decision in this repository.

## Context
The product brief specifies Spring AI as the integration layer to Ollama. Spring AI is a
reasonable choice and this ADR is not an argument against it. Two things pushed the MVP the
other way:

1. **Tool calling is the security-critical path.** The chat feature's correctness depends on
   exactly which tool the model requested, with which arguments, and on the application —
   never the model — deciding whether that call is permitted. Owning the request/response
   contract end to end makes that boundary auditable in one file. Going through an
   abstraction whose tool-calling API is still settling would put a layer of someone else's
   assumptions between "the model asked for X" and "we checked whether X is allowed".

2. **Ollama's `/api/chat` is small, stable and documented.** Roughly a hundred lines of
   `RestClient` code covers everything this product needs: a system prompt, a message list,
   a `tools` array, and a `message.tool_calls` response. That is less code than the
   configuration required to constrain a general-purpose abstraction to the same behaviour.

## Decision
Implement `LocalAiClient` directly against Ollama's HTTP API using Spring's `RestClient`.
Keep `LocalAiClient` as the only seam the rest of the application knows about.

## Consequences
- One fewer dependency whose release cadence and API stability the build has to track.
- The application still satisfies the architectural requirement that matters — a local-first,
  provider-swappable AI layer that core scheduling never depends on. Swapping in a Spring AI
  implementation is one new class implementing `LocalAiClient` plus a Spring profile; no
  caller changes.
- Provider-specific conveniences Spring AI offers (embeddings, vector stores, observability
  integrations) are not available for free. None are needed for the MVP; if a future feature
  needs them, that is the moment to add a Spring AI-backed `LocalAiClient` rather than to
  hand-roll more.
- **If the team prefers the brief's original stack**, the change is contained: add
  `spring-ai-starter-model-ollama`, write `SpringAiLocalAiClient implements LocalAiClient`,
  and select it with a profile. `OllamaLocalAiClient` can then be deleted or kept as a
  fallback.
