package com.aishiftplanner.scheduler.chat.application;

import com.aishiftplanner.scheduler.ai.domain.AiToolSpec;
import com.aishiftplanner.scheduler.auth.application.AuthenticatedUser;
import java.util.Map;

/**
 * One backend capability the chat may use to answer a question.
 *
 * <p>The permission check happens before execution and never consults the model. In addition,
 * request context such as the planning period is passed separately as {@link ChatContext}; it
 * is resolved server-side and is therefore not something the model can replace with a foreign
 * id in its arguments.
 */
public interface ChatTool {

    /** Machine name the model uses to request this tool. Stable; part of the prompt contract. */
    String name();

    /** The model-facing description and parameter list. */
    AiToolSpec spec();

    /** Whether this caller may use this tool at all. */
    boolean isPermittedFor(AuthenticatedUser user);

    /**
     * Runs the tool and returns its result as JSON.
     *
     * <p>{@code context} is trusted server-side state. {@code arguments} are untrusted model
     * output and must be parsed and validated by the implementation.
     *
     * @return JSON that the model may phrase an answer from — never raw entities, never data
     *     the caller is not entitled to
     */
    String execute(AuthenticatedUser user, ChatContext context, Map<String, String> arguments);
}
