# ADR-001: Use Timefold Solver for shift assignment

## Status
Accepted

## Context
Shift assignment must satisfy a large set of hard rules (availability, no double-booking,
skills, rest time, minimum staffing) while optimizing soft goals (cost, fairness,
preferences). This is a textbook constraint-satisfaction / combinatorial optimization
problem, not something that should be solved with hand-written loops or an LLM.

## Decision
Use [Timefold Solver](https://timefold.ai) (the open-source successor to OptaPlanner) as
the deterministic optimization engine. Define `@PlanningEntity` (`ShiftAssignment`),
`@PlanningSolution` (`Schedule`), and constraints via the Constraint Streams API.

## Consequences
- Shift assignment is deterministic, explainable and unit-testable via
  `ConstraintVerifier`.
- No random assignment, no ad-hoc heuristics, no LLM-based assignment anywhere in the
  codebase.
- The team takes on a learning curve for Timefold's API; mitigated by keeping constraints
  in a single, well-tested module (`schedule.domain.constraints`).
