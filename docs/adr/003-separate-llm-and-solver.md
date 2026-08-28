# ADR-003: Hard separation between the LLM layer and the scheduling engine

## Status
Accepted

## Context
An LLM is good at language, bad at guaranteeing hard constraints (legal rest times,
availability, no double-booking) and impossible to unit-test exhaustively. If the LLM could
assign shifts, the system's core guarantees would depend on model behavior that can change
between versions/prompts.

## Decision
The LLM layer (`ai` module) may only:
- extract structured data from free text (comments),
- answer questions using backend tool calls that hit the real database,
- explain decisions using facts supplied by the solver/DB,
- summarize/translate.

The LLM layer may never:
- write a `ShiftAssignment`,
- change a `PlanningPeriod` status,
- bypass authorization,
- be the source of truth for any number shown to a user.

All AI-facing services depend only on the `LocalAiClient` interface, never a concrete LLM
implementation, and never on Timefold internals.

## Consequences
- Core business logic (`planning`, `schedule`, `staffing`, `availability`) has zero compile
  or runtime dependency on `ai`/Ollama and keeps working if Ollama is unreachable
  (`AI_TEMPORARILY_UNAVAILABLE`).
- Every AI-derived fact used in a chat answer or explanation is traceable to a concrete
  tool call result, not to model "knowledge".
