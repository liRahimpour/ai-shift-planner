# ADR-002: Use local Ollama as the default LLM provider

## Status
Accepted

## Context
The platform processes employee comments and schedule data, which is sensitive personal
and business data. Sending it to a third-party cloud LLM by default would conflict with
privacy-by-default and would add an external dependency and cost to every request.

## Decision
Run a local LLM via [Ollama](https://ollama.com) as the default provider, wired through
Spring AI. Configuration (`OLLAMA_BASE_URL`, `OLLAMA_MODEL`) is entirely via environment
variables; the model is never hardcoded in business code.

## Consequences
- No employee data leaves the deployment by default.
- Inference quality/speed depends on the operator's hardware; GPU support is documented
  but not required for the MVP.
- The `LocalAiClient` abstraction (ADR-003) keeps the door open to Azure OpenAI, OpenAI,
  Anthropic or Mistral for organizations that explicitly opt in later.
