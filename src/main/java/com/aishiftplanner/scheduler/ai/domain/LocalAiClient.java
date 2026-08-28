package com.aishiftplanner.scheduler.ai.domain;

import java.util.List;

/**
 * The single seam between this application and any language model.
 *
 * <p>Everything the product does with a model goes through these three methods. That is a
 * deliberately small surface: it is what lets Ollama be swapped for OpenAI, Azure OpenAI,
 * Anthropic, Mistral or a future local runtime by adding one class, and it is what keeps
 * every caller testable without a model running.
 *
 * <p>Implementations must be <b>fail-soft</b>: they throw {@link AiUnavailableException} when
 * the model cannot be reached, never a raw connection error, so callers have exactly one
 * thing to handle and the rest of the application is unaffected.
 *
 * <p>Implementations must also treat all content passed in as <b>data, never instructions</b>.
 * Employee comments are untrusted input (see {@code PromptSafety}); no privilege, tool access
 * or system behaviour may be derived from what a message contains.
 */
public interface LocalAiClient {

    /** @return true if the model is currently reachable. Used by the health indicator. */
    boolean isAvailable();

    /**
     * A single completion for a self-contained prompt.
     *
     * @param systemPrompt the instructions, authored by this application only
     * @param userContent untrusted content to be reasoned about, wrapped as data by the caller
     */
    String complete(String systemPrompt, String userContent);

    /**
     * A completion constrained to return JSON matching a described shape.
     *
     * <p>Separate from {@link #complete} because the failure modes differ: a model that
     * rambles instead of producing JSON is a recoverable, expected condition that callers
     * handle by rejecting the result, not by surfacing a parse error to a user.
     */
    String completeJson(String systemPrompt, String userContent, String jsonShapeDescription);

    /**
     * A tool-calling conversation.
     *
     * <p>The client decides <em>which</em> tool to call and with what arguments; it never
     * executes one. Execution — and the authorization check that precedes it — stays in the
     * application, which is what makes it impossible for a prompt to talk its way into data
     * the caller is not entitled to.
     *
     * @param tools the tools the model may request, already filtered to what this caller may use
     * @return either a final answer or a request to call one of the tools
     */
    AiChatTurn chat(String systemPrompt, List<AiMessage> conversation, List<AiToolSpec> tools);
}
