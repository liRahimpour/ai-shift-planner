package com.aishiftplanner.scheduler.chat.application;

import com.aishiftplanner.scheduler.ai.domain.AiToolSpec;
import com.aishiftplanner.scheduler.auth.application.AuthenticatedUser;
import java.util.Map;

/**
 * One backend capability the chat may use to answer a question.
 *
 * <p>The two-method shape is the whole security design: {@link #isPermittedFor} is asked
 * <em>before</em> {@link #execute} is called, and the model has no part in either. It cannot
 * see a tool it is not permitted to use, cannot invoke one it was not offered, and cannot
 * argue its way past the check, because the check never consults it.
 *
 * <p>Concretely: an employee asking "what does everyone earn?" does not get a refusal
 * negotiated in the prompt. The salary tool is never offered to them, and if a model invented
 * the call anyway it would be rejected before touching the database.
 */
public interface ChatTool {

    /** Machine name the model uses to request this tool. Stable; part of the prompt contract. */
    String name();

    /** The model-facing description and parameter list. */
    AiToolSpec spec();

    /**
     * Whether this caller may use this tool at all.
     *
     * <p>Evaluated per request against the same roles the REST API uses, so chat and API can
     * never disagree about who may see what.
     */
    boolean isPermittedFor(AuthenticatedUser user);

    /**
     * Runs the tool and returns its result as JSON.
     *
     * <p>Implementations must scope every query to {@code user}'s organization, and must treat
     * {@code arguments} as untrusted: they are model output, not validated input.
     *
     * @return JSON that the model may phrase an answer from — never raw entities, never data
     *     the caller is not entitled to
     */
    String execute(AuthenticatedUser user, Map<String, String> arguments);
}
