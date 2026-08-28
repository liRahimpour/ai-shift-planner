package com.aishiftplanner.scheduler.ai.domain;

import java.util.Map;

/**
 * What the model produced on one turn: either a final answer, or a request to call a tool.
 *
 * <p>Modelled as a closed pair of possibilities rather than a free-form string so the calling
 * loop cannot accidentally treat a tool request as an answer, or vice versa.
 *
 * @param answer set when the model is done; null when it wants a tool
 * @param toolName set when the model wants a tool; null when it is done
 * @param toolArguments arguments the model supplied — <b>untrusted</b>, to be validated by the
 *     tool's own parameter checking before use
 */
public record AiChatTurn(String answer, String toolName, Map<String, String> toolArguments) {

    public static AiChatTurn answer(String text) {
        return new AiChatTurn(text, null, Map.of());
    }

    public static AiChatTurn callTool(String toolName, Map<String, String> arguments) {
        return new AiChatTurn(null, toolName, arguments == null ? Map.of() : Map.copyOf(arguments));
    }

    public boolean wantsTool() {
        return toolName != null && !toolName.isBlank();
    }
}
