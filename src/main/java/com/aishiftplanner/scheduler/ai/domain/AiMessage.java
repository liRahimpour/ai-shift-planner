package com.aishiftplanner.scheduler.ai.domain;

/**
 * One turn in a chat conversation.
 *
 * @param role who produced it
 * @param content the text, or a tool result serialized as JSON
 * @param toolName set on {@link Role#TOOL} messages: which tool produced this result
 */
public record AiMessage(Role role, String content, String toolName) {

    public enum Role {
        USER,
        ASSISTANT,
        /** The output of a backend tool, fed back so the model can phrase an answer from it. */
        TOOL
    }

    public static AiMessage user(String content) {
        return new AiMessage(Role.USER, content, null);
    }

    public static AiMessage assistant(String content) {
        return new AiMessage(Role.ASSISTANT, content, null);
    }

    public static AiMessage toolResult(String toolName, String jsonResult) {
        return new AiMessage(Role.TOOL, jsonResult, toolName);
    }
}
